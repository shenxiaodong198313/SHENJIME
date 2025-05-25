package com.shenji.aikeyboard.data.trie

import timber.log.Timber
import java.io.Serializable
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 拼音Trie树实现
 * 支持线程安全的词语插入和前缀查询
 */
class PinyinTrie : Serializable {
    // 根节点
    private val root = TrieNode()
    
    // 拼音音节分隔符，用于辅助多音节查询
    private val syllableSeparator = '\''.code.toByte()
    
    // 读写锁，保证线程安全
    @Transient
    private val lock = ReentrantReadWriteLock()
    
    // 序列化版本ID
    companion object {
        private const val serialVersionUID = 1L
    }
    
    /**
     * 插入一个拼音及其对应的汉字
     * @param pinyin 拼音字符串
     * @param word 对应的汉字
     * @param frequency 词频
     * @param maxWordsPerNode 每个节点的最大词语数量
     */
    fun insert(pinyin: String, word: String, frequency: Int, maxWordsPerNode: Int = TrieNode.MAX_WORDS_PER_NODE) {
        lock.write {
            try {
                var current = root
                
                // 遍历拼音的每个字符，构建Trie路径
                for (char in pinyin) {
                    // 如果子节点不存在，则创建
                    if (!current.children.containsKey(char)) {
                        current.children[char] = TrieNode()
                    }
                    current = current.children[char]!!
                }
                
                // 标记为词语结尾
                current.isEndOfWord = true
                
                // 添加词语到当前节点
                current.addWord(word, frequency, maxWordsPerNode)
            } catch (e: Exception) {
                Timber.e(e, "Trie插入词语失败: $pinyin -> $word")
            }
        }
    }
    
    /**
     * 按前缀搜索拼音
     * @param prefix 拼音前缀
     * @param limit 返回结果的最大数量
     * @return 符合前缀的词语列表，按频率降序排列
     */
    fun searchByPrefix(prefix: String, limit: Int = 10): List<WordItem> {
        lock.read {
            try {
                var current = root
                
                // 定位到前缀对应的节点
                for (char in prefix) {
                    if (!current.children.containsKey(char)) {
                        return emptyList() // 前缀不存在
                    }
                    current = current.children[char]!!
                }
                
                // 收集该节点及其所有子节点的词语
                return collectWordsFromSubtree(current, limit)
            } catch (e: Exception) {
                Timber.e(e, "Trie前缀搜索失败: $prefix")
                return emptyList()
            }
        }
    }
    
    /**
     * 收集节点及其子树中的所有词语
     * @param node 起始节点
     * @param limit 收集的词语数量上限
     * @return 词语列表，按频率降序排列
     */
    private fun collectWordsFromSubtree(node: TrieNode, limit: Int): List<WordItem> {
        val result = mutableListOf<WordItem>()
        
        // 添加当前节点的词语
        result.addAll(node.getWords())
        
        // 使用队列进行广度优先搜索
        val queue = ArrayDeque<TrieNode>()
        queue.addAll(node.children.values)
        
        while (queue.isNotEmpty() && result.size < limit) {
            val currentNode = queue.removeFirst()
            result.addAll(currentNode.getWords())
            queue.addAll(currentNode.children.values)
        }
        
        // 按频率排序并限制数量
        return result.sortedByDescending { it.frequency }.take(limit)
    }
    
    /**
     * 获取Trie树的内存占用统计信息
     */
    fun getMemoryStats(): TrieMemoryStats {
        lock.read {
            return root.calculateMemoryStats()
        }
    }
    
    /**
     * 判断Trie树是否为空
     */
    fun isEmpty(): Boolean {
        lock.read {
            return root.children.isEmpty()
        }
    }
    
    /**
     * 反序列化后重新初始化transient字段
     */
    private fun readObject(inputStream: java.io.ObjectInputStream) {
        inputStream.defaultReadObject()
        // 反序列化后重新初始化lock
        val lockField = PinyinTrie::class.java.getDeclaredField("lock")
        lockField.isAccessible = true
        lockField.set(this, ReentrantReadWriteLock())
    }
} 