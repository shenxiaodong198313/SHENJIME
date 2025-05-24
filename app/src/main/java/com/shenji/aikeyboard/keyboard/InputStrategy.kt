package com.shenji.aikeyboard.keyboard

import com.shenji.aikeyboard.data.trie.TrieType
import timber.log.Timber

/**
 * 输入策略分析器
 * 
 * 模拟成熟商业输入法的拼音输入策略：
 * 1. 单字拼音：ni -> 你、尼、泥
 * 2. 词组拼音：nihao -> 你好
 * 3. 首字母缩写：nh -> 你好、南海、内核
 * 4. 混合输入：ni'h -> 你好（单字+首字母）
 * 5. 长句拼音：nihaoshijie -> 你好世界
 * 6. 模糊拼音：zhi/zi、chi/ci、shi/si
 * 7. 声调输入：ni3hao3 -> 你好
 * 8. 分词输入：ni hao -> 你 好
 */
class InputStrategy {
    
    /**
     * 输入类型枚举
     */
    enum class InputType {
        SINGLE_CHAR,        // 单字拼音：ni, hao
        WORD_PINYIN,        // 词组拼音：nihao, shijie
        ABBREVIATION,       // 首字母缩写：nh, sj
        MIXED_INPUT,        // 混合输入：ni'h, hao'sj
        LONG_SENTENCE,      // 长句拼音：nihaoshijie
        FUZZY_PINYIN,       // 模糊拼音：zi->zhi, ci->chi
        TONE_INPUT,         // 声调输入：ni3hao3
        SEGMENTED_INPUT     // 分词输入：ni hao
    }
    
    /**
     * 查询策略
     */
    data class QueryStrategy(
        val inputType: InputType,
        val trieTypes: List<TrieType>,
        val queryMethods: List<QueryMethod>,
        val priority: Int,
        val description: String
    )
    
    /**
     * 查询方法
     */
    enum class QueryMethod {
        EXACT_MATCH,        // 精确匹配
        PREFIX_MATCH,       // 前缀匹配
        FUZZY_MATCH,        // 模糊匹配
        ABBREVIATION_MATCH, // 缩写匹配
        SEGMENTED_MATCH,    // 分段匹配
        SYLLABLE_MATCH      // 音节匹配
    }
    
