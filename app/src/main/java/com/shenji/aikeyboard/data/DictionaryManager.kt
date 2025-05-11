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
        
        // 高频词库类型列表
        val HIGH_FREQUENCY_DICT_TYPES = listOf("chars", "base")
        
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
    private var trieTree = TrieTree()
    
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
    
    /**
     * 初始化词典管理器
     */
    fun initialize() {
        // 仅设置初始化标志，不执行任何加载操作
        initialized = true
        Timber.d("词典管理器初始化完成")
    }
    
    /**
     * 搜索匹配的词条
     * @param prefix 要匹配的前缀
     * @param limit 返回结果的最大数量
     * @return 匹配的词条列表
     */
    fun searchWords(prefix: String, limit: Int = 10): List<WordFrequency> {
        // 优先从内存Trie树中查询
        val memoryResults = if (trieTree.isLoaded()) {
            trieTree.search(prefix, limit)
        } else {
            emptyList()
        }
        
        return memoryResults
    }
    
    /**
     * 检查是否已加载完成
     */
    fun isLoaded(): Boolean = trieTree.isLoaded()
    
    /**
     * 重置词典管理器，清空内存词库
     */
    fun reset() {
        // 清空Trie树
        trieTree.clear()
        
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
     * @param progressCallback 进度回调函数，参数为0到100的整数
     * @return 导出的文件路径
     */
    suspend fun exportHighFrequencyDictionaryToTrie(progressCallback: suspend (Int) -> Unit): String {
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
            addLog("开始导出高频词库(chars和base)到预编译Trie树 - 极低内存模式")
            progressCallback(5)
            
            // 获取词条数量
            val charsCount = repository.getEntryCountByType("chars")
            val baseCount = repository.getEntryCountByType("base")
            val totalCount = charsCount + baseCount
            
            addLog("chars词库: $charsCount 个词条")
            addLog("base词库: $baseCount 个词条")
            addLog("总计: $totalCount 个词条")
            
            progressCallback(10)
            
            // 设置更极端的批处理参数
            val batchSize = 500  // 减小每批次的大小，避免内存溢出
            val maxNodesPerBatch = 2000  // 减小每棵临时树的节点数
            val numWorkers = 2  // 减少并行工作器数量，避免内存压力
            
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
                    // 更新进度 - chars占40%
                    val adjustedProgress = 10 + ((progress * 40) / 100)
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
            progressCallback(50)
            
            // 使用超低内存模式处理base词库
            addLog("使用超低内存模式处理base词库...")
            val baseTempFiles = processWithUltraLowMemory(
                "base", 
                baseCount, 
                batchSize,
                maxNodesPerBatch,
                tempDir,
                { progress -> 
                    // 更新进度 - base占30%
                    val adjustedProgress = 50 + ((progress * 30) / 100)
                    progressCallback(adjustedProgress)
                    
                    // 更新预计剩余时间
                    val elapsedTime = System.currentTimeMillis() - startTime
                    val estimatedTotalTime = if (adjustedProgress > 0) elapsedTime * 100 / adjustedProgress else 0
                    val remainingTime = estimatedTotalTime - elapsedTime
                    if (progress % 5 == 0 && remainingTime > 0) {
                        addLog("预计剩余时间: ${formatTime(remainingTime)}")
                    }
                }
            )
            
            // 强制GC释放base词库占用的内存
            addLog("所有词库处理完成，执行内存清理...")
            System.gc()
            delay(1000) // 给GC更多时间
            progressCallback(80)
            
            // 使用超低内存模式合并临时文件
            val mergedTreeFile = mergeWithUltraLowMemory(tempDir, charsTempFiles + baseTempFiles, 
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
        
        // 设置极小的批次大小
        val microBatchSize = batchSize
        
        while (processedCount < totalCount) {
            // 取当前批次大小
            val currentBatchSize = min(microBatchSize, totalCount - processedCount)
            
            // 只加载当前批次的词条
            val entries = repository.getEntriesByType(type, processedCount, currentBatchSize)
            
            // 创建临时树
            val tempTree = TrieTree()
            
            // 添加词条到临时树
            for (entry in entries) {
                tempTree.insert(entry.word, entry.frequency)
            }
            
            // 保存临时树到文件
            val tempFile = File(tempDir, "${type}_batch_${processedCount}.bin")
            saveTreeToFile(tempTree, tempFile)
            tempFiles.add(tempFile)
            
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
     */
    private suspend fun mergeWithUltraLowMemory(
        tempDir: File,
        tempFiles: List<File>,
        progressCallback: suspend (Int) -> Unit
    ): File {
        val mergedFile = File(tempDir, "merged_trie.bin")
        val mergedTree = TrieTree()
        
        addLog("开始超低内存合并${tempFiles.size}个临时Trie树文件...")
        
        // 以极小的批次合并文件
        val microBatchSize = 2
        val batches = tempFiles.chunked(microBatchSize)
        
        var processedFiles = 0
        var batchIndex = 0
        
        for (batch in batches) {
            addLog("处理合并批次 ${++batchIndex}/${batches.size}，包含${batch.size}个文件")
            
            // 处理当前批次中的每个文件
            for (file in batch) {
                // 加载单个文件
                val tree = loadTrieFromFile(file)
                
                // 从树中提取所有词条
                val words = tree.getAllWords()
                
                // 分小批次将词条添加到合并树中
                val wordBatchSize = 100
                val wordBatches = words.chunked(wordBatchSize)
                
                for (wordBatch in wordBatches) {
                    for ((word, frequency) in wordBatch) {
                        mergedTree.insert(word, frequency)
                    }
                    
                    // 让出线程时间
                    yield()
                }
                
                // 清空源树，帮助GC
                tree.clear()
                
                // 更新进度
                processedFiles++
                val progress = (processedFiles * 100) / tempFiles.size
                progressCallback(progress)
                
                if (progress % 10 == 0 || progress >= 99) {
                    addLog("合并进度: ${processedFiles}/${tempFiles.size} (${progress}%)")
                }
                
                // 每合并几个文件执行一次GC
                if (processedFiles % (microBatchSize * 2) == 0) {
                    addLog("执行中间内存清理...")
                    System.gc()
                    delay(300)
                }
            }
        }
        
        addLog("合并完成，保存最终Trie树...")
        
        // 保存合并后的树
        saveTreeToFile(mergedTree, mergedFile)
        
        // 清空合并树，释放内存
        mergedTree.clear()
        System.gc()
        
        addLog("临时Trie树文件合并完成")
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
} 