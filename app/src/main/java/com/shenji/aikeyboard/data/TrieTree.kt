package com.shenji.aikeyboard.data

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Trie树节点结构
 * 极低内存优化版本
 */
class TrieNode {
    // 使用HashMap存储子节点，节省内存
    val children = HashMap<Char, TrieNode>(2) // 减小初始容量到2，大部分节点分支数较少
    // 标记是否为单词结尾
    var isEnd = false
    // 词频，用于排序候选词
    var frequency = 0
    // 仅在isEnd=true的节点存储完整信息，减少内存冗余
    var word: String? = null
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
    
    // 节点计数
    private var nodeCount = 0
    
    // 内存紧张时用于缓存的常用结果
    private val commonSearchCache = ConcurrentHashMap<String, List<WordFrequency>>(100)
    
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
        
        // 插入新词条时清空缓存
        commonSearchCache.clear()
        
        var current = root
        for (char in key) {
            val next = current.children[char]
            if (next != null) {
                current = next
            } else {
                val newNode = TrieNode()
                current.children[char] = newNode
                current = newNode
                nodeCount++ // 增加节点计数
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
     * 根据前缀查找匹配的词条列表，按照特定规则排序
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
        
        // 先检查缓存
        val cacheKey = "$prefix:$limit"
        commonSearchCache[cacheKey]?.let { cached ->
            Timber.d("从缓存中获取'$prefix'的搜索结果，共${cached.size}个匹配项")
            return cached
        }
        
        Timber.d("在Trie树中搜索前缀: '$prefix'，字符数：${prefix.length}")
        
        // 检查根节点是否有子节点
        if (root.children.isEmpty()) {
            Timber.d("Trie树的根节点没有子节点，返回空列表")
            return emptyList()
        }
        
        val result = mutableListOf<WordFrequency>()
        
        // 定位到前缀的最后一个节点
        var current = root
        for ((index, char) in prefix.withIndex()) {
            val node = current.children[char]
            if (node == null) {
                // 尝试替代检索：检查是否是拼音声调问题
                val alternativeChars = findAlternativeChars(char)
                if (alternativeChars.isNotEmpty()) {
                    for (altChar in alternativeChars) {
                        val altNode = current.children[altChar]
                        if (altNode != null) {
                            // 继续从替代字符节点搜索剩余前缀
                            val remainingPrefix = prefix.substring(index + 1)
                            val altResult = continueSearch(altNode, remainingPrefix, limit)
                            if (altResult.isNotEmpty()) {
                                result.addAll(altResult)
                                break
                            }
                        }
                    }
                    
                    if (result.isNotEmpty()) {
                        // 如果通过替代字符找到了结果，根据输入规则排序
                        val sortedResult = sortResults(result, prefix, limit)
                        // 存入缓存
                        if (sortedResult.isNotEmpty()) {
                            commonSearchCache[cacheKey] = sortedResult
                        }
                        return sortedResult
                    }
                }
                
                return emptyList()
            }
            current = node
        }
        
        // 从当前节点开始搜集所有词
        collectWords(current, result, limit)
        
        // 根据输入规则排序
        val sortedResult = sortResults(result, prefix, limit)
        
        // 存入缓存
        if (sortedResult.isNotEmpty()) {
            commonSearchCache[cacheKey] = sortedResult
        }
        
        return sortedResult
    }
    
    /**
     * 根据输入规则排序候选词结果
     * 如果是单字拼音输入，优先显示单字，并按照字数和词频排序
     * 如果是多字拼音输入，按照词频排序
     */
    private fun sortResults(results: List<WordFrequency>, prefix: String, limit: Int): List<WordFrequency> {
        // 判断是否为单字拼音输入（不含空格，只有字母）
        val isSinglePinyinInput = prefix.all { it.isLetter() } && !prefix.contains(" ")
        
        return if (isSinglePinyinInput) {
            Timber.d("单字拼音输入，优先显示单字候选词")
            // 优先按字数排序（单字优先），然后按词频排序
            results.sortedWith(
                compareBy<WordFrequency> { it.word.length }  // 首先按字数排序（短的优先）
                    .thenByDescending { it.frequency }       // 然后按词频降序排序
            ).take(limit)
        } else {
            Timber.d("多字拼音输入，按词频排序候选词")
            // 多字拼音输入，仅按词频排序
            results.sortedByDescending { it.frequency }.take(limit)
        }
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
     * 获取当前Trie树的节点数量
     */
    fun getNodeCount(): Int {
        return nodeCount
    }
    
    /**
     * 获取当前Trie树的词条数量
     */
    fun getWordCount(): Int {
        return estimatedWordCount
    }
    
    /**
     * 检查Trie树是否已加载
     */
    fun isLoaded(): Boolean {
        return isLoaded
    }
    
    /**
     * 设置Trie树的加载状态
     */
    fun setLoaded(loaded: Boolean) {
        isLoaded = loaded
    }
    
    /**
     * 清空Trie树
     */
    fun clear() {
        clearNode(root)
        root.children.clear()
        estimatedWordCount = 0
        nodeCount = 0
        isLoaded = false
        clearCache()
        System.gc()
    }
    
    /**
     * 清空缓存
     */
    fun clearCache() {
        commonSearchCache.clear()
    }
    
    /**
     * 递归清空节点以释放内存
     */
    private fun clearNode(node: TrieNode) {
        // 递归清空所有子节点
        for (child in node.children.values) {
            clearNode(child)
        }
        // 清空当前节点
        node.clear()
    }
    
    /**
     * 管理缓存大小，防止缓存占用过多内存
     */
    fun manageCacheSize() {
        // 如果缓存大小超过阈值，清除一半的缓存
        if (commonSearchCache.size > 200) {
            val keysToRemove = commonSearchCache.keys.toList().subList(0, commonSearchCache.size / 2)
            keysToRemove.forEach { commonSearchCache.remove(it) }
            Timber.d("清除了${keysToRemove.size}个搜索结果缓存，当前缓存大小：${commonSearchCache.size}")
        }
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
        val wordCount = getWordCount()
        
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

 