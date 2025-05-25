package com.shenji.aikeyboard.data.trie

import android.content.Context
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.model.WordFrequency
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

/**
 * 高性能Trie树管理器 - 单例模式
 * 负责管理所有类型的Trie树的加载、卸载和查询
 * 
 * 性能优化特性：
 * - 并行加载多个词典
 * - 64KB大缓冲区文件复制
 * - 内存映射文件支持
 * - 预分配内存池
 * - 优化的反序列化流程
 */
class TrieManager private constructor() {
    
    // Trie树存储映射
    private val trieMap = ConcurrentHashMap<TrieType, PinyinTrie>()
    
    // 加载状态映射
    private val loadedStatus = ConcurrentHashMap<TrieType, Boolean>()
    
    // 加载中状态映射 - 新增
    private val loadingStatus = ConcurrentHashMap<TrieType, Boolean>()
    
    // 初始化状态
    private var isInitialized = false
    
    // 并行加载线程池
    private val loadingExecutor = Executors.newFixedThreadPool(
        minOf(Runtime.getRuntime().availableProcessors(), 4)
    )
    
    // 性能优化常量
    companion object {
        private const val LARGE_BUFFER_SIZE = 64 * 1024 // 64KB缓冲区
        private const val MEMORY_MAPPED_THRESHOLD = 10 * 1024 * 1024 // 10MB以上使用内存映射
        private const val PARALLEL_LOAD_TIMEOUT = 60L // 并行加载超时时间（秒）
        
        // 单例实例
        @JvmStatic
        fun getInstance(context: Context): TrieManager {
            return instance
        }
        
        val instance: TrieManager by lazy {
            TrieManager()
        }
    }
    
    /**
     * 高性能初始化TrieManager
     * 优化版本：不在启动时加载所有词典，只进行基础初始化
     */
    fun init() {
        if (isInitialized) return
        
        val startTime = System.currentTimeMillis()
        Timber.d("TrieManager开始轻量级初始化")
        
        // 预分配内存，减少GC压力
        System.gc()
        
        // 🔧 优化：不在启动时并行加载所有词典，避免内存压力
        // 只进行基础的状态检查
        val availableTypes = checkAvailableTrieFiles()
        
        isInitialized = true
        
        val endTime = System.currentTimeMillis()
        val loadTime = endTime - startTime
        
        Timber.d("TrieManager轻量级初始化完成，耗时${loadTime}ms")
        Timber.d("可用的Trie文件: ${availableTypes.map { getDisplayName(it) }}")
        Timber.d("词典将按需加载，减少启动时内存压力")
    }
    
