package com.shenji.aikeyboard.data.trie

import android.content.Context
import android.content.SharedPreferences
import com.shenji.aikeyboard.data.DictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.channels.Channel
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

/**
 * 高性能Trie树构建器 - 支持断点续传和内存优化
 * 特性：
 * - 充分利用8核CPU并行处理
 * - 优化16G内存使用
 * - 断点续传功能
 * - 内存监控和自动调节
 * - 崩溃恢复机制
 * - 改进的序列化/反序列化
 */
class TrieBuilder(
    private val context: Context,
    private val customBatchSize: Int = 1000,  // 增大批量大小，充分利用内存
    private val customNumWorkers: Int = -1   // 默认使用自动计算的线程数
) {

    companion object {
        private const val PREFS_NAME = "trie_build_progress"
        private const val MAX_CONCURRENT_WORKERS = 6 // 最大并发工作线程（保留2核给系统）
        private const val MEMORY_BUFFER_SIZE = 10000 // 内存缓冲区大小
        private const val CHECKPOINT_INTERVAL = 50 // 每50个批次保存一次检查点
        private const val MEMORY_CHECK_INTERVAL = 10 // 每10个批次检查一次内存
        private const val MAX_MEMORY_USAGE_RATIO = 0.75f // 最大内存使用率75%
    }

    // 词典仓库，用于获取词语数据
    private val repository = DictionaryRepository()
    
    // 断点续传相关
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 并发控制
    private val workerSemaphore = Semaphore(MAX_CONCURRENT_WORKERS)
    private val insertChannel = Channel<TrieInsertBatch>(capacity = Channel.UNLIMITED)
    
    // 统计数据
    private val processedCount = AtomicLong(0)
    private val skipCount = AtomicLong(0)
    private val errorCount = AtomicLong(0)
    private val insertCount = AtomicLong(0)
    
    // Trie树类型枚举
    enum class TrieType {
        CHARS, // 单字词典
        BASE,  // 基础词典
    }
    
    // 批次插入数据类
    data class TrieInsertBatch(
        val entries: List<TrieInsertEntry>
    )
    
    data class TrieInsertEntry(
        val key: String,
        val word: String,
        val frequency: Int
    )
    
    // 构建进度数据类
    data class BuildProgress(
        val totalBatches: Int,
        val completedBatches: Int,
        val processedEntries: Long,
        val totalEntries: Long,
        val lastBatchIndex: Int
    )
    
    /**
     * 构建单字Trie树 - 优化版本
     * @param progressCallback 进度回调函数
     * @return 构建完成的PinyinTrie对象
     */
    suspend fun buildCharsTrie(progressCallback: (Int, String) -> Unit): PinyinTrie = withContext(Dispatchers.IO) {
        val trie = PinyinTrie()
        
        try {
            progressCallback(0, "开始从单字词典构建Trie树")
            Timber.d("开始构建单字Trie树")
            
            // 获取单字列表
            val chars = repository.getAllChars()
            val totalCount = chars.size
            
            progressCallback(10, "获取到${totalCount}个单字，开始构建")
            Timber.d("获取到${totalCount}个单字")
            
            // 统计数据
            var processedCount = 0
            var skipCount = 0
            var multiPinyinCount = 0
            var totalPinyinCount = 0
            
            // 分批处理，避免内存溢出
            val batchSize = 1000
            val totalBatches = (totalCount + batchSize - 1) / batchSize
            
            for (batchIndex in 0 until totalBatches) {
                val startIndex = batchIndex * batchSize
                val endIndex = minOf(startIndex + batchSize, totalCount)
                val batch = chars.subList(startIndex, endIndex)
                
                // 处理当前批次
                batch.forEachIndexed { index, entry ->
                    try {
                        // 获取并处理拼音
                        val pinyin = entry.pinyin?.lowercase()
                        if (pinyin.isNullOrBlank()) {
                            skipCount++
                            return@forEachIndexed
                        }
                        
                        // 处理拼音（新数据库结构中拼音已经是无声调的）
                        // 检查是否包含多个拼音（用空格分隔）
                        val pinyinList = if (pinyin.contains(" ")) {
                            // 多音节拼音，如 "bei jing"
                            listOf(pinyin, pinyin.replace(" ", "")) // 同时插入带空格和不带空格的版本
                        } else {
                            // 单音节拼音
                            listOf(pinyin)
                        }
                        
                        if (pinyinList.isEmpty()) {
                            skipCount++
                            return@forEachIndexed
                        }
                        
                        totalPinyinCount += pinyinList.size
                        processedCount++
                        
                        // 为每个拼音创建Trie路径
                        pinyinList.forEach { p ->
                            trie.insert(p.trim(), entry.word, entry.frequency ?: 0)
                        }
                        
                        // 如果有首字母缩写，也插入到Trie中
                        if (!entry.initialLetters.isNullOrBlank()) {
                            trie.insert(entry.initialLetters, entry.word, entry.frequency ?: 0)
                        }
                        
                    } catch (e: Exception) {
                        Timber.e(e, "处理单字'${entry.word}'失败")
                        skipCount++
                    }
                }
                
                // 更新进度
                val progress = 10 + (batchIndex * 80) / totalBatches
                progressCallback(progress, "已处理批次 ${batchIndex + 1}/${totalBatches}")
                
                // 定期垃圾回收
                if (batchIndex % 5 == 4) {
                    System.gc()
                    delay(100L)
                }
            }
            
            progressCallback(90, "Trie树构建完成，正在优化内存")
            
            // 最终垃圾回收
            System.gc()
            delay(500L)
            
            // 获取内存统计信息
            val stats = trie.getMemoryStats()
            
            // 打印详细的构建统计信息
            val statsSummary = "单字总数: $totalCount, 实际处理: $processedCount, " +
                      "跳过: $skipCount, 多音字: $multiPinyinCount, " +
                      "拼音总数: $totalPinyinCount"
            Timber.d("单字Trie构建统计: $statsSummary")
            
            progressCallback(100, "完成: ${stats.toString()} ($statsSummary)")
            
            return@withContext trie
        } catch (e: Exception) {
            Timber.e(e, "构建单字Trie树失败")
            progressCallback(-1, "构建失败: ${e.message}")
            throw e
        }
    }
    
    /**
     * 高性能基础词典Trie树构建 - 支持断点续传
     * @param progressCallback 进度回调函数
     * @return 构建完成的PinyinTrie对象
     */
    suspend fun buildBaseTrie(progressCallback: (Int, String) -> Unit): PinyinTrie = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            progressCallback(0, "初始化高性能Trie构建器...")
            Timber.d("开始高性能基础词典Trie树构建")
            
            // 创建详细日志
            val logFile = createBuildLog()
            logFile.appendText("=== 高性能基础词典Trie树构建开始 ===\n")
            logFile.appendText("开始时间: ${java.util.Date()}\n")
            logFile.appendText("系统信息: ${getSystemInfo()}\n\n")
            
            // 检查是否有之前的构建进度
            val existingProgress = loadBuildProgress()
            val trie = if (existingProgress != null) {
                progressCallback(5, "发现之前的构建进度，准备断点续传...")
                logFile.appendText("发现断点续传数据: 已完成${existingProgress.completedBatches}/${existingProgress.totalBatches}批次\n")
                loadPartialTrie() ?: PinyinTrie()
            } else {
                progressCallback(5, "开始全新构建...")
                logFile.appendText("开始全新构建\n")
                clearBuildProgress() // 清理旧的进度数据
                PinyinTrie()
            }
            
            // 获取基础词典总数
            val totalCount = repository.getEntryCountByType("base")
            progressCallback(10, "获取到${totalCount}个基础词条")
            logFile.appendText("基础词条总数: $totalCount\n")
            
            // 计算优化的批次参数
            val optimizedParams = calculateOptimizedParams(totalCount)
            val batchSize = optimizedParams.batchSize
            val numWorkers = optimizedParams.numWorkers
            val totalBatches = (totalCount + batchSize - 1) / batchSize
            
            logFile.appendText("优化参数: 批次大小=$batchSize, 工作线程=$numWorkers, 总批次=$totalBatches\n\n")
            
            // 确定开始批次（断点续传）
            val startBatchIndex = existingProgress?.lastBatchIndex?.plus(1) ?: 0
            
            if (startBatchIndex > 0) {
                progressCallback(15, "从批次${startBatchIndex + 1}开始断点续传...")
                logFile.appendText("断点续传: 从批次${startBatchIndex + 1}开始\n")
            }
            
            // 重置统计计数器
            resetCounters()
            
            // 启动异步插入处理器
            val insertJob = startAsyncInsertProcessor(trie, logFile)
            
            try {
                // 分批并行处理
                for (batchIndex in startBatchIndex until totalBatches) {
                    try {
                        // 内存检查和清理
                        if (batchIndex % MEMORY_CHECK_INTERVAL == 0) {
                            performMemoryCheck(logFile)
                        }
                        
                        // 处理当前批次
                        val batchStartTime = System.currentTimeMillis()
                        val success = processBatchWithRetry(batchIndex, batchSize, numWorkers, logFile)
                        val batchTime = System.currentTimeMillis() - batchStartTime
                        
                        if (!success) {
                            logFile.appendText("批次${batchIndex + 1}处理失败，跳过\n")
                            continue
                        }
                        
                        // 更新进度
                        val overallProgress = 15 + ((batchIndex - startBatchIndex + 1) * 75) / (totalBatches - startBatchIndex)
                        val progressMsg = "批次${batchIndex + 1}/${totalBatches} (${batchTime}ms) - 已处理${processedCount.get()}条"
                        progressCallback(overallProgress, progressMsg)
                        
                        // 保存检查点
                        if (batchIndex % CHECKPOINT_INTERVAL == 0) {
                            saveCheckpoint(trie, batchIndex, totalBatches, totalCount.toLong(), logFile)
                        }
                        
                        // 自适应延迟，防止过热
                        if (batchIndex % 20 == 19) {
                            delay(100L) // 每20个批次休息100ms
                        }
                        
                    } catch (e: Exception) {
                        errorCount.incrementAndGet()
                        logFile.appendText("批次${batchIndex + 1}异常: ${e.message}\n")
                        Timber.e(e, "批次${batchIndex + 1}处理异常")
                        
                        // 短暂延迟后继续
                        delay(500L)
                    }
                }
                
                // 关闭插入通道并等待完成
                insertChannel.close()
                insertJob.join()
                
                progressCallback(95, "构建完成，正在优化和保存...")
                
                // 最终优化
                performFinalOptimization(trie, logFile)
                
                // 清理断点续传数据
                clearBuildProgress()
                
                val totalTime = System.currentTimeMillis() - startTime
                val stats = trie.getMemoryStats()
                val finalSummary = "总耗时: ${totalTime}ms, 处理: ${processedCount.get()}, 跳过: ${skipCount.get()}, 错误: ${errorCount.get()}, 插入: ${insertCount.get()}"
                
                logFile.appendText("\n=== 构建完成 ===\n")
                logFile.appendText("$finalSummary\n")
                logFile.appendText("Trie统计: $stats\n")
                logFile.appendText("结束时间: ${java.util.Date()}\n")
                
                progressCallback(100, "完成: $stats ($finalSummary)")
                Timber.d("基础词典Trie构建完成: $finalSummary")
                
                return@withContext trie
                
            } catch (e: Exception) {
                // 保存当前进度以便下次继续
                saveEmergencyCheckpoint(trie, logFile)
                throw e
            }
            
        } catch (e: Exception) {
            val errorMsg = "高性能Trie构建失败: ${e.message}"
            Timber.e(e, errorMsg)
            progressCallback(-1, "构建失败: ${e.message}")
            
            // 保存错误日志
            saveErrorLog(e)
            throw e
        }
    }
    
    /**
     * 计算优化的构建参数
     */
    private fun calculateOptimizedParams(totalCount: Int): OptimizedParams {
        val runtime = Runtime.getRuntime()
        val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)
        val availableCores = runtime.availableProcessors()
        
        // 根据内存大小调整批次大小
        val batchSize = when {
            maxMemoryMB >= 1024 -> 2000  // 1GB以上内存
            maxMemoryMB >= 512 -> 1500   // 512MB-1GB内存
            else -> 1000                 // 512MB以下内存
        }.coerceAtMost(totalCount / 10) // 不超过总数的1/10
        
        // 根据CPU核心数调整工作线程数
        val numWorkers = if (customNumWorkers > 0) {
            customNumWorkers.coerceAtMost(MAX_CONCURRENT_WORKERS)
        } else {
            when {
                availableCores >= 8 -> 6  // 8核以上使用6个工作线程
                availableCores >= 4 -> 4  // 4-7核使用4个工作线程
                else -> 2                 // 4核以下使用2个工作线程
            }
        }
        
        Timber.d("优化参数: 内存=${maxMemoryMB}MB, 核心=${availableCores}, 批次大小=${batchSize}, 工作线程=${numWorkers}")
        
        return OptimizedParams(batchSize, numWorkers)
    }
    
    data class OptimizedParams(val batchSize: Int, val numWorkers: Int)
    
    /**
     * 启动异步插入处理器
     */
    private fun startAsyncInsertProcessor(trie: PinyinTrie, logFile: File) = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        try {
            val buffer = mutableListOf<TrieInsertEntry>()
            
            for (batch in insertChannel) {
                buffer.addAll(batch.entries)
                
                // 当缓冲区达到一定大小时批量插入
                if (buffer.size >= MEMORY_BUFFER_SIZE) {
                    val insertStartTime = System.currentTimeMillis()
                    
                    buffer.forEach { entry ->
                        trie.insert(entry.key, entry.word, entry.frequency)
                        insertCount.incrementAndGet()
                    }
                    
                    val insertTime = System.currentTimeMillis() - insertStartTime
                    logFile.appendText("批量插入${buffer.size}条，耗时${insertTime}ms\n")
                    
                    buffer.clear()
                    
                    // 定期触发GC
                    if (insertCount.get() % 50000 == 0L) {
                        System.gc()
                        delay(50L)
                    }
                }
            }
            
            // 插入剩余数据
            if (buffer.isNotEmpty()) {
                buffer.forEach { entry ->
                    trie.insert(entry.key, entry.word, entry.frequency)
                    insertCount.incrementAndGet()
                }
                logFile.appendText("最终插入${buffer.size}条\n")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "异步插入处理器异常")
            logFile.appendText("异步插入处理器异常: ${e.message}\n")
        }
    }
    
    /**
     * 带重试的批次处理
     */
    private suspend fun processBatchWithRetry(
        batchIndex: Int, 
        batchSize: Int, 
        numWorkers: Int, 
        logFile: File,
        maxRetries: Int = 3
    ): Boolean {
        repeat(maxRetries) { attempt ->
            try {
                return processBatch(batchIndex, batchSize, numWorkers, logFile)
            } catch (e: Exception) {
                logFile.appendText("批次${batchIndex + 1}第${attempt + 1}次尝试失败: ${e.message}\n")
                if (attempt < maxRetries - 1) {
                    delay(1000L * (attempt + 1)) // 递增延迟
                }
            }
        }
        return false
    }
    
    /**
     * 处理单个批次
     */
    private suspend fun processBatch(batchIndex: Int, batchSize: Int, numWorkers: Int, logFile: File): Boolean = coroutineScope {
        val offset = batchIndex * batchSize
        
        // 获取批次数据
        val entries = repository.getEntriesByType("base", offset, batchSize)
        if (entries.isEmpty()) {
            return@coroutineScope true
        }
        
        // 分块并行处理
        val chunks = entries.chunked((entries.size + numWorkers - 1) / numWorkers)
        val jobs = chunks.map { chunk ->
            async {
                workerSemaphore.acquire()
                try {
                    processChunk(chunk, logFile)
                } finally {
                    workerSemaphore.release()
                }
            }
        }
        
        // 等待所有任务完成
        jobs.forEach { it.await() }
        
        return@coroutineScope true
    }
    
    /**
     * 处理数据块
     */
    private suspend fun processChunk(chunk: List<com.shenji.aikeyboard.data.Entry>, logFile: File) {
        val insertEntries = mutableListOf<TrieInsertEntry>()
        
        for (entry in chunk) {
            try {
                val pinyin = entry.pinyin?.lowercase() ?: ""
                if (pinyin.isBlank()) {
                    skipCount.incrementAndGet()
                    continue
                }
                
                processedCount.incrementAndGet()
                
                // 插入完整拼音
                insertEntries.add(TrieInsertEntry(pinyin, entry.word, entry.frequency))
                
                // 如果拼音包含空格，也插入无空格版本
                if (pinyin.contains(" ")) {
                    val pinyinNoSpace = pinyin.replace(" ", "")
                    insertEntries.add(TrieInsertEntry(pinyinNoSpace, entry.word, entry.frequency))
                }
                
                // 插入首字母缩写
                val initialLetters = entry.initialLetters
                if (!initialLetters.isNullOrBlank()) {
                    insertEntries.add(TrieInsertEntry(initialLetters, entry.word, entry.frequency))
                }
                
            } catch (e: Exception) {
                errorCount.incrementAndGet()
                logFile.appendText("处理词条'${entry.word}'异常: ${e.message}\n")
            }
        }
        
        // 发送到插入通道
        if (insertEntries.isNotEmpty()) {
            insertChannel.trySend(TrieInsertBatch(insertEntries))
        }
    }
    
    /**
     * 内存检查和清理
     */
    private suspend fun performMemoryCheck(logFile: File) {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val usageRatio = usedMemory.toFloat() / maxMemory.toFloat()
        
        logFile.appendText("内存检查: 使用${usedMemory / (1024 * 1024)}MB/${maxMemory / (1024 * 1024)}MB (${(usageRatio * 100).toInt()}%)\n")
        
        if (usageRatio > MAX_MEMORY_USAGE_RATIO) {
            logFile.appendText("内存使用率过高，执行垃圾回收\n")
            System.gc()
            delay(200L)
            
            // 再次检查
            val newUsedMemory = runtime.totalMemory() - runtime.freeMemory()
            val newUsageRatio = newUsedMemory.toFloat() / maxMemory.toFloat()
            logFile.appendText("GC后内存: ${newUsedMemory / (1024 * 1024)}MB (${(newUsageRatio * 100).toInt()}%)\n")
        }
    }
    
    /**
     * 保存检查点
     */
    private suspend fun saveCheckpoint(trie: PinyinTrie, batchIndex: Int, totalBatches: Int, totalEntries: Long, logFile: File) {
        try {
            // 保存进度信息
            val progress = BuildProgress(
                totalBatches = totalBatches,
                completedBatches = batchIndex + 1,
                processedEntries = processedCount.get(),
                totalEntries = totalEntries,
                lastBatchIndex = batchIndex
            )
            saveBuildProgress(progress)
            
            // 保存部分Trie
            savePartialTrie(trie)
            
            logFile.appendText("检查点已保存: 批次${batchIndex + 1}/${totalBatches}\n")
            Timber.d("检查点已保存: 批次${batchIndex + 1}/${totalBatches}")
            
        } catch (e: Exception) {
            logFile.appendText("保存检查点失败: ${e.message}\n")
            Timber.e(e, "保存检查点失败")
        }
    }
    
    /**
     * 紧急保存检查点
     */
    private suspend fun saveEmergencyCheckpoint(trie: PinyinTrie, logFile: File) {
        try {
            savePartialTrie(trie)
            logFile.appendText("紧急检查点已保存\n")
            Timber.d("紧急检查点已保存")
        } catch (e: Exception) {
            logFile.appendText("保存紧急检查点失败: ${e.message}\n")
            Timber.e(e, "保存紧急检查点失败")
        }
    }
    
    /**
     * 最终优化
     */
    private suspend fun performFinalOptimization(trie: PinyinTrie, logFile: File) {
        logFile.appendText("开始最终优化...\n")
        
        // 强制垃圾回收
        System.gc()
        delay(500L)
        
        // 可以在这里添加Trie树的优化逻辑
        // 例如：压缩节点、重新平衡等
        
        logFile.appendText("最终优化完成\n")
    }
    
    /**
     * 重置计数器
     */
    private fun resetCounters() {
        processedCount.set(0)
        skipCount.set(0)
        errorCount.set(0)
        insertCount.set(0)
    }
    
    /**
     * 创建构建日志文件
     */
    private fun createBuildLog(): File {
        val logDir = File(context.filesDir, "logs")
        logDir.mkdirs()
        val logFile = File(logDir, "base_trie_build_${System.currentTimeMillis()}.log")
        logFile.createNewFile()
        return logFile
    }
    
    /**
     * 获取系统信息
     */
    private fun getSystemInfo(): String {
        val runtime = Runtime.getRuntime()
        return "CPU核心: ${runtime.availableProcessors()}, " +
               "最大内存: ${runtime.maxMemory() / (1024 * 1024)}MB, " +
               "Android版本: ${android.os.Build.VERSION.SDK_INT}, " +
               "设备型号: ${android.os.Build.MODEL}"
    }
    
    /**
     * 保存构建进度
     */
    private fun saveBuildProgress(progress: BuildProgress) {
        prefs.edit()
            .putInt("total_batches", progress.totalBatches)
            .putInt("completed_batches", progress.completedBatches)
            .putLong("processed_entries", progress.processedEntries)
            .putLong("total_entries", progress.totalEntries)
            .putInt("last_batch_index", progress.lastBatchIndex)
            .putLong("save_time", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * 加载构建进度
     */
    private fun loadBuildProgress(): BuildProgress? {
        return try {
            val totalBatches = prefs.getInt("total_batches", -1)
            if (totalBatches == -1) return null
            
            BuildProgress(
                totalBatches = totalBatches,
                completedBatches = prefs.getInt("completed_batches", 0),
                processedEntries = prefs.getLong("processed_entries", 0),
                totalEntries = prefs.getLong("total_entries", 0),
                lastBatchIndex = prefs.getInt("last_batch_index", -1)
            )
        } catch (e: Exception) {
            Timber.e(e, "加载构建进度失败")
            null
        }
    }
    
    /**
     * 清理构建进度
     */
    private fun clearBuildProgress() {
        prefs.edit().clear().apply()
        
        // 删除部分Trie文件
        val partialTrieFile = File(context.filesDir, "trie/base_trie_partial.dat")
        if (partialTrieFile.exists()) {
            partialTrieFile.delete()
        }
    }
    
    /**
     * 保存部分Trie
     */
    private fun savePartialTrie(trie: PinyinTrie) {
        val file = File(context.filesDir, "trie/base_trie_partial.dat")
        file.parentFile?.mkdirs()
        
        FileOutputStream(file).use { fos ->
            ObjectOutputStream(fos).use { oos ->
                oos.writeObject(trie)
            }
        }
    }
    
    /**
     * 加载部分Trie
     */
    private fun loadPartialTrie(): PinyinTrie? {
        val file = File(context.filesDir, "trie/base_trie_partial.dat")
        if (!file.exists()) return null
        
        return try {
            FileInputStream(file).use { fis ->
                ObjectInputStream(fis).use { ois ->
                    ois.readObject() as PinyinTrie
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "加载部分Trie失败")
            null
        }
    }
    
    /**
     * 保存错误日志
     */
    private fun saveErrorLog(error: Throwable) {
        try {
            val errorFile = File(context.filesDir, "logs/base_trie_error_${System.currentTimeMillis()}.log")
            errorFile.parentFile?.mkdirs()
            errorFile.writeText("基础词典Trie构建错误日志\n")
            errorFile.appendText("时间: ${java.util.Date()}\n")
            errorFile.appendText("错误: ${error.message}\n")
            errorFile.appendText("堆栈: ${error.stackTraceToString()}\n")
            errorFile.appendText("系统信息: ${getSystemInfo()}\n")
        } catch (e: Exception) {
            Timber.e(e, "保存错误日志失败")
        }
    }

    /**
     * 保存Trie树到文件 - 改进的序列化方法
     * @param trie 要保存的Trie树
     * @param type Trie树类型
     * @return 保存的文件对象
     */
    fun saveTrie(trie: PinyinTrie, type: TrieType): File {
        val fileName = when (type) {
            TrieType.CHARS -> "chars_trie.dat"
            TrieType.BASE -> "base_trie.dat"
        }
        
        val file = File(context.filesDir, "trie/$fileName")
        
        try {
            // 确保目录存在
            file.parentFile?.mkdirs()
            
            // 使用改进的序列化方法
            FileOutputStream(file).use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    // 写入版本号，便于后续兼容性处理
                    oos.writeInt(1) // 版本号
                    oos.writeObject(trie)
                    oos.flush()
                }
            }
            
            // 验证文件是否正确保存
            if (file.exists() && file.length() > 0) {
                Timber.d("Trie保存成功: ${file.path}, 文件大小: ${file.length() / 1024}KB")
                
                // 立即验证文件是否可以正确读取
                val testTrie = loadTrie(type)
                if (testTrie != null && !testTrie.isEmpty()) {
                    Timber.d("Trie文件验证成功")
                } else {
                    Timber.w("Trie文件验证失败，可能存在序列化问题")
                }
            } else {
                throw Exception("文件保存失败或文件为空")
            }
            
            return file
        } catch (e: Exception) {
            Timber.e(e, "Trie保存失败: ${e.message}")
            // 删除可能损坏的文件
            if (file.exists()) {
                file.delete()
            }
            throw e
        }
    }
    
    /**
     * 从文件加载Trie树 - 改进的反序列化方法
     * @param type Trie树类型
     * @return 加载的PinyinTrie对象，如果加载失败返回null
     */
    fun loadTrie(type: TrieType): PinyinTrie? {
        val fileName = when (type) {
            TrieType.CHARS -> "chars_trie.dat"
            TrieType.BASE -> "base_trie.dat"
        }
        
        val file = File(context.filesDir, "trie/$fileName")
        
        if (!file.exists()) {
            Timber.d("Trie文件不存在: ${file.path}")
            return null
        }
        
        if (file.length() == 0L) {
            Timber.w("Trie文件为空: ${file.path}")
            file.delete() // 删除空文件
            return null
        }
        
        try {
            // 尝试新格式加载（带版本号）
            return tryLoadWithVersion(file) ?: 
                   // 如果新格式失败，尝试旧格式
                   tryLoadWithDirectObjectStream(file) ?: 
                   // 最后尝试GZIP格式
                   tryLoadWithGZIP(file)
        } catch (e: Exception) {
            Timber.e(e, "Trie加载失败: ${e.message}")
            
            // 如果加载失败，备份损坏的文件并删除
            try {
                val backupFile = File(file.parent, "${file.name}.corrupted.${System.currentTimeMillis()}")
                file.renameTo(backupFile)
                Timber.d("损坏的Trie文件已备份为: ${backupFile.name}")
            } catch (backupError: Exception) {
                Timber.e(backupError, "备份损坏文件失败")
                file.delete() // 直接删除
            }
            
            return null
        }
    }
    
    /**
     * 尝试使用新格式加载（带版本号）
     */
    private fun tryLoadWithVersion(file: File): PinyinTrie? {
        return try {
            FileInputStream(file).use { fis ->
                ObjectInputStream(fis).use { ois ->
                    val version = ois.readInt() // 读取版本号
                    if (version == 1) {
                        val trie = ois.readObject() as PinyinTrie
                        
                        // 验证Trie树是否为空
                        if (trie.isEmpty()) {
                            Timber.w("加载的Trie树为空")
                            null
                        } else {
                            Timber.d("使用新格式加载Trie成功: ${file.path}")
                            trie
                        }
                    } else {
                        Timber.w("不支持的Trie文件版本: $version")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d("新格式加载失败: ${e.message}")
            null
        }
    }
    
    /**
     * 尝试使用直接的ObjectInputStream加载（旧格式）
     */
    private fun tryLoadWithDirectObjectStream(file: File): PinyinTrie? {
        return try {
            FileInputStream(file).use { fis ->
                ObjectInputStream(fis).use { ois ->
                    val trie = ois.readObject() as PinyinTrie
                    
                    // 验证Trie树是否为空
                    if (trie.isEmpty()) {
                        Timber.w("加载的Trie树为空")
                        null
                    } else {
                        Timber.d("使用标准ObjectInputStream加载Trie成功: ${file.path}")
                        trie
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d("标准ObjectInputStream加载失败: ${e.message}")
            null
        }
    }
    
    /**
     * 尝试使用GZIP解压缩加载（兼容旧格式）
     */
    private fun tryLoadWithGZIP(file: File): PinyinTrie? {
        return try {
            FileInputStream(file).use { fis ->
                java.util.zip.GZIPInputStream(fis).use { gis ->
                    ObjectInputStream(gis).use { ois ->
                        val trie = ois.readObject() as PinyinTrie
                        
                        // 验证Trie树是否为空
                        if (trie.isEmpty()) {
                            Timber.w("加载的GZIP Trie树为空")
                            null
                        } else {
                            Timber.d("使用GZIP加载Trie成功: ${file.path}")
                            trie
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "GZIP加载失败: ${e.message}")
            null
        }
    }
} 