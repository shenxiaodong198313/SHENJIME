package com.shenji.aikeyboard.data.trie

import android.content.Context
import com.shenji.aikeyboard.data.DictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Trie树构建器
 * 负责从词典构建Trie树并提供序列化/反序列化功能
 */
class TrieBuilder(
    private val context: Context,
    private val customBatchSize: Int = 300,  // 默认批量大小
    private val customNumWorkers: Int = -1   // 默认使用自动计算的线程数
) {

    // 词典仓库，用于获取词语数据
    private val repository = DictionaryRepository()
    
    // Trie树类型枚举
    enum class TrieType {
        CHARS, // 单字词典
        BASE,  // 基础词典
    }
    
    /**
     * 构建单字Trie树
     * @param progressCallback 进度回调函数
     * @return 构建完成的PinyinTrie对象
     */
    suspend fun buildCharsTrie(progressCallback: (Int, String) -> Unit): PinyinTrie = withContext(Dispatchers.IO) {
        val trie = PinyinTrie()
        
        try {
            progressCallback(0, "开始从单字词典构建Trie树")
            
            // 获取单字列表
            val chars = repository.getAllChars()
            val totalCount = chars.size
            
            progressCallback(10, "获取到${totalCount}个单字，开始构建")
            
            // 统计数据
            var processedCount = 0
            var skipCount = 0
            var multiPinyinCount = 0
            var totalPinyinCount = 0
            
            // 插入所有单字到Trie树
            chars.forEachIndexed { index, entry ->
                // 获取并处理拼音
                val pinyin = entry.pinyin?.lowercase()
                if (pinyin.isNullOrBlank()) {
                    skipCount++
                    return@forEachIndexed
                }
                
                // 拆分多音字情况
                val pinyinList = pinyin.split(",").filter { it.isNotBlank() }
                if (pinyinList.size > 1) {
                    multiPinyinCount++
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
                
                // 每处理100个单字更新一次进度
                if (index % 100 == 0) {
                    val progress = 10 + (index * 80) / totalCount
                    progressCallback(progress, "已处理 ${index}/${totalCount} 个单字")
                }
            }
            
            progressCallback(90, "Trie树构建完成，正在优化内存")
            
            // 获取内存统计信息
            val stats = trie.getMemoryStats()
            
            // 打印详细的构建统计信息
            val statsSummary = "单字总数: $totalCount, 实际处理: $processedCount, " +
                      "跳过: $skipCount, 多音字: $multiPinyinCount, " +
                      "拼音总数: $totalPinyinCount"
            Timber.d("Trie构建统计: $statsSummary")
            
            progressCallback(100, "完成: ${stats.toString()} ($statsSummary)")
            
            return@withContext trie
        } catch (e: Exception) {
            Timber.e(e, "构建单字Trie树失败")
            progressCallback(-1, "构建失败: ${e.message}")
            throw e
        }
    }
    
    /**
     * 构建基础词典Trie树
     * @param progressCallback 进度回调函数
     * @return 构建完成的PinyinTrie对象
     */
    suspend fun buildBaseTrie(progressCallback: (Int, String) -> Unit): PinyinTrie = withContext(Dispatchers.IO) {
        val trie = PinyinTrie()
        
        try {
            progressCallback(0, "开始从基础词典构建Trie树")
            Timber.d("开始构建基础词典Trie树")
            
            // 创建临时异常日志文件，记录构建过程中的所有异常
            val logDir = File(context.filesDir, "logs")
            logDir.mkdirs()
            val logFile = File(logDir, "base_trie_build_log.txt")
            logFile.createNewFile()
            logFile.writeText("基础词典Trie树构建日志 - ${java.util.Date()}\n\n")
            
            // 获取基础词典词条数量
            val totalCount = repository.getEntryCountByType("base")
            progressCallback(5, "获取到${totalCount}个基础词条数量")
            Timber.d("获取到${totalCount}个基础词条")
            
            // 检查总内存可用情况
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / (1024 * 1024)
            Timber.d("设备最大可用内存: ${maxMemory}MB")
            logFile.appendText("设备最大可用内存: ${maxMemory}MB\n")
            
            // 根据可用内存调整参数
            var adjustedBatchSize = customBatchSize
            if (maxMemory < 200 && adjustedBatchSize > 200) {
                adjustedBatchSize = 200
                Timber.d("检测到内存较小，自动调整批量大小为: $adjustedBatchSize")
                logFile.appendText("内存较小，自动调整批量大小为: $adjustedBatchSize\n")
            }
            
            // 分批加载并处理词条，减小内存压力
            val batchSize = adjustedBatchSize  // 使用调整后的批量大小
            val totalBatches = (totalCount + batchSize - 1) / batchSize
            
            // 统计数据
            var processedCount = 0
            var skipCount = 0
            var totalPinyinCount = 0
            var errorCount = 0
            
            // 获取可用处理器核心数
            val availableProcessors = Runtime.getRuntime().availableProcessors()
            // 使用自定义线程数，如果没有指定则使用自动计算的线程数
            val actualCores = if (customNumWorkers > 0) {
                customNumWorkers
            } else {
                if (availableProcessors > 2) availableProcessors - 1 else 2  // 保留一个核心给系统
            }
            
            Timber.d("检测到${availableProcessors}个处理器核心，将使用${actualCores}个核心进行处理")
            logFile.appendText("检测到${availableProcessors}个处理器核心，将使用${actualCores}个核心进行处理\n")
            logFile.appendText("批量大小设置为: $batchSize\n\n")
            
            // 添加自动暂停和恢复机制
            var lastPauseTime = System.currentTimeMillis()
            val pauseInterval = 5000L // 每5秒钟暂停一次，让系统有喘息机会
            var batchesProcessedSinceLastPause = 0
            
            try {
                // 分批处理所有基础词条
                for (batchIndex in 0 until totalBatches) {
                    val offset = batchIndex * batchSize
                    
                    try {
                        // 检查是否需要暂停
                        batchesProcessedSinceLastPause++
                        val currentTime = System.currentTimeMillis()
                        if (batchesProcessedSinceLastPause >= 3 && currentTime - lastPauseTime > pauseInterval) {
                            // 让系统短暂休息，减轻CPU负担，避免过热
                            Timber.d("暂停200ms让系统有喘息机会")
                            kotlinx.coroutines.delay(200) // 短暂暂停
                            lastPauseTime = currentTime
                            batchesProcessedSinceLastPause = 0
                            
                            // 释放一部分内存
                            System.gc()
                        }
                        
                        // 日志记录当前批次开始处理
                        Timber.d("开始处理批次 ${batchIndex+1}/${totalBatches}，偏移量: $offset")
                        logFile.appendText("开始处理批次 ${batchIndex+1}/${totalBatches}，偏移量: $offset\n")
                        
                        // 获取当前批次的词条
                        val entries = repository.getEntriesByTypeOrderedByFrequency("base", offset, batchSize)
                        
                        // 周期性释放内存，减轻内存压力
                        if (batchIndex % 3 == 0) {
                            System.gc()
                        }
                        
                        // 使用多线程并行处理词条，提高性能
                        val batchProcessedCount = AtomicInteger(0)
                        val batchSkipCount = AtomicInteger(0)
                        val batchPinyinCount = AtomicInteger(0)
                        val batchErrorCount = AtomicInteger(0)
                        
                        // 使用协程进行并行处理
                        coroutineScope {
                            val chunks = entries.chunked((entries.size / actualCores) + 1)
                            val jobs = chunks.map { chunk ->
                                launch {
                                    for (entry in chunk) {
                                        try {
                                            // 获取并处理拼音
                                            val pinyin = entry.pinyin?.lowercase()
                                            if (pinyin.isNullOrBlank()) {
                                                batchSkipCount.incrementAndGet()
                                                continue
                                            }
                                            
                                            // 移除拼音中的空格，以便在Trie中连续匹配
                                            val pinyinNoSpace = pinyin.replace(" ", "")
                                            if (pinyinNoSpace.isBlank()) {
                                                batchSkipCount.incrementAndGet()
                                                continue
                                            }
                                            
                                            batchPinyinCount.incrementAndGet()
                                            batchProcessedCount.incrementAndGet()
                                            
                                            // 插入完整拼音（无空格）
                                            trie.insert(pinyinNoSpace, entry.word, entry.frequency ?: 0)
                                            
                                            // 同时插入首字母缩写，如"bei jing"的"bj"
                                            val initialLetters = entry.initialLetters
                                            if (!initialLetters.isNullOrBlank()) {
                                                trie.insert(initialLetters, entry.word, entry.frequency ?: 0)
                                            }
                                        } catch (e: Exception) {
                                            // 记录词条处理过程中的异常
                                            batchErrorCount.incrementAndGet()
                                            val errorMsg = "处理词条'${entry.word}'(${entry.pinyin})时异常: ${e.message}"
                                            Timber.e(e, errorMsg)
                                            
                                            // 为防止日志文件过大，只记录每批次的前10个错误
                                            if (batchErrorCount.get() <= 10) {
                                                logFile.appendText("$errorMsg\n${e.stackTraceToString()}\n\n")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // 累加批次统计数据
                        processedCount += batchProcessedCount.get()
                        skipCount += batchSkipCount.get()
                        totalPinyinCount += batchPinyinCount.get()
                        errorCount += batchErrorCount.get()
                        
                        // 更新进度
                        val progress = 5 + (batchIndex * 90) / totalBatches
                        val progressMessage = "已处理 ${processedCount}/${totalCount} 个基础词条 (批次 ${batchIndex+1}/${totalBatches})"
                        progressCallback(progress, progressMessage)
                        
                        // 记录批次处理结果
                        val batchResult = "批次${batchIndex+1}处理完成: 处理${batchProcessedCount.get()}个, " +
                                "跳过${batchSkipCount.get()}个, 异常${batchErrorCount.get()}个"
                        Timber.d(batchResult)
                        logFile.appendText("$batchResult\n")
                        
                        // 检查内存使用情况并记录
                        val runtime = Runtime.getRuntime()
                        val usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                        val totalMemoryMB = runtime.totalMemory() / (1024 * 1024)
                        val memoryInfo = "内存使用: ${usedMemoryMB}MB / ${totalMemoryMB}MB"
                        Timber.d(memoryInfo)
                        logFile.appendText("$memoryInfo\n\n")
                        
                        // 每10个批次，检查一次内存使用率，如果超过80%，则尝试释放内存
                        if (batchIndex % 10 == 9) {
                            val usedRatio = usedMemoryMB.toDouble() / maxMemory.toDouble()
                            if (usedRatio > 0.8) {
                                Timber.w("内存使用率超过80%，尝试释放内存")
                                logFile.appendText("警告: 内存使用率${(usedRatio * 100).toInt()}%，尝试释放内存\n")
                                
                                // 强制垃圾回收
                                System.gc()
                                kotlinx.coroutines.delay(500) // 给GC一些时间
                            }
                        }
                        
                    } catch (e: Exception) {
                        // 捕获并记录批次处理过程中的异常
                        errorCount++
                        val batchErrorMsg = "处理批次${batchIndex+1}时发生异常: ${e.message}"
                        Timber.e(e, batchErrorMsg)
                        logFile.appendText("$batchErrorMsg\n${e.stackTraceToString()}\n\n")
                        
                        // 继续处理下一批次，而不是中断整个构建过程
                        progressCallback(
                            5 + (batchIndex * 90) / totalBatches,
                            "批次${batchIndex+1}异常，继续处理下一批次..."
                        )
                        
                        // 短暂延迟，让系统有时间恢复
                        kotlinx.coroutines.delay(1000)
                        
                        // 强制垃圾回收
                        System.gc()
                        kotlinx.coroutines.delay(500)
                    }
                }
            } catch (e: Exception) {
                // 捕获并记录整个批次循环中的异常
                val criticalError = "构建过程中发生严重异常: ${e.message}"
                Timber.e(e, criticalError)
                logFile.appendText("$criticalError\n${e.stackTraceToString()}\n\n")
                progressCallback(95, "构建过程中发生严重异常，尝试保存已处理部分")
            }
            
            progressCallback(95, "基础词典Trie树构建完成，正在优化内存")
            Timber.d("基础词典构建完成，正在优化内存")
            
            // 手动触发垃圾回收，优化内存使用
            System.gc()
            kotlinx.coroutines.delay(1000) // 给GC一些时间
            
            // 获取内存统计信息
            val stats = trie.getMemoryStats()
            
            // 打印详细的构建统计信息
            val statsSummary = "基础词条总数: $totalCount, 实际处理: $processedCount, " +
                      "跳过: $skipCount, 拼音总数: $totalPinyinCount, 错误: $errorCount"
            Timber.d("基础词典Trie构建统计: $statsSummary")
            
            // 记录最终构建结果
            logFile.appendText("\n最终构建结果:\n")
            logFile.appendText("$statsSummary\n")
            logFile.appendText("Trie树统计: ${stats.toString()}\n")
            logFile.appendText("构建完成时间: ${java.util.Date()}\n")
            
            progressCallback(100, "完成: ${stats.toString()} ($statsSummary)")
            
            return@withContext trie
        } catch (e: Exception) {
            val criticalError = "构建基础词典Trie树失败: ${e.message}"
            Timber.e(e, criticalError)
            
            // 记录关键错误到错误日志文件
            try {
                val errorLogFile = File(context.filesDir, "logs/base_trie_critical_error.txt")
                errorLogFile.parentFile?.mkdirs()
                errorLogFile.writeText("基础词典Trie树构建关键错误 - ${java.util.Date()}\n\n")
                errorLogFile.appendText("$criticalError\n${e.stackTraceToString()}\n\n")
                errorLogFile.appendText("系统信息:\n")
                errorLogFile.appendText("可用处理器: ${Runtime.getRuntime().availableProcessors()}\n")
                errorLogFile.appendText("最大内存: ${Runtime.getRuntime().maxMemory() / (1024*1024)}MB\n")
                errorLogFile.appendText("已分配内存: ${Runtime.getRuntime().totalMemory() / (1024*1024)}MB\n")
                errorLogFile.appendText("空闲内存: ${Runtime.getRuntime().freeMemory() / (1024*1024)}MB\n")
                errorLogFile.appendText("Android版本: ${android.os.Build.VERSION.SDK_INT}\n")
                errorLogFile.appendText("设备型号: ${android.os.Build.MODEL}\n")
            } catch (logError: Exception) {
                Timber.e(logError, "无法写入错误日志文件")
            }
            
            progressCallback(-1, "构建失败: ${e.message}")
            throw e
        }
    }
    
    /**
     * 保存Trie树到文件
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
            
            // 直接使用ObjectOutputStream，不再使用GZIP压缩
            FileOutputStream(file).use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(trie)
                }
            }
            
            Timber.d("Trie保存成功: ${file.path}, 文件大小: ${file.length() / 1024}KB")
            return file
        } catch (e: Exception) {
            Timber.e(e, "Trie保存失败: ${e.message}")
            throw e
        }
    }
    
    /**
     * 从文件加载Trie树
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
        
        try {
            // 尝试多种方式读取文件，兼容旧格式
            return tryLoadWithDirectObjectStream(file) ?: tryLoadWithGZIP(file)
        } catch (e: Exception) {
            Timber.e(e, "Trie加载失败: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 尝试使用直接的ObjectInputStream加载
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
            Timber.d("标准ObjectInputStream加载失败: ${e.message}, 尝试其他方式")
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