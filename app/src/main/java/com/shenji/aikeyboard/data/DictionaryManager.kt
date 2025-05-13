package com.shenji.aikeyboard.data

import com.shenji.aikeyboard.ShenjiApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.Collections
import kotlin.math.min
import kotlin.system.measureTimeMillis

/**
 * 词典管理器类，负责管理内存词库和持久化词库
 */
class DictionaryManager private constructor() {
    
    companion object {
        // 单例实例
        val instance: DictionaryManager by lazy { DictionaryManager() }
        
        // 高频词库类型列表 - 移除chars，只保留base
        val HIGH_FREQUENCY_DICT_TYPES = listOf("base")
        
        // 需要加载到Trie树的词典类型
        val TRIE_DICT_TYPES = listOf(
            "base",          // 基础词库，加载高频词
            "correlation",   // 关联词库，加载高频词
            "people",        // 人名表，全部加载
            "corrections",   // 错音错字，全部加载
            "compatible"     // 兼容词库，全部加载
        )
        
        // Trie树插入批次大小
        private const val TREE_INSERT_BATCH_SIZE = 2000
        
        // 最大并行批次数
        private const val MAX_PARALLEL_BATCHES = 8  // 增加并行度
        
        // 词典版本信息文件名
        private const val DICTIONARY_VERSION_FILENAME = "dictionary_versions.bin"
        
        // 初始化函数
        fun init() {
            instance.initialize()
        }
    }
    
    // 词典仓库实例
    private val repository = DictionaryRepository()
    
    // 高频词库Trie树
    private var _trieTree = TrieTree()
    
    // 获取Trie树的公共访问器
    val trieTree: TrieTree
        get() = _trieTree
    
    // 各类型词库的加载总词条数记录
    private val typeEntryCount = mutableMapOf<String, Int>()
    
    // 各类型词库的已加载词条数记录
    private val typeLoadedCount = mutableMapOf<String, Int>()
    
    // 各类型词库的已加载词条数记录公开访问器
    val typeLoadedCountMap: Map<String, Int>
        get() = typeLoadedCount.toMap()
    
    // 各类型词库的版本哈希值（用于检测更新）
    private val typeVersionHash = mutableMapOf<String, String>()
    
    // 日志记录
    private val loadingLogs = ConcurrentHashMap<Long, String>()
    private var logId: Long = 0
    
    // Trie树插入锁，确保并发安全
    private val treeInsertLock = Mutex()
    
    // 是否已加载标志
    private var initialized = false
    
    // 加载标志，防止重复加载
    @Volatile
    private var isLoadingInProgress = false
    
    /**
     * 初始化词典管理器
     */
    fun initialize() {
        // 仅设置初始化标志
        initialized = true
        
        // 在后台线程中加载词典到Trie树
        Thread {
            try {
                Timber.d("开始加载词典到Trie树")
                loadDictionariesToTrie()
                Timber.d("词典加载到Trie树完成")
            } catch (e: Exception) {
                Timber.e(e, "加载词典到Trie树失败: ${e.message}")
                addLog("加载词典到Trie树失败: ${e.message}")
            }
        }.start()
        
        Timber.d("词典管理器初始化完成")
    }
    
