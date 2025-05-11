package com.shenji.aikeyboard.data

import com.shenji.aikeyboard.ShenjiApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.system.measureTimeMillis

/**
 * 词典管理器类，负责管理内存词库和持久化词库
 */
class DictionaryManager private constructor() {
    
    companion object {
        // 单例实例
        val instance: DictionaryManager by lazy { DictionaryManager() }
        
        // 高频词库类型列表
        private val HIGH_FREQUENCY_DICT_TYPES = listOf("chars", "base")
        
        // 每批加载的词条数量
        private const val BATCH_SIZE = 1000
        
        // 初始化函数
        fun init() {
            instance.initialize()
        }
    }
    
    // 词典仓库实例
    private val repository = DictionaryRepository()
    
    // 高频词库Trie树
    private val trieTree = TrieTree()
    
    // 加载任务
    private var loadingJob: Job? = null
    
    // 加载状态监听器
    private var loadingListener: ((Boolean) -> Unit)? = null
    
    /**
     * 初始化词典管理器，开始异步加载高频词库
     */
    fun initialize() {
        // 如果已经在加载中，则不重复加载
        if (loadingJob?.isActive == true) {
            Timber.d("词典已在加载中，跳过重复初始化")
            return
        }
        
        // 清空当前树
        trieTree.clear()
        
        // 异步加载高频词库
        loadingJob = CoroutineScope(Dispatchers.IO).launch {
            Timber.i("开始加载高频词库到内存")
            
            val loadTime = measureTimeMillis {
                var totalLoaded = 0
                
                // 加载所有高频词库类型
                for (dictType in HIGH_FREQUENCY_DICT_TYPES) {
                    val count = loadDictionaryType(dictType)
                    totalLoaded += count
                    Timber.d("已加载词库 $dictType: $count 个词条")
                }
                
                // 设置加载完成标志
                trieTree.setLoaded(true)
                
                // 通知监听器加载完成
                loadingListener?.invoke(true)
                
                Timber.i("高频词库加载完成，共加载 $totalLoaded 个词条，实际树中词条数约 ${trieTree.getEstimatedWordCount()} 个")
            }
            
            Timber.i("词库加载耗时: ${loadTime}ms")
        }
    }
    
    /**
     * 加载指定类型的词典到Trie树
     */
    private fun loadDictionaryType(type: String): Int {
        var loadedCount = 0
        var offset = 0
        
        try {
            // 分批加载词条，避免一次性加载过多数据导致内存问题
            while (true) {
                val entries = repository.getEntriesByType(type, offset, BATCH_SIZE)
                if (entries.isEmpty()) break
                
                // 将词条插入Trie树
                for (entry in entries) {
                    trieTree.insert(entry.word, entry.frequency)
                    loadedCount++
                }
                
                offset += entries.size
                
                // 如果获取的数量小于批次大小，说明已经加载完毕
                if (entries.size < BATCH_SIZE) break
            }
        } catch (e: Exception) {
            Timber.e(e, "加载词典类型 $type 失败")
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
     * 检查是否已加载完成
     */
    fun isLoaded(): Boolean = trieTree.isLoaded()
    
    /**
     * 重置词典管理器，清空内存词库
     */
    fun reset() {
        // 取消正在执行的加载任务
        loadingJob?.cancel()
        
        // 清空Trie树
        trieTree.clear()
        
        Timber.i("词典管理器已重置")
    }
} 