    /**
     * 分析输入类型并返回查询策略
     */
    fun analyzeInput(input: String): List<QueryStrategy> {
        val normalizedInput = input.trim().lowercase()
        val strategies = mutableListOf<QueryStrategy>()
        
        Timber.d("分析输入: '$normalizedInput'")
        
        // 1. 单字拼音策略 (1-4字符)
        if (normalizedInput.length in 1..4 && isSingleSyllable(normalizedInput)) {
            strategies.add(QueryStrategy(
                inputType = InputType.SINGLE_CHAR,
                trieTypes = listOf(TrieType.CHARS),
                queryMethods = listOf(QueryMethod.EXACT_MATCH, QueryMethod.PREFIX_MATCH),
                priority = 10,
                description = "单字拼音查询"
            ))
        }
        
        // 2. 词组拼音策略 (2-12字符)
        if (normalizedInput.length in 2..12) {
            strategies.add(QueryStrategy(
                inputType = InputType.WORD_PINYIN,
                trieTypes = listOf(TrieType.BASE, TrieType.CORRELATION),
                queryMethods = listOf(QueryMethod.EXACT_MATCH, QueryMethod.PREFIX_MATCH),
                priority = 9,
                description = "词组拼音查询"
            ))
        }
        
        // 3. 首字母缩写策略 (2-8字符，全是辅音)
        if (normalizedInput.length in 2..8 && isAbbreviation(normalizedInput)) {
            strategies.add(QueryStrategy(
                inputType = InputType.ABBREVIATION,
                trieTypes = listOf(TrieType.BASE, TrieType.PEOPLE, TrieType.PLACE),
                queryMethods = listOf(QueryMethod.ABBREVIATION_MATCH),
                priority = 8,
                description = "首字母缩写查询"
            ))
        }
        
        // 4. 长句拼音策略 (8+字符)
        if (normalizedInput.length >= 8) {
            strategies.add(QueryStrategy(
                inputType = InputType.LONG_SENTENCE,
                trieTypes = listOf(TrieType.BASE, TrieType.CHARS, TrieType.CORRELATION),
                queryMethods = listOf(QueryMethod.SEGMENTED_MATCH, QueryMethod.SYLLABLE_MATCH),
                priority = 7,
                description = "长句分段查询"
            ))
        }
        
        // 5. 人名地名策略 (特定长度和模式)
        if (normalizedInput.length in 2..8) {
            strategies.add(QueryStrategy(
                inputType = InputType.WORD_PINYIN,
                trieTypes = listOf(TrieType.PEOPLE, TrieType.PLACE),
                queryMethods = listOf(QueryMethod.EXACT_MATCH, QueryMethod.PREFIX_MATCH),
                priority = 6,
                description = "人名地名查询"
            ))
        }
        
        // 6. 模糊拼音策略
        if (containsFuzzyPinyin(normalizedInput)) {
            strategies.add(QueryStrategy(
                inputType = InputType.FUZZY_PINYIN,
                trieTypes = listOf(TrieType.CHARS, TrieType.BASE),
                queryMethods = listOf(QueryMethod.FUZZY_MATCH),
                priority = 5,
                description = "模糊拼音查询"
            ))
        }
        
        // 7. 诗词查询策略 (4+字符)
        if (normalizedInput.length >= 4) {
            strategies.add(QueryStrategy(
                inputType = InputType.WORD_PINYIN,
                trieTypes = listOf(TrieType.POETRY),
                queryMethods = listOf(QueryMethod.PREFIX_MATCH),
                priority = 4,
                description = "诗词查询"
            ))
        }
        
        // 按优先级排序
        strategies.sortByDescending { it.priority }
        
        Timber.d("生成${strategies.size}个查询策略: ${strategies.map { "${it.inputType}(${it.priority})" }}")
        return strategies
    }
    
    /**
     * 判断是否为单音节
     */
    private fun isSingleSyllable(input: String): Boolean {
        // 常见单音节模式
        val singleSyllablePatterns = listOf(
            "^[bpmfdtnlgkhjqxzcsryw]?[aeiouv]+[ng]?$",  // 基本音节
            "^[zcs]h[aeiouv]+[ng]?$",                    // zh/ch/sh音节
            "^[aeiouv]+[ng]?$"                           // 零声母音节
        )
        
        return singleSyllablePatterns.any { pattern ->
            input.matches(Regex(pattern))
        }
    }
    
    /**
     * 判断是否为首字母缩写
     */
    private fun isAbbreviation(input: String): Boolean {
        // 全部是辅音字母，且不超过8个字符
        val consonants = "bcdfghjklmnpqrstvwxyz"
        return input.length <= 8 && input.all { it in consonants }
    }
    
    /**
     * 判断是否包含模糊拼音
     */
    private fun containsFuzzyPinyin(input: String): Boolean {
        val fuzzyPatterns = listOf(
            "zh", "ch", "sh",  // 可能被输入为z, c, s
            "ang", "an",       // 前后鼻音
            "eng", "en",       // 前后鼻音
            "ing", "in"        // 前后鼻音
        )
        
        return fuzzyPatterns.any { pattern ->
            input.contains(pattern)
        }
    }
    
