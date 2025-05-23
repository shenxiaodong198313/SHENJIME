package com.shenji.aikeyboard.engine.generator.layers

import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.engine.analyzer.InputAnalysis
import com.shenji.aikeyboard.engine.analyzer.InputMode
import com.shenji.aikeyboard.model.*
import io.realm.kotlin.ext.query
import timber.log.Timber

/**
 * 前缀匹配层
 * 
 * 负责处理前缀匹配的候选词生成
 * 包括拼音前缀匹配、首字母前缀匹配等
 */
class PrefixMatchLayer : GenerationLayer {
    
    override suspend fun generate(analysis: InputAnalysis, limit: Int): List<Candidate> {
        val startTime = System.currentTimeMillis()
        val candidates = mutableListOf<Candidate>()
        
        try {
            when (analysis.mode) {
                InputMode.PURE_PINYIN,
                InputMode.PARTIAL_PINYIN -> {
                    candidates.addAll(generatePinyinPrefix(analysis, limit))
                }
                
                InputMode.PURE_ACRONYM -> {
                    candidates.addAll(generateAcronymPrefix(analysis, limit))
                }
                
                InputMode.PROGRESSIVE_INPUT -> {
                    candidates.addAll(generateProgressivePrefix(analysis, limit))
                }
                
                else -> {
                    candidates.addAll(generateGenericPrefix(analysis, limit))
                }
            }
            
            val endTime = System.currentTimeMillis()
            Timber.d("前缀匹配层完成: ${candidates.size}个候选词 (耗时: ${endTime - startTime}ms)")
            
            return candidates.take(limit)
            
        } catch (e: Exception) {
            Timber.e(e, "前缀匹配层异常: ${analysis.input}")
            return emptyList()
        }
    }
    
    /**
     * 拼音前缀匹配
     */
    private suspend fun generatePinyinPrefix(analysis: InputAnalysis, limit: Int): List<Candidate> {
        val realm = ShenjiApplication.realm
        val input = analysis.input
        
        // 查询拼音以输入开头的词条
        val entries = realm.query<Entry>(
            "pinyin BEGINSWITH $0", 
            input
        ).limit(limit * 2).find()
        
        return entries.map { entry ->
            CandidateBuilder()
                .word(entry.word)
                .pinyin(entry.pinyin)
                .initialLetters(entry.initialLetters)
                .frequency(entry.frequency)
                .type(entry.type)
                .weight(calculatePrefixWeight(entry, analysis))
                .source(CandidateSource(
                    generator = GeneratorType.PREFIX_MATCH,
                    matchType = MatchType.PREFIX,
                    layer = 2,
                    confidence = 0.8f
                ))
                .build()
        }.sortedByDescending { it.finalWeight }.take(limit)
    }
    
    /**
     * 缩写前缀匹配
     */
    private suspend fun generateAcronymPrefix(analysis: InputAnalysis, limit: Int): List<Candidate> {
        val realm = ShenjiApplication.realm
        val input = analysis.input
        
        // 查询首字母以输入开头的词条
        val entries = realm.query<Entry>(
            "initialLetters BEGINSWITH $0", 
            input
        ).limit(limit * 2).find()
        
        return entries.map { entry ->
            CandidateBuilder()
                .word(entry.word)
                .pinyin(entry.pinyin)
                .initialLetters(entry.initialLetters)
                .frequency(entry.frequency)
                .type(entry.type)
                .weight(calculatePrefixWeight(entry, analysis))
                .source(CandidateSource(
                    generator = GeneratorType.PREFIX_MATCH,
                    matchType = MatchType.PREFIX,
                    layer = 2,
                    confidence = 0.75f
                ))
                .build()
        }.sortedByDescending { it.finalWeight }.take(limit)
    }
    
