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
                Timber.d("成功加载单字Trie树: $stats")
            } else {
                Timber.d("单字Trie树不存在，需要构建")
            }
        } catch (e: Exception) {
            Timber.e(e, "加载单字Trie树失败")
        }
        
        // 尝试加载基础词典Trie树（暂未实现）
        try {
            baseTrie = builder.loadTrie(TrieBuilder.TrieType.BASE)
            
            if (baseTrie != null) {
                val stats = baseTrie!!.getMemoryStats()
                Timber.d("成功加载基础词典Trie树: $stats")
            } else {
                Timber.d("基础词典Trie树不存在，需要构建")
            }
        } catch (e: Exception) {
            Timber.e(e, "加载基础词典Trie树失败")
        }
    }
    
    /**
     * 根据拼音前缀查询单字
     * @param prefix 拼音前缀
     * @param limit 返回结果的最大数量
     * @return 匹配的汉字列表，按频率排序
     */
    fun searchCharsByPrefix(prefix: String, limit: Int = 10): List<WordFrequency> {
        if (!isInitialized) init()
        
        if (charsTrie == null) {
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
     * 释放Trie树资源
     * 在内存紧张时调用
     */
    fun release() {
        charsTrie = null
        baseTrie = null
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