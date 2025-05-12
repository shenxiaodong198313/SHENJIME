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
    // 节点存储的拼音
    var word: String? = null
    // 节点存储的汉字，与拼音对应
    var chinese: String? = null
    
    // 清空节点引用，帮助GC
    fun clear() {
        word = null
        chinese = null
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
     * 拼音条目数据类，用于存储拼音、汉字和频率信息
     */
    data class PinyinEntry(
        val pinyin: String,  // 拼音信息
        val chinese: String, // 汉字信息
        val frequency: Int   // 词频
    )

    /**
     * 插入一个词条
     * @param key 作为索引的键（拼音）
     * @param frequency 词频
     * @param chinese 可选的汉字，如果不提供则使用key作为汉字
     */
    fun insert(key: String, frequency: Int, chinese: String? = null) {
        if (key.isBlank()) return
        
        var current = root
        for (char in key) {
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
        current.word = key
        // 设置汉字，如果提供了chinese参数则使用，否则使用key
        current.chinese = chinese
    }
    
    /**
     * 根据前缀查找匹配的词条列表，按词频排序
     */
    fun search(prefix: String, limit: Int): List<WordFrequency> {
        if (prefix.isBlank()) {
            Timber.d("搜索前缀为空，返回空列表")
            return emptyList()
        }
        
        if (!isLoaded) {
            Timber.d("Trie树未加载，返回空列表")
            return emptyList()
        }
        
        Timber.d("在Trie树中搜索前缀: '$prefix'，字符数：${prefix.length}")
        
        // 增加诊断信息：分析前缀字符
        Timber.d("前缀字符分析：${prefix.toCharArray().joinToString(" ") { "0x${it.code.toString(16)}(${it})" }}")
        
        // 检查根节点是否有子节点
        if (root.children.isEmpty()) {
            Timber.d("Trie树的根节点没有子节点，返回空列表")
            return emptyList()
        }
        
        // 记录根节点的所有子节点的键
        val rootKeys = root.children.keys.joinToString(", ") { 
            "$it (0x${it.code.toString(16)})" 
        }
        Timber.d("Trie树根节点的所有子节点键: $rootKeys")
        
        val result = mutableListOf<WordFrequency>()
        
        // 定位到前缀的最后一个节点
        var current = root
        for ((index, char) in prefix.withIndex()) {
            val node = current.children[char]
            if (node == null) {
                // 详细记录搜索路径
                val path = prefix.substring(0, index)
                Timber.d("在Trie树中未找到字符'$char'(0x${char.code.toString(16)})，搜索路径：'$path'，搜索失败")
                
                // 打印当前节点的所有子节点，帮助排查
                val childChars = current.children.keys.joinToString(", ") { 
                    "$it (0x${it.code.toString(16)})" 
                }
                Timber.d("当前节点的子节点字符：[$childChars]")
                
                // 尝试替代检索：检查是否是拼音声调问题
                // 例如，用户输入的是没有声调的拼音，但词典存储的是带声调的
                val alternativeChars = findAlternativeChars(char)
                if (alternativeChars.isNotEmpty()) {
                    Timber.d("尝试查找字符'$char'的替代字符: $alternativeChars")
                    
                    for (altChar in alternativeChars) {
                        val altNode = current.children[altChar]
                        if (altNode != null) {
                            Timber.d("找到替代字符'$altChar'的匹配节点")
                            // 继续从替代字符节点搜索剩余前缀
                            val remainingPrefix = prefix.substring(index + 1)
                            val altResult = continueSearch(altNode, remainingPrefix, limit)
                            if (altResult.isNotEmpty()) {
                                Timber.d("使用替代字符'$altChar'找到${altResult.size}个匹配结果")
                                result.addAll(altResult)
                                break
                            }
                        }
                    }
                    
                    if (result.isNotEmpty()) {
                        // 如果通过替代字符找到了结果，就按频率排序返回
                        return result.sortedByDescending { it.frequency }
                    }
                }
                
                return emptyList()
            }
            current = node
            Timber.d("搜索路径匹配字符'$char'成功，当前已匹配：'${prefix.substring(0, index+1)}'")
        }
        
        Timber.d("前缀'$prefix'完全匹配，开始收集词条")
        
        // 从当前节点开始搜集所有词
        collectWords(current, result, limit)
        
        if (result.isEmpty()) {
            Timber.d("虽然前缀'$prefix'完全匹配，但未找到任何词条")
            
            // 检查当前节点是否有子节点
            if (current.children.isNotEmpty()) {
                Timber.d("当前节点有${current.children.size}个子节点，但未标记为词条结尾")
                val childChars = current.children.keys.joinToString(", ") { 
                    "$it (0x${it.code.toString(16)})" 
                }
                Timber.d("子节点字符：[$childChars]")
            } else {
                Timber.d("当前节点没有子节点，是叶子节点但未标记为词条结尾")
            }
            
            // 检查当前节点属性
            Timber.d("当前节点属性: isEnd=${current.isEnd}, word=${current.word}, chinese=${current.chinese}, frequency=${current.frequency}")
        } else {
            Timber.d("在Trie树中找到${result.size}个匹配'$prefix'的词条")
            
            // 打印前几个匹配的词条
            val sampleSize = minOf(3, result.size)
            val samples = result.take(sampleSize)
            samples.forEachIndexed { index, wf ->
                Timber.d("匹配词条${index+1}: 词='${wf.word}', 频率=${wf.frequency}")
            }
        }
        
        // 按词频排序
        return result.sortedByDescending { it.frequency }
    }
    
    /**
     * 从指定节点继续搜索剩余前缀
     */
    private fun continueSearch(startNode: TrieNode, remainingPrefix: String, limit: Int): List<WordFrequency> {
        if (remainingPrefix.isEmpty()) {
            // 如果没有剩余前缀，直接从当前节点收集词条
            val result = mutableListOf<WordFrequency>()
            collectWords(startNode, result, limit)
            return result
        }
        
        var current = startNode
        for ((index, char) in remainingPrefix.withIndex()) {
            val node = current.children[char]
            if (node == null) {
                // 搜索失败
                return emptyList()
            }
            current = node
        }
        
        // 从当前节点收集词条
        val result = mutableListOf<WordFrequency>()
        collectWords(current, result, limit)
        return result
    }
    
    /**
     * 查找字符的替代字符(主要用于拼音声调匹配)
     */
    private fun findAlternativeChars(char: Char): List<Char> {
        val result = mutableListOf<Char>()
        
        // 无声调to有声调映射
        when (char) {
            'a' -> result.addAll(listOf('ā', 'á', 'ǎ', 'à'))
            'e' -> result.addAll(listOf('ē', 'é', 'ě', 'è'))
            'i' -> result.addAll(listOf('ī', 'í', 'ǐ', 'ì'))
            'o' -> result.addAll(listOf('ō', 'ó', 'ǒ', 'ò'))
            'u' -> result.addAll(listOf('ū', 'ú', 'ǔ', 'ù'))
            'v' -> result.addAll(listOf('ǖ', 'ǘ', 'ǚ', 'ǜ', 'ü'))
        }
        
        // 有声调to无声调映射
        when (char) {
            'ā', 'á', 'ǎ', 'à' -> result.add('a')
            'ē', 'é', 'ě', 'è' -> result.add('e')
            'ī', 'í', 'ǐ', 'ì' -> result.add('i')
            'ō', 'ó', 'ǒ', 'ò' -> result.add('o')
            'ū', 'ú', 'ǔ', 'ù' -> result.add('u')
            'ǖ', 'ǘ', 'ǚ', 'ǜ', 'ü' -> result.add('v')
        }
        
        return result
    }
    
    /**
     * 递归收集节点下的所有词
     */
    private fun collectWords(node: TrieNode, result: MutableList<WordFrequency>, limit: Int) {
        if (result.size >= limit) return
        
        if (node.isEnd && node.word != null) {
            // 使用chinese属性作为词条（如果存在），否则使用word
            val displayWord = node.chinese ?: node.word ?: ""
            if (displayWord.isNotEmpty()) {
                result.add(WordFrequency(displayWord, node.frequency))
            } else {
                Timber.e("警告：节点中的word和chinese都为空或null")
            }
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
     * 获取树中所有单词、汉字和它们的频率
     * 采用非递归实现，减少栈空间使用
     * @return 包含拼音、汉字和频率的PinyinEntry列表
     */
    fun getAllWordEntries(): List<PinyinEntry> {
        val entries = mutableListOf<PinyinEntry>()
        
        // 采用分批处理的遍历方式
        val stack = java.util.ArrayDeque<TrieNode>()
        stack.push(root)
        
        var count = 0
        val batchSize = 1000 // 每批处理的节点数
        
        while (stack.isNotEmpty()) {
            val node = stack.pop()
            
            // 如果是单词结尾，添加到结果列表
            if (node.isEnd && node.word != null) {
                // 获取拼音
                val pinyin = node.word!!
                // 获取汉字（如果没有，则使用拼音）
                val chinese = node.chinese ?: pinyin
                
                entries.add(PinyinEntry(pinyin, chinese, node.frequency))
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
        
        return entries
    }
    
    /**
     * 获取树中所有单词和它们的频率
     * 采用非递归实现，减少栈空间使用
     * @return 词条和频率对的列表
     */
    fun getAllWords(): List<Pair<String, Int>> {
        // 使用getAllWordEntries实现，保持兼容性
        return getAllWordEntries().map { entry ->
            // 保持旧的行为，使用chinese作为word返回
            Pair(entry.chinese, entry.frequency)
        }
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

    /**
     * 获取树中的节点总数
     */
    fun getNodeCount(): Int {
        return countNodes(root)
    }

    /**
     * 检查树是否为空
     */
    fun isEmpty(): Boolean {
        return root.children.isEmpty()
    }

    /**
     * 获取树中存储的词条总数
     */
    fun getWordCount(): Int {
        return countWords(root)
    }
    
    /**
     * 递归计算从给定节点开始的词条数量
     */
    private fun countWords(node: TrieNode): Int {
        var count = 0
        if (node.isEnd) {
            count = 1
        }
        
        for (child in node.children.values) {
            count += countWords(child)
        }
        
        return count
    }
    
    /**
     * 获取根节点的所有子节点键（首字符）
     * 用于诊断和分析
     */
    fun getRootChildKeys(): String {
        return root.children.keys.joinToString(", ") { 
            "$it (0x${it.code.toString(16)})" 
        }
    }
    
    /**
     * 通过汉字搜索词条
     * 遍历所有词条，查找包含指定汉字的词条
     * @param chineseChar 要搜索的汉字
     * @param limit 返回结果数量限制
     * @return 包含指定汉字的词条列表
     */
    fun searchByChineseChar(chineseChar: String, limit: Int): List<WordFrequency> {
        if (!isLoaded || chineseChar.length != 1) {
            return emptyList()
        }
        
        Timber.d("搜索包含汉字'$chineseChar'的词条")
        
        // 获取所有词条
        val allWords = getAllWords()
        
        // 过滤包含指定汉字的词条
        val matchingWords = allWords.filter { it.first.contains(chineseChar) }
            .take(limit)
            .map { WordFrequency(it.first, it.second) }
        
        Timber.d("找到${matchingWords.size}个包含汉字'$chineseChar'的词条")
        return matchingWords
    }
}

 