    /**
     * 渐进式前缀匹配
     */
    private suspend fun generateProgressivePrefix(analysis: InputAnalysis, limit: Int): List<Candidate> {
        val realm = ShenjiApplication.realm
        val input = analysis.input
        val candidates = mutableListOf<Candidate>()
        
        // 1. 拼音前缀匹配
        val pinyinEntries = realm.query<Entry>(
            "pinyin BEGINSWITH $0", 
            input
        ).limit(limit).find()
        
        candidates.addAll(pinyinEntries.map { entry ->
            CandidateBuilder()
                .word(entry.word)
                .pinyin(entry.pinyin)
                .initialLetters(entry.initialLetters)
                .frequency(entry.frequency)
                .type(entry.type)
                .weight(calculatePrefixWeight(entry, analysis))
                .source(CandidateSource(
                    generator = GeneratorType.PREFIX_MATCH,
                    matchType = MatchType.PREFIX,
                    layer = 2,
                    confidence = 0.7f
                ))
                .build()
        })
        
        // 2. 如果拼音匹配不足，尝试首字母匹配
        if (candidates.size < limit / 2) {
            val acronymEntries = realm.query<Entry>(
                "initialLetters BEGINSWITH $0", 
                input
            ).limit(limit - candidates.size).find()
            
            candidates.addAll(acronymEntries.map { entry ->
                CandidateBuilder()
                    .word(entry.word)
                    .pinyin(entry.pinyin)
                    .initialLetters(entry.initialLetters)
                    .frequency(entry.frequency)
                    .type(entry.type)
                    .weight(calculatePrefixWeight(entry, analysis))
                    .source(CandidateSource(
                        generator = GeneratorType.PREFIX_MATCH,
                        matchType = MatchType.PREFIX,
                        layer = 2,
                        confidence = 0.6f
                    ))
                    .build()
            })
        }
        
        return candidates.distinctBy { it.word }.sortedByDescending { it.finalWeight }.take(limit)
    }
    
    /**
     * 通用前缀匹配
     */
    private suspend fun generateGenericPrefix(analysis: InputAnalysis, limit: Int): List<Candidate> {
        val realm = ShenjiApplication.realm
        val input = analysis.input
        val candidates = mutableListOf<Candidate>()
        
        // 尝试拼音和首字母前缀匹配
        val pinyinEntries = realm.query<Entry>(
            "pinyin BEGINSWITH $0", 
            input
        ).limit(limit / 2).find()
        
        candidates.addAll(pinyinEntries.map { entry ->
            CandidateBuilder()
                .word(entry.word)
                .pinyin(entry.pinyin)
                .initialLetters(entry.initialLetters)
                .frequency(entry.frequency)
                .type(entry.type)
                .weight(calculatePrefixWeight(entry, analysis))
                .source(CandidateSource(
                    generator = GeneratorType.PREFIX_MATCH,
                    matchType = MatchType.PREFIX,
                    layer = 2,
                    confidence = 0.7f
                ))
                .build()
        })
        
        val acronymEntries = realm.query<Entry>(
            "initialLetters BEGINSWITH $0", 
            input
        ).limit(limit / 2).find()
        
        candidates.addAll(acronymEntries.map { entry ->
            CandidateBuilder()
                .word(entry.word)
                .pinyin(entry.pinyin)
                .initialLetters(entry.initialLetters)
                .frequency(entry.frequency)
                .type(entry.type)
                .weight(calculatePrefixWeight(entry, analysis))
                .source(CandidateSource(
                    generator = GeneratorType.PREFIX_MATCH,
                    matchType = MatchType.PREFIX,
                    layer = 2,
                    confidence = 0.65f
                ))
                .build()
        })
        
        return candidates.distinctBy { it.word }.sortedByDescending { it.finalWeight }.take(limit)
    }
    
    /**
     * 计算前缀匹配权重
     */
    private fun calculatePrefixWeight(entry: Entry, analysis: InputAnalysis): CandidateWeight {
        val baseFreq = CandidateWeight.normalizeFrequency(entry.frequency)
        
        // 前缀匹配的精度中等
        val matchAccuracy = 0.7f
        
        // 计算前缀匹配度
        val prefixRatio = if (entry.pinyin.isNotEmpty()) {
            analysis.input.length.toFloat() / entry.pinyin.replace(" ", "").length
        } else 0.5f
        
        val inputEfficiency = prefixRatio.coerceAtMost(1.0f)
        
        return CandidateWeight(
            baseFrequency = baseFreq,
            matchAccuracy = matchAccuracy,
            userPreference = 0.0f,
            contextRelevance = 0.0f,
            inputEfficiency = inputEfficiency,
            temporalFactor = 0.5f
        )
    }
} 