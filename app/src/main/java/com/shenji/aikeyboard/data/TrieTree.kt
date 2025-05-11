package com.shenji.aikeyboard.data

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Trie树节点结构
 * 极低内存优化版本
 */
class TrieNode {
    // 使用HashMap存储子节点，节省内存
    val children = HashMap<Char, TrieNode>(4) // 使用更小的初始容量
    // 标记是否为单词结尾
    var isEnd = false
    // 词频，用于排序候选词
    var frequency = 0
    // 仅在叶子节点存储完整单词，减少内存冗余
    var word: String? = null
    
    // 清空节点引用，帮助GC
    fun clear() {
        word = null
        children.clear()
    }
}

/**
 * Trie树实现，用于高效的前缀匹配查询
 * 极低内存优化版本
 */
class TrieTree {
    private val root = TrieNode()
    
    // 加载状态标志
    private var isLoaded = false
    
    // 词条数量估计
    private var estimatedWordCount = 0
    
    /**
     * 插入一个词条
     */
    fun insert(word: String, frequency: Int) {
        if (word.isBlank()) return
        
        var current = root
        for (char in word) {
            val next = current.children[char]
            if (next != null) {
                current = next
            } else {
                val newNode = TrieNode()
                current.children[char] = newNode
                current = newNode
            }
        }
        
        // 只有在节点之前不是单词结尾时才递增计数
        if (!current.isEnd) {
            estimatedWordCount++
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
        // 递归清空所有节点
        clearNode(root)
        root.children.clear()
        isLoaded = false
        estimatedWordCount = 0
    }
    
    /**
     * 递归清空节点
     */
    private fun clearNode(node: TrieNode) {
        // 先清空所有子节点
        node.children.values.forEach { child ->
            clearNode(child)
        }
        // 清空当前节点
        node.clear()
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
        // 如果已缓存估计值则直接返回
        if (estimatedWordCount > 0) return estimatedWordCount
        
        // 否则递归计算
        var count = 0
        
        fun countWords(node: TrieNode) {
            if (node.isEnd) count++
            for (child in node.children.values) {
                countWords(child)
            }
        }
        
        countWords(root)
        estimatedWordCount = count
        return count
    }
    
    /**
     * 获取树中所有单词和它们的频率
     * 采用非递归实现，减少栈空间使用
     * @return 词条和频率对的列表
     */
    fun getAllWords(): List<Pair<String, Int>> {
        val words = mutableListOf<Pair<String, Int>>()
        
        // 采用分批处理的遍历方式
        val stack = java.util.ArrayDeque<TrieNode>()
        stack.push(root)
        
        var count = 0
        val batchSize = 1000 // 每批处理的节点数
        
        while (stack.isNotEmpty()) {
            val node = stack.pop()
            
            // 如果是单词结尾，添加到结果列表
            if (node.isEnd && node.word != null) {
                words.add(Pair(node.word!!, node.frequency))
            }
            
            // 将子节点加入栈
            for (child in node.children.values) {
                stack.push(child)
            }
            
            // 每处理一定数量的节点，让出CPU时间
            if (++count % batchSize == 0) {
                Thread.yield()
            }
        }
        
        return words
    }
    
    /**
     * 估计当前树占用的内存大小（近似值）
     * @return 内存大小（字节）
     */
    fun estimateMemoryUsage(): Long {
        val nodeCount = countNodes(root)
        val wordCount = getEstimatedWordCount()
        
        // 每个节点基本结构约 32 字节
        // 每个字符约 2 字节
        // 每个HashMap引用约 24 字节
        val avgWordLen = 2L // 平均词长估计
        val bytesPerNode = 32L
        val bytesPerChar = 2L * avgWordLen
        
        return (nodeCount * bytesPerNode) + (wordCount * bytesPerChar)
    }
    
    /**
     * 计算树中的节点总数
     */
    private fun countNodes(node: TrieNode): Int {
        var count = 1 // 当前节点
        
        // 递归计算子节点
        for (child in node.children.values) {
            count += countNodes(child)
        }
        
        return count
    }
}

 