package com.shenji.aikeyboard.data.strategy

import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.data.WordFrequency
import com.shenji.aikeyboard.utils.PinyinSplitter
import com.shenji.aikeyboard.utils.PinyinSyllableManager
import timber.log.Timber

/**
 * 音节拆分处理策略
 * 处理输入不完全符合音节表的场景，采用最长匹配原则拆分
 */
class SyllableSplitStrategy(private val repository: DictionaryRepository) : CandidateStrategy {
    
    /**
     * 判断是否适用于音节拆分策略
     * 条件：标准拼音分词失败，但可以部分分解为有效音节或单字母
     */
    override fun isApplicable(input: String): Boolean {
        if (input.isEmpty()) return false
        
        // 首先尝试标准拼音分词
        val standardSplit = PinyinSplitter.split(input)
        if (standardSplit.isNotEmpty()) return false  // 若可以完整分词，不适用此策略
        
        // 尝试智能拆分
        val partialSplit = smartSplit(input)
        
        val isValid = partialSplit.isNotEmpty()
        
        if (isValid) {
            Timber.d("检测到需要音节拆分的输入: '$input' -> '${partialSplit.joinToString(", ")}'，使用音节拆分策略")
        }
        
        return isValid
    }
    
    /**
     * 生成候选词
     * 根据拆分结果决定查询策略：
     * 1. 拆分为多个有效音节：查询组合词
     * 2. 拆分为有效音节+单字母：分别查询并组合结果
     * 3. 其他情况：根据拆分部分单独查询
     */
    override suspend fun generateCandidates(input: String, limit: Int): List<WordFrequency> {
        Timber.d("音节拆分策略处理输入: '$input'")
        
        val results = mutableListOf<WordFrequency>()
        
        try {
            // 智能拆分音节
            val parts = PinyinSplitter.smartSplit(input)
            if (parts.isEmpty()) return emptyList()
            
            Timber.d("输入'$input'拆分结果: ${parts.joinToString(", ")}")
            
            // 判断拆分结果类型
            val validSyllables = parts.filter { PinyinSyllableManager.isValidSyllable(it) }
            val singleLetters = parts.filter { it.length == 1 && !PinyinSyllableManager.isValidSyllable(it) }
            
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
                    return repository.searchByInitials(initialLetters, limit)
                }
            }
            
