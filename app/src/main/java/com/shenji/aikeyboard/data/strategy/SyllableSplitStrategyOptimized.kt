package com.shenji.aikeyboard.data.strategy

import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.data.WordFrequency
import com.shenji.aikeyboard.utils.PinyinSegmenterOptimized
import com.shenji.aikeyboard.utils.PinyinSyllableManager
import timber.log.Timber

/**
 * 优化版音节拆分处理策略
 * 使用PinyinSegmenterOptimized进行拼音分词，解决"nihao"被错误分割为"n+i+hao"的问题
 */
class SyllableSplitStrategyOptimized(private val repository: DictionaryRepository) : CandidateStrategy {
    
    /**
     * 判断是否适用于音节拆分策略
     */
    override fun isApplicable(input: String): Boolean {
        if (input.isEmpty()) return false
        
        // 使用优化版分词器进行分词
        val result = PinyinSegmenterOptimized.cut(input)
        val isValid = result.size > 1 || (result.size == 1 && result[0] != input)
        
        if (isValid) {
            Timber.d("检测到需要音节拆分的输入: '$input' -> '${result.joinToString(", ")}'，使用优化版音节拆分策略")
        }
        
        return isValid
    }
    
    /**
     * 生成候选词
     */
    override suspend fun generateCandidates(input: String, limit: Int): List<WordFrequency> {
        Timber.d("优化版音节拆分策略处理输入: '$input'")
        
        val results = mutableListOf<WordFrequency>()
        
        try {
            // 使用优化版分词器进行分词
            val parts = PinyinSegmenterOptimized.cut(input)
            if (parts.isEmpty() || (parts.size == 1 && parts[0] == input)) {
                return emptyList()
            }
            
            Timber.d("输入'$input'拆分结果: ${parts.joinToString(", ")}")
            
            // 判断拆分结果类型
            val validSyllables = parts.filter { PinyinSegmenterOptimized.isValidSyllable(it) }
            val singleLetters = parts.filter { it.length == 1 && !PinyinSegmenterOptimized.isValidSyllable(it) }
            
            Timber.d("有效音节: ${validSyllables.joinToString(", ")}, 单字母: ${singleLetters.joinToString(", ")}")
            
            // 根据拆分结果类型选择查询策略
            when {
                // 情况1: 全部是有效音节且数量>1 (如"wei"+"xi")，优先查询多字词
                validSyllables.size > 1 && singleLetters.isEmpty() -> {
                    Timber.d("使用多音节查询策略: ${validSyllables.joinToString("+")}")
                    val multiSyllableResults = queryMultiSyllable(validSyllables, limit)
                    if (multiSyllableResults.isNotEmpty()) {
                        return multiSyllableResults
                    }
                }
                
                // 情况2: 有效音节+单字母 (如"wei"+"x")，组合查询结果
                validSyllables.isNotEmpty() && singleLetters.isNotEmpty() -> {
                    Timber.d("使用音节+单字母查询策略: ${validSyllables.joinToString("+")}+${singleLetters.joinToString("+")}")
                    return querySyllableWithLetter(validSyllables, singleLetters, limit)
                }
                
                // 情况3: 只有一个有效音节，查询该音节对应的单字和词组
                validSyllables.size == 1 && singleLetters.isEmpty() -> {
                    val syllable = validSyllables[0]
                    Timber.d("使用单音节查询策略: $syllable")
                    return repository.searchBasicEntries(
                        pinyin = syllable,
                        limit = limit,
                        excludeTypes = listOf("poetry", "associational")
                    )
                }
                
                // 情况4: 只有单字母，按首字母查询
                validSyllables.isEmpty() && singleLetters.isNotEmpty() -> {
                    // 将所有单字母合并作为首字母组合查询
                    val initialLetters = singleLetters.joinToString("")
                    Timber.d("使用首字母查询策略: $initialLetters")
                    return queryInitialLetters(initialLetters, limit)
                }
            }
            
            // 默认处理：逐个部分查询并组合结果
            Timber.d("使用默认查询策略，分别查询各部分")
            return fallbackQuery(parts, limit)
            
        } catch (e: Exception) {
            Timber.e(e, "优化版音节拆分策略查询异常: ${e.message}")
        }
        
        // 按词频排序
        return results.sortedByDescending { it.frequency }.take(limit)
    }
    
