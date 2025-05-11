package com.shenji.aikeyboard.data

import timber.log.Timber
import java.io.Serializable

/**
 * 可序列化的Trie树节点结构
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
}

/**
 * 可序列化的Trie树实现，用于持久化存储
 */
class SerializableTrieTree : Serializable {
    companion object {
        private const val serialVersionUID = 1L
        
        /**
         * 从运行时Trie树创建可序列化的树
         */
        fun fromRuntimeTree(runtimeTree: TrieTree): SerializableTrieTree {
            val serializableTree = SerializableTrieTree()
            
            Timber.d("开始从运行时树创建可序列化树")
            val wordCount = runtimeTree.getEstimatedWordCount()
            
            // 获取运行时树中的所有词及其频率
            val words = mutableListOf<WordFrequency>()
            
            // 使用反射获取root节点
            try {
                val rootField = TrieTree::class.java.getDeclaredField("root")
                rootField.isAccessible = true
                val runtimeRoot = rootField.get(runtimeTree) as TrieNode
                
                // 收集所有词
                collectWordsFromNode(runtimeRoot, words)
                
                // 将词条插入可序列化树
                for (word in words) {
                    serializableTree.insert(word.word, word.frequency)
                }
                
                // 设置加载状态
                serializableTree.isLoaded = runtimeTree.isLoaded()
                
                Timber.d("成功创建可序列化树，包含 ${words.size} 个词条")
            } catch (e: Exception) {
                Timber.e(e, "从运行时树创建可序列化树失败")
            }
            
            return serializableTree
        }
        
        /**
         * 从TrieNode收集所有词
         */
        private fun collectWordsFromNode(node: TrieNode, words: MutableList<WordFrequency>) {
            if (node.isEnd && node.word != null) {
                words.add(WordFrequency(node.word!!, node.frequency))
            }
            
            // 递归处理子节点
            for (child in node.children.values) {
                collectWordsFromNode(child, words)
            }
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
     */
    fun toRuntimeTree(): TrieTree {
        val runtimeTree = TrieTree()
        
        try {
            Timber.d("开始将可序列化树转换为运行时树")
            
            // 收集所有词条
            val words = mutableListOf<WordFrequency>()
            collectWordsFromSerializableNode(root, words)
            
            // 向运行时树中插入所有词
            for (word in words) {
                runtimeTree.insert(word.word, word.frequency)
            }
            
            // 设置加载状态
            runtimeTree.setLoaded(isLoaded)
            
            Timber.d("成功转换为运行时树，包含 ${words.size} 个词条")
        } catch (e: Exception) {
            Timber.e(e, "转换为运行时树失败")
        }
        
        return runtimeTree
    }
    
    /**
     * 从SerializableTrieNode收集所有词
     */
    private fun collectWordsFromSerializableNode(node: SerializableTrieNode, words: MutableList<WordFrequency>) {
        if (node.isEnd && node.word != null) {
            words.add(WordFrequency(node.word!!, node.frequency))
        }
        
        // 递归处理子节点
        for (child in node.children.values) {
            collectWordsFromSerializableNode(child, words)
        }
    }
} 