    /**
     * 检查可用的Trie文件（不加载到内存）
     */
    private fun checkAvailableTrieFiles(): List<TrieType> {
        val context = ShenjiApplication.appContext
        val availableTypes = mutableListOf<TrieType>()
        
        for (trieType in TrieType.values()) {
            val assetPath = "trie/${getTypeString(trieType)}_trie.dat"
            
            if (isAssetFileExists(context, assetPath)) {
                availableTypes.add(trieType)
                Timber.d("发现可用Trie文件: ${getDisplayName(trieType)}")
            }
        }
        
        return availableTypes
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * 检查所有Trie是否已加载
     */
    fun isTriesLoaded(): Boolean {
        return trieMap.isNotEmpty()
    }
    
    /**
     * 公开加载Trie的方法
     */
    fun loadTries() {
        if (!isInitialized) {
            init()
        }
    }
    
    /**
     * 并行加载预构建的Trie文件
     * @return 是否成功加载任何Trie文件
     */
    private fun loadPrebuiltTriesParallel(): Boolean {
        val context = ShenjiApplication.appContext
        val futures = mutableListOf<CompletableFuture<Pair<TrieType, Boolean>>>()
        
        // 为每个词典类型创建并行加载任务
        for (trieType in TrieType.values()) {
            val future = CompletableFuture.supplyAsync({
                try {
                    val assetPath = "trie/${getTypeString(trieType)}_trie.dat"
                    
                    if (isAssetFileExists(context, assetPath)) {
                        val startTime = System.currentTimeMillis()
                        val trie = loadTrieFromAssetsOptimized(context, assetPath)
                        val endTime = System.currentTimeMillis()
                        
                        if (trie != null) {
                            trieMap[trieType] = trie
                            loadedStatus[trieType] = true
                            
                            val stats = trie.getMemoryStats()
                            Timber.d("并行加载${getDisplayName(trieType)}Trie成功，耗时${endTime - startTime}ms: $stats")
                            return@supplyAsync Pair(trieType, true)
                        }
                    }
                    Pair(trieType, false)
                } catch (e: Exception) {
                    Timber.e(e, "并行加载${getDisplayName(trieType)}Trie失败")
                    Pair(trieType, false)
                }
            }, loadingExecutor)
            
            futures.add(future)
        }
        
        // 等待所有加载任务完成
        return try {
            val results = CompletableFuture.allOf(*futures.toTypedArray())
                .get(PARALLEL_LOAD_TIMEOUT, TimeUnit.SECONDS)
            
            val successCount = futures.count { 
                try { it.get().second } catch (e: Exception) { false }
            }
            
            Timber.d("并行加载完成，成功加载${successCount}个词典")
            successCount > 0
        } catch (e: Exception) {
            Timber.e(e, "并行加载超时或失败")
            false
        }
    }
    
    /**
     * 检查assets中是否存在指定文件
     */
    private fun isAssetFileExists(context: Context, fileName: String): Boolean {
        return try {
            val inputStream = context.assets.open(fileName)
            inputStream.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 优化版本：从assets加载Trie文件
     * 使用内存映射和大缓冲区优化
     */
    private fun loadTrieFromAssetsOptimized(context: Context, assetPath: String): PinyinTrie? {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                val fileSize = inputStream.available()
                
                // 根据文件大小选择加载策略
                if (fileSize > MEMORY_MAPPED_THRESHOLD) {
                    loadLargeTrieWithMemoryMapping(inputStream, fileSize, context)
                } else {
                    loadSmallTrieWithOptimizedBuffer(inputStream, context)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "优化加载Trie文件失败: $assetPath")
            null
        }
    }
    
    /**
     * 使用内存映射加载大型Trie文件
     */
    private fun loadLargeTrieWithMemoryMapping(
        inputStream: InputStream, 
        fileSize: Int, 
        context: Context
    ): PinyinTrie? {
        val tempFile = File(context.cacheDir, "temp_trie_mmap_${System.currentTimeMillis()}.dat")
        
        return try {
            // 使用大缓冲区复制文件
            copyInputStreamToFileOptimized(inputStream, tempFile)
            
            // 使用内存映射读取
            RandomAccessFile(tempFile, "r").use { randomAccessFile ->
                val channel = randomAccessFile.channel
                val mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, tempFile.length())
                
                // 从映射缓冲区反序列化
                val trie = deserializeFromMappedBuffer(mappedBuffer)
                
                // 验证Trie树
                if (trie?.isEmpty() == false) {
                    trie
                } else {
                    Timber.w("内存映射加载的Trie树为空")
                    null
                }
            }
        } finally {
            // 清理临时文件
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
    
    /**
     * 使用优化缓冲区加载小型Trie文件
     */
    private fun loadSmallTrieWithOptimizedBuffer(inputStream: InputStream, context: Context): PinyinTrie? {
        val tempFile = File(context.cacheDir, "temp_trie_opt_${System.currentTimeMillis()}.dat")
        
        return try {
            // 使用大缓冲区复制文件
            copyInputStreamToFileOptimized(inputStream, tempFile)
            
            // 优化的反序列化流程
            deserializeTrieOptimized(tempFile)
        } finally {
            // 清理临时文件
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
    
    /**
     * 优化的文件复制方法 - 使用64KB缓冲区
     */
    private fun copyInputStreamToFileOptimized(inputStream: InputStream, file: File) {
        FileOutputStream(file).use { outputStream ->
            val buffer = ByteArray(LARGE_BUFFER_SIZE) // 64KB缓冲区
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            outputStream.flush()
        }
    }
    
    /**
     * 从内存映射缓冲区反序列化Trie
     */
    private fun deserializeFromMappedBuffer(mappedBuffer: MappedByteBuffer): PinyinTrie? {
        return try {
            // 创建临时文件用于ObjectInputStream
            val tempFile = File.createTempFile("mmap_deserialize", ".tmp")
            try {
                val fos = FileOutputStream(tempFile)
                val channel = fos.channel
                channel.write(mappedBuffer)
                fos.close()
                
                deserializeTrieOptimized(tempFile)
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            Timber.e(e, "内存映射反序列化失败")
            null
        }
    }
    
    /**
     * 简化的Trie反序列化方法 - 统一使用版本3格式
     */
    private fun deserializeTrieOptimized(file: File): PinyinTrie? {
        return try {
            FileInputStream(file).use { fis ->
                deserializeSimplifiedFormat(fis.buffered(LARGE_BUFFER_SIZE))
            }
        } catch (e: Exception) {
            Timber.e(e, "Trie反序列化失败: ${file.name}")
            null
        }
    }
    
    /**
     * 统一的简化格式反序列化（版本3格式）
     */
    private fun deserializeSimplifiedFormat(inputStream: java.io.InputStream): PinyinTrie? {
        return try {
            val trie = PinyinTrie()
            
            // 读取版本号（固定为3）
            val versionBytes = ByteArray(4)
            if (inputStream.read(versionBytes) != 4) {
                Timber.e("无法读取版本号")
                return null
            }
            val version = java.nio.ByteBuffer.wrap(versionBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            
            if (version != 3) {
                Timber.e("不支持的版本号: $version，期望版本3")
                return null
            }
            
            // 读取拼音条目数量
            val countBytes = ByteArray(4)
            if (inputStream.read(countBytes) != 4) {
                Timber.e("无法读取拼音条目数量")
                return null
            }
            val count = java.nio.ByteBuffer.wrap(countBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            Timber.d("开始加载 $count 个拼音条目")
            
            var loadedCount = 0
            var totalWords = 0
            var skippedWords = 0
            
            // 内存检查
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val initialUsedMemory = runtime.totalMemory() - runtime.freeMemory()
            
            for (i in 0 until count) {
                try {
                    // 每1000个条目检查一次内存
                    if (loadedCount % 1000 == 0) {
                        val currentUsedMemory = runtime.totalMemory() - runtime.freeMemory()
                        val memoryUsagePercent = (currentUsedMemory * 100) / maxMemory
                        
                        if (memoryUsagePercent > 80) {
                            Timber.w("内存使用率过高 (${memoryUsagePercent}%)，停止加载以避免OOM")
                            break
                        }
                        
                        if (loadedCount % 10000 == 0) {
                            Timber.d("已加载 $loadedCount/$count 个拼音条目，内存使用率: ${memoryUsagePercent}%")
                        }
                    }
                    
                    // 读取拼音长度
                    val pinyinLenBytes = ByteArray(4)
                    if (inputStream.read(pinyinLenBytes) != 4) break
                    val pinyinLen = java.nio.ByteBuffer.wrap(pinyinLenBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                    
                    // 读取拼音
                    val pinyinBytes = ByteArray(pinyinLen)
                    if (inputStream.read(pinyinBytes) != pinyinLen) break
                    val pinyin = String(pinyinBytes, Charsets.UTF_8)
                    
                    // 读取词语数量
                    val wordCountBytes = ByteArray(4)
                    if (inputStream.read(wordCountBytes) != 4) break
                    val wordCount = java.nio.ByteBuffer.wrap(wordCountBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                    
                    // 处理拼音格式：只保留连写格式
                    val normalizedPinyin = pinyin.replace(" ", "").lowercase()
                    
                    // 读取每个词语
                    for (j in 0 until wordCount) {
                        // 读取词语长度
                        val wordLenBytes = ByteArray(4)
                        if (inputStream.read(wordLenBytes) != 4) break
                        val wordLen = java.nio.ByteBuffer.wrap(wordLenBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                        
                        // 读取词语
                        val wordBytes = ByteArray(wordLen)
                        if (inputStream.read(wordBytes) != wordLen) break
                        val word = String(wordBytes, Charsets.UTF_8)
                        
                        // 读取词频
                        val frequencyBytes = ByteArray(4)
                        if (inputStream.read(frequencyBytes) != 4) break
                        val frequency = java.nio.ByteBuffer.wrap(frequencyBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                        
                        // 插入到Trie树
                        try {
                            // 只加载高频词汇（词频>100）以减少内存压力
                            if (frequency > 100) {
                                trie.insert(normalizedPinyin, word, frequency)
                                totalWords++
                            } else {
                                skippedWords++
                            }
                        } catch (e: OutOfMemoryError) {
                            Timber.w("内存不足，跳过词语: $word")
                            skippedWords++
                            System.gc()
                        }
                    }
                    
                    loadedCount++
                } catch (e: Exception) {
                    Timber.w(e, "加载第 $i 个拼音条目时出错，跳过")
                    continue
                }
            }
            
            val stats = trie.getMemoryStats()
            val finalUsedMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryIncrease = (finalUsedMemory - initialUsedMemory) / 1024 / 1024
            
            Timber.d("Trie加载完成:")
            Timber.d("  - 加载了 $loadedCount/$count 个拼音条目")
            Timber.d("  - 总词语数: $totalWords")
            Timber.d("  - 跳过词语数: $skippedWords")
            Timber.d("  - 内存增加: ${memoryIncrease}MB")
            Timber.d("  - Trie统计: $stats")
            
            if (trie.isEmpty()) {
                Timber.w("加载的Trie树为空")
                return null
            }
            
            trie
        } catch (e: Exception) {
            Timber.e(e, "简化格式反序列化失败")
            null
        }
    }
    
    /**
     * 手动加载指定类型的Trie树到内存（优化版本）
     * @param type Trie树类型
     * @return 是否加载成功
     */
    fun loadTrieToMemory(type: TrieType): Boolean {
        val context = ShenjiApplication.appContext
        
        return try {
            // 设置加载中状态
            loadingStatus[type] = true
            
            val startTime = System.currentTimeMillis()
            var trie: PinyinTrie? = null
            
            // 尝试从assets加载预编译文件
            val assetPath = "trie/${getTypeString(type)}_trie.dat"
            if (isAssetFileExists(context, assetPath)) {
                trie = loadTrieFromAssetsOptimized(context, assetPath)
                if (trie != null) {
                    Timber.d("从assets预编译文件加载${getDisplayName(type)}Trie成功")
                }
            }
            
            if (trie != null) {
                trieMap[type] = trie
                loadedStatus[type] = true
                val stats = trie.getMemoryStats()
                val endTime = System.currentTimeMillis()
                Timber.d("手动加载${getDisplayName(type)}Trie树成功，耗时${endTime - startTime}ms: $stats")
                true
            } else {
                Timber.w("手动加载${getDisplayName(type)}Trie树失败: 文件不存在或格式错误")
                loadedStatus[type] = false
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "手动加载${getDisplayName(type)}Trie树失败")
            loadedStatus[type] = false
            false
        } finally {
            // 清除加载中状态
            loadingStatus[type] = false
        }
    }
    
    /**
     * 卸载指定类型的Trie树，释放内存
     * @param type Trie树类型
     */
    fun unloadTrie(type: TrieType) {
        trieMap.remove(type)
        loadedStatus[type] = false
        Timber.d("${getDisplayName(type)}Trie树已卸载")
    }
    
    /**
     * 检查指定类型的Trie树是否已加载到内存
     * @param type Trie树类型
     * @return 是否已加载
     */
    fun isTrieLoaded(type: TrieType): Boolean {
        return loadedStatus[type] == true && trieMap.containsKey(type)
    }
    
    /**
     * 获取指定类型的Trie树实例
     * @param type Trie树类型
     * @return Trie树实例，如果未加载则返回null
     */
    fun getTrie(type: TrieType): PinyinTrie? {
        return trieMap[type]
    }
    
    /**
     * 检查指定类型的Trie树是否正在加载中
     * @param type Trie树类型
     * @return 是否正在加载
     */
    fun isLoading(type: TrieType): Boolean {
        return loadingStatus[type] == true
    }
    
    /**
     * 获取指定类型Trie树的内存统计信息
     * @param type Trie树类型
     * @return 内存统计信息，如果未加载则返回null
     */
    fun getTrieMemoryStats(type: TrieType): TrieMemoryStats? {
        return trieMap[type]?.getMemoryStats()
    }
    
    /**
     * 获取指定类型Trie树的大致内存占用（估计值）
     * @param type Trie树类型
     * @return 估计的内存占用字节数，如果未加载则返回0
     */
    fun getTrieMemoryUsage(type: TrieType): Long {
        val stats = getTrieMemoryStats(type) ?: return 0
        
        // 粗略估计：每个节点约100字节，每个词条约50字节
        return (stats.nodeCount * 100L) + (stats.wordCount * 50L)
    }
    
    /**
     * 根据拼音前缀查询指定类型的Trie树
     * @param type Trie树类型
     * @param prefix 拼音前缀
     * @param limit 返回结果的最大数量
     * @return 匹配的词语列表，按频率排序
     */
    fun searchByPrefix(type: TrieType, prefix: String, limit: Int = 10): List<WordFrequency> {
        if (!isInitialized) init()
        
        val trie = trieMap[type]
        if (trie == null || !isTrieLoaded(type)) {
            Timber.w("${getDisplayName(type)}Trie树未加载，无法查询 - 已加载类型: ${getLoadedTrieTypes().map { getDisplayName(it) }}")
            return emptyList()
        }
        
        try {
            val startTime = System.currentTimeMillis()
            
            // 统一处理为小写
            val normalizedPrefix = prefix.lowercase().trim()
            
            // 检查Trie树是否为空
            if (trie.isEmpty()) {
                Timber.w("${getDisplayName(type)}Trie树为空，无法查询")
                return emptyList()
            }
            
            // 从Trie树查询结果
            val results = trie.searchByPrefix(normalizedPrefix, limit)
            
            // 转换为WordFrequency对象
            val wordFrequencies = results.map { WordFrequency(it.word, it.frequency) }
            
            val endTime = System.currentTimeMillis()
            val stats = trie.getMemoryStats()
            Timber.d("${getDisplayName(type)}Trie查询'$normalizedPrefix'，找到${results.size}个结果，耗时${endTime - startTime}ms，Trie统计: $stats")
            
            if (results.isEmpty() && normalizedPrefix.isNotEmpty()) {
                Timber.d("${getDisplayName(type)}Trie查询'$normalizedPrefix'无结果，Trie状态: 节点数=${stats.nodeCount}, 词条数=${stats.wordCount}")
            }
            
            return wordFrequencies
        } catch (e: Exception) {
            Timber.e(e, "${getDisplayName(type)}Trie树查询失败: $prefix")
            return emptyList()
        }
    }
    
    /**
     * 根据拼音前缀查询单字（兼容性方法）
     */
    fun searchCharsByPrefix(prefix: String, limit: Int = 10): List<WordFrequency> {
        return searchByPrefix(TrieType.CHARS, prefix, limit)
    }
    
    /**
     * 根据拼音前缀查询基础词典（兼容性方法）
     */
    fun searchBaseByPrefix(prefix: String, limit: Int = 10): List<WordFrequency> {
        return searchByPrefix(TrieType.BASE, prefix, limit)
    }
    
    /**
     * 检查是否存在某类型的Trie树文件
     * @param type Trie树类型
     * @return 是否存在文件（包括assets中的预构建文件）
     */
    fun isTrieFileExists(type: TrieType): Boolean {
        val context = ShenjiApplication.appContext
        val fileName = "trie/${getTypeString(type)}_trie.dat"
        
        // 首先检查用户构建的文件
        val file = File(context.filesDir, fileName)
        if (file.exists() && file.length() > 0) {
            return true
        }
        
        // 然后检查assets中的预构建文件
        return isAssetFileExists(context, fileName)
    }
    
    /**
     * 检查是否存在预编译的Trie文件（assets中）
     * @param type Trie树类型
     * @return 是否存在预编译文件
     */
    fun hasPrebuiltTrie(type: TrieType): Boolean {
        val context = ShenjiApplication.appContext
        val fileName = "trie/${getTypeString(type)}_trie.dat"
        return isAssetFileExists(context, fileName)
    }
    
    /**
     * 检查是否存在用户构建的Trie文件
     * @param type Trie树类型
     * @return 是否存在用户构建文件
     */
    fun hasUserBuiltTrie(type: TrieType): Boolean {
        val context = ShenjiApplication.appContext
        val fileName = "trie/${getTypeString(type)}_trie.dat"
        val file = File(context.filesDir, fileName)
        return file.exists() && file.length() > 0
    }
    
    /**
     * 获取Trie文件的详细状态信息
     * @param type Trie树类型
     * @return Trie文件状态信息
     */
    fun getTrieFileStatus(type: TrieType): TrieFileStatus {
        val context = ShenjiApplication.appContext
        val fileName = "trie/${getTypeString(type)}_trie.dat"
        
        // 检查用户构建的文件
        val userFile = File(context.filesDir, fileName)
        val hasUserBuilt = userFile.exists() && userFile.length() > 0
        val userFileSize = if (hasUserBuilt) userFile.length() else 0L
        val userFileTime = if (hasUserBuilt) userFile.lastModified() else 0L
        
        // 检查预编译文件
        val hasPrebuilt = isAssetFileExists(context, fileName)
        
        // 检查是否已加载到内存
        val isLoaded = isTrieLoaded(type)
        
        return TrieFileStatus(
            type = type,
            hasPrebuiltFile = hasPrebuilt,
            hasUserBuiltFile = hasUserBuilt,
            userFileSize = userFileSize,
            userFileLastModified = userFileTime,
            isLoadedInMemory = isLoaded
        )
    }
    
    /**
     * Trie文件状态数据类
     */
    data class TrieFileStatus(
        val type: TrieType,
        val hasPrebuiltFile: Boolean,
        val hasUserBuiltFile: Boolean,
        val userFileSize: Long,
        val userFileLastModified: Long,
        val isLoadedInMemory: Boolean
    )
    
    /**
     * 获取所有已加载的Trie类型
     */
    fun getLoadedTrieTypes(): List<TrieType> {
        return loadedStatus.filter { it.value }.keys.toList()
    }
    
    /**
     * 获取所有可用的Trie文件类型
     */
    fun getAvailableTrieTypes(): List<TrieType> {
        return TrieType.values().filter { isTrieFileExists(it) }
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
     * 释放Trie树资源
     * 在内存紧张时调用
     */
    fun release() {
        trieMap.clear()
        loadedStatus.clear()
        isInitialized = false
        
        // 关闭线程池
        try {
            loadingExecutor.shutdown()
            if (!loadingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                loadingExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            loadingExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        
        Timber.d("TrieManager资源已释放，线程池已关闭")
    }
} 