    /**
     * 查询多个有效音节组成的词
     */
    private suspend fun queryMultiSyllable(syllables: List<String>, limit: Int): List<WordFrequency> {
        val fullPinyin = syllables.joinToString("")
        Timber.d("查询多音节组合: $fullPinyin (源自 ${syllables.joinToString("+")})")
        
        // 构建不同的拼音形式进行查询
        val pinyinWithSpace = syllables.joinToString(" ")
        val results = mutableListOf<WordFrequency>()
        
        // 1. 首先尝试使用有空格的拼音进行精确查询（数据库中的标准格式）
        val spaceResults = repository.searchBasicEntries(
            pinyin = pinyinWithSpace,
            limit = limit / 2,
            excludeTypes = listOf("poetry", "associational")
        )
        
        results.addAll(spaceResults)
        Timber.d("空格拼音'$pinyinWithSpace'查询结果: ${spaceResults.size}个")
        
        // 2. 如果结果不足，使用无空格拼音进行查询
        if (results.size < limit) {
            val noSpaceResults = repository.searchBasicEntries(
                pinyin = fullPinyin,
                limit = limit - results.size,
                excludeTypes = listOf("poetry", "associational")
            )
            
            // 添加非重复结果
            val existingWords = results.map { it.word }.toSet()
            val newResults = noSpaceResults.filter { it.word !in existingWords }
            results.addAll(newResults)
            
            Timber.d("无空格拼音'$fullPinyin'查询额外结果: ${newResults.size}个")
        }
        
        // 3. 如果通过基础词典查询结果仍然不足，尝试其他词典
        if (results.size < limit / 2) {
            val dictResults = mutableListOf<WordFrequency>()
            
            // 尝试查询地名和人名词典
            val dictTypes = listOf("place", "people")
            for (dictType in dictTypes) {
                val typeResults = repository.searchDictionary(
                    pinyin = pinyinWithSpace,  // 优先使用带空格的标准拼音
                    limit = (limit - results.size) / 2,
                    dictType = dictType
                )
                
                // 添加非重复结果
                val existingWords = results.map { it.word }.toSet()
                val newResults = typeResults.filter { it.word !in existingWords }
                dictResults.addAll(newResults)
                
                Timber.d("词典查询结果: ${newResults.size}个")
            }
            
            results.addAll(dictResults)
        }
        
        // 最后，如果结果仍然不足，尝试单独查询每个音节并组合
        if (results.isEmpty() && syllables.size > 1) {
            Timber.d("多音节词组查询无结果，尝试单独查询各音节")
            results.addAll(querySeparateSyllables(syllables, limit))
        }
        
        return results.sortedByDescending { it.frequency }.take(limit)
    }
    
    /**
     * 单独查询每个音节，然后组合结果
     */
    private suspend fun querySeparateSyllables(syllables: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        val singleResults = mutableListOf<List<WordFrequency>>()
        
        // 查询每个音节的单字
        for (syllable in syllables) {
            val chars = repository.searchSingleCharsForSyllable(syllable, 3)
            if (chars.isNotEmpty()) {
                singleResults.add(chars)
            }
        }
        
        // 如果每个音节都有对应的单字，构建组合结果
        if (singleResults.size == syllables.size && singleResults.all { it.isNotEmpty() }) {
            // 取第一个字的最高频结果，减少组合数
            val primary = singleResults[0].take(2)
            
            // 生成组合
            for (char1 in primary) {
                var word = char1.word
                var freq = char1.frequency
                
                // 组合剩余音节的字
                for (i in 1 until singleResults.size) {
                    val char2 = singleResults[i].firstOrNull() ?: continue
                    word += char2.word
                    freq = (freq + char2.frequency) / 2  // 取平均词频
                }
                
                results.add(WordFrequency(word, freq))
            }
        }
        
        return results.take(limit)
    }
    
