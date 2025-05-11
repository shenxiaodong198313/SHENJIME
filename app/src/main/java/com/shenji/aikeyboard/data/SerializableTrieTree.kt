package com.shenji.aikeyboard.data

import timber.log.Timber
import java.io.Serializable

/**
 * 可序列化的Trie树节点结构
 * 极低内存优化版本
 */
class SerializableTrieNode : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
    
    // 使用HashMap存储子节点
    val children = HashMap<Char, SerializableTrieNode>()
    // 标记是否为单词结尾
    var isEnd = false
    // 词频，用于排序候选词
    var frequency = 0
    // 仅在叶子节点存储完整单词，减少内存冗余
    var word: String? = null
    
    // 清空引用，帮助GC
    fun clear() {
        word = null
        children.clear()
    }
}

/**
 * 可序列化的Trie树实现，用于持久化存储
 * 极低内存优化版本
 */
class SerializableTrieTree : Serializable {
    companion object {
        private const val serialVersionUID = 1L
        
        /**
         * 从运行时Trie树创建可序列化的树
         * 使用批量处理方式，减少内存占用
         */
        fun fromRuntimeTree(runtimeTree: TrieTree): SerializableTrieTree {
            val serializableTree = SerializableTrieTree()
            
            Timber.d("开始从运行时树创建可序列化树")
            
            try {
                // 获取所有词条，批量处理
                val words = runtimeTree.getAllWords()
                val batchSize = 100
                val batches = words.chunked(batchSize)
                
                // 批量添加词条
                for (batch in batches) {
                    for ((word, frequency) in batch) {
                        serializableTree.insert(word, frequency)
                    }
                    // 短暂暂停，让GC有机会工作
                    Thread.yield()
                }
                
                // 设置加载状态
                serializableTree.isLoaded = runtimeTree.isLoaded()
                
                Timber.d("成功创建可序列化树，包含 ${words.size} 个词条")
            } catch (e: Exception) {
                Timber.e(e, "从运行时树创建可序列化树失败: ${e.message}")
            }
            
            return serializableTree
        }
    }
    
    // 根节点
    private val root = SerializableTrieNode()
    
    // 加载状态标志
    var isLoaded = false
    
    /**
     * 插入一个词条
     */
    fun insert(word: String, frequency: Int) {
        if (word.isBlank()) return
        
        var current = root
        for (char in word) {
            current = current.children.computeIfAbsent(char) { SerializableTrieNode() }
        }
        current.isEnd = true
        current.frequency = frequency
        current.word = word
    }
    
    /**
     * 转换为运行时Trie树
     * 采用分批处理方式，降低内存占用
     */
    fun toRuntimeTree(): TrieTree {
        val runtimeTree = TrieTree()
        
        try {
            Timber.d("开始将可序列化树转换为运行时树")
            
            // 收集所有词条，使用分批处理
            val words = mutableListOf<WordFrequency>()
            collectWordsFromSerializableNode(root, words)
            
            // 分批次向运行时树中插入所有词
            val batchSize = 100
            val batches = words.chunked(batchSize)
            
            for (batch in batches) {
                for (word in batch) {
                    runtimeTree.insert(word.word, word.frequency)
                }
                // 短暂暂停，让GC有机会工作
                Thread.yield()
            }
            
            // 清空临时列表，帮助GC
            words.clear()
            
            // 设置加载状态
            runtimeTree.setLoaded(isLoaded)
            
            Timber.d("成功转换为运行时树")
        } catch (e: Exception) {
            Timber.e(e, "转换为运行时树失败: ${e.message}")
        }
        
        return runtimeTree
    }
    
    /**
     * 从SerializableTrieNode收集所有词
     * 采用递归方式，但避免深度递归
     */
    private fun collectWordsFromSerializableNode(node: SerializableTrieNode, words: MutableList<WordFrequency>) {
        if (node.isEnd && node.word != null) {
            words.add(WordFrequency(node.word!!, node.frequency))
        }
        
        // 处理子节点
        node.children.values.forEach { child ->
            collectWordsFromSerializableNode(child, words)
        }
    }
    
    /**
     * 清空树内容，释放内存
     */
    fun clear() {
        // 递归清空所有节点
        clearNode(root)
    }
    
    /**
     * 递归清空节点
     */
    private fun clearNode(node: SerializableTrieNode) {
        // 先清空所有子节点
        node.children.values.forEach { child ->
            clearNode(child)
        }
        // 清空当前节点
        node.clear()
    }
} 