package com.shenji.aikeyboard.data.trie

import java.io.Serializable

/**
 * Trie树节点实现
 * 实现了Serializable接口以支持序列化
 */
class TrieNode : Serializable {
    // 子节点映射，键为字符，值为对应的子节点
    val children: MutableMap<Char, TrieNode> = HashMap()
    
    // 该节点存储的词语及其频率信息
    val words: MutableList<WordItem> = ArrayList()
    
    // 是否为终结节点（表示一个完整词语的结尾）
    var isEndOfWord: Boolean = false
    
    // 限制节点存储的最大词语数量，避免内存占用过大
    companion object {
        const val MAX_WORDS_PER_NODE = 50
        const val MAX_WORDS_PER_NODE_CHARS = 1000  // chars词典使用更大的容量，确保能容纳所有同音字
        private const val serialVersionUID = 1L
    }
    
    /**
     * 添加一个词语到当前节点
     * @param word 词语
     * @param frequency 词频
     * @param maxWordsPerNode 每个节点的最大词语数量
     * @return 是否成功添加（如果已达到最大容量且新词频率不够高，则不添加）
     */
    fun addWord(word: String, frequency: Int, maxWordsPerNode: Int = MAX_WORDS_PER_NODE): Boolean {
        // 如果列表未满，直接添加
        if (words.size < maxWordsPerNode) {
            words.add(WordItem(word, frequency))
            words.sortByDescending { it.frequency } // 按频率排序
            return true
        }
        
        // 如果列表已满，但新词频率高于列表中最低频率的词，则替换它
        val lowestFrequencyItem = words.minByOrNull { it.frequency }
        if (lowestFrequencyItem != null && frequency > lowestFrequencyItem.frequency) {
            words.remove(lowestFrequencyItem)
            words.add(WordItem(word, frequency))
            words.sortByDescending { it.frequency } // 按频率排序
            return true
        }
        
        return false
    }
    
    /**
     * 获取当前节点包含的所有词语，按频率排序
     * @param limit 返回结果的最大数量
     */
    fun getWords(limit: Int = Int.MAX_VALUE): List<WordItem> {
        return words.take(limit.coerceAtMost(words.size))
    }
    
    /**
     * 计算此节点及其所有子节点的内存占用统计信息
     */
    fun calculateMemoryStats(): TrieMemoryStats {
        val stats = TrieMemoryStats()
        
        // 统计当前节点
        stats.nodeCount++
        stats.wordCount += words.size
        
        // 递归统计所有子节点
        for (child in children.values) {
            val childStats = child.calculateMemoryStats()
            stats.nodeCount += childStats.nodeCount
            stats.wordCount += childStats.wordCount
        }
        
        return stats
    }
}

/**
 * 节点上存储的词语项，包含词语和频率信息
 */
data class WordItem(
    val word: String,
    val frequency: Int
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Trie树内存占用统计信息
 */
data class TrieMemoryStats(
    var nodeCount: Int = 0,
    var wordCount: Int = 0
) {
    override fun toString(): String {
        return "节点数: $nodeCount, 词语数: $wordCount"
    }
} 