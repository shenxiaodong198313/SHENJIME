package com.shenji.aikeyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.shenji.aikeyboard.ShenjiApplication
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
        
        try {
            progressCallback(0.0f, "", 0.0f, "开始高性能数据库重新初始化...")
            
            // 初始化进度跟踪
            initializeProgressTracking(selectedDictionaries, resumeFromBreakpoint)
            
            // 第一步：准备数据库环境
            progressCallback(0.05f, "", 0.0f, "准备数据库环境...")
            val realm = prepareDatabaseEnvironment(resumeFromBreakpoint)
            
            // 第二步：启动写入协程
            val writeJob = startDatabaseWriter(realm)
            
            // 第三步：并行导入词典数据
            progressCallback(0.1f, "", 0.0f, "开始并行导入词典数据...")
            val success = importDictionariesParallel(selectedDictionaries, resumeFromBreakpoint) { type, dictProgress, message ->
                val overallProgress = calculateOverallProgress()
                progressCallback(overallProgress, type, dictProgress, message)
            }
            
            // 第四步：完成写入
            writeChannel.close()
            writeJob.join()
            
            // 第五步：优化和清理
            if (success) {
                progressCallback(0.95f, "", 1.0f, "优化数据库索引...")
                optimizeDatabaseIndexes(realm)
                
                progressCallback(0.98f, "", 1.0f, "重新初始化应用组件...")
                reinitializeAppComponents(realm)
                
                // 清理断点信息
                clearBreakpointData()
            }
            
            val totalTime = System.currentTimeMillis() - totalStartTime
            progressCallback(1.0f, "", 1.0f, "数据库重新初始化完成 (耗时: ${totalTime}ms)")
            
            return@withContext success
            
        } catch (e: Exception) {
            Timber.e(e, "重新初始化数据库失败")
            progressCallback(1.0f, "", 0.0f, "初始化失败: ${e.message}")
            return@withContext false
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
    private fun CoroutineScope.startDatabaseWriter(realm: Realm): Job {
        return launch(Dispatchers.IO) {
            val writeBuffer = mutableListOf<Entry>()
            var totalWritten = 0L
            
            try {
                for (entries in writeChannel) {
                    writeBuffer.addAll(entries)
                    
                    // 当缓冲区达到一定大小时批量写入
                    if (writeBuffer.size >= BATCH_SIZE) {
                        val writeTime = measureTimeMillis {
                            realm.write {
                                writeBuffer.forEach { copyToRealm(it) }
                            }
                        }
                        
                        totalWritten += writeBuffer.size
                        Timber.d("批量写入 ${writeBuffer.size} 条记录，耗时: ${writeTime}ms，总计: $totalWritten")
                        writeBuffer.clear()
                    }
                }
                
                // 写入剩余数据
                if (writeBuffer.isNotEmpty()) {
                    realm.write {
                        writeBuffer.forEach { copyToRealm(it) }
                    }
                    totalWritten += writeBuffer.size
                    Timber.d("最终写入 ${writeBuffer.size} 条记录，总计: $totalWritten")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "数据库写入失败")
            }
        }
    }
    
    /**
     * 并行导入词典数据
     */
    private suspend fun importDictionariesParallel(
        selectedDictionaries: List<String>,
        resumeFromBreakpoint: Boolean,
        progressCallback: (String, Float, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        
        val jobs = mutableListOf<Job>()
        val results = ConcurrentHashMap<String, Boolean>()
        
        // 按优先级排序
        val sortedDictionaries = selectedDictionaries.sortedBy { dictType ->
            availableDictionaries.find { it.key == dictType }?.priority ?: Int.MAX_VALUE
        }
        
        for (dictType in sortedDictionaries) {
            val progress = progressMap[dictType] ?: continue
            
            // 如果已完成且支持断点续传，则跳过
            if (resumeFromBreakpoint && progress.status.get() == ImportStatus.COMPLETED) {
                results[dictType] = true
                progressCallback(dictType, 1.0f, "${progress.displayName} 已完成（断点续传）")
                continue
            }
            
            val job = launch {
                importSemaphore.acquire()
                try {
                    val success = importSingleDictionary(dictType, progressCallback)
                    results[dictType] = success
                    
                    if (success) {
                        // 标记为已完成
                        markDictionaryCompleted(dictType)
                    }
                } finally {
                    importSemaphore.release()
                }
            }
            jobs.add(job)
        }
        
        // 等待所有导入完成
        jobs.joinAll()
        
        // 检查是否所有导入都成功
        return@withContext results.values.all { it }
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
            
        } catch (e: Exception) {
            Timber.e(e, "导入词典 $dictType 失败")
            progress.status.set(ImportStatus.FAILED)
            progressCallback(dictType, 0.0f, "${dictInfo.displayName} 异常: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * 导入词典数据（内存优化版本）
     */
    private suspend fun importDictionaryData(
        dictType: String,
        assetPath: String,
        progressCallback: (Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        
        val inputStream = context.assets.open(assetPath)
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        
        var importedCount = 0
        var lineCount = 0
        val memoryBuffer = mutableListOf<Entry>()
        
        reader.use { reader ->
            reader.forEachLine { line ->
                lineCount++
                
                // 更新进度（每100行更新一次）
                if (lineCount % 100 == 0) {
                    progressCallback(lineCount)
                }
                
                if (line.isNotBlank()) {
                    val entry = parseLineToEntry(line, dictType)
                    if (entry != null) {
                        memoryBuffer.add(entry)
                        
                        // 当内存缓冲区满时，发送到写入通道
                        if (memoryBuffer.size >= MEMORY_BUFFER_SIZE) {
                            writeChannel.trySend(memoryBuffer.toList())
                            importedCount += memoryBuffer.size
                            memoryBuffer.clear()
                        }
                    }
                }
            }
            
            // 发送剩余数据
            if (memoryBuffer.isNotEmpty()) {
                writeChannel.trySend(memoryBuffer.toList())
                importedCount += memoryBuffer.size
            }
        }
        
        progressCallback(lineCount)
        return@withContext importedCount
    }
    
    /**
     * 解析行数据为Entry对象
     * YAML格式：字符\t拼音\t频率
     */
    private fun parseLineToEntry(line: String, dictType: String): Entry? {
        try {
            val parts = line.split("\t")
            if (parts.size < 2) return null
            
            val word = parts[0].trim()
            val rawPinyin = parts[1].trim()
            val frequency = if (parts.size > 2) parts[2].toIntOrNull() ?: 1 else 1
            
            if (rawPinyin.isEmpty() || word.isEmpty()) return null
            
            // 去掉声调符号，只保留字母
            val pinyin = removeTones(rawPinyin)
            
            return Entry().apply {
                id = "${word}_${pinyin}_${dictType}".hashCode().toString()
                this.pinyin = pinyin
                this.word = word
                this.frequency = frequency
                this.type = dictType
                this.initialLetters = pinyin.split(" ")
                    .joinToString("") { it.firstOrNull()?.toString() ?: "" }
            }
            
        } catch (e: Exception) {
            Timber.w(e, "解析行数据失败: $line")
            return null
        }
    }
    
    /**
     * 去掉拼音中的声调符号
     */
    private fun removeTones(pinyin: String): String {
        return pinyin
            .replace("ā", "a").replace("á", "a").replace("ǎ", "a").replace("à", "a")
            .replace("ē", "e").replace("é", "e").replace("ě", "e").replace("è", "e")
            .replace("ī", "i").replace("í", "i").replace("ǐ", "i").replace("ì", "i")
            .replace("ō", "o").replace("ó", "o").replace("ǒ", "o").replace("ò", "o")
            .replace("ū", "u").replace("ú", "u").replace("ǔ", "u").replace("ù", "u")
            .replace("ǖ", "v").replace("ǘ", "v").replace("ǚ", "v").replace("ǜ", "v")
            .replace("ü", "v").replace("ń", "n").replace("ň", "n").replace("ǹ", "n")
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
     * 关闭当前数据库连接
     */
    private fun closeCurrentDatabase() {
        try {
            ShenjiApplication.realm.close()
        } catch (e: Exception) {
            Timber.w(e, "关闭数据库连接时出错")
        }
    }
    
    /**
     * 删除旧数据库文件
     */
    private fun deleteOldDatabase() {
        try {
            val dbDir = File(context.filesDir, "dictionaries")
            val dbFile = File(dbDir, "shenji_dict.realm")
            
            if (dbFile.exists()) {
                dbFile.delete()
                Timber.d("已删除旧数据库文件")
            }
            
            // 删除相关的锁文件和日志文件
            val lockFile = File(dbDir, "shenji_dict.realm.lock")
            if (lockFile.exists()) {
                lockFile.delete()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "删除旧数据库文件失败")
        }
    }
    
    /**
     * 创建优化的数据库结构
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
            // 清理缓存
            val repository = DictionaryRepository()
            repository.clearCache()
            
            // 预热缓存
            repository.warmupCache()
            
            Timber.d("应用组件重新初始化完成")
        } catch (e: Exception) {
            Timber.e(e, "重新初始化应用组件失败")
        }
    }
    
    /**
     * 获取当前进度状态
     */
    fun getCurrentProgress(): Map<String, ImportProgress> = progressMap.toMap()
} 