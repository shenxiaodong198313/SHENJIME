package com.shenji.aikeyboard.engine

import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.data.WordFrequency
import com.shenji.aikeyboard.model.Candidate
import com.shenji.aikeyboard.model.CandidateBuilder
import com.shenji.aikeyboard.model.CandidateSource
import com.shenji.aikeyboard.model.CandidateWeight
import com.shenji.aikeyboard.model.GeneratorType
import com.shenji.aikeyboard.model.MatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 组合候选词生成器
 * 用于处理长句子的候选词生成，通过组合短词来构建完整句子
 */
class CombinationCandidateGenerator(
    private val dictionaryRepository: DictionaryRepository
) {
    
    /**
     * 生成组合候选词
     * @param syllables 音节列表
     * @param limit 候选词数量限制
     * @return 组合生成的候选词列表
     */
    suspend fun generateCombinationCandidates(
        syllables: List<String>,
        limit: Int = 10
    ): List<Candidate> = withContext(Dispatchers.IO) {
        
        if (syllables.isEmpty()) return@withContext emptyList()
        
        Timber.d("开始生成组合候选词，音节: ${syllables.joinToString(" ")}")
        
        val combinations = mutableListOf<Candidate>()
        
        try {
            // 1. 尝试完整匹配
            val fullMatch = tryFullMatch(syllables)
            if (fullMatch != null) {
                combinations.add(fullMatch)
            }
            
            // 2. 生成分段组合
            val segmentCombinations = generateSegmentCombinations(syllables, limit - combinations.size)
            combinations.addAll(segmentCombinations)
            
            // 3. 生成渐进组合（从左到右逐步匹配）
            if (combinations.size < limit) {
                val progressiveCombinations = generateProgressiveCombinations(syllables, limit - combinations.size)
                combinations.addAll(progressiveCombinations)
            }
            
            Timber.d("组合候选词生成完成，共 ${combinations.size} 个")
            
        } catch (e: Exception) {
            Timber.e(e, "组合候选词生成异常")
        }
        
        return@withContext combinations.take(limit)
    }
    
    /**
     * 尝试完整匹配
     */
    private suspend fun tryFullMatch(syllables: List<String>): Candidate? {
        val fullPinyin = syllables.joinToString(" ")
        val results = dictionaryRepository.searchBasicEntries(fullPinyin, 1)
        
        return if (results.isNotEmpty()) {
            val wordFreq = results.first()
            CandidateBuilder()
                .word(wordFreq.word)
                .pinyin(fullPinyin)
                .initialLetters(syllables.joinToString("") { it.first().toString() })
                .frequency(wordFreq.frequency)
                .type("full_match")
                .weight(CandidateWeight.default(wordFreq.frequency))
                .source(CandidateSource(
                    generator = GeneratorType.EXACT_MATCH,
                    matchType = MatchType.EXACT,
                    layer = 1,
                    confidence = 1.0f
                ))
                .build()
        } else {
            null
        }
    }
    
    /**
     * 生成分段组合候选词
     */
    private suspend fun generateSegmentCombinations(
        syllables: List<String>,
        limit: Int
    ): List<Candidate> {
        val combinations = mutableListOf<Candidate>()
        
        // 尝试不同的分段方式
        val segmentOptions = generateSegmentOptions(syllables)
        
        for (segments in segmentOptions.take(3)) { // 只取前3种分段方式
            val combination = buildCombinationFromSegments(segments)
            if (combination != null && !combinations.any { it.word == combination.word }) {
                combinations.add(combination)
                if (combinations.size >= limit) break
            }
        }
        
        return combinations
    }
    
    /**
     * 生成分段选项
     */
    private fun generateSegmentOptions(syllables: List<String>): List<List<List<String>>> {
        val options = mutableListOf<List<List<String>>>()
        
        // 选项1: 2-2-2 分段（适合6音节）
        if (syllables.size == 6) {
            options.add(listOf(
                syllables.subList(0, 2),  // wo bu
                syllables.subList(2, 4),  // shi bei
                syllables.subList(4, 6)   // jing ren
            ))
        }
        
        // 选项2: 1-2-2-1 分段
        if (syllables.size >= 6) {
            options.add(listOf(
                syllables.subList(0, 1),  // wo
                syllables.subList(1, 3),  // bu shi
                syllables.subList(3, 5),  // bei jing
                syllables.subList(5, syllables.size)  // ren
            ))
        }
        
        // 选项3: 1-1-2-2 分段
        if (syllables.size >= 6) {
            options.add(listOf(
                syllables.subList(0, 1),  // wo
                syllables.subList(1, 2),  // bu
                syllables.subList(2, 4),  // shi bei / bei jing
                syllables.subList(4, syllables.size)  // jing ren / ren
            ))
        }
        
        return options
    }
    
    /**
     * 从分段构建组合候选词
     */
    private suspend fun buildCombinationFromSegments(segments: List<List<String>>): Candidate? {
        val words = mutableListOf<String>()
        var totalFrequency = 0
        var confidence = 1.0f
        
        for (segment in segments) {
            val segmentPinyin = segment.joinToString(" ")
            val results = dictionaryRepository.searchBasicEntries(segmentPinyin, 1)
            
            if (results.isNotEmpty()) {
                val wordFreq = results.first()
                words.add(wordFreq.word)
                totalFrequency += wordFreq.frequency
                confidence *= 0.9f // 每个分段降低一点置信度
            } else {
                // 如果某个分段找不到，尝试单字匹配
                val singleChars = mutableListOf<String>()
                for (syllable in segment) {
                    val charResults = dictionaryRepository.searchBasicEntries(syllable, 1)
                    if (charResults.isNotEmpty()) {
                        singleChars.add(charResults.first().word)
                        totalFrequency += charResults.first().frequency / 10 // 单字权重较低
                    } else {
                        return null // 如果连单字都找不到，放弃这个组合
                    }
                }
                words.addAll(singleChars)
                confidence *= 0.7f // 单字匹配进一步降低置信度
            }
        }
        
        if (words.isEmpty()) return null
        
        val combinedWord = words.joinToString("")
        val fullPinyin = segments.flatten().joinToString(" ")
        
        return CandidateBuilder()
            .word(combinedWord)
            .pinyin(fullPinyin)
            .initialLetters(segments.flatten().joinToString("") { it.first().toString() })
            .frequency(totalFrequency / segments.size) // 平均频率
            .type("combination")
            .weight(CandidateWeight.default(totalFrequency / segments.size))
            .source(CandidateSource(
                generator = GeneratorType.COMBINATION,
                matchType = MatchType.PARTIAL,
                layer = 2,
                confidence = confidence
            ))
            .build()
    }
    
    /**
     * 生成渐进组合候选词
     */
    private suspend fun generateProgressiveCombinations(
        syllables: List<String>,
        limit: Int
    ): List<Candidate> {
        val combinations = mutableListOf<Candidate>()
        
        // 从左到右逐步匹配
        var pos = 0
        val words = mutableListOf<String>()
        var totalFrequency = 0
        
        while (pos < syllables.size && combinations.size < limit) {
            var bestMatch: WordFrequency? = null
            var bestLength = 0
            
            // 尝试从当前位置开始的不同长度匹配
            for (len in minOf(syllables.size - pos, 4) downTo 1) {
                val segmentPinyin = syllables.subList(pos, pos + len).joinToString(" ")
                val results = dictionaryRepository.searchBasicEntries(segmentPinyin, 1)
                
                if (results.isNotEmpty()) {
                    bestMatch = results.first()
                    bestLength = len
                    break // 优先选择最长匹配
                }
            }
            
            if (bestMatch != null) {
                words.add(bestMatch.word)
                totalFrequency += bestMatch.frequency
                pos += bestLength
            } else {
                // 如果找不到匹配，跳过当前音节
                pos++
            }
        }
        
        if (words.isNotEmpty()) {
            val combinedWord = words.joinToString("")
            val fullPinyin = syllables.joinToString(" ")
            
            val candidate = CandidateBuilder()
                .word(combinedWord)
                .pinyin(fullPinyin)
                .initialLetters(syllables.joinToString("") { it.first().toString() })
                .frequency(totalFrequency / words.size)
                .type("progressive")
                .weight(CandidateWeight.default(totalFrequency / words.size))
                .source(CandidateSource(
                    generator = GeneratorType.PROGRESSIVE,
                    matchType = MatchType.PARTIAL,
                    layer = 3,
                    confidence = 0.8f
                ))
                .build()
                
            combinations.add(candidate)
        }
        
        return combinations
    }
} 