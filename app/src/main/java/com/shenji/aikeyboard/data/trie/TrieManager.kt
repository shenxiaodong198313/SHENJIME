package com.shenji.aikeyboard.data.trie

import android.content.Context
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.model.WordFrequency
import timber.log.Timber

/**
 * Trie树管理器
 * 负责管理Trie树的生命周期和提供查询接口
 */
class TrieManager private constructor() {
    // 单字Trie树
    private var charsTrie: PinyinTrie? = null
    
    // 基础词典Trie树（暂未实现）
    private var baseTrie: PinyinTrie? = null
    
    // 初始化状态
    private var isInitialized = false
    
    // 内存加载状态
    private var isCharsTrieLoaded = false
    private var isBaseTrieLoaded = false
    
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
        val builder = TrieBuilder(context)
        
        // 尝试加载单字Trie树
        try {
            charsTrie = builder.loadTrie(TrieBuilder.TrieType.CHARS)
            
            if (charsTrie != null) {
                val stats = charsTrie!!.getMemoryStats()
                isCharsTrieLoaded = true
                Timber.d("成功加载单字Trie树: $stats")
            } else {
                Timber.d("单字Trie树不存在，需要构建")
                isCharsTrieLoaded = false
            }
        } catch (e: Exception) {
            Timber.e(e, "加载单字Trie树失败")
            isCharsTrieLoaded = false
        }
        
        // 尝试加载基础词典Trie树
        try {
            baseTrie = builder.loadTrie(TrieBuilder.TrieType.BASE)
            
            if (baseTrie != null) {
                val stats = baseTrie!!.getMemoryStats()
                isBaseTrieLoaded = true
                Timber.d("成功加载基础词典Trie树: $stats")
            } else {
                Timber.d("基础词典Trie树不存在，需要构建")
                isBaseTrieLoaded = false
            }
        } catch (e: Exception) {
            Timber.e(e, "加载基础词典Trie树失败")
            isBaseTrieLoaded = false
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
            when (type) {
                TrieBuilder.TrieType.CHARS -> {
                    charsTrie = builder.loadTrie(type)
                    if (charsTrie != null) {
                        isCharsTrieLoaded = true
                        Timber.d("手动加载单字Trie树成功: ${charsTrie!!.getMemoryStats()}")
                        true
                    } else {
                        Timber.w("手动加载单字Trie树失败: 文件不存在或格式错误")
                        isCharsTrieLoaded = false
                        false
                    }
                }
                TrieBuilder.TrieType.BASE -> {
                    baseTrie = builder.loadTrie(type)
                    if (baseTrie != null) {
                        isBaseTrieLoaded = true
                        Timber.d("手动加载基础词典Trie树成功: ${baseTrie!!.getMemoryStats()}")
                        true
                    } else {
                        Timber.w("手动加载基础词典Trie树失败: 文件不存在或格式错误")
                        isBaseTrieLoaded = false
                        false
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "手动加载${type.name}Trie树失败")
            if (type == TrieBuilder.TrieType.CHARS) isCharsTrieLoaded = false
            else isBaseTrieLoaded = false
            false
        }
    }
    
    /**
     * 卸载指定类型的Trie树，释放内存
     * @param type Trie树类型
     */
    fun unloadTrie(type: TrieBuilder.TrieType) {
        when (type) {
            TrieBuilder.TrieType.CHARS -> {
                charsTrie = null
                isCharsTrieLoaded = false
                Timber.d("单字Trie树已卸载")
            }
            TrieBuilder.TrieType.BASE -> {
                baseTrie = null
                isBaseTrieLoaded = false
                Timber.d("基础词典Trie树已卸载")
            }
        }
    }
    
    /**
     * 检查指定类型的Trie树是否已加载到内存
     * @param type Trie树类型
     * @return 是否已加载
     */
    fun isTrieLoaded(type: TrieBuilder.TrieType): Boolean {
        return when (type) {
            TrieBuilder.TrieType.CHARS -> isCharsTrieLoaded && charsTrie != null
            TrieBuilder.TrieType.BASE -> isBaseTrieLoaded && baseTrie != null
        }
    }
    
    /**
     * 获取指定类型Trie树的内存统计信息
     * @param type Trie树类型
     * @return 内存统计信息，如果未加载则返回null
     */
    fun getTrieMemoryStats(type: TrieBuilder.TrieType): TrieMemoryStats? {
        return when (type) {
            TrieBuilder.TrieType.CHARS -> charsTrie?.getMemoryStats()
            TrieBuilder.TrieType.BASE -> baseTrie?.getMemoryStats()
        }
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
     * 根据拼音前缀查询单字
     * @param prefix 拼音前缀
     * @param limit 返回结果的最大数量
     * @return 匹配的汉字列表，按频率排序
     */
    fun searchCharsByPrefix(prefix: String, limit: Int = 10): List<WordFrequency> {
        if (!isInitialized) init()
        
        if (charsTrie == null || !isCharsTrieLoaded) {
            Timber.w("单字Trie树未加载，无法查询")
            return emptyList()
        }
        
        try {
            val results = charsTrie!!.searchByPrefix(prefix.lowercase(), limit)
            return results.map { WordFrequency(it.word, it.frequency) }
        } catch (e: Exception) {
            Timber.e(e, "单字Trie树查询失败: $prefix")
            return emptyList()
        }
    }
    
    /**
     * 根据拼音前缀查询基础词典
     * @param prefix 拼音前缀（无空格）或首字母缩写
     * @param limit 返回结果的最大数量
     * @return 匹配的词语列表，按频率排序
     */
    fun searchBaseByPrefix(prefix: String, limit: Int = 10): List<WordFrequency> {
        if (!isInitialized) init()
        
        if (baseTrie == null || !isBaseTrieLoaded) {
            Timber.w("基础词典Trie树未加载，无法查询")
            return emptyList()
        }
        
        try {
            val startTime = System.currentTimeMillis()
            
            // 统一处理为小写
            val normalizedPrefix = prefix.lowercase().trim()
            
            // 从Trie树查询结果
            val results = baseTrie!!.searchByPrefix(normalizedPrefix, limit)
            
            // 转换为WordFrequency对象
            val wordFrequencies = results.map { WordFrequency(it.word, it.frequency) }
            
            val endTime = System.currentTimeMillis()
            Timber.d("基础词典Trie查询'$normalizedPrefix'，找到${results.size}个结果，耗时${endTime - startTime}ms")
            
            return wordFrequencies
        } catch (e: Exception) {
            Timber.e(e, "基础词典Trie树查询失败: $prefix")
            return emptyList()
        }
    }
    
    /**
     * 检查是否存在某类型的Trie树文件
     * @param type Trie树类型
     * @return 是否存在文件
     */
    fun isTrieFileExists(type: TrieBuilder.TrieType): Boolean {
        val context = ShenjiApplication.appContext
        val fileName = when (type) {
            TrieBuilder.TrieType.CHARS -> "trie/chars_trie.dat"
            TrieBuilder.TrieType.BASE -> "trie/base_trie.dat"
        }
        
        val file = java.io.File(context.filesDir, fileName)
        return file.exists() && file.length() > 0
    }
    
    /**
     * 释放Trie树资源
     * 在内存紧张时调用
     */
    fun release() {
        charsTrie = null
        baseTrie = null
        isCharsTrieLoaded = false
        isBaseTrieLoaded = false
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