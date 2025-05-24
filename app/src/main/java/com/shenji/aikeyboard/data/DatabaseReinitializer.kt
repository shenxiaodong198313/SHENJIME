package com.shenji.aikeyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.Entry
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import timber.log.Timber
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.measureTimeMillis

/**
 * 高性能数据库重新初始化器
 * 特性：
 * - 多线程并行处理（充分利用8核CPU）
 * - 内存优化（充分利用16G内存）
 * - 断点续传（支持意外中断后继续构建）
 * - 选择性导入（可选择导入哪些词典）
 * - 详细进度显示（每个词典单独显示进度）
 */
class DatabaseReinitializer(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "database_reinit_progress"
        private const val MAX_CONCURRENT_IMPORTS = 4 // 并发导入数量
        private const val BATCH_SIZE = 5000 // 增大批次大小，充分利用内存
        private const val MEMORY_BUFFER_SIZE = 50000 // 内存缓冲区大小
    }
    
    // 词典类型定义
    data class DictionaryType(
        val key: String,
        val displayName: String,
        val description: String,
        val estimatedSize: Int,
        val priority: Int
    )
    
    // 进度状态
    data class ImportProgress(
        val type: String,
        val displayName: String,
        val currentCount: AtomicInteger = AtomicInteger(0),
        val totalCount: AtomicInteger = AtomicInteger(0),
        val status: AtomicReference<ImportStatus> = AtomicReference(ImportStatus.PENDING),
        val errorMessage: String? = null
    )
    
    enum class ImportStatus {
        PENDING, ANALYZING, IMPORTING, COMPLETED, FAILED, SKIPPED
    }
    
    // 可用的词典类型
    private val availableDictionaries = listOf(
        DictionaryType("chars", "字符词典", "单字和常用字符", 50000, 1),
        DictionaryType("base", "基础词典", "常用词汇和短语", 200000, 2),
        DictionaryType("correlation", "关联词典", "相关词汇联想", 100000, 3),
        DictionaryType("associational", "联想词典", "词汇联想扩展", 80000, 4),
        DictionaryType("place", "地名词典", "地理位置名称", 60000, 5),
        DictionaryType("people", "人名词典", "人物姓名", 40000, 6),
        DictionaryType("poetry", "诗词词典", "古诗词内容", 30000, 7),
        DictionaryType("corrections", "纠错词典", "拼写纠错", 20000, 8),
        DictionaryType("compatible", "兼容词典", "兼容性词汇", 15000, 9)
    )
    
    // 进度跟踪
    private val progressMap = ConcurrentHashMap<String, ImportProgress>()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 并发控制
    private val importSemaphore = Semaphore(MAX_CONCURRENT_IMPORTS)
    private val writeChannel = Channel<List<Entry>>(capacity = Channel.UNLIMITED)
    
    /**
     * 获取可用的词典列表
     */
    fun getAvailableDictionaries(): List<DictionaryType> = availableDictionaries
    
    /**
     * 获取已完成的词典列表（用于断点续传）
     */
    fun getCompletedDictionaries(): Set<String> {
        return prefs.getStringSet("completed_dictionaries", emptySet()) ?: emptySet()
    }
    
    /**
     * 重新初始化数据库（支持选择性导入）
     * @param selectedDictionaries 选择要导入的词典类型
     * @param resumeFromBreakpoint 是否从断点继续
     * @param progressCallback 进度回调 (overallProgress: Float, currentDict: String, dictProgress: Float, message: String)
     * @return 是否成功
     */
    suspend fun reinitializeDatabase(
        selectedDictionaries: List<String>,
        resumeFromBreakpoint: Boolean = true,
        progressCallback: (Float, String, Float, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        
        val totalStartTime = System.currentTimeMillis()
        var realm: Realm? = null
        var writeJob: Job? = null
        
        try {
            // 检查协程是否被取消
            ensureActive()
            progressCallback(0.0f, "", 0.0f, "开始高性能数据库重新初始化...")
            
            // 初始化进度跟踪
            initializeProgressTracking(selectedDictionaries, resumeFromBreakpoint)
            
            // 第一步：准备数据库环境
            ensureActive()
            progressCallback(0.05f, "", 0.0f, "准备数据库环境...")
            realm = prepareDatabaseEnvironment(resumeFromBreakpoint)
            
            // 第二步：启动写入协程
            ensureActive()
            writeJob = startDatabaseWriter(realm)
            
            // 第三步：并行导入词典数据
            ensureActive()
            progressCallback(0.1f, "", 0.0f, "开始并行导入词典数据...")
            val success = importDictionariesParallel(selectedDictionaries, resumeFromBreakpoint) { type, dictProgress, message ->
                val overallProgress = calculateOverallProgress()
                progressCallback(overallProgress, type, dictProgress, message)
            }
            
            // 第四步：等待所有数据写入完成
            ensureActive()
            progressCallback(0.9f, "", 1.0f, "等待数据写入完成...")
            writeChannel.close()
            writeJob.join() // 确保所有数据都写入完成
            
            // 第五步：验证数据写入
            ensureActive()
            progressCallback(0.92f, "", 1.0f, "验证数据写入...")
            val totalCount = realm.query(Entry::class).count().find().toInt()
            Timber.d("数据写入验证: 总词条数 = $totalCount")
            
            if (totalCount == 0) {
                Timber.e("数据写入失败: 数据库中没有词条")
                progressCallback(1.0f, "", 0.0f, "数据写入失败: 数据库为空")
                return@withContext false
            }
            
            // 第六步：优化和清理
            if (success) {
                ensureActive()
                progressCallback(0.95f, "", 1.0f, "优化数据库索引...")
                optimizeDatabaseIndexes(realm)
                
                ensureActive()
                progressCallback(0.98f, "", 1.0f, "重新初始化应用组件...")
                reinitializeAppComponents(realm)
                
                // 清理断点信息
                clearBreakpointData()
            }
            
            val totalTime = System.currentTimeMillis() - totalStartTime
            progressCallback(1.0f, "", 1.0f, "数据库重新初始化完成 (耗时: ${totalTime}ms, 词条数: $totalCount)")
            
            return@withContext success
            
        } catch (e: CancellationException) {
            Timber.d("数据库重新初始化被取消")
            progressCallback(0.0f, "", 0.0f, "初始化已取消")
            throw e // 重新抛出CancellationException以正确处理协程取消
        } catch (e: Exception) {
            Timber.e(e, "重新初始化数据库失败")
            progressCallback(1.0f, "", 0.0f, "初始化失败: ${e.message}")
            return@withContext false
        } finally {
            // 确保写入协程被正确关闭
            writeJob?.cancel()
        }
    }
    
    /**
     * 初始化进度跟踪
     */
    private fun initializeProgressTracking(selectedDictionaries: List<String>, resumeFromBreakpoint: Boolean) {
        progressMap.clear()
        
        val completedDictionaries = if (resumeFromBreakpoint) getCompletedDictionaries() else emptySet()
        
        for (dictType in selectedDictionaries) {
            val dictInfo = availableDictionaries.find { it.key == dictType }
            if (dictInfo != null) {
                val progress = ImportProgress(
                    type = dictType,
                    displayName = dictInfo.displayName
                )
                
                if (dictType in completedDictionaries) {
                    progress.status.set(ImportStatus.COMPLETED)
                    progress.currentCount.set(progress.totalCount.get())
                }
                
                progressMap[dictType] = progress
            }
        }
    }
    
    /**
     * 准备数据库环境
     */
    private suspend fun prepareDatabaseEnvironment(resumeFromBreakpoint: Boolean): Realm = withContext(Dispatchers.IO) {
        if (!resumeFromBreakpoint) {
            // 完全重新开始
            closeCurrentDatabase()
            deleteOldDatabase()
        }
        
        return@withContext createOptimizedDatabase()
    }
    
    /**
     * 启动数据库写入协程
     */
    private fun CoroutineScope.startDatabaseWriter(realm: Realm) = launch(Dispatchers.IO) {
        var batchCount = 0
        var totalWritten = 0
        val writtenIds = mutableSetOf<String>() // 跟踪已写入的ID，避免重复
        
        try {
            for (entries in writeChannel) {
                ensureActive()
                batchCount++
                
                // 过滤重复的条目
                val uniqueEntries = entries.filter { entry ->
                    if (writtenIds.contains(entry.id)) {
                        Timber.w("跳过重复条目: ${entry.word} (${entry.id})")
                        false
                    } else {
                        writtenIds.add(entry.id)
                        true
                    }
                }
                
                if (uniqueEntries.isNotEmpty()) {
                    realm.write {
                        uniqueEntries.forEach { entry ->
                            try {
                                copyToRealm(entry)
                                totalWritten++
                            } catch (e: Exception) {
                                Timber.e(e, "写入条目失败: ${entry.word} (${entry.id})")
                            }
                        }
                    }
                }
                
                // 每100个批次记录一次日志
                if (batchCount % 100 == 0) {
                    Timber.d("已写入 $batchCount 个批次，总条目: $totalWritten")
                }
            }
            Timber.d("数据库写入完成，总共写入 $batchCount 个批次，$totalWritten 条记录")
        } catch (e: CancellationException) {
            Timber.d("数据库写入协程被取消，已写入 $batchCount 个批次，$totalWritten 条记录")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "数据库写入失败，已写入 $batchCount 个批次，$totalWritten 条记录")
            throw e
        }
    }
    
    /**
     * 并行导入词典数据
     */
    private suspend fun importDictionariesParallel(
        selectedDictionaries: List<String>,
        resumeFromBreakpoint: Boolean,
        progressCallback: (String, Float, String) -> Unit
    ): Boolean = coroutineScope {
        
        val completedDictionaries = if (resumeFromBreakpoint) getCompletedDictionaries() else emptySet()
        val pendingDictionaries = selectedDictionaries.filter { it !in completedDictionaries }
        
        if (pendingDictionaries.isEmpty()) {
            progressCallback("", 1.0f, "所有词典已完成，无需重新导入")
            return@coroutineScope true
        }
        
        // 按优先级排序
        val sortedDictionaries = pendingDictionaries.sortedBy { dictType ->
            availableDictionaries.find { it.key == dictType }?.priority ?: Int.MAX_VALUE
        }
        
        // 并行导入
        val jobs = sortedDictionaries.map { dictType ->
            async {
                importSemaphore.acquire()
                try {
                    importSingleDictionary(dictType, progressCallback)
                } finally {
                    importSemaphore.release()
                }
            }
        }
        
        // 等待所有导入完成
        val results = jobs.awaitAll()
        val allSuccess = results.all { it }
        
        if (allSuccess) {
            // 标记所有词典为已完成
            for (dictType in selectedDictionaries) {
                markDictionaryCompleted(dictType)
            }
        }
        
        return@coroutineScope allSuccess
    }
    
    /**
     * 导入单个词典
     */
    private suspend fun importSingleDictionary(
        dictType: String,
        progressCallback: (String, Float, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        
        val progress = progressMap[dictType] ?: return@withContext false
        val dictInfo = availableDictionaries.find { it.key == dictType } ?: return@withContext false
        
        try {
            progress.status.set(ImportStatus.ANALYZING)
            progressCallback(dictType, 0.0f, "分析 ${dictInfo.displayName} 文件结构...")
            
            val assetPath = "cn_dicts/${dictType}.dict.yaml"
            
            // 检查文件是否存在
            if (!assetExists(assetPath)) {
                progress.status.set(ImportStatus.SKIPPED)
                progressCallback(dictType, 1.0f, "${dictInfo.displayName} 文件不存在，跳过")
                return@withContext true
            }
            
            // 第一遍：计算总行数
            val totalLines = countLinesInAsset(assetPath)
            progress.totalCount.set(totalLines)
            
            progress.status.set(ImportStatus.IMPORTING)
            progressCallback(dictType, 0.0f, "开始导入 ${dictInfo.displayName} ($totalLines 行)")
            
            // 第二遍：实际导入数据
            val importedCount = importDictionaryData(dictType, assetPath) { currentLine ->
                progress.currentCount.set(currentLine)
                val dictProgress = if (totalLines > 0) currentLine.toFloat() / totalLines else 0.0f
                progressCallback(dictType, dictProgress, "导入 ${dictInfo.displayName}: $currentLine/$totalLines")
            }
            
            if (importedCount > 0) {
                progress.status.set(ImportStatus.COMPLETED)
                progressCallback(dictType, 1.0f, "${dictInfo.displayName} 完成 ($importedCount 条)")
                return@withContext true
            } else {
                progress.status.set(ImportStatus.FAILED)
                progressCallback(dictType, 0.0f, "${dictInfo.displayName} 导入失败")
                return@withContext false
            }
            
        } catch (e: CancellationException) {
            progress.status.set(ImportStatus.FAILED)
            throw e
        } catch (e: Exception) {
            progress.status.set(ImportStatus.FAILED)
            progressCallback(dictType, 0.0f, "${dictInfo.displayName} 导入异常: ${e.message}")
            Timber.e(e, "导入词典失败: $dictType")
            return@withContext false
        }
    }
    
    /**
     * 导入词典数据
     */
    private suspend fun importDictionaryData(
        dictType: String,
        assetPath: String,
        progressCallback: (Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        
        var importedCount = 0
        var currentLine = 0
        val batch = mutableListOf<Entry>()
        
        try {
            val inputStream = context.assets.open(assetPath)
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            
            reader.use { r ->
                r.forEachLine { line ->
                    ensureActive()
                    currentLine++
                    
                    val entry = parseLineToEntry(line, dictType)
                    if (entry != null) {
                        batch.add(entry)
                        importedCount++
                        
                        // 批量写入
                        if (batch.size >= BATCH_SIZE) {
                            writeChannel.trySend(batch.toList())
                            batch.clear()
                        }
                    }
                    
                    // 更新进度
                    if (currentLine % 1000 == 0) {
                        progressCallback(currentLine)
                    }
                }
            }
            
            // 写入剩余数据
            if (batch.isNotEmpty()) {
                writeChannel.trySend(batch.toList())
            }
            
            progressCallback(currentLine)
            return@withContext importedCount
            
        } catch (e: Exception) {
            Timber.e(e, "导入词典数据失败: $dictType")
            return@withContext 0
        }
    }
    
    /**
     * 解析行数据为Entry对象
     */
    private fun parseLineToEntry(line: String, dictType: String): Entry? {
        try {
            if (line.trim().isEmpty() || line.startsWith("#")) {
                return null
            }
            
            val parts = line.split("\t")
            if (parts.size < 2) return null
            
            val word = parts[0].trim()
            val pinyin = removeTones(parts[1].trim())
            val frequency = if (parts.size > 2) parts[2].toIntOrNull() ?: 0 else 0
            
            // 生成唯一ID，避免主键冲突
            val uniqueId = "${dictType}_${word}_${pinyin}_${frequency}".hashCode().toString()
            
            return Entry().apply {
                this.id = uniqueId
                this.word = word
                this.pinyin = pinyin
                this.frequency = frequency
                this.type = dictType
                this.initialLetters = generateInitialLetters(pinyin)
            }
            
        } catch (e: Exception) {
            Timber.w("解析行数据失败: $line")
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
     * 生成首字母缩写
     */
    private fun generateInitialLetters(pinyin: String): String {
        return pinyin.split(" ").joinToString("") { syllable ->
            if (syllable.isNotEmpty()) syllable[0].toString() else ""
        }
    }
    
    /**
     * 计算总体进度
     */
    private fun calculateOverallProgress(): Float {
        if (progressMap.isEmpty()) return 0.0f
        
        var totalProgress = 0.0f
        var totalWeight = 0
        
        for ((_, progress) in progressMap) {
            val dictInfo = availableDictionaries.find { it.key == progress.type }
            val weight = dictInfo?.estimatedSize ?: 1000
            
            val dictProgress = when (progress.status.get()) {
                ImportStatus.COMPLETED -> 1.0f
                ImportStatus.FAILED, ImportStatus.SKIPPED -> 0.0f
                else -> {
                    val total = progress.totalCount.get()
                    val current = progress.currentCount.get()
                    if (total > 0) current.toFloat() / total else 0.0f
                }
            }
            
            totalProgress += dictProgress * weight
            totalWeight += weight
        }
        
        return if (totalWeight > 0) (totalProgress / totalWeight) * 0.9f + 0.1f else 0.1f
    }
    
    /**
     * 检查资源文件是否存在
     */
    private fun assetExists(assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).use { true }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 计算资源文件行数
     */
    private suspend fun countLinesInAsset(assetPath: String): Int = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.assets.open(assetPath)
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            return@withContext reader.use { it.readLines().size }
        } catch (e: Exception) {
            Timber.e(e, "计算文件行数失败: $assetPath")
            return@withContext 0
        }
    }
    
    /**
     * 标记词典为已完成
     */
    private fun markDictionaryCompleted(dictType: String) {
        val completed = getCompletedDictionaries().toMutableSet()
        completed.add(dictType)
        prefs.edit().putStringSet("completed_dictionaries", completed).apply()
    }
    
    /**
     * 清理断点数据
     */
    private fun clearBreakpointData() {
        prefs.edit().clear().apply()
    }
    
    /**
     * 关闭当前数据库
     */
    private fun closeCurrentDatabase() {
        try {
            ShenjiApplication.realm.close()
            Timber.d("数据库连接已关闭")
        } catch (e: Exception) {
            Timber.e(e, "关闭数据库失败")
        }
    }
    
    /**
     * 删除旧数据库文件
     */
    private fun deleteOldDatabase() {
        try {
            val dbDir = File(context.filesDir, "dictionaries")
            if (dbDir.exists()) {
                // 删除所有数据库相关文件
                dbDir.listFiles()?.forEach { file ->
                    if (file.name.contains("shenji_dict")) {
                        val deleted = file.delete()
                        Timber.d("删除文件 ${file.name}: $deleted")
                    }
                }
                
                // 如果目录为空，删除目录
                if (dbDir.listFiles()?.isEmpty() == true) {
                    dbDir.delete()
                    Timber.d("删除空目录: dictionaries")
                }
            }
            
            // 强制垃圾回收，释放文件句柄
            System.gc()
            Thread.sleep(100) // 等待一下确保文件句柄释放
            
        } catch (e: Exception) {
            Timber.e(e, "删除旧数据库失败")
        }
    }
    
    /**
     * 创建优化的数据库
     */
    private fun createOptimizedDatabase(): Realm {
        val dbDir = File(context.filesDir, "dictionaries")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        
        val config = RealmConfiguration.Builder(schema = setOf(
            Entry::class
        ))
            .directory(dbDir.absolutePath)
            .name("shenji_dict.realm")
            .deleteRealmIfMigrationNeeded()
            .build()
        
        val realm = Realm.open(config)
        
        // 更新全局realm引用
        ShenjiApplication.realm = realm
        
        return realm
    }
    
    /**
     * 优化数据库索引
     */
    private suspend fun optimizeDatabaseIndexes(realm: Realm) = withContext(Dispatchers.IO) {
        try {
            // Realm会自动为@Index注解的字段创建索引
            Timber.d("数据库索引优化完成")
        } catch (e: Exception) {
            Timber.e(e, "优化数据库索引失败")
        }
    }
    
    /**
     * 重新初始化应用组件
     */
    private suspend fun reinitializeAppComponents(realm: Realm) = withContext(Dispatchers.IO) {
        try {
            Timber.d("开始重新初始化应用组件...")
            
            // 确保全局realm引用已更新
            ShenjiApplication.realm = realm
            
            // 强制重新创建DictionaryRepository实例
            // 这样它会使用新的realm实例
            val repository = DictionaryRepository()
            
            // 清理所有缓存
            repository.clearCache()
            
            // 验证数据库连接
            val totalCount = repository.getTotalEntryCount()
            Timber.d("数据库验证: 总词条数 = $totalCount")
            
            if (totalCount > 0) {
                // 预热缓存
                repository.warmupCache()
                Timber.d("应用组件重新初始化完成，词条总数: $totalCount")
            } else {
                Timber.w("警告: 数据库重新初始化后词条数为0")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "重新初始化应用组件失败")
            throw e
        }
    }
    
    /**
     * 获取当前进度状态
     */
    fun getCurrentProgress(): Map<String, ImportProgress> = progressMap.toMap()
} 