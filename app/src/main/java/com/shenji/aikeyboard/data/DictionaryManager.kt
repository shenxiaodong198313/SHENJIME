package com.shenji.aikeyboard.data

import com.shenji.aikeyboard.ShenjiApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.*
import java.util.concurrent.ConcurrentHashMap
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
        
        // 每批加载的词条数量
        private const val BATCH_SIZE = 1000
        
        // 最大并行批次数
        private const val MAX_PARALLEL_BATCHES = 4
        
        // 预编译树文件名
        private const val PRECOMPILED_TREE_FILENAME = "precompiled_trie.bin"
        
        // 内存使用信息文件名
        private const val MEMORY_USAGE_FILENAME = "memory_usage.bin"
        
        // 词典版本信息文件名
        private const val DICTIONARY_VERSION_FILENAME = "dict_version.bin"
        
        // 初始化函数
        fun init() {
            instance.initialize()
        }
    }
    
    // 词典仓库实例
    private val repository = DictionaryRepository()
    
    // 高频词库Trie树
    private var trieTree = TrieTree()
    
    // 各类型词库的内存占用记录
    private val typeMemoryUsage = mutableMapOf<String, Long>()
    
    // 各类型词库的加载总词条数记录
    private val typeEntryCount = mutableMapOf<String, Int>()
    
    // 各类型词库的已加载词条数记录
    private val typeLoadedCount = mutableMapOf<String, Int>()
    
    // 各类型词库的版本哈希值（用于检测更新）
    private val typeVersionHash = mutableMapOf<String, String>()
    
    // 加载任务
    private var loadingJob: Job? = null
    
    // 后台更新任务
    private var updateJob: Job? = null
    
    // 加载状态监听器
    private var loadingListener: ((Boolean) -> Unit)? = null
    
    // 加载进度监听器列表 - 允许多个组件同时监听进度
    private val progressListeners = mutableListOf<(String, Float) -> Unit>()
    
    // 日志记录
    private val loadingLogs = ConcurrentHashMap<Long, String>()
    private var logId: Long = 0
    
    // 存储每个类型的加载进度
    private val typeLoadingProgress = mutableMapOf<String, Float>()
    
    // Trie树插入锁，确保并发安全
    private val treeInsertLock = Mutex()
    
    /**
     * 初始化词典管理器，开始异步加载高频词库
     */
    fun initialize() {
        // 如果已经在加载中，则不重复加载
        if (loadingJob?.isActive == true) {
            Timber.d("词典已在加载中，跳过重复初始化")
            addLog("词典已在加载中，跳过重复初始化")
            return
        }
        
        // 尝试加载预编译树
        loadingJob = CoroutineScope(Dispatchers.IO).launch {
            addLog("开始初始化词典管理器")
            
            val loadTime = measureTimeMillis {
                // 尝试加载预编译树
                if (loadPrecompiledTrie()) {
                    addLog("成功从预编译文件加载Trie树")
                    
                    // 通知监听器加载完成
                    loadingListener?.invoke(true)
                    
                    // 在后台检查词典是否有更新
                    scheduleVersionCheck()
                    
                    return@measureTimeMillis
                }
                
                // 如果没有预编译树或加载失败，使用并行加载
                addLog("预编译树加载失败，使用并行加载")
                
                // 清空当前树
                trieTree.clear()
                typeMemoryUsage.clear()
                typeLoadedCount.clear()
                
                // 获取词库类型的总词条数
                for (dictType in HIGH_FREQUENCY_DICT_TYPES) {
                    val count = repository.getEntryCountByType(dictType)
                    typeEntryCount[dictType] = count
                    addLog("词库 $dictType 总词条数: $count")
                }
                
                // 并行加载高频词库类型
                var totalLoaded = 0
                
                val results = HIGH_FREQUENCY_DICT_TYPES.map { dictType ->
                    async {
                        val count = loadDictionaryTypeParallel(dictType)
                        addLog("已加载词库 $dictType: $count 个词条")
                        Timber.d("已加载词库 $dictType: $count 个词条")
                        Pair(dictType, count)
                    }
                }
                
                // 等待所有加载任务完成
                val loadResults = results.awaitAll()
                totalLoaded = loadResults.sumOf { it.second }
                
                // 设置加载完成标志
                trieTree.setLoaded(true)
                
                // 通知监听器加载完成
                loadingListener?.invoke(true)
                
                // 计算并保存词典版本哈希
                calculateAndSaveDictionaryVersions()
                
                // 保存预编译树供下次使用
                savePrecompiledTrie()
                
                addLog("高频词库加载完成，共加载 $totalLoaded 个词条，实际树中词条数约 ${trieTree.getEstimatedWordCount()} 个")
                Timber.i("高频词库加载完成，共加载 $totalLoaded 个词条，实际树中词条数约 ${trieTree.getEstimatedWordCount()} 个")
            }
            
            addLog("词典加载耗时: ${loadTime}ms")
            Timber.i("词典加载耗时: ${loadTime}ms")
        }
    }
    
    /**
     * 并行加载指定类型的词典到Trie树
     */
    private suspend fun loadDictionaryTypeParallel(type: String): Int {
        val totalCount = typeEntryCount[type] ?: repository.getEntryCountByType(type)
        val batchCount = (totalCount + BATCH_SIZE - 1) / BATCH_SIZE
        
        addLog("开始并行加载词典类型 $type，总词条数：$totalCount，分为 $batchCount 批")
        
        // 清空已加载计数
        typeLoadedCount[type] = 0
        
        try {
            // 创建多个并行任务，每个处理一批数据
            val parallelBatches = min(batchCount, MAX_PARALLEL_BATCHES)
            
            // 如果总批次数少于预设的并行度，那就按总批次数创建任务
            val tasks = (0 until parallelBatches).map { batchIndex ->
                CoroutineScope(Dispatchers.IO).async {
                    var loadedCount = 0
                    var currentBatch = batchIndex
                    
                    while (currentBatch < batchCount) {
                        val offset = currentBatch * BATCH_SIZE
                        val entries = repository.getEntriesByType(type, offset, BATCH_SIZE)
                        
                        if (entries.isEmpty()) break
                        
                        // 加锁保证Trie树插入安全
                        treeInsertLock.withLock {
                            for (entry in entries) {
                                trieTree.insert(entry.word, entry.frequency)
                                loadedCount++
                            }
                        }
                        
                        // 更新进度
                        val localLoadedCount = loadedCount
                        synchronized(typeLoadedCount) {
                            typeLoadedCount[type] = (typeLoadedCount[type] ?: 0) + entries.size
                            val progress = typeLoadedCount[type]!!.toFloat() / totalCount
                            notifyProgress(type, progress)
                        }
                        
                        // 跳到下一个需要处理的批次
                        currentBatch += parallelBatches
                    }
                    
                    loadedCount
                }
            }
            
            // 等待所有批次处理完成并汇总
            val results = tasks.awaitAll()
            val totalLoaded = results.sum()
            
            // 估算并记录该类型词库的内存占用
            if (totalLoaded > 0) {
                // 一个字符约占用2字节，加上节点开销约4字节，词频等其他字段约4字节
                val averageWordLength = 2 // 假设平均词长为2
                val bytesPerWord = (averageWordLength * 2) + 8 // 字符+节点开销+词频
                val memoryUsage = bytesPerWord * totalLoaded
                
                typeMemoryUsage[type] = memoryUsage.toLong()
                addLog("词典类型 $type 并行加载完成，共 $totalLoaded 个词条，估计内存占用：${formatFileSize(memoryUsage.toLong())}")
            }
            
            return totalLoaded
        } catch (e: Exception) {
            Timber.e(e, "并行加载词典类型 $type 失败")
            addLog("并行加载词典类型 $type 失败: ${e.message}")
            return 0
        }
    }
    
    /**
     * 计算并保存词典版本哈希值
     */
    private fun calculateAndSaveDictionaryVersions() {
        try {
            val context = ShenjiApplication.appContext
            
            // 计算各类型词典的版本哈希
            for (type in HIGH_FREQUENCY_DICT_TYPES) {
                // 获取该类型的最后更新时间和总词条数作为版本特征
                val lastModified = repository.getLastModifiedTime(type)
                val totalCount = repository.getEntryCountByType(type)
                // 简单哈希：结合更新时间和词条数
                val versionHash = "$lastModified-$totalCount"
                typeVersionHash[type] = versionHash
                
                addLog("词典类型 $type 的版本哈希: $versionHash")
            }
            
            // 保存版本信息到文件
            val versionFile = File(context.filesDir, DICTIONARY_VERSION_FILENAME)
            ObjectOutputStream(FileOutputStream(versionFile)).use { out ->
                out.writeObject(typeVersionHash)
            }
            
            addLog("词典版本信息保存成功")
        } catch (e: Exception) {
            Timber.e(e, "计算和保存词典版本信息失败")
            addLog("计算和保存词典版本信息失败: ${e.message}")
        }
    }
    
    /**
     * 加载保存的词典版本哈希值
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadDictionaryVersions(): Map<String, String> {
        val context = ShenjiApplication.appContext
        val versionFile = File(context.filesDir, DICTIONARY_VERSION_FILENAME)
        
        if (!versionFile.exists()) {
            addLog("没有找到词典版本信息文件")
            return emptyMap()
        }
        
        try {
            // 从文件加载版本信息
            val savedVersions = ObjectInputStream(FileInputStream(versionFile)).use { input ->
                input.readObject() as Map<String, String>
            }
            
            addLog("已加载保存的词典版本信息")
            return savedVersions
        } catch (e: Exception) {
            Timber.e(e, "加载词典版本信息失败")
            addLog("加载词典版本信息失败: ${e.message}")
            
            // 删除可能损坏的版本文件
            versionFile.delete()
            
            return emptyMap()
        }
    }
    
    /**
     * 检查词典是否有更新，并在必要时触发重建
     */
    private fun scheduleVersionCheck() {
        // 取消之前的检查任务
        updateJob?.cancel()
        
        // 创建新的检查任务
        updateJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                addLog("开始检查词典版本")
                
                // 加载保存的版本哈希
                val savedVersions = loadDictionaryVersions()
                if (savedVersions.isEmpty()) {
                    addLog("没有保存的版本信息，计算当前版本")
                    calculateAndSaveDictionaryVersions()
                    return@launch
                }
                
                // 检查各类型词典是否有更新
                var needsUpdate = false
                
                for (type in HIGH_FREQUENCY_DICT_TYPES) {
                    // 获取当前版本特征
                    val lastModified = repository.getLastModifiedTime(type)
                    val totalCount = repository.getEntryCountByType(type)
                    val currentVersionHash = "$lastModified-$totalCount"
                    
                    // 与保存的版本比较
                    val savedVersionHash = savedVersions[type]
                    if (savedVersionHash != currentVersionHash) {
                        addLog("词典类型 $type 已更新: $savedVersionHash -> $currentVersionHash")
                        needsUpdate = true
                        break
                    }
                }
                
                // 如果有更新，在后台重建预编译树
                if (needsUpdate) {
                    addLog("检测到词典更新，开始在后台重建预编译树")
                    rebuildPrecompiledTreeInBackground()
                } else {
                    addLog("词典版本检查完成，没有发现更新")
                }
            } catch (e: Exception) {
                Timber.e(e, "检查词典版本失败")
                addLog("检查词典版本失败: ${e.message}")
            }
        }
    }
    
    /**
     * 在后台重建预编译树
     */
    private fun rebuildPrecompiledTreeInBackground() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                addLog("开始在后台重建预编译树")
                
                // 创建临时Trie树
                val tempTree = TrieTree()
                
                // 并行加载所有高频词库类型
                val tasks = HIGH_FREQUENCY_DICT_TYPES.map { dictType ->
                    async {
                        loadDictionaryTypeToTempTree(dictType, tempTree)
                    }
                }
                
                // 等待所有加载任务完成
                tasks.awaitAll()
                
                // 设置加载完成标志
                tempTree.setLoaded(true)
                
                // 计算并保存新的词典版本哈希
                calculateAndSaveDictionaryVersions()
                
                // 创建可序列化树
                val serializableTree = SerializableTrieTree.fromRuntimeTree(tempTree)
                
                // 保存到临时文件
                val context = ShenjiApplication.appContext
                val tempTreeFile = File(context.filesDir, "${PRECOMPILED_TREE_FILENAME}.temp")
                
                ObjectOutputStream(FileOutputStream(tempTreeFile)).use { out ->
                    out.writeObject(serializableTree)
                }
                
                // 保存内存使用信息
                val tempMemoryFile = File(context.filesDir, "${MEMORY_USAGE_FILENAME}.temp")
                ObjectOutputStream(FileOutputStream(tempMemoryFile)).use { out ->
                    out.writeObject(typeMemoryUsage)
                }
                
                // 成功后，替换原文件
                val treeFile = File(context.filesDir, PRECOMPILED_TREE_FILENAME)
                val memoryFile = File(context.filesDir, MEMORY_USAGE_FILENAME)
                
                if (tempTreeFile.exists()) {
                    tempTreeFile.renameTo(treeFile)
                }
                
                if (tempMemoryFile.exists()) {
                    tempMemoryFile.renameTo(memoryFile)
                }
                
                addLog("预编译树后台重建完成，文件大小: ${formatFileSize(treeFile.length())}")
                Timber.i("预编译树后台重建完成，文件大小: ${formatFileSize(treeFile.length())}")
            } catch (e: Exception) {
                Timber.e(e, "后台重建预编译树失败")
                addLog("后台重建预编译树失败: ${e.message}")
            }
        }
    }
    
    /**
     * 加载词典类型到临时树
     */
    private suspend fun loadDictionaryTypeToTempTree(type: String, tempTree: TrieTree): Int {
        var loadedCount = 0
        var offset = 0
        val totalCount = repository.getEntryCountByType(type)
        val batchCount = (totalCount + BATCH_SIZE - 1) / BATCH_SIZE
        
        try {
            // 使用并行批处理
            val parallelBatches = min(batchCount, MAX_PARALLEL_BATCHES)
            val mutex = Mutex()
            
            val tasks = (0 until parallelBatches).map { batchIndex ->
                CoroutineScope(Dispatchers.IO).async {
                    var localLoadedCount = 0
                    var currentBatch = batchIndex
                    
                    while (currentBatch < batchCount) {
                        val batchOffset = currentBatch * BATCH_SIZE
                        val entries = repository.getEntriesByType(type, batchOffset, BATCH_SIZE)
                        
                        if (entries.isEmpty()) break
                        
                        // 加锁保证Trie树插入安全
                        mutex.withLock {
                            for (entry in entries) {
                                tempTree.insert(entry.word, entry.frequency)
                                localLoadedCount++
                            }
                        }
                        
                        // 跳到下一个需要处理的批次
                        currentBatch += parallelBatches
                    }
                    
                    localLoadedCount
                }
            }
            
            // 等待所有批次处理完成并汇总
            val results = tasks.awaitAll()
            loadedCount = results.sum()
            
            addLog("词典类型 $type 后台加载完成，共 $loadedCount 个词条")
        } catch (e: Exception) {
            Timber.e(e, "后台加载词典类型 $type 失败")
            addLog("后台加载词典类型 $type 失败: ${e.message}")
        }
        
        return loadedCount
    }
    
    /**
     * 保存预编译Trie树到文件
     */
    private fun savePrecompiledTrie() {
        val context = ShenjiApplication.appContext
        
        try {
            addLog("开始保存预编译Trie树")
            
            // 创建可序列化树
            val serializableTree = SerializableTrieTree.fromRuntimeTree(trieTree)
            
            // 保存树到文件
            val treeFile = File(context.filesDir, PRECOMPILED_TREE_FILENAME)
            ObjectOutputStream(FileOutputStream(treeFile)).use { out ->
                out.writeObject(serializableTree)
            }
            
            // 保存内存使用信息
            val memoryFile = File(context.filesDir, MEMORY_USAGE_FILENAME)
            ObjectOutputStream(FileOutputStream(memoryFile)).use { out ->
                out.writeObject(typeMemoryUsage)
            }
            
            addLog("预编译Trie树保存成功，文件大小: ${formatFileSize(treeFile.length())}")
            Timber.i("预编译Trie树保存成功，文件大小: ${formatFileSize(treeFile.length())}")
        } catch (e: Exception) {
            Timber.e(e, "保存预编译Trie树失败")
            addLog("保存预编译Trie树失败: ${e.message}")
        }
    }
    
    /**
     * 从预编译文件加载Trie树
     * @return 是否成功加载
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadPrecompiledTrie(): Boolean {
        val context = ShenjiApplication.appContext
        val treeFile = File(context.filesDir, PRECOMPILED_TREE_FILENAME)
        
        if (!treeFile.exists()) {
            addLog("没有找到预编译Trie树文件")
            return false
        }
        
        try {
            addLog("开始加载预编译Trie树，文件大小: ${formatFileSize(treeFile.length())}")
            
            // 加载可序列化树
            val serializableTree = ObjectInputStream(FileInputStream(treeFile)).use { input ->
                input.readObject() as SerializableTrieTree
            }
            
            // 转换为运行时树
            trieTree = serializableTree.toRuntimeTree()
            
            // 加载内存占用信息
            val memoryFile = File(context.filesDir, MEMORY_USAGE_FILENAME)
            if (memoryFile.exists()) {
                ObjectInputStream(FileInputStream(memoryFile)).use { input ->
                    typeMemoryUsage.clear()
                    typeMemoryUsage.putAll(input.readObject() as Map<String, Long>)
                }
            }
            
            // 推导已加载词条数量
            for (type in HIGH_FREQUENCY_DICT_TYPES) {
                val memoryUsage = typeMemoryUsage[type] ?: 0L
                val averageWordLength = 2 // 假设平均词长为2
                val bytesPerWord = (averageWordLength * 2) + 8 // 字符+节点开销+词频
                
                if (bytesPerWord > 0 && memoryUsage > 0) {
                    val estimatedWordCount = (memoryUsage / bytesPerWord).toInt()
                    typeLoadedCount[type] = estimatedWordCount
                    
                    // 查询总词条数
                    val totalCount = repository.getEntryCountByType(type)
                    typeEntryCount[type] = totalCount
                    
                    // 计算并通知进度
                    if (totalCount > 0) {
                        val progress = min(1.0f, estimatedWordCount.toFloat() / totalCount)
                        notifyProgress(type, progress)
                    }
                }
            }
            
            addLog("预编译Trie树加载成功，已加载到内存中")
            Timber.i("预编译Trie树加载成功，已加载到内存中")
            return true
        } catch (e: Exception) {
            Timber.e(e, "加载预编译Trie树失败")
            addLog("加载预编译Trie树失败: ${e.message}")
            
            // 如果加载失败，删除可能损坏的预编译文件
            treeFile.delete()
            File(context.filesDir, MEMORY_USAGE_FILENAME).delete()
            
            return false
        }
    }
    
    /**
     * 加载指定类型的词典到Trie树（传统非并行方式，仅作为备用）
     */
    private fun loadDictionaryType(type: String): Int {
        var loadedCount = 0
        var offset = 0
        
        try {
            val totalCount = typeEntryCount[type] ?: repository.getEntryCountByType(type)
            typeEntryCount[type] = totalCount
            
            addLog("开始加载词典类型 $type，总词条数：$totalCount")
            
            // 清空已加载计数
            typeLoadedCount[type] = 0
            
            // 分批加载词条，避免一次性加载过多数据导致内存问题
            while (true) {
                val entries = repository.getEntriesByType(type, offset, BATCH_SIZE)
                if (entries.isEmpty()) break
                
                // 将词条插入Trie树
                for (entry in entries) {
                    trieTree.insert(entry.word, entry.frequency)
                    loadedCount++
                }
                
                // 更新已加载计数并通知进度
                typeLoadedCount[type] = loadedCount
                val progress = if (totalCount > 0) loadedCount.toFloat() / totalCount else 0f
                notifyProgress(type, progress)
                
                offset += entries.size
                
                // 如果获取的数量小于批次大小，说明已经加载完毕
                if (entries.size < BATCH_SIZE) break
            }
            
            // 估算并记录该类型词库的内存占用
            if (loadedCount > 0) {
                // 一个字符约占用2字节，加上节点开销约4字节，词频等其他字段约4字节
                val averageWordLength = 2 // 假设平均词长为2
                val bytesPerWord = (averageWordLength * 2) + 8 // 字符+节点开销+词频
                val memoryUsage = bytesPerWord * loadedCount
                
                typeMemoryUsage[type] = memoryUsage.toLong()
                addLog("词典类型 $type 加载完成，共 $loadedCount 个词条，估计内存占用：${formatFileSize(memoryUsage.toLong())}")
            }
        } catch (e: Exception) {
            Timber.e(e, "加载词典类型 $type 失败")
            addLog("加载词典类型 $type 失败: ${e.message}")
        }
        
        return loadedCount
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
     * 设置加载状态监听器
     */
    fun setLoadingListener(listener: (Boolean) -> Unit) {
        this.loadingListener = listener
    }
    
    /**
     * 添加加载进度监听器
     */
    fun addProgressListener(listener: (String, Float) -> Unit) {
        progressListeners.add(listener)
    }
    
    /**
     * 移除加载进度监听器
     */
    fun removeProgressListener(listener: (String, Float) -> Unit) {
        progressListeners.remove(listener)
    }
    
    /**
     * 通知进度更新
     */
    private fun notifyProgress(type: String, progress: Float) {
        // 保存最新进度以便页面重新加载时恢复
        typeLoadingProgress[type] = progress
        
        // 通知所有监听器
        for (listener in progressListeners) {
            listener(type, progress)
        }
    }
    
    /**
     * 检查特定类型的词库是否已加载到内存
     */
    fun isTypeLoaded(type: String): Boolean {
        // 如果类型不在高频词库列表中，直接返回false
        if (type !in HIGH_FREQUENCY_DICT_TYPES) return false
        
        // 检查整体树是否已加载
        if (!trieTree.isLoaded()) return false
        
        // 检查加载进度，如果进度达到95%以上，视为已加载
        val progress = getTypeLoadingProgress(type)
        if (progress >= 0.95f) return true
        
        // 检查已加载计数
        val loaded = typeLoadedCount[type] ?: 0
        val total = typeEntryCount[type] ?: 1
        
        // 如果已加载超过95%或者已加载超过1000个词条，视为已加载
        return (loaded >= total * 0.95) || (loaded > 1000)
    }
    
    /**
     * 获取指定类型词库的加载进度 (0.0f - 1.0f)
     */
    fun getTypeLoadingProgress(type: String): Float {
        // 优先从已保存的进度中获取
        val savedProgress = typeLoadingProgress[type]
        if (savedProgress != null && savedProgress > 0) {
            return savedProgress
        }
        
        // 如果没有已保存的进度，计算当前进度
        val loaded = typeLoadedCount[type] ?: 0
        val total = typeEntryCount[type] ?: return 0f
        return if (total > 0) loaded.toFloat() / total else 0f
    }
    
    /**
     * 获取指定类型词库的内存占用(字节)
     */
    fun getTypeMemoryUsage(type: String): Long {
        return typeMemoryUsage[type] ?: 0L
    }
    
    /**
     * 获取总内存占用
     */
    fun getTotalMemoryUsage(): Long {
        return typeMemoryUsage.values.sum()
    }
    
    /**
     * 检查是否已加载完成
     */
    fun isLoaded(): Boolean = trieTree.isLoaded()
    
    /**
     * 重置词典管理器，清空内存词库
     */
    fun reset() {
        // 取消正在执行的加载任务
        loadingJob?.cancel()
        
        // 取消后台更新任务
        updateJob?.cancel()
        
        // 清空Trie树
        trieTree.clear()
        
        // 清空内存占用和加载计数
        typeMemoryUsage.clear()
        typeLoadedCount.clear()
        
        addLog("词典管理器已重置")
        Timber.i("词典管理器已重置")
    }
    
    /**
     * 强制重建预编译Trie树
     * 用于手动触发重建，例如当用户添加或删除词条后
     */
    fun forceRebuildPrecompiledTree() {
        CoroutineScope(Dispatchers.IO).launch {
            // 先计算新的版本哈希
            calculateAndSaveDictionaryVersions()
            
            // 然后重建预编译树
            rebuildPrecompiledTreeInBackground()
            
            addLog("手动触发重建预编译树")
        }
    }
    
    /**
     * 添加日志记录
     */
    private fun addLog(message: String) {
        val timestamp = System.currentTimeMillis()
        val id = logId++
        loadingLogs[id] = "[$timestamp] $message"
        
        // 仅保留最新的100条日志
        if (loadingLogs.size > 100) {
            val oldestKey = loadingLogs.keys.minOrNull()
            oldestKey?.let { loadingLogs.remove(it) }
        }
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
     * 将字节大小转换为可读性好的字符串形式
     */
    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
} 