            // 默认处理：逐个部分查询并组合结果
            Timber.d("使用默认查询策略，分别查询各部分")
            return fallbackQuery(parts, limit)
            
        } catch (e: Exception) {
            Timber.e(e, "音节拆分策略查询异常: ${e.message}")
        }
        
        // 按词频排序
        return results.sortedByDescending { it.frequency }.take(limit)
    }
    
    /**
     * 智能拆分输入
     * 采用贪婪匹配，从左到右尽可能匹配最长的有效音节
     * 优化处理单字母和有效音节的组合情况
     */
    private fun smartSplit(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        
        val parts = mutableListOf<String>()
        var pos = 0
        
        while (pos < input.length) {
            // 尝试从当前位置匹配最长的有效音节
            var found = false
            
            // 从最长可能的音节长度开始尝试
            val maxLen = minOf(PinyinSyllableManager.MAX_SYLLABLE_LENGTH, input.length - pos)
            
            for (len in maxLen downTo 1) {
                if (pos + len <= input.length) {
                    val part = input.substring(pos, pos + len)
                    
                    if (PinyinSyllableManager.isValidSyllable(part)) {
                        parts.add(part)
                        pos += len
                        found = true
                        break
                    }
                }
            }
            
            // 如果无法匹配有效音节，添加单字母作为独立部分
            if (!found) {
                val letter = input.substring(pos, pos + 1)
                
                // 优化处理：检查是否可以与后续字母组成有效音节
                var canFormSyllable = false
                for (lookAhead in 1..minOf(PinyinSyllableManager.MAX_SYLLABLE_LENGTH - 1, input.length - pos - 1)) {
                    val potentialSyllable = input.substring(pos, pos + 1 + lookAhead)
                    if (PinyinSyllableManager.isValidSyllable(potentialSyllable)) {
                        canFormSyllable = true
                        break
                    }
                }
                
                // 如果是单字母，作为独立部分添加
                parts.add(letter)
                pos += 1
                
                // 记录日志
                Timber.d("添加单字母'$letter'作为独立部分，可能与后续字母组成音节: $canFormSyllable")
            }
        }
        
        return parts
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
                Timber.d("音节'$syllable'查询到单字: ${chars.joinToString { it.word }}")
            } else {
                // 如果找不到单字，添加音节本身作为占位符
                singleResults.add(listOf(WordFrequency(syllable, 0)))
                Timber.d("音节'$syllable'无匹配单字")
            }
        }
        
        // 如果存在至少两个有效的单字列表，进行组合
        if (singleResults.size >= 2 && singleResults.all { it.isNotEmpty() }) {
            val first = singleResults[0]
            val second = singleResults[1]
            
            // 组合前两个音节的单字
            for (word1 in first) {
                for (word2 in second) {
                    val combinedWord = word1.word + word2.word
                    val combinedFreq = (word1.frequency + word2.frequency) / 2
                    results.add(WordFrequency(combinedWord, combinedFreq))
                    
                    if (results.size >= limit) break
                }
                if (results.size >= limit) break
            }
            
            // 如果有三个或更多音节，尝试添加第三个音节
            if (singleResults.size >= 3 && results.size < limit) {
                val third = singleResults[2]
                val combinedResults = mutableListOf<WordFrequency>()
                
                for (combined in results.take(Math.min(3, results.size))) {
                    for (word3 in third) {
                        val threeWord = combined.word + word3.word
                        val threeFreq = (combined.frequency + word3.frequency) / 2
                        combinedResults.add(WordFrequency(threeWord, threeFreq))
                        
                        if (combinedResults.size >= limit/2) break
                    }
                    if (combinedResults.size >= limit/2) break
                }
                
                results.addAll(combinedResults)
            }
        }
        
        Timber.d("单独音节组合结果: ${results.take(3).joinToString { it.word }}${if (results.size > 3) "..." else ""}")
        return results.sortedByDescending { it.frequency }.take(limit)
    }
    
    /**
     * 查询有效音节+单字母的组合
     */
    private suspend fun querySyllableWithLetter(syllables: List<String>, letters: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        // 1. 对有效音节部分查询
        val syllablePart = syllables.joinToString("")
        val syllablePinyinWithSpace = syllables.joinToString(" ")
        
        Timber.d("查询音节部分: $syllablePart / $syllablePinyinWithSpace")
        
        // 尝试两种拼音格式（有空格和无空格）
        val syllableResults = mutableListOf<WordFrequency>()
        
        // 带空格的标准格式查询
        val spaceResults = repository.searchBasicEntries(
            pinyin = syllablePinyinWithSpace,
            limit = limit / 3,
            excludeTypes = if (syllables.size == 1) listOf("poetry", "associational") else emptyList()
        )
        syllableResults.addAll(spaceResults)
        
        // 无空格格式查询
        if (syllableResults.size < limit / 3) {
            val noSpaceResults = repository.searchBasicEntries(
                pinyin = syllablePart,
                limit = limit / 3 - syllableResults.size,
                excludeTypes = if (syllables.size == 1) listOf("poetry", "associational") else emptyList()
            )
            
            // 添加非重复结果
            val existingWords = syllableResults.map { it.word }.toSet()
            syllableResults.addAll(noSpaceResults.filter { it.word !in existingWords })
        }
        
        // 2. 对单字母部分，作为首字母查询
        val letterPart = letters.joinToString("")
        Timber.d("查询首字母部分: $letterPart")
        val letterResults = repository.searchByInitials(letterPart, limit / 3)
        
        // 3. 组合音节和字母对应的结果
        val combinedResults = mutableListOf<WordFrequency>()
        if (syllables.size == 1 && letters.size == 1) {
            // 特殊处理：如果是单个音节+单个字母，尝试预测性查询
            val syllable = syllables[0]
            val letter = letters[0]
            
            // 查询以该音节开头且第二个字的拼音以该字母开头的词组
            val predictResults = repository.searchPredictive(
                syllable = syllable,
                nextInitial = letter,
                limit = limit / 3
            )
            
            if (predictResults.isNotEmpty()) {
                Timber.d("预测性查询结果: ${predictResults.take(3).joinToString { it.word }}${if (predictResults.size > 3) "..." else ""}")
                combinedResults.addAll(predictResults)
            }
        }
        
        // 4. 组合结果并排序
        results.addAll(syllableResults)
        results.addAll(letterResults)
        results.addAll(combinedResults)
        
        // 日志记录
        Timber.d("音节查询结果: ${syllableResults.size}个, 首字母查询结果: ${letterResults.size}个, 组合查询结果: ${combinedResults.size}个")
        
        return results.distinctBy { it.word }.sortedByDescending { it.frequency }.take(limit)
    }
    
    /**
     * 默认查询方式（分别查询各部分并组合）
     */
    private suspend fun fallbackQuery(parts: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        // 逐个查询每个部分对应的候选词
        val partResults = mutableListOf<List<WordFrequency>>()
        
        for (part in parts) {
            // 每个部分查询最高权重的几个结果
            val partCandidates = if (PinyinSyllableManager.isValidSyllable(part)) {
                repository.searchBasicEntries(
                    pinyin = part,
                    limit = 5,  // 每部分最多取5个候选词
                    excludeTypes = if (part.length == 1) emptyList() else listOf("poetry", "associational")
                )
            } else {
                // 单字母作为首字母查询
                repository.searchByInitials(part, 5)
            }
            
            if (partCandidates.isNotEmpty()) {
                partResults.add(partCandidates)
                Timber.d("部分'$part'查询结果: ${partCandidates.joinToString { it.word }}")
            } else {
                // 如有部分无结果，添加原始拼音作为结果
                partResults.add(listOf(WordFrequency(part, 0)))
                Timber.d("部分'$part'无匹配结果，使用原始拼音")
            }
        }
        
        // 特殊处理：如果只有一个部分，直接返回其结果
        if (partResults.size == 1) {
            return partResults[0].take(limit)
        }
        
        // 组合生成候选词（简化版，只组合前两部分）
        val part1 = partResults.getOrNull(0) ?: return emptyList()
        val part2 = partResults.getOrNull(1) ?: return part1.take(limit)
        
        // 笛卡尔积组合前两部分的候选词
        for (word1 in part1) {
            for (word2 in part2) {
                val combinedWord = word1.word + word2.word
                val combinedFreq = (word1.frequency + word2.frequency) / 2
                results.add(WordFrequency(combinedWord, combinedFreq))
                
                // 限制组合数量
                if (results.size >= limit) break
            }
            if (results.size >= limit) break
        }
        
        return results.sortedByDescending { it.frequency }.take(limit)
    }
} 