package com.shenji.aikeyboard.data

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Trie树节点结构
 */
class TrieNode {
    // 使用HashMap存储子节点，节省内存
    val children = ConcurrentHashMap<Char, TrieNode>()
    // 标记是否为单词结尾
    var isEnd = false
    // 词频，用于排序候选词
    var frequency = 0
    // 仅在叶子节点存储完整单词，减少内存冗余
    var word: String? = null
}

/**
 * Trie树实现，用于高效的前缀匹配查询
 */
class TrieTree {
    private val root = TrieNode()
    
    // 加载状态标志
    private var isLoaded = false
    
    /**
     * 插入一个词条
     */
    fun insert(word: String, frequency: Int) {
        if (word.isBlank()) return
        
        var current = root
        for (char in word) {
            current = current.children.computeIfAbsent(char) { TrieNode() }
        }
        current.isEnd = true
        current.frequency = frequency
        current.word = word
    }
    
    /**
     * 根据前缀查找匹配的词条列表，按词频排序
     */
    fun search(prefix: String, limit: Int = 10): List<WordFrequency> {
        if (prefix.isBlank() || !isLoaded) return emptyList()
        
        val result = mutableListOf<WordFrequency>()
        
        // 定位到前缀的最后一个节点
        var current = root
        for (char in prefix) {
            val node = current.children[char] ?: return emptyList()
            current = node
        }
        
        // 从当前节点开始搜集所有词
        collectWords(current, result, limit)
        
        // 按词频排序
        return result.sortedByDescending { it.frequency }
    }
    
    /**
     * 递归收集节点下的所有词
     */
    private fun collectWords(node: TrieNode, result: MutableList<WordFrequency>, limit: Int) {
        if (result.size >= limit) return
        
        if (node.isEnd && node.word != null) {
            result.add(WordFrequency(node.word!!, node.frequency))
        }
        
        // 递归遍历子节点
        for (child in node.children.values) {
            collectWords(child, result, limit)
        }
    }
    
    /**
     * 清空Trie树
     */
    fun clear() {
        root.children.clear()
        isLoaded = false
    }
    
    /**
     * 设置加载状态
     */
    fun setLoaded(loaded: Boolean) {
        isLoaded = loaded
    }
    
    /**
     * 检查是否已加载完成
     */
    fun isLoaded(): Boolean = isLoaded
    
    /**
     * 获取已加载词条数量的估算值
     */
    fun getEstimatedWordCount(): Int {
        var count = 0
        
        fun countWords(node: TrieNode) {
            if (node.isEnd) count++
            for (child in node.children.values) {
                countWords(child)
            }
        }
        
        countWords(root)
        return count
    }
}

 