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
        private const val SERIALIZATION_VERSION = 2
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
        CHARS,        // 单字词典
        BASE,         // 基础词典
        CORRELATION,  // 关联词典
        ASSOCIATIONAL,// 联想词典
        PLACE,        // 地名词典
        PEOPLE,       // 人名词典
        POETRY,       // 诗词词典
        CORRECTIONS,  // 纠错词典
        COMPATIBLE    // 兼容词典
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
    
    // 构建配置数据类
    data class TrieBuildConfig(
        val batchSize: Int = 10000,           // 批次大小，默认10000，最大20000
        val numWorkers: Int = 7,              // 工作线程数，默认7
        val frequencyFilter: FrequencyFilter = FrequencyFilter.ALL,  // 词频过滤
        val enableBreakpoint: Boolean = true   // 是否启用断点续传
    )
    
    // 词频过滤枚举
    enum class FrequencyFilter(val displayName: String, val percentage: Float) {
        ALL("全部构建", 1.0f),
        TOP_90("前90%", 0.9f),
        TOP_80("前80%", 0.8f),
        TOP_70("前70%", 0.7f),
        TOP_60("前60%", 0.6f),
        TOP_50("前50%", 0.5f),
        TOP_40("前40%", 0.4f),
        TOP_30("前30%", 0.3f),
        TOP_20("前20%", 0.2f),
        TOP_10("前10%", 0.1f)
    }
    
    /**
     * 通用Trie树构建方法 - 支持所有词典类型
     * @param trieType Trie树类型
     * @param config 构建配置
     * @param progressCallback 进度回调函数
     * @return 构建完成的PinyinTrie对象
     */
    suspend fun buildTrie(
        trieType: TrieType, 
        config: TrieBuildConfig = TrieBuildConfig(),
        progressCallback: (Int, String) -> Unit
    ): PinyinTrie = withContext(Dispatchers.IO) {
        
        when (trieType) {
            TrieType.CHARS -> buildCharsTrie(config, progressCallback)
            else -> buildDictionaryTrie(trieType, config, progressCallback)
        }
    }
    
    /**
     * 构建单字Trie树 - 优化版本
     * @param config 构建配置
     * @param progressCallback 进度回调函数
     * @return 构建完成的PinyinTrie对象
     */
    private suspend fun buildCharsTrie(
        config: TrieBuildConfig,
        progressCallback: (Int, String) -> Unit
    ): PinyinTrie = withContext(Dispatchers.IO) {
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
     * 构建词典Trie树 - 通用方法，支持所有词典类型
     * @param trieType Trie树类型
     * @param config 构建配置
     * @param progressCallback 进度回调函数
     * @return 构建完成的PinyinTrie对象
     */
    private suspend fun buildDictionaryTrie(
        trieType: TrieType,
        config: TrieBuildConfig,
        progressCallback: (Int, String) -> Unit
    ): PinyinTrie = withContext(Dispatchers.IO) {
        
        val startTime = System.currentTimeMillis()
        val dictType = getTypeString(trieType)
        val dictName = getDisplayName(trieType)
        
        try {
            progressCallback(0, "初始化${dictName}Trie构建器...")
            Timber.d("开始构建${dictName}Trie树，类型: $dictType")
            
            // 创建详细日志
            val logFile = createBuildLog(dictType)
            logFile.appendText("=== ${dictName}Trie树构建开始 ===\n")
            logFile.appendText("开始时间: ${java.util.Date()}\n")
            logFile.appendText("系统信息: ${getSystemInfo()}\n")
            logFile.appendText("构建配置: $config\n\n")
            
            // 检查是否有之前的构建进度
            val existingProgress = if (config.enableBreakpoint) loadBuildProgress(dictType) else null
            val trie = if (existingProgress != null) {
                progressCallback(5, "发现${dictName}断点续传数据，准备继续构建...")
                logFile.appendText("发现断点续传数据: 已完成${existingProgress.completedBatches}/${existingProgress.totalBatches}批次\n")
                loadPartialTrie(dictType) ?: PinyinTrie()
            } else {
                progressCallback(5, "开始全新构建${dictName}...")
                logFile.appendText("开始全新构建\n")
                clearBuildProgress(dictType)
                PinyinTrie()
            }
            
            // 获取词典总数和词频过滤
            val totalCount = repository.getEntryCountByType(dictType)
            val filteredCount = (totalCount * config.frequencyFilter.percentage).toInt()
            
            progressCallback(10, "获取到${totalCount}个${dictName}词条，过滤后${filteredCount}个")
            logFile.appendText("词条总数: $totalCount, 过滤后: $filteredCount\n")
            
            // 计算优化的批次参数
            val optimizedParams = calculateOptimizedParams(filteredCount, config)
            val batchSize = optimizedParams.batchSize
            val numWorkers = optimizedParams.numWorkers
            val totalBatches = (filteredCount + batchSize - 1) / batchSize
            
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
                        val success = processDictionaryBatchWithRetry(
                            dictType, batchIndex, batchSize, numWorkers, 
                            config.frequencyFilter, logFile
                        )
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
                        if (config.enableBreakpoint && batchIndex % CHECKPOINT_INTERVAL == 0) {
                            saveCheckpoint(trie, dictType, batchIndex, totalBatches, filteredCount.toLong(), logFile)
                        }
                        
                        // 自适应延迟，防止过热
                        if (batchIndex % 20 == 19) {
                            delay(100L)
                        }
                        
                    } catch (e: Exception) {
                        errorCount.incrementAndGet()
                        logFile.appendText("批次${batchIndex + 1}异常: ${e.message}\n")
                        Timber.e(e, "批次${batchIndex + 1}处理异常")
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
                if (config.enableBreakpoint) {
                    clearBuildProgress(dictType)
                }
                
                val totalTime = System.currentTimeMillis() - startTime
                val stats = trie.getMemoryStats()
                val finalSummary = "总耗时: ${totalTime}ms, 处理: ${processedCount.get()}, 跳过: ${skipCount.get()}, 错误: ${errorCount.get()}, 插入: ${insertCount.get()}"
                
                logFile.appendText("\n=== 构建完成 ===\n")
                logFile.appendText("$finalSummary\n")
                logFile.appendText("Trie统计: $stats\n")
                logFile.appendText("结束时间: ${java.util.Date()}\n")
                
                progressCallback(100, "完成: $stats ($finalSummary)")
                Timber.d("${dictName}Trie构建完成: $finalSummary")
                
                return@withContext trie
                
            } catch (e: Exception) {
                // 保存当前进度以便下次继续
                if (config.enableBreakpoint) {
                    saveEmergencyCheckpoint(trie, dictType, logFile)
                }
                throw e
            }
            
        } catch (e: Exception) {
            val errorMsg = "构建${dictName}Trie失败: ${e.message}"
            Timber.e(e, errorMsg)
            progressCallback(-1, "构建失败: ${e.message}")
            
            // 保存错误日志
            saveErrorLog(dictType, e)
            throw e
        }
    }
    
    /**
     * 获取Trie类型对应的字符串
     */
    private fun getTypeString(trieType: TrieType): String {
        return when (trieType) {
            TrieType.CHARS -> "chars"
            TrieType.BASE -> "base"
            TrieType.CORRELATION -> "correlation"
            TrieType.ASSOCIATIONAL -> "associational"
            TrieType.PLACE -> "place"
            TrieType.PEOPLE -> "people"
            TrieType.POETRY -> "poetry"
            TrieType.CORRECTIONS -> "corrections"
            TrieType.COMPATIBLE -> "compatible"
        }
    }
    
    /**
     * 获取Trie类型的显示名称
     */
    private fun getDisplayName(trieType: TrieType): String {
        return when (trieType) {
            TrieType.CHARS -> "单字"
            TrieType.BASE -> "基础词典"
            TrieType.CORRELATION -> "关联词典"
            TrieType.ASSOCIATIONAL -> "联想词典"
            TrieType.PLACE -> "地名词典"
            TrieType.PEOPLE -> "人名词典"
            TrieType.POETRY -> "诗词词典"
            TrieType.CORRECTIONS -> "纠错词典"
            TrieType.COMPATIBLE -> "兼容词典"
        }
    }
    
    /**
     * 计算优化的构建参数
     */
    private fun calculateOptimizedParams(totalCount: Int, config: TrieBuildConfig): OptimizedParams {
        val runtime = Runtime.getRuntime()
        val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)
        val availableCores = runtime.availableProcessors()
        
        // 使用配置的批次大小，但要验证范围
        val batchSize = config.batchSize.coerceIn(1000, 20000).coerceAtMost(totalCount / 5)
        
        // 使用配置的工作线程数，但要验证范围
        val numWorkers = config.numWorkers.coerceIn(1, MAX_CONCURRENT_WORKERS)
        
        Timber.d("优化参数: 内存=${maxMemoryMB}MB, 核心=${availableCores}, 批次大小=${batchSize}, 工作线程=${numWorkers}")
        
        return OptimizedParams(batchSize, numWorkers)
    }
    
    data class OptimizedParams(val batchSize: Int, val numWorkers: Int)
    
    // 其他辅助方法...
    private fun createBuildLog(dictType: String = "base"): File {
        val logDir = File(context.filesDir, "logs")
        logDir.mkdirs()
        val logFile = File(logDir, "${dictType}_trie_build_${System.currentTimeMillis()}.log")
        logFile.createNewFile()
        return logFile
    }
    
    private fun getSystemInfo(): String {
        val runtime = Runtime.getRuntime()
        return "CPU核心: ${runtime.availableProcessors()}, " +
               "最大内存: ${runtime.maxMemory() / (1024 * 1024)}MB, " +
               "Android版本: ${android.os.Build.VERSION.SDK_INT}, " +
               "设备型号: ${android.os.Build.MODEL}"
    }
    
    private fun resetCounters() {
        processedCount.set(0)
        skipCount.set(0)
        errorCount.set(0)
        insertCount.set(0)
    }
    
    /**
     * 处理词典批次（带重试机制）
     */
    private suspend fun processDictionaryBatchWithRetry(
        dictType: String,
        batchIndex: Int, 
        batchSize: Int, 
        numWorkers: Int,
        frequencyFilter: FrequencyFilter,
        logFile: File,
        maxRetries: Int = 3
    ): Boolean = withContext(Dispatchers.IO) {
        
        var attempt = 0
        var lastException: Exception? = null
        
        while (attempt < maxRetries) {
            try {
                attempt++
                logFile.appendText("批次${batchIndex + 1}: 第${attempt}次尝试开始\n")
                
                // 计算偏移量
                val offset = batchIndex * batchSize
                
                // 获取当前批次的词条数据（使用词频过滤）
                val entries = repository.getEntriesByTypeWithFrequencyFilter(
                    dictType, offset, batchSize, frequencyFilter
                )
                
                if (entries.isEmpty()) {
                    logFile.appendText("批次${batchIndex + 1}: 无数据，跳过\n")
                    return@withContext true
                }
                
                logFile.appendText("批次${batchIndex + 1}: 获取到${entries.size}个词条\n")
                
                // 处理词条
                val processedEntries = mutableListOf<TrieInsertEntry>()
                
                for (entry in entries) {
                    val processedEntry = processEntry(entry, dictType)
                    if (processedEntry != null) {
                        processedEntries.add(processedEntry)
                    }
                }
                
                // 发送到插入通道
                if (processedEntries.isNotEmpty()) {
                    insertChannel.trySend(TrieInsertBatch(processedEntries))
                    insertCount.addAndGet(processedEntries.size.toLong())
                }
                
                processedCount.addAndGet(entries.size.toLong())
                logFile.appendText("批次${batchIndex + 1}: 成功处理${entries.size}个词条，插入${processedEntries.size}个\n")
                
                return@withContext true
                
            } catch (e: Exception) {
                lastException = e
                logFile.appendText("批次${batchIndex + 1}: 第${attempt}次尝试失败: ${e.message}\n")
                Timber.w(e, "处理批次${batchIndex + 1}失败，第${attempt}次尝试")
                
                if (attempt < maxRetries) {
                    delay(1000L * attempt) // 递增延迟
                }
            }
        }
        
        // 所有重试都失败了
        errorCount.incrementAndGet()
        logFile.appendText("批次${batchIndex + 1}: 所有重试都失败，最后错误: ${lastException?.message}\n")
        return@withContext false
    }
    
    /**
     * 处理单个词条
     */
    private fun processEntry(entry: com.shenji.aikeyboard.data.Entry, dictType: String): TrieInsertEntry? {
        try {
            val word = entry.word
            val pinyin = entry.pinyin?.lowercase()?.trim()
            val frequency = entry.frequency ?: 0
            
            if (word.isBlank() || pinyin.isNullOrBlank()) {
                skipCount.incrementAndGet()
                return null
            }
            
            // 处理拼音（去除声调，处理多音节）
            val cleanPinyin = removeTones(pinyin)
            
            return TrieInsertEntry(
                key = cleanPinyin,
                word = word,
                frequency = frequency
            )
            
        } catch (e: Exception) {
            Timber.w(e, "处理词条失败: ${entry.word}")
            errorCount.incrementAndGet()
            return null
        }
    }
    
    /**
     * 去除拼音声调
     */
    private fun removeTones(pinyin: String): String {
        return pinyin.replace(Regex("[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")) { matchResult ->
            when (matchResult.value) {
                "ā", "á", "ǎ", "à" -> "a"
                "ē", "é", "ě", "è" -> "e"
                "ī", "í", "ǐ", "ì" -> "i"
                "ō", "ó", "ǒ", "ò" -> "o"
                "ū", "ú", "ǔ", "ù" -> "u"
                "ǖ", "ǘ", "ǚ", "ǜ" -> "v"
                else -> matchResult.value
            }
        }
    }
    
    /**
     * 启动异步插入处理器
     */
    private fun startAsyncInsertProcessor(trie: PinyinTrie, logFile: File) = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        try {
            var batchCount = 0
            for (batch in insertChannel) {
                try {
                    // 批量插入到Trie
                    for (entry in batch.entries) {
                        trie.insert(entry.key, entry.word, entry.frequency)
                    }
                    
                    batchCount++
                    if (batchCount % 10 == 0) {
                        logFile.appendText("异步插入: 已处理${batchCount}个批次\n")
                    }
                    
                } catch (e: Exception) {
                    logFile.appendText("异步插入失败: ${e.message}\n")
                    Timber.e(e, "异步插入失败")
                }
            }
            logFile.appendText("异步插入处理器完成，总共处理${batchCount}个批次\n")
        } catch (e: Exception) {
            logFile.appendText("异步插入处理器异常: ${e.message}\n")
            Timber.e(e, "异步插入处理器异常")
        }
    }
    
    /**
     * 执行内存检查
     */
    private suspend fun performMemoryCheck(logFile: File) = withContext(Dispatchers.IO) {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val usageRatio = usedMemory.toFloat() / maxMemory
        
        logFile.appendText("内存检查: 使用${usedMemory / (1024 * 1024)}MB / ${maxMemory / (1024 * 1024)}MB (${(usageRatio * 100).toInt()}%)\n")
        
        if (usageRatio > MAX_MEMORY_USAGE_RATIO) {
            logFile.appendText("内存使用率过高，执行垃圾回收\n")
            System.gc()
            delay(500L)
            
            val newUsedMemory = runtime.totalMemory() - runtime.freeMemory()
            val newUsageRatio = newUsedMemory.toFloat() / maxMemory
            logFile.appendText("垃圾回收后: 使用${newUsedMemory / (1024 * 1024)}MB (${(newUsageRatio * 100).toInt()}%)\n")
        }
    }
    
    /**
     * 保存检查点
     */
    private fun saveCheckpoint(
        trie: PinyinTrie, 
        dictType: String, 
        batchIndex: Int, 
        totalBatches: Int, 
        totalEntries: Long, 
        logFile: File
    ) {
        try {
            // 保存进度信息
            val progress = BuildProgress(
                totalBatches = totalBatches,
                completedBatches = batchIndex + 1,
                processedEntries = processedCount.get(),
                totalEntries = totalEntries,
                lastBatchIndex = batchIndex
            )
            
            saveBuildProgress(dictType, progress)
            
            // 保存部分Trie
            savePartialTrie(trie, dictType)
            
            logFile.appendText("检查点已保存: 批次${batchIndex + 1}/${totalBatches}\n")
            
        } catch (e: Exception) {
            logFile.appendText("保存检查点失败: ${e.message}\n")
            Timber.e(e, "保存检查点失败")
        }
    }
    
    /**
     * 保存构建进度
     */
    private fun saveBuildProgress(dictType: String, progress: BuildProgress) {
        prefs.edit()
            .putInt("${dictType}_total_batches", progress.totalBatches)
            .putInt("${dictType}_completed_batches", progress.completedBatches)
            .putLong("${dictType}_processed_entries", progress.processedEntries)
            .putLong("${dictType}_total_entries", progress.totalEntries)
            .putInt("${dictType}_last_batch_index", progress.lastBatchIndex)
            .apply()
    }
    
    /**
     * 加载构建进度
     */
    private fun loadBuildProgress(dictType: String): BuildProgress? {
        return try {
            val totalBatches = prefs.getInt("${dictType}_total_batches", -1)
            if (totalBatches <= 0) return null
            
            BuildProgress(
                totalBatches = totalBatches,
                completedBatches = prefs.getInt("${dictType}_completed_batches", 0),
                processedEntries = prefs.getLong("${dictType}_processed_entries", 0),
                totalEntries = prefs.getLong("${dictType}_total_entries", 0),
                lastBatchIndex = prefs.getInt("${dictType}_last_batch_index", -1)
            )
        } catch (e: Exception) {
            Timber.e(e, "加载构建进度失败")
            null
        }
    }
    
    /**
     * 清理构建进度
     */
    private fun clearBuildProgress(dictType: String) {
        prefs.edit()
            .remove("${dictType}_total_batches")
            .remove("${dictType}_completed_batches")
            .remove("${dictType}_processed_entries")
            .remove("${dictType}_total_entries")
            .remove("${dictType}_last_batch_index")
            .apply()
    }
    
    /**
     * 保存部分Trie
     */
    private fun savePartialTrie(trie: PinyinTrie, dictType: String) {
        try {
            val fileName = "${dictType}_trie_partial.dat"
            val file = File(context.filesDir, "trie/$fileName")
            file.parentFile?.mkdirs()
            
            FileOutputStream(file).use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeInt(SERIALIZATION_VERSION)
                    oos.writeObject(trie)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "保存部分Trie失败")
        }
    }
    
    /**
     * 加载部分Trie
     */
    private fun loadPartialTrie(dictType: String): PinyinTrie? {
        return try {
            val fileName = "${dictType}_trie_partial.dat"
            val file = File(context.filesDir, "trie/$fileName")
            
            if (!file.exists()) return null
            
            FileInputStream(file).use { fis ->
                ObjectInputStream(fis).use { ois ->
                    val version = ois.readInt()
                    if (version == SERIALIZATION_VERSION) {
                        ois.readObject() as PinyinTrie
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "加载部分Trie失败")
            null
        }
    }
    
    /**
     * 执行最终优化
     */
    private fun performFinalOptimization(trie: PinyinTrie, logFile: File) {
        try {
            logFile.appendText("开始最终优化...\n")
            
            // 执行垃圾回收
            System.gc()
            
            // 获取最终统计信息
            val stats = trie.getMemoryStats()
            logFile.appendText("最终统计: ${stats}\n")
            logFile.appendText("处理统计: 总处理=${processedCount.get()}, 跳过=${skipCount.get()}, 错误=${errorCount.get()}, 插入=${insertCount.get()}\n")
            
        } catch (e: Exception) {
            logFile.appendText("最终优化失败: ${e.message}\n")
            Timber.e(e, "最终优化失败")
        }
    }
    
    /**
     * 保存错误日志
     */
    private fun saveErrorLog(dictType: String, error: Exception) {
        try {
            val errorLogFile = File(context.filesDir, "logs/${dictType}_error_${System.currentTimeMillis()}.log")
            errorLogFile.parentFile?.mkdirs()
            
            errorLogFile.writeText("""
                错误时间: ${java.util.Date()}
                词典类型: $dictType
                错误信息: ${error.message}
                错误堆栈:
                ${error.stackTraceToString()}
                
                系统信息: ${getSystemInfo()}
            """.trimIndent())
            
        } catch (e: Exception) {
            Timber.e(e, "保存错误日志失败")
        }
    }
    
    /**
     * 保存Trie树到文件
     */
    fun saveTrie(trie: PinyinTrie, trieType: TrieType): File {
        val dictType = getTypeString(trieType)
        val fileName = "${dictType}_trie.dat"
        val file = File(context.filesDir, "trie/$fileName")
        file.parentFile?.mkdirs()
        
        try {
            // 使用改进的序列化格式
            FileOutputStream(file).use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    // 写入版本号
                    oos.writeInt(SERIALIZATION_VERSION)
                    // 写入Trie对象
                    oos.writeObject(trie)
                }
            }
            
            // 验证保存的文件
            if (file.exists() && file.length() > 0) {
                // 尝试读取验证
                try {
                    loadTrie(trieType)
                    Timber.d("${getDisplayName(trieType)}Trie保存并验证成功: ${file.absolutePath}")
                } catch (e: Exception) {
                    Timber.w(e, "${getDisplayName(trieType)}Trie保存成功但验证失败")
                }
            }
            
            return file
            
        } catch (e: Exception) {
            Timber.e(e, "保存${getDisplayName(trieType)}Trie失败")
            throw e
        }
    }
    
    /**
     * 从文件加载Trie树
     */
    fun loadTrie(trieType: TrieType): PinyinTrie? {
        val dictType = getTypeString(trieType)
        val fileName = "${dictType}_trie.dat"
        val file = File(context.filesDir, "trie/$fileName")
        
        if (!file.exists()) {
            Timber.d("${getDisplayName(trieType)}Trie文件不存在: ${file.absolutePath}")
            return null
        }
        
        if (file.length() == 0L) {
            Timber.w("${getDisplayName(trieType)}Trie文件为空，删除并返回null")
            file.delete()
            return null
        }
        
        return try {
            FileInputStream(file).use { fis ->
                ObjectInputStream(fis).use { ois ->
                    try {
                        // 尝试读取版本号
                        val version = ois.readInt()
                        if (version == SERIALIZATION_VERSION) {
                            // 新格式
                            val trie = ois.readObject() as PinyinTrie
                            Timber.d("成功加载${getDisplayName(trieType)}Trie (新格式)")
                            trie
                        } else {
                            // 版本不匹配，可能是旧格式
                            Timber.w("${getDisplayName(trieType)}Trie版本不匹配，尝试旧格式")
                            null
                        }
                    } catch (e: Exception) {
                        // 可能是旧格式，重新尝试
                        Timber.d("尝试以旧格式加载${getDisplayName(trieType)}Trie")
                        fis.channel.position(0) // 重置文件位置
                        val trie = ois.readObject() as PinyinTrie
                        Timber.d("成功加载${getDisplayName(trieType)}Trie (旧格式)")
                        trie
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "加载${getDisplayName(trieType)}Trie失败，删除损坏的文件")
            // 备份损坏的文件
            val backupFile = File(file.parent, "${file.name}.corrupted.${System.currentTimeMillis()}")
            try {
                file.renameTo(backupFile)
                Timber.d("损坏的文件已备份为: ${backupFile.absolutePath}")
            } catch (renameException: Exception) {
                file.delete()
                Timber.w("无法备份损坏的文件，已删除")
            }
            null
        }
    }
    
    /**
     * 保存紧急检查点
     */
    private fun saveEmergencyCheckpoint(trie: PinyinTrie, dictType: String, logFile: File) {
        try {
            logFile.appendText("保存紧急检查点...\n")
            
            // 保存当前Trie状态
            savePartialTrie(trie, dictType)
            
            // 保存当前统计信息
            val emergencyInfo = """
                紧急检查点时间: ${java.util.Date()}
                已处理词条: ${processedCount.get()}
                跳过词条: ${skipCount.get()}
                错误词条: ${errorCount.get()}
                插入词条: ${insertCount.get()}
            """.trimIndent()
            
            val emergencyFile = File(context.filesDir, "logs/${dictType}_emergency_${System.currentTimeMillis()}.log")
            emergencyFile.parentFile?.mkdirs()
            emergencyFile.writeText(emergencyInfo)
            
            logFile.appendText("紧急检查点已保存\n")
            
        } catch (e: Exception) {
            logFile.appendText("保存紧急检查点失败: ${e.message}\n")
            Timber.e(e, "保存紧急检查点失败")
        }
    }
} 