    /**
     * 将词典按条件加载到Trie树
     * 实现新的加载策略:
     * - base词库：加载词频前20%的高频词
     * - correlation词库：加载词频前5-8万高频词
     * - people、corrections、compatible词库：全部加载
     */
    private fun loadDictionariesToTrie() {
        // 如果已经有加载任务在进行，直接返回避免重复加载
        if (isLoadingInProgress) {
            Timber.d("词典加载已在进行中，忽略重复请求")
            return
        }
        
        isLoadingInProgress = true
        
        try {
            addLog("开始按条件加载词典到Trie树...")
            
            // 记录开始时间
            val startTime = System.currentTimeMillis()
            
            // 清空当前Trie树，确保释放内存
            _trieTree.clear()
            System.gc()
            
            // 记录各词典类型的总词条数
            for (type in TRIE_DICT_TYPES) {
                val count = repository.getEntryCountByType(type)
                typeEntryCount[type] = count
                addLog(type + "词库共有" + count + "个词条")
            }
            
            // 批量加载词条
            val batchSize = 1000  // 减小批次大小，降低内存压力
            var totalLoadedCount = 0
            
            // 启动后台线程加载词典，防止阻塞UI线程
            Thread {
                try {
                    // 1. 加载base词库(词频前20%，但使用批量处理)
                    val baseCount = typeEntryCount["base"] ?: 0
                    if (baseCount > 0) {
                        val loadCount = (baseCount * 0.2).toInt() // 加载前20%
                        addLog("加载base词库的前${loadCount}个高频词(总共${baseCount}个)")
                        
                        // 分批加载以降低内存压力
                        var loadedCount = 0
                        while (loadedCount < loadCount) {
                            // 检查可用内存，如果过低则主动GC
                            checkMemory()
                            
                            val currentBatchSize = minOf(batchSize, loadCount - loadedCount)
                            val baseEntries = repository.getEntriesByTypeOrderedByFrequency("base", loadedCount, currentBatchSize)
                            
                            // 逐条添加词条到Trie树
                            baseEntries.forEach { entry ->
                                val normalizedPinyin = normalizePinyin(entry.pinyin)
                                _trieTree.insert(normalizedPinyin, entry.frequency, entry.word)
                            }
                            
                            loadedCount += baseEntries.size
                            
                            // 更新已加载计数和日志
                            if (loadedCount % 10000 == 0 || loadedCount == loadCount) {
                                val progress = (loadedCount * 100) / loadCount
                                addLog("已加载${loadedCount}/${loadCount}个base词条 (${progress}%)")
                            }
                            
                            // 短暂睡眠，让出CPU时间给其他线程
                            Thread.sleep(5)
                        }
                        
                        // 更新已加载计数
                        typeLoadedCount["base"] = loadedCount
                        totalLoadedCount += loadedCount
                        addLog("base词库加载完成，共加载${loadedCount}个词条到Trie树")
                    }
                    
                    // 2. 加载correlation词库(词频前5万，也使用批量处理)
                    val correlationCount = typeEntryCount["correlation"] ?: 0
                    if (correlationCount > 0) {
                        val loadCount = minOf(50000, correlationCount) // 减少为5万个
                        addLog("加载correlation词库的前${loadCount}个高频词(总共${correlationCount}个)")
                        
                        // 分批加载
                        var loadedCount = 0
                        while (loadedCount < loadCount) {
                            // 检查可用内存，如果过低则主动GC
                            checkMemory()
                            
                            val currentBatchSize = minOf(batchSize, loadCount - loadedCount)
                            val correlationEntries = repository.getEntriesByTypeOrderedByFrequency("correlation", loadedCount, currentBatchSize)
                            
                            correlationEntries.forEach { entry ->
                                val normalizedPinyin = normalizePinyin(entry.pinyin)
                                _trieTree.insert(normalizedPinyin, entry.frequency, entry.word)
                            }
                            
                            loadedCount += correlationEntries.size
                            
                            // 更新已加载计数和日志
                            if (loadedCount % 10000 == 0 || loadedCount == loadCount) {
                                val progress = (loadedCount * 100) / loadCount
                                addLog("已加载${loadedCount}/${loadCount}个correlation词条 (${progress}%)")
                            }
                            
                            // 短暂睡眠，让出CPU时间
                            Thread.sleep(5)
                        }
                        
                        // 更新已加载计数
                        typeLoadedCount["correlation"] = loadedCount
                        totalLoadedCount += loadedCount
                        addLog("correlation词库加载完成，共加载${loadedCount}个词条到Trie树")
                    }
                    
                    // 3. 加载小型词库 - corrections(全部)
                    val correctionsCount = typeEntryCount["corrections"] ?: 0
                    if (correctionsCount > 0) {
                        addLog("加载corrections词库的全部${correctionsCount}个词条")
                        
                        val correctionsEntries = repository.getEntriesByType("corrections", 0, correctionsCount)
                        correctionsEntries.forEach { entry ->
                            val normalizedPinyin = normalizePinyin(entry.pinyin)
                            _trieTree.insert(normalizedPinyin, entry.frequency, entry.word)
                        }
                        
                        // 更新已加载计数
                        typeLoadedCount["corrections"] = correctionsEntries.size
                        totalLoadedCount += correctionsEntries.size
                        addLog("corrections词库加载完成，共加载${correctionsEntries.size}个词条到Trie树")
                    }
                    
                    // 4. 加载compatible词库(全部)
                    val compatibleCount = typeEntryCount["compatible"] ?: 0
                    if (compatibleCount > 0) {
                        addLog("加载compatible词库的全部${compatibleCount}个词条")
                        
                        var loadedCount = 0
                        while (loadedCount < compatibleCount) {
                            // 检查可用内存
                            checkMemory()
                            
                            val currentBatchSize = minOf(batchSize, compatibleCount - loadedCount)
                            val compatibleEntries = repository.getEntriesByType("compatible", loadedCount, currentBatchSize)
                            
                            compatibleEntries.forEach { entry ->
                                val normalizedPinyin = normalizePinyin(entry.pinyin)
                                _trieTree.insert(normalizedPinyin, entry.frequency, entry.word)
                            }
                            
                            loadedCount += compatibleEntries.size
                        }
                        
                        // 更新已加载计数
                        typeLoadedCount["compatible"] = loadedCount
                        totalLoadedCount += loadedCount
                        addLog("compatible词库加载完成，共加载${loadedCount}个词条到Trie树")
                    }
                    
                    // 5. people词库暂时不加载，太大了，可能会影响性能
                    val peopleCount = typeEntryCount["people"] ?: 0
                    if (peopleCount > 0) {
                        addLog("people词库暂时不全部加载，仅加载前5000个高频词条")
                        
                        val maxToLoad = minOf(5000, peopleCount)
                        val peopleEntries = repository.getEntriesByTypeOrderedByFrequency("people", 0, maxToLoad)
                        
                        peopleEntries.forEach { entry ->
                            val normalizedPinyin = normalizePinyin(entry.pinyin)
                            _trieTree.insert(normalizedPinyin, entry.frequency, entry.word)
                        }
                        
                        // 更新已加载计数
                        typeLoadedCount["people"] = peopleEntries.size
                        totalLoadedCount += peopleEntries.size
                        addLog("people词库加载完成，共加载${peopleEntries.size}个高频词条到Trie树")
                    }
                    
                    // 设置已加载标志
                    _trieTree.setLoaded(true)
                    
                    // 记录加载完成时间和内存使用
                    val endTime = System.currentTimeMillis()
                    val loadTime = endTime - startTime
                    val memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                    
                    // 记录加载完成信息
                    addLog("词典加载到Trie树完成!")
                    addLog("加载耗时: ${loadTime/1000}秒")
                    addLog("内存占用: ${formatFileSize(memoryUsage)}")
                    addLog("共加载${totalLoadedCount}个词条到Trie树")
                    
                    // 添加Trie树结构信息
                    addLog("Trie树节点数: ${_trieTree.getNodeCount()}")
                    addLog("Trie树词条数: ${_trieTree.getWordCount()}")
                    
                    Timber.i("词典加载到Trie树完成，加载耗时: ${loadTime/1000}秒，内存占用: ${formatFileSize(memoryUsage)}")
                } catch (e: Exception) {
                    addLog("加载词典到Trie树失败: ${e.message}")
                    Timber.e(e, "加载词典到Trie树失败: ${e.message}")
                    
                    // 确保在出错时也重置Trie树状态
                    _trieTree.setLoaded(false)
                    typeLoadedCount.clear()
                } finally {
                    // 无论成功还是失败，都将加载状态重置为false
                    isLoadingInProgress = false
                }
            }.apply {
                // 设置为守护线程，不阻止应用退出
                isDaemon = true
                // 设置线程优先级为低，减少对主线程的影响
                priority = Thread.MIN_PRIORITY
                // 启动线程
                start()
            }
        } catch (e: Exception) {
            addLog("初始化词典加载失败: ${e.message}")
            Timber.e(e, "初始化词典加载失败: ${e.message}")
            isLoadingInProgress = false
        }
    }
    
    /**
     * 检查可用内存，如果内存不足则触发GC
     * 当可用内存低于阈值时主动触发GC
     */
    private fun checkMemory() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val usedPercentage = usedMemory.toDouble() / maxMemory.toDouble() * 100
        
