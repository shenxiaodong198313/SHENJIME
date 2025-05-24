package com.shenji.aikeyboard.data.trie

import android.content.Context
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.WordFrequency
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
    private val trieMap = ConcurrentHashMap<TrieBuilder.TrieType, PinyinTrie>()
    
    // 加载状态映射
    private val loadedStatus = ConcurrentHashMap<TrieBuilder.TrieType, Boolean>()
    
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
     * 使用并行加载和优化的I/O操作
     */
    fun init() {
        if (isInitialized) return
        
        val startTime = System.currentTimeMillis()
        Timber.d("TrieManager开始高性能初始化")
        
        // 预分配内存，减少GC压力
        System.gc()
        
        // 并行加载预构建的Trie文件
        val loadSuccess = loadPrebuiltTriesParallel()
        
        isInitialized = true
        
        val endTime = System.currentTimeMillis()
        val loadTime = endTime - startTime
        
        if (loadSuccess) {
            val loadedTypes = getLoadedTrieTypes()
            Timber.d("TrieManager高性能初始化完成，耗时${loadTime}ms，成功加载${loadedTypes.size}个预构建Trie: ${loadedTypes.map { getDisplayName(it) }}")
        } else {
            Timber.d("TrieManager初始化完成，耗时${loadTime}ms，未找到预构建Trie文件")
        }
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
        val futures = mutableListOf<CompletableFuture<Pair<TrieBuilder.TrieType, Boolean>>>()
        
        // 为每个词典类型创建并行加载任务
        for (trieType in TrieBuilder.TrieType.values()) {
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
                FileOutputStream(tempFile).use { fos ->
                    val channel = fos.channel
                    channel.write(mappedBuffer)
                }
                
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
     * 优化的Trie反序列化方法
     */
    private fun deserializeTrieOptimized(file: File): PinyinTrie? {
        return try {
            FileInputStream(file).buffered(LARGE_BUFFER_SIZE).use { fis ->
                ObjectInputStream(fis).use { ois ->
                    try {
                        // 尝试读取版本号（新格式）
                        val version = ois.readInt()
                        if (version == 2) { // SERIALIZATION_VERSION
                            ois.readObject() as PinyinTrie
                        } else {
                            // 版本不匹配，按旧格式重新读取
                            deserializeTrieLegacyFormat(file)
                        }
                    } catch (e: Exception) {
                        // 可能是旧格式，直接读取对象
                        deserializeTrieLegacyFormat(file)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "优化反序列化失败")
            null
        }
    }
    
    /**
     * 旧格式Trie反序列化
     */
    private fun deserializeTrieLegacyFormat(file: File): PinyinTrie? {
        return try {
            FileInputStream(file).buffered(LARGE_BUFFER_SIZE).use { fis ->
                ObjectInputStream(fis).use { ois ->
                    ois.readObject() as PinyinTrie
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "旧格式反序列化失败")
            null
        }
    }
    
    /**
     * 手动加载指定类型的Trie树到内存（优化版本）
     * @param type Trie树类型
     * @return 是否加载成功
     */
    fun loadTrieToMemory(type: TrieBuilder.TrieType): Boolean {
        val context = ShenjiApplication.appContext
        
        return try {
            val startTime = System.currentTimeMillis()
            var trie: PinyinTrie? = null
            
            // 首先尝试从用户构建的文件加载
            val builder = TrieBuilder(context)
            trie = builder.loadTrie(type)
            
            if (trie != null) {
                Timber.d("从用户构建文件加载${getDisplayName(type)}Trie成功")
            } else {
                // 如果用户文件不存在，尝试从assets加载预编译文件（使用优化方法）
                val assetPath = "trie/${getTypeString(type)}_trie.dat"
                if (isAssetFileExists(context, assetPath)) {
                    trie = loadTrieFromAssetsOptimized(context, assetPath)
                    if (trie != null) {
                        Timber.d("从assets预编译文件加载${getDisplayName(type)}Trie成功")
                    }
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
        }
    }
    
    /**
     * 卸载指定类型的Trie树，释放内存
     * @param type Trie树类型
     */
    fun unloadTrie(type: TrieBuilder.TrieType) {
        trieMap.remove(type)
        loadedStatus[type] = false
        Timber.d("${getDisplayName(type)}Trie树已卸载")
    }
    
    /**
     * 检查指定类型的Trie树是否已加载到内存
     * @param type Trie树类型
     * @return 是否已加载
     */
    fun isTrieLoaded(type: TrieBuilder.TrieType): Boolean {
        return loadedStatus[type] == true && trieMap.containsKey(type)
    }
    
    /**
     * 获取指定类型Trie树的内存统计信息
     * @param type Trie树类型
     * @return 内存统计信息，如果未加载则返回null
     */
    fun getTrieMemoryStats(type: TrieBuilder.TrieType): TrieMemoryStats? {
        return trieMap[type]?.getMemoryStats()
    }
    
    /**
     * 获取指定类型Trie树的大致内存占用（估计值）
     * @param type Trie树类型
     * @return 估计的内存占用字节数，如果未加载则返回0
     */
    fun getTrieMemoryUsage(type: TrieBuilder.TrieType): Long {
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
    fun searchByPrefix(type: TrieBuilder.TrieType, prefix: String, limit: Int = 10): List<WordFrequency> {
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
        return searchByPrefix(TrieBuilder.TrieType.CHARS, prefix, limit)
    }
    
    /**
     * 根据拼音前缀查询基础词典（兼容性方法）
     */
    fun searchBaseByPrefix(prefix: String, limit: Int = 10): List<WordFrequency> {
        return searchByPrefix(TrieBuilder.TrieType.BASE, prefix, limit)
    }
    
    /**
     * 检查是否存在某类型的Trie树文件
     * @param type Trie树类型
     * @return 是否存在文件（包括assets中的预构建文件）
     */
    fun isTrieFileExists(type: TrieBuilder.TrieType): Boolean {
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
    fun hasPrebuiltTrie(type: TrieBuilder.TrieType): Boolean {
        val context = ShenjiApplication.appContext
        val fileName = "trie/${getTypeString(type)}_trie.dat"
        return isAssetFileExists(context, fileName)
    }
    
    /**
     * 检查是否存在用户构建的Trie文件
     * @param type Trie树类型
     * @return 是否存在用户构建文件
     */
    fun hasUserBuiltTrie(type: TrieBuilder.TrieType): Boolean {
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
    fun getTrieFileStatus(type: TrieBuilder.TrieType): TrieFileStatus {
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
        val type: TrieBuilder.TrieType,
        val hasPrebuiltFile: Boolean,
        val hasUserBuiltFile: Boolean,
        val userFileSize: Long,
        val userFileLastModified: Long,
        val isLoadedInMemory: Boolean
    )
    
    /**
     * 获取所有已加载的Trie类型
     */
    fun getLoadedTrieTypes(): List<TrieBuilder.TrieType> {
        return loadedStatus.filter { it.value }.keys.toList()
    }
    
    /**
     * 获取所有可用的Trie文件类型
     */
    fun getAvailableTrieTypes(): List<TrieBuilder.TrieType> {
        return TrieBuilder.TrieType.values().filter { isTrieFileExists(it) }
    }
    
    /**
     * 获取Trie类型对应的字符串
     */
    private fun getTypeString(trieType: TrieBuilder.TrieType): String {
        return when (trieType) {
            TrieBuilder.TrieType.CHARS -> "chars"
            TrieBuilder.TrieType.BASE -> "base"
            TrieBuilder.TrieType.CORRELATION -> "correlation"
            TrieBuilder.TrieType.ASSOCIATIONAL -> "associational"
            TrieBuilder.TrieType.PLACE -> "place"
            TrieBuilder.TrieType.PEOPLE -> "people"
            TrieBuilder.TrieType.POETRY -> "poetry"
            TrieBuilder.TrieType.CORRECTIONS -> "corrections"
            TrieBuilder.TrieType.COMPATIBLE -> "compatible"
        }
    }
    
    /**
     * 获取Trie类型的显示名称
     */
    private fun getDisplayName(trieType: TrieBuilder.TrieType): String {
        return when (trieType) {
            TrieBuilder.TrieType.CHARS -> "单字"
            TrieBuilder.TrieType.BASE -> "基础词典"
            TrieBuilder.TrieType.CORRELATION -> "关联词典"
            TrieBuilder.TrieType.ASSOCIATIONAL -> "联想词典"
            TrieBuilder.TrieType.PLACE -> "地名词典"
            TrieBuilder.TrieType.PEOPLE -> "人名词典"
            TrieBuilder.TrieType.POETRY -> "诗词词典"
            TrieBuilder.TrieType.CORRECTIONS -> "纠错词典"
            TrieBuilder.TrieType.COMPATIBLE -> "兼容词典"
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