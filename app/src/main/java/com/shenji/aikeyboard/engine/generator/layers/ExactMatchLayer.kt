package com.shenji.aikeyboard.engine.generator.layers

import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.engine.analyzer.InputAnalysis
import com.shenji.aikeyboard.engine.analyzer.InputMode
import com.shenji.aikeyboard.engine.analyzer.SegmentType
import com.shenji.aikeyboard.model.*
import io.realm.kotlin.ext.query
import timber.log.Timber

/**
 * 精确匹配层
 * 
 * 负责处理完全匹配的候选词生成
 * 包括拼音精确匹配、首字母精确匹配等
 */
class ExactMatchLayer : GenerationLayer {
    
    override suspend fun generate(analysis: InputAnalysis, limit: Int): List<Candidate> {
        val startTime = System.currentTimeMillis()
        val candidates = mutableListOf<Candidate>()
        
        try {
            when (analysis.mode) {
                InputMode.SINGLE_LETTER -> {
                    candidates.addAll(generateSingleLetterExact(analysis, limit))
                }
                
                InputMode.PURE_ACRONYM -> {
                    candidates.addAll(generateAcronymExact(analysis, limit))
                }
                
                InputMode.PURE_PINYIN -> {
                    candidates.addAll(generatePinyinExact(analysis, limit))
                }
                
                InputMode.SENTENCE_INPUT -> {
                    candidates.addAll(generateSentenceExact(analysis, limit))
                }
                
                InputMode.ACRONYM_PINYIN_MIX,
                InputMode.PINYIN_ACRONYM_MIX -> {
                    candidates.addAll(generateMixedExact(analysis, limit))
                }
                
                else -> {
                    // 其他模式尝试通用精确匹配
                    candidates.addAll(generateGenericExact(analysis, limit))
                }
            }
            
            val endTime = System.currentTimeMillis()
            Timber.d("精确匹配层完成: ${candidates.size}个候选词 (耗时: ${endTime - startTime}ms)")
            
            return candidates.take(limit)
            
        } catch (e: Exception) {
            Timber.e(e, "精确匹配层异常: ${analysis.input}")
            return emptyList()
        }
    }
    
    /**
     * 单字母精确匹配
     */
    private suspend fun generateSingleLetterExact(analysis: InputAnalysis, limit: Int): List<Candidate> {
        val realm = ShenjiApplication.realm
        val input = analysis.input
        
        // 查询以该字母开头的单字
        val entries = realm.query<Entry>(
            "initialLetters == $0 AND type == 'chars'", 
            input
        ).limit(limit).find()
        
        return entries.map { entry ->
            CandidateBuilder()
                .word(entry.word)
                .pinyin(entry.pinyin)
                .initialLetters(entry.initialLetters)
                .frequency(entry.frequency)
                .type(entry.type)
                .weight(calculateExactWeight(entry, analysis))
                .source(CandidateSource(
                    generator = GeneratorType.EXACT_MATCH,
                    matchType = MatchType.EXACT,
                    layer = 1,
                    confidence = 1.0f
                ))
                .build()
        }
    }
    
    /**
     * 缩写精确匹配
     */
    private suspend fun generateAcronymExact(analysis: InputAnalysis, limit: Int): List<Candidate> {
        val realm = ShenjiApplication.realm
        val input = analysis.input
        
        // 查询首字母完全匹配的词条
        val entries = realm.query<Entry>(
            "initialLetters == $0", 
            input
        ).limit(limit * 2).find()
        
        return entries.map { entry ->
            CandidateBuilder()
                .word(entry.word)
                .pinyin(entry.pinyin)
                .initialLetters(entry.initialLetters)
                .frequency(entry.frequency)
                .type(entry.type)
                .weight(calculateExactWeight(entry, analysis))
                .source(CandidateSource(
                    generator = GeneratorType.EXACT_MATCH,
                    matchType = MatchType.ACRONYM,
                    layer = 1,
                    confidence = 0.95f
                ))
                .build()
        }.sortedByDescending { it.finalWeight }.take(limit)
    }
    
    /**
     * 拼音精确匹配
     */
    private suspend fun generatePinyinExact(analysis: InputAnalysis, limit: Int): List<Candidate> {
        val realm = ShenjiApplication.realm
        val input = analysis.input
        
        // 尝试不同的拼音格式匹配
        val candidates = mutableListOf<Candidate>()
        
        // 1. 直接匹配（无空格）
        val directEntries = realm.query<Entry>(
            "pinyin == $0", 
            input
        ).limit(limit).find()
        
        candidates.addAll(directEntries.map { entry ->
            CandidateBuilder()
                .word(entry.word)
                .pinyin(entry.pinyin)
                .initialLetters(entry.initialLetters)
                .frequency(entry.frequency)
                .type(entry.type)
                .weight(calculateExactWeight(entry, analysis))
                .source(CandidateSource(
                    generator = GeneratorType.EXACT_MATCH,
                    matchType = MatchType.EXACT,
                    layer = 1,
                    confidence = 1.0f
                ))
                .build()
        })
        
        // 2. 拼音拆分后匹配（带空格）
        if (analysis.syllableStructure.canSplit) {
            val spacedPinyin = analysis.syllableStructure.syllables.joinToString(" ")
            val spacedEntries = realm.query<Entry>(
                "pinyin == $0", 
                spacedPinyin
            ).limit(limit).find()
            
            candidates.addAll(spacedEntries.map { entry ->
                CandidateBuilder()
                    .word(entry.word)
                    .pinyin(entry.pinyin)
                    .initialLetters(entry.initialLetters)
                    .frequency(entry.frequency)
                    .type(entry.type)
                    .weight(calculateExactWeight(entry, analysis))
                    .source(CandidateSource(
                        generator = GeneratorType.EXACT_MATCH,
                        matchType = MatchType.EXACT,
                        layer = 1,
                        confidence = 0.98f
                    ))
                    .build()
            })
        }
        
        return candidates.distinctBy { it.word }.sortedByDescending { it.finalWeight }.take(limit)
    }
    
