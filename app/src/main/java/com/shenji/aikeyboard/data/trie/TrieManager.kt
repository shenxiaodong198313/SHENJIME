package com.shenji.aikeyboard.data.trie

import android.content.Context
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.model.WordFrequency
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Trie树管理器 - 支持所有9种词典类型
 * 负责管理Trie树的生命周期和提供查询接口
 */
class TrieManager private constructor() {
    // 所有类型的Trie树存储
    private val trieMap = ConcurrentHashMap<TrieBuilder.TrieType, PinyinTrie>()
    
    // 内存加载状态
    private val loadedStatus = ConcurrentHashMap<TrieBuilder.TrieType, Boolean>()
    
    // 初始化状态
    private var isInitialized = false
    
    /**
     * 初始化Trie管理器，加载可用的Trie树
     * 应在应用启动时调用
     */
    fun init() {
        if (isInitialized) return
        
        try {
            loadTries()
            isInitialized = true
        } catch (e: Exception) {
            Timber.e(e, "TrieManager初始化失败")
        }
    }
    
    /**
     * 加载所有可用的Trie树
     */
    private fun loadTries() {
        val context = ShenjiApplication.appContext
        
        // 首先尝试从assets加载预构建Trie
        if (loadPrebuiltTries(context)) {
            Timber.d("成功从assets加载预构建Trie文件")
            return
        }
        
        // 如果assets中没有预构建文件，则尝试加载用户构建的文件
        val builder = TrieBuilder(context)
        
        // 尝试加载所有类型的Trie树
        for (trieType in TrieBuilder.TrieType.values()) {
            try {
                val trie = builder.loadTrie(trieType)
                
                if (trie != null) {
                    trieMap[trieType] = trie
                    loadedStatus[trieType] = true
                    val stats = trie.getMemoryStats()
                    Timber.d("成功加载${getDisplayName(trieType)}Trie树: $stats")
                } else {
                    loadedStatus[trieType] = false
                    Timber.d("${getDisplayName(trieType)}Trie树不存在，需要构建")
                }
            } catch (e: Exception) {
                loadedStatus[trieType] = false
                Timber.e(e, "加载${getDisplayName(trieType)}Trie树失败")
            }
        }
    }
    
    /**
     * 从assets目录加载预构建的Trie文件
     * @param context 上下文对象
     * @return 是否成功加载了任何预构建文件
     */
    private fun loadPrebuiltTries(context: Context): Boolean {
        var anySuccess = false
        
        for (trieType in TrieBuilder.TrieType.values()) {
            try {
                val assetPath = "trie/${getTypeString(trieType)}_trie.dat"
                
                if (isAssetFileExists(context, assetPath)) {
                    val trie = loadTrieFromAssets(context, assetPath)
                    if (trie != null) {
                        trieMap[trieType] = trie
                        loadedStatus[trieType] = true
                        anySuccess = true
                        
                        val stats = trie.getMemoryStats()
                        Timber.d("成功从assets加载预构建${getDisplayName(trieType)}Trie: $stats")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "从assets加载${getDisplayName(trieType)}Trie失败")
            }
        }
        
        return anySuccess
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
     * 从assets加载Trie文件
     */
    private fun loadTrieFromAssets(context: Context, assetPath: String): PinyinTrie? {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                // 先复制到临时文件，因为assets中的文件可能很大
                val tempFile = File(context.cacheDir, "temp_trie_${System.currentTimeMillis()}.dat")
                copyInputStreamToFile(inputStream, tempFile)
                
                // 从临时文件加载
                try {
                    val trie = ObjectInputStream(tempFile.inputStream()).use { ois ->
                        ois.readObject() as PinyinTrie
                    }
                    
                    // 验证Trie树是否为空
                    if (trie.isEmpty()) {
                        Timber.w("从assets加载的Trie树为空: $assetPath")
                        null
                    } else {
                        trie
                    }
                } finally {
                    // 清理临时文件
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "从assets加载Trie文件失败: $assetPath")
            null
        }
    }
    
    /**
     * 将输入流复制到文件
     */
    private fun copyInputStreamToFile(inputStream: InputStream, file: File) {
        FileOutputStream(file).use { outputStream ->
            val buffer = ByteArray(4 * 1024) // 4KB buffer
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            outputStream.flush()
        }
    }
    
    /**
     * 手动加载指定类型的Trie树到内存
     * @param type Trie树类型
     * @return 是否加载成功
     */
    fun loadTrieToMemory(type: TrieBuilder.TrieType): Boolean {
        val context = ShenjiApplication.appContext
        val builder = TrieBuilder(context)
        
        return try {
            val trie = builder.loadTrie(type)
            if (trie != null) {
                trieMap[type] = trie
                loadedStatus[type] = true
                Timber.d("手动加载${getDisplayName(type)}Trie树成功: ${trie.getMemoryStats()}")
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
            Timber.w("${getDisplayName(type)}Trie树未加载，无法查询")
            return emptyList()
        }
        
        try {
            val startTime = System.currentTimeMillis()
            
            // 统一处理为小写
            val normalizedPrefix = prefix.lowercase().trim()
            
            // 从Trie树查询结果
            val results = trie.searchByPrefix(normalizedPrefix, limit)
            
            // 转换为WordFrequency对象
            val wordFrequencies = results.map { WordFrequency(it.word, it.frequency) }
            
            val endTime = System.currentTimeMillis()
            Timber.d("${getDisplayName(type)}Trie查询'$normalizedPrefix'，找到${results.size}个结果，耗时${endTime - startTime}ms")
            
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
     * @return 是否存在文件
     */
    fun isTrieFileExists(type: TrieBuilder.TrieType): Boolean {
        val context = ShenjiApplication.appContext
        val fileName = "trie/${getTypeString(type)}_trie.dat"
        
        val file = File(context.filesDir, fileName)
        return file.exists() && file.length() > 0
    }
    
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
        Timber.d("TrieManager资源已释放")
    }
    
    companion object {
        // 单例实例
        val instance: TrieManager by lazy {
            TrieManager()
        }
    }
} 