    /**
     * 处理音节+单字母的组合查询
     */
    private suspend fun querySyllableWithLetter(
        syllables: List<String>,
        letters: List<String>,
        limit: Int
    ): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        // 1. 首先查询有效音节部分
        val syllableResults = if (syllables.size == 1) {
            // 单个音节，直接查询
            repository.searchBasicEntries(
                pinyin = syllables[0],
                limit = limit / 2,
                excludeTypes = listOf("poetry", "associational")
            )
        } else {
            // 多个音节，组合查询
            queryMultiSyllable(syllables, limit / 2)
        }
        
        // 2. 然后使用单字母进行过滤/补充查询
        if (syllableResults.isNotEmpty()) {
            // 组合字母形成首字母过滤条件
            val letterPattern = letters.joinToString("")
            
            // 过滤：词的各部分首字母必须匹配单字母序列
            val filteredResults = syllableResults.filter { candidate ->
                val initials = candidate.word.indices.map { candidate.word[it].toString() }.joinToString("")
                letterPattern.startsWith(initials) || initials.startsWith(letterPattern)
            }
            
            results.addAll(filteredResults)
            
            // 如果过滤后结果太少，补充一些原始结果
            if (filteredResults.size < limit / 4 && syllableResults.size > filteredResults.size) {
                val remainingSlots = limit / 2 - filteredResults.size
                val additionalResults = syllableResults
                    .filter { it !in filteredResults }
                    .take(remainingSlots)
                results.addAll(additionalResults)
            }
        }
        
        // 3. 如果结果仍然不足，使用单字母查询首字母匹配的词
        if (results.size < limit / 2) {
            val initialQuery = letters.joinToString("")
            val initialResults = repository.searchByInitials(
                initials = initialQuery,
                limit = limit - results.size
            )
            
            // 添加非重复结果
            val existingWords = results.map { it.word }.toSet()
            results.addAll(initialResults.filter { it.word !in existingWords })
        }
        
        return results.sortedByDescending { it.frequency }.take(limit)
    }
    
    /**
     * 查询首字母匹配的词条
     */
    private suspend fun queryInitialLetters(initialLetters: String, limit: Int): List<WordFrequency> {
        Timber.d("查询首字母: '$initialLetters'")
        return repository.searchByInitials(initials = initialLetters, limit = limit)
    }
    
    /**
     * 默认查询方法
     * 对每个部分分别查询，然后组合结果
     */
    private suspend fun fallbackQuery(parts: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        // 对每个部分进行查询
        for (part in parts) {
            // 查询此部分的结果
            val partResults = if (part.length == 1 && !PinyinSegmenterOptimized.isValidSyllable(part)) {
                // 单字母，查询首字母匹配的单字
                repository.searchByInitials(initials = part, limit = 5)
            } else {
                // 有效音节，查询对应的单字和词语
                repository.searchBasicEntries(
                    pinyin = part,
                    limit = 5,
                    excludeTypes = listOf("poetry", "associational")
                )
            }
            
            // 添加查询结果
            if (partResults.isNotEmpty()) {
                // 如果是第一个部分，全部添加
                if (results.isEmpty()) {
                    results.addAll(partResults)
                } 
                // 如果已有结果，尝试组合
                else {
                    val combined = results.flatMap { existing ->
                        partResults.map { new ->
                            WordFrequency(
                                word = existing.word + new.word,
                                frequency = (existing.frequency + new.frequency) / 2
                            )
                        }
                    }
                    results.clear()
                    results.addAll(combined.take(limit))
                }
            }
        }
        
        return results.sortedByDescending { it.frequency }.take(limit)
    }
} 