    /**
     * 句子精确匹配
     */
    private suspend fun generateSentenceExact(analysis: InputAnalysis, limit: Int): List<Candidate> {
        val realm = ShenjiApplication.realm
        val candidates = mutableListOf<Candidate>()
        
        // 尝试完整句子匹配
        if (analysis.syllableStructure.canSplit) {
            val spacedPinyin = analysis.syllableStructure.syllables.joinToString(" ")
            val entries = realm.query<Entry>(
                "pinyin == $0 AND word.@size >= 3", 
                spacedPinyin
            ).limit(limit).find()
            
            candidates.addAll(entries.map { entry ->
                CandidateBuilder()
                    .word(entry.word)
                    .pinyin(entry.pinyin)
                    .initialLetters(entry.initialLetters)
                    .frequency(entry.frequency)
                    .type(entry.type)
                    .weight(calculateExactWeight(entry, analysis))
                    .source(CandidateSource(
                        generator = GeneratorType.EXACT_MATCH,
                        matchType = MatchType.EXACT,
                        layer = 1,
                        confidence = 1.0f
                    ))
                    .build()
            })
        }
        
        return candidates.take(limit)
    }
    
    /**
     * 混合模式精确匹配
     */
    private suspend fun generateMixedExact(analysis: InputAnalysis, limit: Int): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        
        // 分别处理各个分段
        val syllableSegments = analysis.syllableSegments
        val acronymSegments = analysis.acronymSegments
        
        if (syllableSegments.isNotEmpty() && acronymSegments.isNotEmpty()) {
            // 尝试组合匹配
            candidates.addAll(generateCombinedMatch(syllableSegments, acronymSegments, analysis, limit))
        }
        
        return candidates.take(limit)
    }
    
    /**
     * 通用精确匹配
     */
    private suspend fun generateGenericExact(analysis: InputAnalysis, limit: Int): List<Candidate> {
        val realm = ShenjiApplication.realm
        val input = analysis.input
        
        // 尝试多种匹配方式
        val candidates = mutableListOf<Candidate>()
        
        // 1. 拼音匹配
        val pinyinEntries = realm.query<Entry>(
            "pinyin == $0", 
            input
        ).limit(limit / 2).find()
        
        candidates.addAll(pinyinEntries.map { entry ->
            CandidateBuilder()
                .word(entry.word)
                .pinyin(entry.pinyin)
                .initialLetters(entry.initialLetters)
                .frequency(entry.frequency)
                .type(entry.type)
                .weight(calculateExactWeight(entry, analysis))
                .source(CandidateSource(
                    generator = GeneratorType.EXACT_MATCH,
                    matchType = MatchType.EXACT,
                    layer = 1,
                    confidence = 0.9f
                ))
                .build()
        })
        
        // 2. 首字母匹配
        val acronymEntries = realm.query<Entry>(
            "initialLetters == $0", 
            input
        ).limit(limit / 2).find()
        
        candidates.addAll(acronymEntries.map { entry ->
            CandidateBuilder()
                .word(entry.word)
                .pinyin(entry.pinyin)
                .initialLetters(entry.initialLetters)
                .frequency(entry.frequency)
                .type(entry.type)
                .weight(calculateExactWeight(entry, analysis))
                .source(CandidateSource(
                    generator = GeneratorType.EXACT_MATCH,
                    matchType = MatchType.ACRONYM,
                    layer = 1,
                    confidence = 0.8f
                ))
                .build()
        })
        
        return candidates.distinctBy { it.word }.sortedByDescending { it.finalWeight }.take(limit)
    }
    
    /**
     * 组合匹配
     */
    private suspend fun generateCombinedMatch(
        syllableSegments: List<com.shenji.aikeyboard.engine.analyzer.InputSegment>,
        acronymSegments: List<com.shenji.aikeyboard.engine.analyzer.InputSegment>,
        analysis: InputAnalysis,
        limit: Int
    ): List<Candidate> {
        // 这里可以实现复杂的组合匹配逻辑
        // 暂时返回空列表，后续可以扩展
        return emptyList()
    }
    
    /**
     * 计算精确匹配权重
     */
    private fun calculateExactWeight(entry: Entry, analysis: InputAnalysis): CandidateWeight {
        val baseFreq = CandidateWeight.normalizeFrequency(entry.frequency)
        
        // 精确匹配的匹配精度很高
        val matchAccuracy = when (analysis.mode) {
            InputMode.SINGLE_LETTER -> 1.0f
            InputMode.PURE_PINYIN -> 0.95f
            InputMode.PURE_ACRONYM -> 0.9f
            else -> 0.85f
        }
        
        // 输入效率：越短的输入产生越长的词，效率越高
        val inputEfficiency = if (analysis.input.isNotEmpty()) {
            (entry.word.length.toFloat() / analysis.input.length).coerceAtMost(2.0f) / 2.0f
        } else 0.5f
        
        return CandidateWeight(
            baseFrequency = baseFreq,
            matchAccuracy = matchAccuracy,
            userPreference = 0.0f, // 初始无用户偏好
            contextRelevance = 0.0f, // 精确匹配层不考虑上下文
            inputEfficiency = inputEfficiency,
            temporalFactor = 0.5f // 默认时间因子
        )
    }
} 