        // 当内存使用超过70%时，主动触发GC
        if (usedPercentage > 70) {
            Timber.d("内存使用率达到${usedPercentage.toInt()}%，触发GC")
            System.gc()
            Thread.sleep(100) // 给GC一点时间
        }
    }
    
    /**
     * 将拼音标准化为无声调版本
     */
    private fun normalizePinyin(pinyin: String): String {
        if (pinyin.isBlank()) return pinyin
        
        // 去除可能包含的方括号
        val cleanPinyin = pinyin.replace("[\\[\\]]".toRegex(), "")
        
        // 替换所有声调字符为无声调版本
        return cleanPinyin.replace("[āáǎà]".toRegex(), "a")
            .replace("[ēéěè]".toRegex(), "e")
            .replace("[īíǐì]".toRegex(), "i")
            .replace("[ōóǒò]".toRegex(), "o")
            .replace("[ūúǔù]".toRegex(), "u")
            .replace("[ǖǘǚǜü]".toRegex(), "v")
    }
    
    /**
     * 搜索匹配的词条
     * @param prefix 要匹配的前缀
     * @param limit 返回结果的最大数量
     * @return 匹配的词条列表
     */
    fun searchWords(prefix: String, limit: Int = 10): List<WordFrequency> {
        Timber.d("开始搜索前缀: '$prefix', 限制数量: $limit")
        
        // 优先从内存Trie树中查询
        Timber.d("第一步: 尝试从Trie树中查询")
        val memoryResults = if (_trieTree.isLoaded()) {
            val results = _trieTree.search(prefix, limit)
            if (results.isNotEmpty()) {
                Timber.d("从Trie树中找到${results.size}个匹配'$prefix'的候选词")
            } else {
                Timber.d("在Trie树中没有找到匹配'$prefix'的候选词")
            }
            results
        } else {
            Timber.d("Trie树未加载或为空")
            emptyList()
        }
        
        // 如果内存中没有找到结果或结果数量少于限制，从Realm数据库中查询
        if (memoryResults.size < limit) {
            // 计算还需要多少个结果
            val needMore = limit - memoryResults.size
            Timber.d("第二步: 从Trie树中只找到${memoryResults.size}个结果，需要从Realm数据库中再查询${needMore}个结果")
            
            // 从Realm数据库中搜索
            try {
                // 排除已加载到Trie树的词典类型
                val realmResults = repository.searchEntries(prefix, needMore, TRIE_DICT_TYPES)
                
                // 如果从数据库查到了结果，合并结果
                if (realmResults.isNotEmpty()) {
                    Timber.d("从Realm数据库中查询到${realmResults.size}个候选词")
                    // 创建一个新列表包含内存结果和数据库结果
                    val combinedResults = memoryResults.toMutableList()
                    combinedResults.addAll(realmResults)
                    // 按词频排序
                    val sortedResults = combinedResults.sortedByDescending { it.frequency }
                    Timber.d("合并后共有${sortedResults.size}个结果")
                    return sortedResults
                } else {
                    Timber.d("Realm数据库中没有找到额外的候选词")
                }
            } catch (e: Exception) {
                Timber.e(e, "从Realm数据库查询候选词失败")
            }
        } else {
            Timber.d("从Trie树中已找到足够的结果(${memoryResults.size}个)，不需要查询Realm数据库")
        }
        
        Timber.d("返回${memoryResults.size}个查询结果")
        return memoryResults
    }
    
    /**
     * 检查是否已加载完成
     */
    fun isLoaded(): Boolean = _trieTree.isLoaded()
    
    /**
     * 获取所有已加载到Trie中的词条总数
     */
    fun getTotalLoadedCount(): Int {
        if (!isLoaded()) return 0
        return _trieTree.getWordCount()
    }
    
    /**
     * 重置词典管理器，清空内存词库
     */
    fun reset() {
        // 清空Trie树
        _trieTree.clear()
        
        // 清空加载计数
        typeLoadedCount.clear()
        
        Timber.i("词典管理器已重置")
    }
    
    /**
     * 添加日志记录
     */
    private fun addLog(message: String) {
        val timestamp = System.currentTimeMillis()
        val id = logId++
        val formattedTimestamp = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        loadingLogs[id] = "[$formattedTimestamp] $message"
    }
    
    /**
     * 获取所有日志记录
     */
    fun getLogs(): List<String> {
        return loadingLogs.entries
            .sortedBy { it.key }
            .map { it.value }
    }
    
    /**
     * 清空所有日志记录
     */
    fun clearLogs() {
        loadingLogs.clear()
        logId = 0
    }
    
    /**
     * 将字节大小转换为可读性好的字符串形式
     */
    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
    
    /**
     * 导出词典信息到外部存储
     * 此方法导出词典相关的统计信息，用于分析和调试
     */
    fun exportPrecompiledTrieForBuilding() {
        val context = ShenjiApplication.appContext
        
        try {
            addLog("开始导出词典信息用于分析")
            
            // 准备导出文件夹
            val exportDir = File(context.getExternalFilesDir(null), "export")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            // 计算词典版本哈希
            calculateVersionHashes()
            
            // 导出词典版本信息
            val versionExportFile = File(exportDir, DICTIONARY_VERSION_FILENAME)
            ObjectOutputStream(FileOutputStream(versionExportFile)).use { out ->
                out.writeObject(typeVersionHash)
            }
            
            // 导出词库信息文件 (JSON格式便于检查)
            val infoFile = File(exportDir, "dict_info.json")
            val info = StringBuilder()
            info.append("{\n")
            info.append("  \"exportTime\": \"${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\",\n")
            info.append("  \"dictTypes\": [\n")
            
            HIGH_FREQUENCY_DICT_TYPES.forEachIndexed { index, type ->
                val count = repository.getEntryCountByType(type)
                val version = typeVersionHash[type] ?: ""
                info.append("    {\n")
                info.append("      \"type\": \"$type\",\n")
                info.append("      \"count\": $count,\n")
                info.append("      \"version\": \"$version\"\n")
                info.append("    }")
                if (index < HIGH_FREQUENCY_DICT_TYPES.size - 1) {
                    info.append(",")
                }
                info.append("\n")
            }
            
            info.append("  ]\n")
            info.append("}\n")
            
            infoFile.writeText(info.toString())
            
            addLog("词典信息导出成功，位置：${exportDir.absolutePath}")
            Timber.i("词典信息导出成功，位置：${exportDir.absolutePath}")
            
        } catch (e: Exception) {
            Timber.e(e, "导出词典信息失败")
            addLog("导出词典信息失败: ${e.message}")
        }
    }
    
    /**
     * 计算词典版本哈希
     */
    private fun calculateVersionHashes() {
        for (type in HIGH_FREQUENCY_DICT_TYPES) {
            val lastModTime = repository.getLastModifiedTime(type)
            val hash = "v-${lastModTime}-${repository.getEntryCountByType(type)}"
            typeVersionHash[type] = hash
        }
    }
    
    /**
     * 导出指定词库到预编译Trie树 - 极低内存占用版本
     * 针对内存溢出问题特别优化
     * @param batchSize 每批处理的词条数
     * @param maxNodesPerBatch 每批最大节点数
     * @param numWorkers 工作线程数
     * @param progressCallback 进度回调函数，参数为0到100的整数
     * @return 导出的文件路径
     */
    suspend fun exportHighFrequencyDictionaryToTrie(
        batchSize: Int = 500,
        maxNodesPerBatch: Int = 2000,
        numWorkers: Int = 2,
        progressCallback: suspend (Int) -> Unit
    ): String {
        val context = ShenjiApplication.appContext
        val exportDir = File(context.getExternalFilesDir(null), "export")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        
        // 创建临时文件存储中间结果
        val tempDir = File(exportDir, "temp")
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
        tempDir.mkdirs()
        
        try {
            addLog("开始导出高频词库(chars和base)到预编译Trie树 - 自定义参数模式")
            addLog("批次大小: $batchSize, 每批最大节点: $maxNodesPerBatch, 工作线程: $numWorkers")
            progressCallback(5)
            
            // 获取词条数量
            val charsCount = repository.getEntryCountByType("chars")
            val baseCount = repository.getEntryCountByType("base")
            val totalCount = charsCount + baseCount
            
            addLog("chars词库: $charsCount 个词条")
            addLog("base词库: $baseCount 个词条")
            addLog("总计: $totalCount 个词条")
            
            progressCallback(10)
            
            // 加载前强制GC一次，清理内存
            addLog("执行内存清理...")
            System.gc()
            delay(500) // 给GC一点时间
            
            // 记录开始时间
            val startTime = System.currentTimeMillis()
            
            // 使用超低内存模式处理chars词库
            addLog("使用超低内存模式处理chars词库...")
            val charsTempFiles = processWithUltraLowMemory(
                "chars", 
                charsCount, 
                batchSize,
                maxNodesPerBatch,
                tempDir,
                { progress -> 
                    // 更新进度 - chars占20%
                    val adjustedProgress = 10 + ((progress * 20) / 100)
                    progressCallback(adjustedProgress)
                    
                    // 更新预计剩余时间
                    val elapsedTime = System.currentTimeMillis() - startTime
                    val estimatedTotalTime = if (progress > 0) elapsedTime * 100 / progress else 0
                    val remainingTime = estimatedTotalTime - elapsedTime
                    if (progress % 5 == 0 && remainingTime > 0) {
                        addLog("预计剩余时间: ${formatTime(remainingTime)}")
                    }
                }
            )
            
            // 强制GC释放chars词库占用的内存
            addLog("chars词库处理完成，执行内存清理...")
            System.gc()
            delay(1000) // 给GC更多时间
            progressCallback(30)
            
            // 将base词库分成7个子集处理
            addLog("将base词库分成7个子集处理...")
            val baseTempFiles = mutableListOf<File>()
            val baseSubsetSize = baseCount / 7
            val remainingItems = baseCount % 7
            
            for (i in 0 until 7) {
                val subsetStart = i * baseSubsetSize
                val subsetSize = if (i == 6) baseSubsetSize + remainingItems else baseSubsetSize
                
                addLog("处理base词库子集 ${i+1}/7 (${subsetStart}到${subsetStart+subsetSize-1})...")
                
                val subsetTempFiles = processBaseSubset(
                    subsetIndex = i,
                    offset = subsetStart,
                    count = subsetSize,
                    batchSize = batchSize,
                    maxNodesPerBatch = maxNodesPerBatch,
                    tempDir = tempDir,
                    { progress -> 
                        // 更新进度 - 每个base子集占7%，总共49%
                        val baseProgress = 30 + (i * 7) + ((progress * 7) / 100)
                        progressCallback(baseProgress)
                        
                        // 更新预计剩余时间
                        val elapsedTime = System.currentTimeMillis() - startTime
                        val estimatedTotalTime = if (baseProgress > 0) elapsedTime * 100 / baseProgress else 0
                        val remainingTime = estimatedTotalTime - elapsedTime
                        if (progress % 10 == 0 && remainingTime > 0) {
                            addLog("预计剩余时间: ${formatTime(remainingTime)}")
                        }
                    }
                )
                
                baseTempFiles.addAll(subsetTempFiles)
                
                // 每处理完一个子集，执行一次GC
                addLog("base词库子集 ${i+1}/7 处理完成，执行内存清理...")
                System.gc()
                delay(500)
            }
            
            // 强制GC释放base词库占用的内存
            addLog("所有词库处理完成，执行内存清理...")
            System.gc()
            delay(1000) // 给GC更多时间
            progressCallback(80)
            
            // 使用超低内存模式合并临时文件
            val mergedTreeFile = mergeWithUltraLowMemory(
                tempDir, 
                charsTempFiles + baseTempFiles,
                numWorkers,
                { progress ->
                    val adjustedProgress = 80 + ((progress * 15) / 100)
                    progressCallback(adjustedProgress)
                }
            )
            
            progressCallback(95)
            
            // 生成最终Trie树文件
            val finalTrieFile = File(exportDir, "precompiled_trie.bin")
            mergedTreeFile.copyTo(finalTrieFile, overwrite = true)
            
            // 导出词库信息
            progressCallback(98)
            addLog("导出词库信息")
            
            val infoFile = File(exportDir, "dict_info.json")
            val info = StringBuilder()
            info.append("{\n")
            info.append("  \"exportTime\": \"${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\",\n")
            info.append("  \"estimatedWordCount\": $totalCount,\n")
            info.append("  \"dictTypes\": [\n")
            
            HIGH_FREQUENCY_DICT_TYPES.forEachIndexed { index, type ->
                val count = repository.getEntryCountByType(type)
                val version = calculateVersionHash(type)
                info.append("    {\n")
                info.append("      \"type\": \"$type\",\n")
                info.append("      \"count\": $count,\n")
                info.append("      \"version\": \"$version\"\n")
                info.append("    }")
                if (index < HIGH_FREQUENCY_DICT_TYPES.size - 1) {
                    info.append(",")
                }
                info.append("\n")
            }
            
            info.append("  ]\n")
            info.append("}\n")
            
            infoFile.writeText(info.toString())
            
            // 清理临时文件
            addLog("清理临时文件...")
            tempDir.deleteRecursively()
            
            progressCallback(100)
            addLog("预编译Trie树导出成功，文件大小: ${formatFileSize(finalTrieFile.length())}")
            addLog("总耗时: ${formatTime(System.currentTimeMillis() - startTime)}")
            
            // 显式回收内存
            System.gc()
            
            return finalTrieFile.absolutePath
            
        } catch (e: Exception) {
            // 清理临时文件
            tempDir.deleteRecursively()
            
            Timber.e(e, "导出高频词库到预编译Trie树失败: ${e.message}")
            addLog("导出失败: ${e.message}")
            throw e
        }
    }
    
    /**
     * 格式化时间（毫秒转为易读形式）
     */
    private fun formatTime(timeMs: Long): String {
        val seconds = timeMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> String.format("%d小时%d分钟", hours, minutes % 60)
            minutes > 0 -> String.format("%d分钟%d秒", minutes, seconds % 60)
            else -> String.format("%d秒", seconds)
        }
    }
    
    /**
     * 超低内存处理词典类型，直接写入到临时文件，不在内存中构建完整Trie树
     */
    private suspend fun processWithUltraLowMemory(
        type: String,
        totalCount: Int,
        batchSize: Int,
        maxNodesPerBatch: Int,
        tempDir: File,
        progressCallback: suspend (Int) -> Unit
    ): List<File> {
        val tempFiles = mutableListOf<File>()
        var processedCount = 0
        
        addLog("开始超低内存处理${type}词库，共${totalCount}个词条")
        addLog("使用批次大小: $batchSize, 每批最大节点: $maxNodesPerBatch")
        
        // 使用传入的批次大小
        val microBatchSize = batchSize.coerceAtLeast(100) // 确保至少为100
        
        while (processedCount < totalCount) {
            // 取当前批次大小
            val currentBatchSize = min(microBatchSize, totalCount - processedCount)
            
            // 只加载当前批次的词条
            val entries = repository.getEntriesByType(type, processedCount, currentBatchSize)
            
            // 创建临时树
            val tempTree = TrieTree()
            
            // 添加词条到临时树
            for (entry in entries) {
                // 关键修改：使用entry.pinyin作为Trie树的键，而不是entry.word
                // 确保pinyin不为空
                val pinyin = entry.pinyin.takeIf { it.isNotBlank() } ?: continue
                
                // 记录诊断信息，但仅对一小部分词条记录，避免日志过多
                if (processedCount < 10 || processedCount % 1000 == 0) {
                    addLog("添加词条: 拼音='${pinyin}', 词语='${entry.word}', 频率=${entry.frequency}")
                }
                
                // 将拼音作为key，汉字作为chinese插入到Trie树中
                tempTree.insert(pinyin, entry.frequency, entry.word)
                
                // 如果树节点数超过最大限制，提前保存并创建新树
                if (tempTree.getNodeCount() > maxNodesPerBatch) {
                    // 保存当前树
                    val tempFile = File(tempDir, "${type}_batch_${processedCount}_part_${tempFiles.size}.bin")
                    saveTreeToFile(tempTree, tempFile)
                    tempFiles.add(tempFile)
                    
                    // 清空树，创建新树
                    tempTree.clear()
                    System.gc()
                }
            }
            
            // 保存最后的临时树（如果不为空）
            if (tempTree.getWordCount() > 0) {
                val tempFile = File(tempDir, "${type}_batch_${processedCount}_part_${tempFiles.size}.bin")
                saveTreeToFile(tempTree, tempFile)
                tempFiles.add(tempFile)
            }
            
            // 更新已处理数量
            processedCount += currentBatchSize
            
            // 更新进度
            val progress = (processedCount * 100) / totalCount
            
            // 仅在每5%更新一次UI，减少UI压力
            if (progress % 5 == 0 || progress >= 99) {
                addLog("${type}词库处理进度: ${processedCount}/${totalCount} (${progress}%)")
                progressCallback(progress)
            }
            
            // 清空树对象，帮助GC
            tempTree.clear()
            
            // 主动GC
            if (processedCount % (microBatchSize * 10) == 0) {
                addLog("执行中间内存清理...")
                System.gc()
                delay(200)
            }
            
            // 让出线程时间，允许UI更新
            yield()
        }
        
        addLog("${type}词库处理完成，生成了${tempFiles.size}个临时文件")
        return tempFiles
    }
    
    /**
     * 保存Trie树到文件（极低内存版本）
     */
    private fun saveTreeToFile(tree: TrieTree, file: File) {
        try {
            tree.setLoaded(true)
            val serializedTree = SerializableTrieTree.fromRuntimeTree(tree)
            
            FileOutputStream(file).use { fileOut ->
                ObjectOutputStream(BufferedOutputStream(fileOut, 8192)).use { out ->
                    out.writeObject(serializedTree)
                    out.flush()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "保存Trie树到文件失败: ${e.message}")
            addLog("保存Trie树到文件失败: ${e.message}")
            throw e
        }
    }
    
    /**
     * 超低内存合并多个临时Trie树文件
     * @param tempDir 临时文件目录
     * @param tempFiles 需要合并的临时文件列表
     * @param numWorkers 工作线程数
     * @param progressCallback 进度回调函数
     * @return 合并后的文件
     */
    private suspend fun mergeWithUltraLowMemory(
        tempDir: File,
        tempFiles: List<File>,
        numWorkers: Int = 2,
        progressCallback: suspend (Int) -> Unit
    ): File {
        addLog("开始超低内存合并${tempFiles.size}个临时Trie树文件...")
        
        // 确保临时目录存在
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        // 记录开始时间
        val startTime = System.currentTimeMillis()
        
        // 将所有临时文件分成更小的组，每组最多4个文件
        // 减小组大小，降低内存需求
        val maxFilesPerGroup = 3  // 从4减少到3
        val fileGroups = tempFiles.chunked(maxFilesPerGroup)
        
        addLog("文件分成${fileGroups.size}组，每组最多${maxFilesPerGroup}个文件")
        
        val intermediateFiles = mutableListOf<File>()
        var totalProcessedFiles = 0
        
        // 先处理每个组内的文件，生成中间合并文件
        for ((groupIndex, group) in fileGroups.withIndex()) {
            addLog("处理文件组 ${groupIndex + 1}/${fileGroups.size}，包含${group.size}个文件")
            
            // 为当前组创建一个新的合并树
            val groupTree = TrieTree()
            var processedFilesInGroup = 0
            
            // 处理当前组中的每个文件
            for (file in group) {
                try {
                    // 加载单个文件
                    val tree = loadTrieFromFile(file)
                    
                    // 从树中提取所有词条
                    val wordEntries = tree.getAllWordEntries()
                    
                    // 分小批次将词条添加到组合并树中
                    val wordBatchSize = 100 // 从200减少到100，降低每批内存占用
                    val wordBatches = wordEntries.chunked(wordBatchSize)
                    
                    for (wordBatch in wordBatches) {
                        for (entry in wordBatch) {
                            // 使用完整信息插入词条（拼音、频率、汉字）
                            groupTree.insert(entry.pinyin, entry.frequency, entry.chinese)
                            
                            // 记录样本，帮助诊断
                            if (wordBatches.indexOf(wordBatch) == 0 && wordBatch.indexOf(entry) < 3) {
                                addLog("合并样本词条: 拼音='${entry.pinyin}', 汉字='${entry.chinese}', 频率=${entry.frequency}")
                            }
                        }
                        
                        // 让出线程时间
                        yield()
                    }
                    
                    // 清空源树，帮助GC
                    tree.clear()
                    
                    // 更新进度
                    processedFilesInGroup++
                    totalProcessedFiles++
                    
                    val progress = (totalProcessedFiles * 100) / tempFiles.size
                    if (progress % 5 == 0 || processedFilesInGroup == group.size) {
                        addLog("组${groupIndex + 1}合并进度: ${processedFilesInGroup}/${group.size} (总进度: ${totalProcessedFiles}/${tempFiles.size}, ${progress}%)")
                        progressCallback(progress)
                    }
                    
                    // 每处理完一个文件执行一次GC
                    System.gc()
                    delay(200)
                    
                } catch (e: Exception) {
                    addLog("合并文件失败: ${e.message}，尝试继续处理其他文件")
                    Timber.e(e, "合并文件失败: ${e.message}")
                }
            }
            
            // 保存当前组的合并树到中间文件
            val intermediateFile = File(tempDir, "intermediate_group_${groupIndex}.bin")
            saveTreeToFile(groupTree, intermediateFile)
            intermediateFiles.add(intermediateFile)
            
            // 清空组合并树，释放内存
            groupTree.clear()
            System.gc()
            delay(300)  // 增加延迟，给GC更多时间
            
            addLog("完成文件组 ${groupIndex + 1}/${fileGroups.size} 的合并，生成中间文件: ${intermediateFile.name}")
        }
        
        // 最后合并所有中间文件
        addLog("开始合并${intermediateFiles.size}个中间文件...")
        
        // 最终的合并树
        val mergedTree = TrieTree()
        val mergedFile = File(tempDir, "merged_result.bin")
        
        // 处理中间文件
        for ((index, file) in intermediateFiles.withIndex()) {
            try {
                // 加载中间文件
                val tree = loadTrieFromFile(file)
                
                // 从树中提取所有词条
                val wordEntries = tree.getAllWordEntries()
                
                // 分大批次将词条添加到最终合并树中
                val wordBatchSize = 200 // 从500减少到200
                val wordBatches = wordEntries.chunked(wordBatchSize)
                
                for (wordBatch in wordBatches) {
                    for (entry in wordBatch) {
                        // 使用完整信息插入词条（拼音、频率、汉字）
                        mergedTree.insert(entry.pinyin, entry.frequency, entry.chinese)
                    }
                    
                    // 让出线程时间
                    yield()
                    
                    // 每处理50个批次，执行一次GC
                    if (wordBatches.indexOf(wordBatch) % 50 == 0) {
                        System.gc()
                        delay(100)
                    }
                }
                
                // 清空源树，帮助GC
                tree.clear()
                
                // 更新进度
                val progress = 80 + ((index + 1) * 20 / intermediateFiles.size)
                addLog("中间文件合并进度: ${index + 1}/${intermediateFiles.size}")
                progressCallback(progress)
                
                // 每处理一个文件执行一次GC
                System.gc()
                delay(300)  // 增加延迟
                
            } catch (e: Exception) {
                addLog("处理中间文件失败: ${e.message}，尝试继续处理其他文件")
                Timber.e(e, "合并中间文件失败: ${e.message}")
            }
        }
        
        addLog("合并完成，保存最终Trie树...")
        
        // 保存合并后的树
        saveTreeToFile(mergedTree, mergedFile)
        
        // 清空合并树，释放内存
        mergedTree.clear()
        System.gc()
        
        // 删除中间文件
        intermediateFiles.forEach { 
            try {
                it.delete()
            } catch (e: Exception) {
                Timber.e(e, "删除中间文件失败: ${e.message}")
            }
        }
        
        val endTime = System.currentTimeMillis()
        val timeCost = endTime - startTime
        addLog("临时Trie树文件合并完成，最终文件: ${mergedFile.name}")
        addLog("合并耗时: ${formatTime(timeCost)}")
        
        return mergedFile
    }
    
    /**
     * 从文件加载Trie树（极低内存版本）
     */
    private fun loadTrieFromFile(file: File): TrieTree {
        try {
            FileInputStream(file).use { fileIn ->
                ObjectInputStream(BufferedInputStream(fileIn, 8192)).use { input ->
                    val serializedTree = input.readObject() as SerializableTrieTree
                    // 转换为运行时树
                    return serializedTree.toRuntimeTree()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "加载Trie树文件失败: ${e.message}")
            addLog("加载Trie树文件失败: ${e.message}")
            throw e
        }
    }
    
    /**
     * 计算词典版本哈希值
     */
    private fun calculateVersionHash(type: String): String {
        val lastModTime = repository.getLastModifiedTime(type)
        return "v-${lastModTime}-${repository.getEntryCountByType(type)}"
    }
    
    /**
     * 处理base词典的子集
     */
    private suspend fun processBaseSubset(
        subsetIndex: Int,
        offset: Int,
        count: Int,
        batchSize: Int,
        maxNodesPerBatch: Int,
        tempDir: File,
        progressCallback: suspend (Int) -> Unit
    ): List<File> {
        addLog("处理base词典子集${subsetIndex+1}/7，共${count}个词条，从${offset}开始")
        
        // 创建临时文件存储目录
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        // 记录开始时间
        val startTime = System.currentTimeMillis()
        
        // 分批处理词条，避免内存溢出
        val tempFiles = mutableListOf<File>()
        val totalBatches = (count + batchSize - 1) / batchSize
        
        addLog("将分${totalBatches}批处理")
        
        var processedCount = 0
        var currentBatch = 0
        var tree = TrieTree()
        var nodeCount = 0
        
        // 使用分批加载数据
        var remainingCount = count
        var currentOffset = offset
        
        while (remainingCount > 0) {
            val batchCount = minOf(batchSize, remainingCount)
            
            // 获取当前批次的词条
            val entries = repository.getEntriesByType("base", currentOffset, batchCount)
            
            addLog("加载了${entries.size}个词条，批次${currentBatch+1}/${totalBatches}")
            
            for (entry in entries) {
                // 修改：使用拼音作为键，汉字作为chinese参数
                val pinyin = entry.pinyin.takeIf { it.isNotBlank() } ?: continue
                
                // 添加到Trie树
                tree.insert(pinyin, entry.frequency, entry.word)
                nodeCount = tree.getNodeCount()
                processedCount++
                
                // 检查是否达到批次大小或节点数量限制
                if (processedCount % batchSize == 0 || nodeCount >= maxNodesPerBatch || processedCount == count) {
                    // 保存当前批次
                    val tempFile = File(tempDir, "base_subset${subsetIndex}_batch${currentBatch}.bin")
                    saveTreeToFile(tree, tempFile)
                    tempFiles.add(tempFile)
                    
                    // 重置树
                    tree = TrieTree()
                    nodeCount = 0
                    currentBatch++
                    
                    // 更新进度
                    val progress = (processedCount * 100) / count
                    progressCallback(progress)
                    
                    addLog("处理进度: $progress%, 已处理: $processedCount/$count, 批次: $currentBatch/$totalBatches")
                }
            }
            
            // 更新剩余数量和偏移量
            remainingCount -= batchCount
            currentOffset += batchCount
            
            // 让出协程时间，允许UI更新
            yield()
        }
        
        // 确保最后一批被保存
        if (nodeCount > 0) {
            val tempFile = File(tempDir, "base_subset${subsetIndex}_batch${currentBatch}.bin")
            saveTreeToFile(tree, tempFile)
            tempFiles.add(tempFile)
        }
        
        val endTime = System.currentTimeMillis()
        val timeCost = endTime - startTime
        addLog("子集${subsetIndex+1}/7处理完成，耗时: ${formatTime(timeCost)}")
        
        return tempFiles
    }
    
    /**
     * 处理单个词典并保存到临时文件
     * @param type 词典类型，如"chars"
     * @param subsetIndex 子集索引，仅对base词典有效，-1表示不是子集
     * @param batchSize 每批处理的词条数
     * @param maxNodesPerBatch 每批最大节点数
     * @param progressCallback 进度回调函数，参数为0到100的整数
     * @return 生成的临时文件路径
     */
    suspend fun processSingleDictionary(
        type: String,
        subsetIndex: Int = -1,
        batchSize: Int = 500,
        maxNodesPerBatch: Int = 2000,
        progressCallback: suspend (Int) -> Unit
    ): String {
        val context = ShenjiApplication.appContext
        val exportDir = File(context.getExternalFilesDir(null), "export")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        
        // 创建临时文件存储目录
        val tempDir = File(exportDir, "temp_dicts")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        addLog("开始处理${if (type == "base" && subsetIndex >= 0) "base词典子集 ${subsetIndex+1}/7" else type + "词典"}")
        
        val startTime = System.currentTimeMillis()
        
        // 根据类型处理不同词典
        val tempFiles = if (type == "base" && subsetIndex >= 0) {
            // 计算这个子集应该处理的词条范围
            val totalBaseEntries = repository.getEntryCountByType("base")
            val subsetSize = totalBaseEntries / 7
            val offset = if (subsetIndex < 6) subsetIndex * subsetSize else 6 * subsetSize
            val count = if (subsetIndex < 6) subsetSize else (totalBaseEntries - 6 * subsetSize)
            
            addLog("base词典总词条数: $totalBaseEntries, 子集${subsetIndex+1}范围: $offset - ${offset+count-1}")
            
            // 处理base词典子集
            processBaseSubset(subsetIndex, offset, count, batchSize, maxNodesPerBatch, tempDir, progressCallback)
        } else {
            // 处理其他类型词典
            val count = repository.getEntryCountByType(type)
            addLog("${type}词典总词条数: $count")
            
            processDictionary(type, batchSize, maxNodesPerBatch, tempDir, progressCallback)
        }
        
        // 如果只有一个临时文件，直接返回
        if (tempFiles.size == 1) {
            val outputFile = File(exportDir, "${type}${if (subsetIndex >= 0) "_subset${subsetIndex+1}" else ""}_trie.bin")
            tempFiles.first().copyTo(outputFile, overwrite = true)
            
            val endTime = System.currentTimeMillis()
            val timeCost = endTime - startTime
            addLog("处理完成，耗时: ${formatTime(timeCost)}")
            
            return outputFile.absolutePath
        }
        
        // 如果有多个临时文件，需要合并
        addLog("需要合并${tempFiles.size}个临时文件")
        
        // 合并临时文件
        val mergedFile = mergeWithUltraLowMemory(tempDir, tempFiles, 2) { progress ->
            // 合并进度从50%开始，到100%结束
            progressCallback(50 + progress / 2)
        }
        
        // 复制到最终位置
        val outputFile = File(exportDir, "${type}${if (subsetIndex >= 0) "_subset${subsetIndex+1}" else ""}_trie.bin")
        mergedFile.copyTo(outputFile, overwrite = true)
        
        // 删除临时文件
        tempFiles.forEach { it.delete() }
        mergedFile.delete()
        
        val endTime = System.currentTimeMillis()
        val timeCost = endTime - startTime
        addLog("处理完成，耗时: ${formatTime(timeCost)}")
        
        return outputFile.absolutePath
    }
    
    /**
     * 处理普通词典
     */
    private suspend fun processDictionary(
        type: String,
        batchSize: Int,
        maxNodesPerBatch: Int,
        tempDir: File,
        progressCallback: suspend (Int) -> Unit
    ): List<File> {
        // 创建临时文件存储目录
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        // 记录开始时间
        val startTime = System.currentTimeMillis()
        
        // 获取词典总数
        val totalCount = repository.getEntryCountByType(type)
        
        // 分批处理词条，避免内存溢出
        val tempFiles = mutableListOf<File>()
        val totalBatches = (totalCount + batchSize - 1) / batchSize
        
        addLog("将分${totalBatches}批处理")
        
        var processedCount = 0
        var currentBatch = 0
        var tree = TrieTree()
        var nodeCount = 0
        
        // 使用分批加载数据
        var remainingCount = totalCount
        var currentOffset = 0
        
        while (remainingCount > 0) {
            val batchCount = minOf(batchSize, remainingCount)
            
            // 获取当前批次的词条
            val entries = repository.getEntriesByType(type, currentOffset, batchCount)
            
            addLog("加载了${entries.size}个词条，批次${currentBatch+1}/${totalBatches}")
            
            for (entry in entries) {
                // 修改：使用拼音作为键，汉字作为chinese参数
                val pinyin = entry.pinyin.takeIf { it.isNotBlank() } ?: continue
                
                // 添加到Trie树
                tree.insert(pinyin, entry.frequency, entry.word)
                nodeCount = tree.getNodeCount()
                processedCount++
                
                // 检查是否达到批次大小或节点数量限制
                if (processedCount % batchSize == 0 || nodeCount >= maxNodesPerBatch || processedCount == totalCount) {
                    // 保存当前批次
                    val tempFile = File(tempDir, "${type}_batch${currentBatch}.bin")
                    saveTreeToFile(tree, tempFile)
                    tempFiles.add(tempFile)
                    
                    // 重置树
                    tree = TrieTree()
                    nodeCount = 0
                    currentBatch++
                    
                    // 更新进度
                    val progress = (processedCount * 100) / totalCount
                    progressCallback(progress)
                    
                    addLog("处理进度: $progress%, 已处理: $processedCount/$totalCount, 批次: $currentBatch/$totalBatches")
                }
            }
            
            // 更新剩余数量和偏移量
            remainingCount -= batchCount
            currentOffset += batchCount
            
            // 让出协程时间，允许UI更新
            yield()
        }
        
        // 确保最后一批被保存
        if (nodeCount > 0) {
            val tempFile = File(tempDir, "${type}_batch${currentBatch}.bin")
            saveTreeToFile(tree, tempFile)
            tempFiles.add(tempFile)
        }
        
        val endTime = System.currentTimeMillis()
        val timeCost = endTime - startTime
        addLog("词典${type}处理完成，耗时: ${formatTime(timeCost)}")
        
        return tempFiles
    }
    
    /**
     * 合并所有临时词典文件生成最终Trie树
     * @param progressCallback 进度回调函数，参数为0到100的整数
     * @return 合并后的文件路径
     */
    suspend fun mergeAllDictionaries(progressCallback: suspend (Int) -> Unit): String {
        val context = ShenjiApplication.appContext
        val exportDir = File(context.getExternalFilesDir(null), "export")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        
        // 创建临时文件存储目录
        val tempDir = File(exportDir, "temp_merge")
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
        tempDir.mkdirs()
        
        addLog("开始合并所有词典文件")
        
        try {
            // 主动触发内存回收
            addLog("执行初始内存清理...")
            System.gc()
            delay(1000)  // 给GC足够时间
            
            // 记录开始时间
            val startTime = System.currentTimeMillis()
            
            // 查找所有需要合并的词典文件
            val dictFiles = mutableListOf<File>()
            
            // 查找base词典子集文件，分批次处理
            val baseFiles = mutableListOf<File>()
            for (i in 0..6) {
                val file = File(exportDir, "base_subset${i+1}_trie.bin")
                if (file.exists()) {
                    baseFiles.add(file)
                    addLog("找到base词典子集${i+1}文件: ${file.name}")
                } else {
                    addLog("警告: 未找到base词典子集${i+1}文件")
                }
            }
            
            // 查找chars词典文件
            val charsFile = File(exportDir, "chars_trie.bin")
            if (charsFile.exists()) {
                dictFiles.add(charsFile)
                addLog("找到chars词典文件: ${charsFile.name}")
            } else {
                addLog("警告: 未找到chars词典文件")
            }
            
            // 将所有base词典文件添加到最后
            dictFiles.addAll(baseFiles)
            
            // 检查是否有足够的文件需要合并
            if (dictFiles.size <= 1) {
                addLog("错误: 没有足够的词典文件需要合并，至少需要2个文件")
                throw IllegalStateException("没有足够的词典文件需要合并，至少需要2个文件")
            }
            
            addLog("共找到${dictFiles.size}个词典文件需要合并")
            progressCallback(5)
            
            // 再次强制GC
            System.gc()
            delay(500)
            
            // 复制所有文件到临时目录，每处理一个文件就进行一次GC
            val tempFiles = mutableListOf<File>()
            for ((index, file) in dictFiles.withIndex()) {
                val tempFile = File(tempDir, "dict_${index}.bin")
                file.copyTo(tempFile, overwrite = true)
                tempFiles.add(tempFile)
                
                // 每复制一个文件后执行GC
                if (index % 2 == 1) {
                    System.gc()
                    delay(200)
                }
                
                // 更新进度
                val progress = 5 + ((index * 15) / dictFiles.size)
                progressCallback(progress)
            }
            
            addLog("所有词典文件已复制到临时目录")
            progressCallback(20)
            
            // 再次强制GC
            System.gc()
            delay(1000)
            addLog("再次执行内存清理...")
            
            // 使用超低内存模式合并临时文件，采用更保守的参数
            val mergedFile = mergeWithUltraLowMemory(
                tempDir = tempDir,
                tempFiles = tempFiles,
                numWorkers = 1,  // 减少工作线程，降低内存压力
                progressCallback = { progress ->
                    val adjustedProgress = 20 + ((progress * 70) / 100)
                    progressCallback(adjustedProgress)
                }
            )
            
            progressCallback(90)
            
            // 强制GC
            System.gc()
            delay(500)
            
            // 复制到最终位置
            val outputFile = File(exportDir, "shenji_dict_trie.bin")
            
            // 如果目标文件已存在，先删除
            if (outputFile.exists()) {
                outputFile.delete()
            }
            
            // 使用输入输出流复制而不是直接copyTo，可能有助于减少内存使用
            addLog("将合并结果复制到最终位置...")
            FileInputStream(mergedFile).use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192) // 8KB的缓冲区
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            
            // 清理临时文件
            addLog("清理临时文件...")
            try {
                tempDir.deleteRecursively()
            } catch (e: Exception) {
                Timber.e(e, "清理临时文件失败: ${e.message}")
                addLog("清理临时文件失败: ${e.message}")
            }
            
            val endTime = System.currentTimeMillis()
            val timeCost = endTime - startTime
            addLog("所有词典合并完成，耗时: ${formatTime(timeCost)}")
            addLog("最终词典文件: ${outputFile.absolutePath}")
            addLog("文件大小: ${formatFileSize(outputFile.length())}")
            
            progressCallback(100)
            
            return outputFile.absolutePath
            
        } catch (e: Exception) {
            // 清理临时文件，防止占用存储空间
            try {
                tempDir.deleteRecursively()
            } catch (cleanupEx: Exception) {
                Timber.e(cleanupEx, "清理临时文件失败: ${cleanupEx.message}")
            }
            
            Timber.e(e, "合并词典失败: ${e.message}")
            addLog("合并词典失败: ${e.message}")
            throw e
        }
    }
    
    /**
     * 加载预编译的高频词典到内存
     * @param file 预编译的Trie树文件
     */
    fun loadPrecompiledDictionary(file: File) {
        if (!file.exists()) {
            addLog("预编译高频词典文件不存在: ${file.absolutePath}")
            Timber.e("预编译高频词典文件不存在: ${file.absolutePath}")
            return
        }
        
        try {
            addLog("开始加载预编译高频词典(chars和base)...")
            
            // 记录开始时间
            val startTime = System.currentTimeMillis()
            
            // 清空当前Trie树，确保释放内存
            _trieTree.clear()
            System.gc()
            
            // 从文件加载预编译的Trie树
            _trieTree = loadTrieFromFile(file)
            
            // 设置已加载标志
            _trieTree.setLoaded(true)
            
            // 获取总词条数
            val totalWordCount = _trieTree.getWordCount()
            
            // 更新加载计数器
            // 假设词条按照chars和base的比例分布，这里简单以3:7的比例分配
            typeLoadedCount["chars"] = (totalWordCount * 0.3).toInt()
            typeLoadedCount["base"] = (totalWordCount * 0.7).toInt()
            
            // 记录加载完成时间和内存使用
            val endTime = System.currentTimeMillis()
            val loadTime = endTime - startTime
            val memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            
            // 记录加载完成信息
            addLog("预编译高频词典加载完成!")
            addLog("加载耗时: ${loadTime/1000}秒")
            addLog("内存占用: ${formatFileSize(memoryUsage)}")
            addLog("包含chars和base词典的${totalWordCount}个词条")
            
            // 深入分析Trie树结构问题
            addLog("--- Trie树结构分析 ---")
            try {
                // 1. 分析根节点子节点
                val rootKeys = _trieTree.getRootChildKeys()
                addLog("Trie树根节点的子节点首字符: $rootKeys")
                
                // 2. 获取词条样本进行分析 - 使用getAllWordEntries获取拼音和汉字信息
                val sampleWordEntries = _trieTree.getAllWordEntries().take(10)
                if (sampleWordEntries.isNotEmpty()) {
                    addLog("首个词条分析:")
                    val firstEntry = sampleWordEntries.first()
                    addLog("- 拼音='${firstEntry.pinyin}', 汉字='${firstEntry.chinese}', 频率=${firstEntry.frequency}")
                    addLog("- 拼音字符分析: ${firstEntry.pinyin.toCharArray().joinToString(" ") { "0x${it.code.toString(16)}(${it})" }}")
                    
                    // 尝试根据拼音直接搜索
                    val directSearch = _trieTree.search(firstEntry.pinyin, 1)
                    addLog("- 使用拼音'${firstEntry.pinyin}'直接搜索结果: ${if (directSearch.isNotEmpty()) "找到" else "未找到"}")
                } else {
                    addLog("无法获取词条样本")
                }
                
                // 3. 测试几种不同类型的输入
                val testInputs = listOf("a", "1", "p", "_", ".", " ")
                testInputs.forEach { input -> 
                    val results = _trieTree.search(input, 1)
                    addLog("测试字符'$input': ${if (results.isNotEmpty()) "找到匹配" else "无匹配"}")
                }
                
                // 4. 分析SerializableTrieTree转换过程
                addLog("序列化/反序列化分析:")
                // 检查是否有特殊字符作为索引
                val specialChars = listOf('\u0000', '\u0001', '\u0002', '\u0003')
                specialChars.forEach { char ->
                    if (rootKeys.contains(char.toString())) {
                        addLog("发现特殊控制字符: 0x${char.code.toString(16)}")
                    }
                }
                
                // 5. 检查Trie树大小和词条数量是否匹配
                addLog("结构一致性检查:")
                addLog("- Trie树节点数: ${_trieTree.getNodeCount()}")
                addLog("- Trie树词条数: ${_trieTree.getWordCount()}")
                addLog("- getAllWordEntries()返回的词条数: ${_trieTree.getAllWordEntries().size}")
                
                // 添加测试"显示"的拼音不同形式
                val testPinyins = listOf(
                    "xian shi", // 无声调有空格
                    "xianshi",  // 无声调无空格
                    "xiǎn shì", // 有声调有空格
                    "xiǎnshì"   // 有声调无空格
                )
                
                testPinyins.forEach { pinyin ->
                    val results = _trieTree.search(pinyin, 3)
                    if (results.isNotEmpty()) {
                        addLog("拼音'$pinyin'匹配词条: ${results.joinToString { "${it.word}(${it.frequency})" }}")
                    } else {
                        addLog("拼音'$pinyin'无匹配词条")
                    }
                }
                
                // 执行进一步的高级测试
                runAdvancedDictionaryTests()
                
            } catch (e: Exception) {
                addLog("分析Trie树结构出错: ${e.message}")
                e.printStackTrace()
            }
            
            Timber.i("预编译高频词典加载完成，加载耗时: ${loadTime/1000}秒，内存占用: ${formatFileSize(memoryUsage)}")
        } catch (e: Exception) {
            Timber.e(e, "加载预编译高频词典失败: ${e.message}")
            addLog("加载预编译高频词典失败: ${e.message}")
            
            // 重置加载状态
            _trieTree.setLoaded(false)
            _trieTree.clear()
            typeLoadedCount.clear()
            System.gc()
        }
    }
    
    /**
     * 执行高级词典测试
     * 深入测试各种类型的拼音输入，找出词典问题
     */
    private fun runAdvancedDictionaryTests() {
        addLog("--- 开始执行高级词典测试 ---")
        
        // 0. 检查词典的总体情况
        val nodeCount = _trieTree.getNodeCount()
        val wordCount = _trieTree.getWordCount()
        val rootKeys = _trieTree.getRootChildKeys()
        addLog("Trie树统计信息:")
        addLog("- 节点总数: $nodeCount")
        addLog("- 词条总数: $wordCount")
        addLog("- 根节点子节点: $rootKeys")
        
        // 查看原始数据库中的词条是什么样的
        addLog("检查原始词典样本（从Realm数据库）:")
        
        try {
            val sampleEntries = repository.getSampleEntries("chars", 10)
            sampleEntries.forEachIndexed { index, entry ->
                addLog("样本${index+1}: 词='${entry.word}', 拼音='${entry.pinyin}', 频率=${entry.frequency}")
            }
        } catch (e: Exception) {
            addLog("获取原始词典样本失败: ${e.message}")
        }
        
        addLog("--- 高级词典测试完成 ---")
    }
    
    /**
     * 检查Trie树是否为空
     */
    fun isTrieEmpty(): Boolean {
        return _trieTree.getWordCount() == 0
    }


} 