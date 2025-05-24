package com.shenji.aikeyboard.keyboard

import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.trie.TrieType
import com.shenji.aikeyboard.model.WordFrequency
import timber.log.Timber

/**
 * 智能查询引擎
 * 
 * 实现各种输入策略的具体查询方法：
 * - 精确匹配
 * - 前缀匹配
 * - 模糊匹配
 * - 缩写匹配
 * - 分段匹配
 * - 音节匹配
 */
class IntelligentQueryEngine {
    
    private val trieManager = TrieManager.instance
    private val inputStrategy = InputStrategy()
    
    /**
     * 执行智能查询
     */
    suspend fun executeQuery(
        strategy: InputStrategy.QueryStrategy,
        input: String,
        limit: Int = 10
    ): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        Timber.d("执行查询策略: ${strategy.description}, 输入: '$input'")
        
        for (trieType in strategy.trieTypes) {
            if (results.size >= limit) break
            
            // 确保Trie已加载
            ensureTrieLoaded(trieType)
            
            for (queryMethod in strategy.queryMethods) {
                if (results.size >= limit) break
                
                val methodResults = when (queryMethod) {
                    InputStrategy.QueryMethod.EXACT_MATCH -> 
                        executeExactMatch(trieType, input, limit - results.size)
                    
                    InputStrategy.QueryMethod.PREFIX_MATCH -> 
                        executePrefixMatch(trieType, input, limit - results.size)
                    
                    InputStrategy.QueryMethod.FUZZY_MATCH -> 
                        executeFuzzyMatch(trieType, input, limit - results.size)
                    
                    InputStrategy.QueryMethod.ABBREVIATION_MATCH -> 
                        executeAbbreviationMatch(trieType, input, limit - results.size)
                    
                    InputStrategy.QueryMethod.SEGMENTED_MATCH -> 
                        executeSegmentedMatch(trieType, input, limit - results.size)
                    
                    InputStrategy.QueryMethod.SYLLABLE_MATCH -> 
                        executeSyllableMatch(trieType, input, limit - results.size)
                }
                
                results.addAll(methodResults)
                Timber.d("${queryMethod}在${trieType}中找到${methodResults.size}个结果")
            }
        }
        
        // 去重并按频率排序
        val finalResults = results
            .distinctBy { it.word }
            .sortedByDescending { it.frequency }
            .take(limit)
        
