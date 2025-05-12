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
    // 存储拼音
    var word: String? = null
    // 存储汉字
    var chinese: String? = null
    
    // 清空引用，帮助GC
    fun clear() {
        word = null
        chinese = null
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
                val entries = runtimeTree.getAllWordEntries()
                val batchSize = 100
                val batches = entries.chunked(batchSize)
                
                // 批量添加词条
                for (batch in batches) {
                    for (entry in batch) {
                        // 使用拼音作为key，汉字存储在chinese属性中
                        serializableTree.insert(entry.pinyin, entry.frequency, entry.chinese)
                    }
                    // 短暂暂停，让GC有机会工作
                    Thread.yield()
                }
                
                // 设置加载状态
                serializableTree.isLoaded = runtimeTree.isLoaded()
                
                Timber.d("成功创建可序列化树，包含 ${entries.size} 个词条")
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
     * @param word 词条内容（拼音或汉字）
     * @param frequency 词频
     * @param chinese 可选的汉字，如果提供则与word（拼音）分开存储
     */
    fun insert(word: String, frequency: Int, chinese: String? = null) {
        if (word.isBlank()) {
            Timber.e("尝试插入空词条，已忽略，频率=$frequency")
            return
        }
        
        // 添加日志以诊断问题
        if (word.length < 5) {  // 只为简短词条添加日志，避免日志过多
            Timber.d("插入词条: '$word'，频率=$frequency，长度=${word.length}")
        }
        
        var current = root
        for (char in word) {
            current = current.children.computeIfAbsent(char) { SerializableTrieNode() }
        }
        current.isEnd = true
        current.frequency = frequency
        
        // 确保只存储非空词条
        if (word.isNotEmpty()) {
            current.word = word
            
            // 如果提供了汉字，则存储汉字
            if (chinese != null && chinese.isNotEmpty()) {
                current.chinese = chinese
            }
            // 如果没有提供汉字，则尝试检测word是否为汉字
            // 如果是汉字，则同时设置word和chinese为相同值
            else if (word.codePoints().anyMatch { cp -> 
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN
            }) {
                current.chinese = word
            }
        } else {
            Timber.e("词条内容为空，但已通过isBlank检查，这是一个异常情况")
            // 不设置word属性，使其保持为null
        }
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
            val words = mutableListOf<PinyinWordPair>()
            collectWordsFromSerializableNode(root, words)
            
            val wordCount = words.size
            Timber.d("从可序列化树中收集到 $wordCount 个词条")
            
            // 诊断：检查收集到的词条样本
            if (words.isNotEmpty()) {
                val sampleSize = minOf(5, words.size)
                val samples = words.take(sampleSize)
                Timber.d("词条样本(${sampleSize}个):")
                samples.forEachIndexed { index, pair ->
                    Timber.d("样本${index+1}: 拼音='${pair.pinyin}', 汉字='${pair.chinese}', 频率=${pair.frequency}, " +
                           "拼音字符数=${pair.pinyin.length}, " +
                           "拼音字符分析=${pair.pinyin.toCharArray().joinToString(" ") { "0x${it.code.toString(16)}" }}")
                }
                
                // 检查是否有空词条
                val emptyPinyins = words.filter { it.pinyin.isEmpty() }
                if (emptyPinyins.isNotEmpty()) {
                    Timber.e("警告：发现${emptyPinyins.size}个空拼音词条！")
                    Timber.e("空拼音样本: ${emptyPinyins.take(3).joinToString { "汉字='${it.chinese}', 频率=${it.frequency}" }}")
                }
                
                val emptyChineseWords = words.filter { it.chinese.isEmpty() }
                if (emptyChineseWords.isNotEmpty()) {
                    Timber.e("警告：发现${emptyChineseWords.size}个空汉字词条！")
                    Timber.e("空汉字样本: ${emptyChineseWords.take(3).joinToString { "拼音='${it.pinyin}', 频率=${it.frequency}" }}")
                }
            }
            
            // 分批次向运行时树中插入所有词
            val batchSize = 100
            val batches = words.chunked(batchSize)
            
            for ((index, batch) in batches.withIndex()) {
                for (pair in batch) {
                    // 在这里添加词条长度检查
                    if (pair.pinyin.isNotEmpty()) {
                        // 确保使用拼音作为key，汉字作为chinese参数
                        runtimeTree.insert(pair.pinyin, pair.frequency, pair.chinese)
                    } else {
                        Timber.e("跳过空拼音词条，汉字='${pair.chinese}', 频率=${pair.frequency}")
                    }
                }
                // 每处理10批记录一次进度
                if (index % 10 == 0) {
                    val progress = (index * batchSize * 100) / wordCount
                    Timber.d("转换进度: $progress% (${index * batchSize}/$wordCount)")
                }
                // 短暂暂停，让GC有机会工作
                Thread.yield()
            }
            
            // 清空临时列表，帮助GC
            words.clear()
            
            // 设置加载状态为true，确保被标记为已加载
            runtimeTree.setLoaded(true)
            
            Timber.d("成功转换为运行时树，设置加载状态: true")
            
            // 诊断：校验转换后的树
            val resultingWords = runtimeTree.getAllWordEntries().take(5)
            Timber.d("转换后的树中的词条样本:")
            resultingWords.forEach { entry ->
                Timber.d("转换后词条: 拼音='${entry.pinyin}', 汉字='${entry.chinese}', 频率=${entry.frequency}, " +
                       "拼音字符数=${entry.pinyin.length}, " +
                       "拼音字符分析=${entry.pinyin.toCharArray().joinToString(" ") { "0x${it.code.toString(16)}" }}")
            }
            
            // 获取树的根节点子节点
            val rootKeys = runtimeTree.getRootChildKeys()
            Timber.d("转换后树的根节点子节点: $rootKeys")
            
        } catch (e: Exception) {
            Timber.e(e, "转换为运行时树失败: ${e.message}")
            // 即使失败也标记为已加载，避免反复尝试加载
            runtimeTree.setLoaded(true)
        }
        
        return runtimeTree
    }
    
    /**
     * 拼音和汉字对数据类，用于词条转换
     */
    data class PinyinWordPair(
        val pinyin: String,
        val chinese: String,
        val frequency: Int
    )
    
    /**
     * 从SerializableTrieNode收集所有词
     * 采用递归方式，但避免深度递归
     */
    private fun collectWordsFromSerializableNode(node: SerializableTrieNode, words: MutableList<PinyinWordPair>) {
        if (node.isEnd) {
            // 添加诊断信息：检查节点中的word和chinese
            val pinyin = node.word ?: ""
            val chinese = node.chinese ?: pinyin  // 如果chinese为空，则使用word
            
            // 检查非法情况
            if (pinyin.isEmpty() && chinese.isEmpty()) {
                Timber.e("警告：SerializableTrieNode中发现拼音和汉字都为空的词条，频率=${node.frequency}")
                return
            }
            
            // 使用PinyinWordPair而不是WordFrequency，保留拼音和汉字分离
            words.add(PinyinWordPair(pinyin, chinese, node.frequency))
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