    /**
     * 生成模糊拼音变体
     */
    fun generateFuzzyVariants(input: String): List<String> {
        val variants = mutableSetOf<String>()
        variants.add(input)
        
        var current = input
        
        // zh/z 模糊
        if (current.contains("zh")) {
            variants.add(current.replace("zh", "z"))
        }
        if (current.contains("z") && !current.contains("zh")) {
            variants.add(current.replace("z", "zh"))
        }
        
        // ch/c 模糊
        if (current.contains("ch")) {
            variants.add(current.replace("ch", "c"))
        }
        if (current.contains("c") && !current.contains("ch")) {
            variants.add(current.replace("c", "ch"))
        }
        
        // sh/s 模糊
        if (current.contains("sh")) {
            variants.add(current.replace("sh", "s"))
        }
        if (current.contains("s") && !current.contains("sh")) {
            variants.add(current.replace("s", "sh"))
        }
        
        // 前后鼻音模糊
        if (current.contains("ang")) {
            variants.add(current.replace("ang", "an"))
        }
        if (current.contains("an") && !current.contains("ang")) {
            variants.add(current.replace("an", "ang"))
        }
        
        return variants.toList()
    }
    
    /**
     * 拼音分段策略
     */
    fun segmentPinyin(input: String): List<List<String>> {
        val segments = mutableListOf<List<String>>()
        
        // 策略1: 按常见音节长度分段 (2-4字符)
        segments.addAll(segmentByLength(input, 2, 4))
        
        // 策略2: 按声母韵母分段
        segments.addAll(segmentBySyllable(input))
        
        // 策略3: 固定长度分段 (适用于长输入)
        if (input.length >= 8) {
            segments.addAll(segmentByFixedLength(input, 2))
            segments.addAll(segmentByFixedLength(input, 3))
        }
        
        return segments.distinctBy { it.joinToString("") }
    }
    
    /**
     * 按长度分段
     */
    private fun segmentByLength(input: String, minLen: Int, maxLen: Int): List<List<String>> {
        val segments = mutableListOf<List<String>>()
        
        fun backtrack(start: Int, current: MutableList<String>) {
            if (start >= input.length) {
                if (current.isNotEmpty()) {
                    segments.add(current.toList())
                }
                return
            }
            
            for (len in minLen..minOf(maxLen, input.length - start)) {
                val segment = input.substring(start, start + len)
                if (isValidSyllable(segment)) {
                    current.add(segment)
                    backtrack(start + len, current)
                    current.removeAt(current.size - 1)
                }
            }
        }
        
        backtrack(0, mutableListOf())
        return segments
    }
    
    /**
     * 按音节分段
     */
    private fun segmentBySyllable(input: String): List<List<String>> {
        // 简化的音节识别
        val syllables = mutableListOf<String>()
        var i = 0
        
        while (i < input.length) {
            var found = false
            
            // 尝试匹配3-4字符音节
            for (len in 4 downTo 2) {
                if (i + len <= input.length) {
                    val candidate = input.substring(i, i + len)
                    if (isValidSyllable(candidate)) {
                        syllables.add(candidate)
                        i += len
                        found = true
                        break
                    }
                }
            }
            
            if (!found) {
                // 单字符作为音节
                syllables.add(input.substring(i, i + 1))
                i++
            }
        }
        
        return if (syllables.isNotEmpty()) listOf(syllables) else emptyList()
    }
    
    /**
     * 固定长度分段
     */
    private fun segmentByFixedLength(input: String, length: Int): List<List<String>> {
        val segments = mutableListOf<String>()
        
        for (i in 0 until input.length step length) {
            val end = minOf(i + length, input.length)
            segments.add(input.substring(i, end))
        }
        
        return if (segments.isNotEmpty()) listOf(segments) else emptyList()
    }
    
    /**
     * 判断是否为有效音节
     */
    private fun isValidSyllable(syllable: String): Boolean {
        if (syllable.length < 1 || syllable.length > 6) return false
        
        // 基本音节模式检查
        val validPatterns = listOf(
            "^[bpmfdtnlgkhjqxzcsryw]?[aeiouv]+[ng]?$",
            "^[zcs]h[aeiouv]+[ng]?$",
            "^[aeiouv]+[ng]?$"
        )
        
        return validPatterns.any { pattern ->
            syllable.matches(Regex(pattern))
        }
    }
} 