        Timber.d("查询完成，最终返回${finalResults.size}个候选词")
        return finalResults
    }
    
    /**
     * 精确匹配
     */
    private suspend fun executeExactMatch(
        trieType: TrieType,
        input: String,
        limit: Int
    ): List<WordFrequency> {
        if (!trieManager.isTrieLoaded(trieType)) return emptyList()
        
        return try {
            trieManager.searchByPrefix(trieType, input, limit)
                .filter { it.word.length == getExpectedWordLength(input) }
        } catch (e: Exception) {
            Timber.w(e, "精确匹配失败: $trieType, $input")
            emptyList()
        }
    }
    
    /**
     * 前缀匹配
     */
    private suspend fun executePrefixMatch(
        trieType: TrieType,
        input: String,
        limit: Int
    ): List<WordFrequency> {
        if (!trieManager.isTrieLoaded(trieType)) return emptyList()
        
        return try {
            trieManager.searchByPrefix(trieType, input, limit)
        } catch (e: Exception) {
            Timber.w(e, "前缀匹配失败: $trieType, $input")
            emptyList()
        }
    }
    
    /**
     * 模糊匹配
     */
    private suspend fun executeFuzzyMatch(
        trieType: TrieType,
        input: String,
        limit: Int
    ): List<WordFrequency> {
        if (!trieManager.isTrieLoaded(trieType)) return emptyList()
        
        val results = mutableListOf<WordFrequency>()
        val variants = inputStrategy.generateFuzzyVariants(input)
        
        for (variant in variants) {
            if (results.size >= limit) break
            
            try {
                val variantResults = trieManager.searchByPrefix(trieType, variant, limit - results.size)
                results.addAll(variantResults)
                
                if (variantResults.isNotEmpty()) {
                    Timber.d("模糊匹配变体 '$variant' 找到 ${variantResults.size} 个结果")
                }
            } catch (e: Exception) {
                Timber.w(e, "模糊匹配变体失败: $variant")
            }
        }
        
        return results.distinctBy { it.word }
    }
    
    /**
     * 缩写匹配
     */
    private suspend fun executeAbbreviationMatch(
        trieType: TrieType,
        input: String,
        limit: Int
    ): List<WordFrequency> {
        if (!trieManager.isTrieLoaded(trieType)) return emptyList()
        
        val results = mutableListOf<WordFrequency>()
        
        try {
            // 获取所有可能的候选词
            val allCandidates = trieManager.searchByPrefix(trieType, "", limit * 5)
            
            // 筛选匹配缩写的词
            for (candidate in allCandidates) {
                if (results.size >= limit) break
                
                if (matchesAbbreviation(candidate.word, input)) {
                    results.add(candidate)
                }
            }
            
            Timber.d("缩写匹配 '$input' 找到 ${results.size} 个结果")
        } catch (e: Exception) {
            Timber.w(e, "缩写匹配失败: $trieType, $input")
        }
        
        return results
    }
    
    /**
     * 分段匹配
     */
    private suspend fun executeSegmentedMatch(
        trieType: TrieType,
        input: String,
        limit: Int
    ): List<WordFrequency> {
        if (!trieManager.isTrieLoaded(trieType)) return emptyList()
        
        val results = mutableListOf<WordFrequency>()
        val segments = inputStrategy.segmentPinyin(input)
        
        Timber.d("分段匹配，生成 ${segments.size} 种分段方案")
        
        for (segmentList in segments) {
            if (results.size >= limit) break
            
            try {
                // 为每个分段查询候选词
                val segmentResults = mutableListOf<List<WordFrequency>>()
                var allSegmentsHaveResults = true
                
                for (segment in segmentList) {
                    val segmentCandidates = trieManager.searchByPrefix(TrieType.CHARS, segment, 5)
                    if (segmentCandidates.isEmpty()) {
                        allSegmentsHaveResults = false
                        break
                    }
                    segmentResults.add(segmentCandidates)
                }
                
                // 如果所有分段都有结果，组合成候选词
                if (allSegmentsHaveResults && segmentResults.isNotEmpty()) {
                    val combinations = generateCombinations(segmentResults, limit - results.size)
                    results.addAll(combinations)
                    
                    Timber.d("分段 ${segmentList.joinToString("+")} 生成 ${combinations.size} 个组合")
                }
            } catch (e: Exception) {
                Timber.w(e, "分段匹配失败: $segmentList")
            }
        }
        
        return results.distinctBy { it.word }
    }
    
    /**
     * 音节匹配
     */
    private suspend fun executeSyllableMatch(
        trieType: TrieType,
        input: String,
        limit: Int
    ): List<WordFrequency> {
        if (!trieManager.isTrieLoaded(trieType)) return emptyList()
        
        val results = mutableListOf<WordFrequency>()
        
        try {
            // 按音节分解输入
            val syllables = extractSyllables(input)
            
            if (syllables.isNotEmpty()) {
                // 为每个音节查询单字
                for (syllable in syllables) {
                    if (results.size >= limit) break
                    
                    val syllableResults = trieManager.searchByPrefix(TrieType.CHARS, syllable, 3)
                    results.addAll(syllableResults)
                }
                
                Timber.d("音节匹配 ${syllables.joinToString("+")} 找到 ${results.size} 个结果")
            }
        } catch (e: Exception) {
            Timber.w(e, "音节匹配失败: $input")
        }
        
        return results.distinctBy { it.word }
    }
    
    /**
     * 确保Trie已加载
     */
    private suspend fun ensureTrieLoaded(trieType: TrieType) {
        if (!trieManager.isTrieLoaded(trieType)) {
            try {
                trieManager.loadTrieToMemory(trieType)
                Timber.d("按需加载Trie: $trieType")
            } catch (e: Exception) {
                Timber.w(e, "加载Trie失败: $trieType")
            }
        }
    }
    
    /**
     * 判断词语是否匹配缩写
     */
    private fun matchesAbbreviation(word: String, abbreviation: String): Boolean {
        if (word.length < abbreviation.length) return false
        
        // 简化的缩写匹配：检查每个字的拼音首字母
        // 这里需要一个拼音转换器，暂时用简化逻辑
        val wordInitials = getWordInitials(word)
        return wordInitials.startsWith(abbreviation, ignoreCase = true)
    }
    
    /**
     * 获取词语的拼音首字母
     */
    private fun getWordInitials(word: String): String {
        // 简化实现：假设每个汉字对应一个拼音首字母
        // 实际应该使用拼音转换库
        val initials = StringBuilder()
        
        for (char in word) {
            val initial = getCharInitial(char)
            if (initial != null) {
                initials.append(initial)
            }
        }
        
        return initials.toString()
    }
    
    /**
     * 获取单个汉字的拼音首字母
     */
    private fun getCharInitial(char: Char): Char? {
        // 简化的汉字拼音首字母映射
        // 实际应该使用完整的拼音库
        return when (char.code) {
            in 0x4e00..0x9fff -> {
                // 简化映射，实际需要完整的拼音数据
                val code = char.code
                when {
                    code % 23 == 0 -> 'a'
                    code % 23 == 1 -> 'b'
                    code % 23 == 2 -> 'c'
                    code % 23 == 3 -> 'd'
                    code % 23 == 4 -> 'f'
                    code % 23 == 5 -> 'g'
                    code % 23 == 6 -> 'h'
                    code % 23 == 7 -> 'j'
                    code % 23 == 8 -> 'k'
                    code % 23 == 9 -> 'l'
                    code % 23 == 10 -> 'm'
                    code % 23 == 11 -> 'n'
                    code % 23 == 12 -> 'p'
                    code % 23 == 13 -> 'q'
                    code % 23 == 14 -> 'r'
                    code % 23 == 15 -> 's'
                    code % 23 == 16 -> 't'
                    code % 23 == 17 -> 'w'
                    code % 23 == 18 -> 'x'
                    code % 23 == 19 -> 'y'
                    code % 23 == 20 -> 'z'
                    else -> 'n'
                }
            }
            else -> null
        }
    }
    
    /**
     * 生成分段组合
     */
    private fun generateCombinations(
        segmentResults: List<List<WordFrequency>>,
        limit: Int
    ): List<WordFrequency> {
        if (segmentResults.isEmpty()) return emptyList()
        
        val combinations = mutableListOf<WordFrequency>()
        
        fun backtrack(index: Int, current: StringBuilder, frequency: Int) {
            if (combinations.size >= limit) return
            
            if (index >= segmentResults.size) {
                if (current.isNotEmpty()) {
                    combinations.add(WordFrequency(current.toString(), frequency, "分段组合"))
                }
                return
            }
            
            for (candidate in segmentResults[index].take(3)) { // 每个分段最多取3个候选
                val newLength = current.length + candidate.word.length
                if (newLength <= 8) { // 限制组合长度
                    current.append(candidate.word)
                    backtrack(index + 1, current, frequency + candidate.frequency)
                    current.setLength(current.length - candidate.word.length)
                }
            }
        }
        
        backtrack(0, StringBuilder(), 0)
        return combinations
    }
    
    /**
     * 提取音节
     */
    private fun extractSyllables(input: String): List<String> {
        val syllables = mutableListOf<String>()
        var i = 0
        
        while (i < input.length) {
            var found = false
            
            // 尝试匹配2-4字符的音节
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
                if (i < input.length) {
                    syllables.add(input.substring(i, i + 1))
                    i++
                }
            }
        }
        
        return syllables
    }
    
    /**
     * 判断是否为有效音节
     */
    private fun isValidSyllable(syllable: String): Boolean {
        if (syllable.length < 1 || syllable.length > 6) return false
        
        val validPatterns = listOf(
            "^[bpmfdtnlgkhjqxzcsryw]?[aeiouv]+[ng]?$",
            "^[zcs]h[aeiouv]+[ng]?$",
            "^[aeiouv]+[ng]?$"
        )
        
        return validPatterns.any { pattern ->
            syllable.matches(Regex(pattern))
        }
    }
    
    /**
     * 根据输入估算期望的词长
     */
    private fun getExpectedWordLength(input: String): Int {
        return when {
            input.length <= 2 -> 1
            input.length <= 6 -> 2
            input.length <= 9 -> 3
            else -> input.length / 3
        }
    }
} 