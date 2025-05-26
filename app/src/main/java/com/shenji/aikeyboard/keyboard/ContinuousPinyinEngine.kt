package com.shenji.aikeyboard.keyboard

import android.util.LruCache
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.trie.TrieType
import com.shenji.aikeyboard.model.WordFrequency
import com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * 连续拼音处理引擎 - 性能优化�?
 * 
 * 核心优化�?
 * 1. 简化分词算法：减少动态规划复杂度，优先使用主要分词结�?
 * 2. 智能查询策略：减少重复查询，快速返回最佳结�?
 * 3. 激进缓存机制：增大缓存容量，延长缓存时�?
 * 4. 减少日志输出：仅保留关键日志，提升性能
 * 5. 优化组合算法：限制组合数量，避免指数级增�?
 */
class ContinuousPinyinEngine private constructor() {
    
    private val trieManager = TrieManager.instance
    
    // 🚀 性能优化：增大缓存容�?
    private val continuousCache = LruCache<String, List<List<WordFrequency>>>(200) // 50 -> 200
    private val segmentationCache = LruCache<String, List<List<String>>>(300)      // 100 -> 300
    
    // 性能统计
    private val queryCount = AtomicLong(0)
    private val cacheHits = AtomicLong(0)
    private val segmentationTime = AtomicLong(0)
    private val queryTime = AtomicLong(0)
    
    companion object {
        @Volatile
        private var INSTANCE: ContinuousPinyinEngine? = null
        
        fun getInstance(): ContinuousPinyinEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ContinuousPinyinEngine().also { INSTANCE = it }
            }
        }
        
        // 🚀 性能优化：调整限制参�?
        private const val MAX_INPUT_LENGTH = 25        // 30 -> 25，减少处理长�?
        private const val MAX_SEGMENT_COUNT = 6        // 8 -> 6，减少分段数�?
        private const val MAX_SEGMENTATION_SCHEMES = 3 // 5 -> 3，减少分词方案数
        private const val MIN_SEGMENT_LENGTH = 1       
        private const val MAX_SEGMENT_LENGTH = 5       // 6 -> 5，减少最大分段长�?
        private const val MAX_COMBINATIONS = 5         // 🚀 优化：严格限制组合数量到5�?
    }
    
    /**
     * 连续拼音查询结果
     */
    data class ContinuousQueryResult(
        val segmentationSchemes: List<List<String>>,  
        val candidateGroups: List<List<WordFrequency>>, 
        val bestCombinations: List<WordFrequency>,    
        val queryTime: Long,                          
        val cacheHit: Boolean                         
    )
    
    /**
     * 主要查询接口：处理连续拼音输�?- 性能优化�?
     */
    suspend fun queryContinuous(input: String, limit: Int = 20): ContinuousQueryResult = withContext(Dispatchers.IO) {
        if (input.isBlank()) {
            return@withContext ContinuousQueryResult(
                emptyList(), emptyList(), emptyList(), 0, false
            )
        }
        
        val cleanInput = input.trim().lowercase()
        queryCount.incrementAndGet()
        
        // 🚀 检查是否被取消
        yield()
        
        // 🚀 优化：更激进的缓存策略
        val cacheKey = "${cleanInput}_$limit"
        continuousCache.get(cacheKey)?.let { cached ->
            cacheHits.incrementAndGet()
            return@withContext ContinuousQueryResult(
                emptyList(), cached, cached.flatten().distinctBy { it.word }.take(limit), 0, true
            )
        }
        
        val startTime = System.currentTimeMillis()
        
        // 🚀 优化：更严格的输入长度检�?
        if (cleanInput.length > MAX_INPUT_LENGTH) {
            yield() // 检查是否被取消
            val fallbackResult = fallbackToNormalQueryWithCancellation(cleanInput, limit)
            return@withContext ContinuousQueryResult(
                emptyList(), listOf(fallbackResult), fallbackResult, 
                System.currentTimeMillis() - startTime, false
            )
        }
        
        try {
            // 🚀 检查是否被取消
            yield()
            
            // 1. 🚀 优化：简化分词算�?
            val segmentationSchemes = generateOptimizedSegmentationSchemesWithCancellation(cleanInput)
            
            // 🚀 检查是否被取消
            yield()
            
            // 2. 🚀 优化：智能查询策�?
            val candidateGroups = performOptimizedLayeredQueryWithCancellation(segmentationSchemes, limit)
            
            // 🚀 检查是否被取消
            yield()
            
            // 3. 🚀 优化：快速生成最佳组�?
            val bestCombinations = generateOptimizedBestCombinations(candidateGroups, limit)
            
            val totalTime = System.currentTimeMillis() - startTime
            
            // 缓存结果
            continuousCache.put(cacheKey, candidateGroups)
            
            val result = ContinuousQueryResult(
                segmentationSchemes = segmentationSchemes,
                candidateGroups = candidateGroups,
                bestCombinations = bestCombinations,
                queryTime = totalTime,
                cacheHit = false
            )
            
            // 🚀 优化：减少日志输出，仅保留关键信�?
            if (totalTime > 100) { // 仅当耗时超过100ms时才记录
                Timber.w("连续拼音查询耗时较长: '$cleanInput' -> ${bestCombinations.size}个结�?(${totalTime}ms)")
            }
            
            return@withContext result
            
        } catch (e: CancellationException) {
            Timber.d("🚫 连续拼音查询被取�? '$cleanInput'")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "连续拼音查询失败: '$cleanInput'")
            val fallbackResult = fallbackToNormalQueryWithCancellation(cleanInput, limit)
            return@withContext ContinuousQueryResult(
                emptyList(), listOf(fallbackResult), fallbackResult,
                System.currentTimeMillis() - startTime, false
            )
        }
    }
    
    /**
     * 🚀 优化：简化分词方案生�?
     * 优先使用主要分词结果，减少替代方案的生成
     */
    private fun generateOptimizedSegmentationSchemes(input: String): List<List<String>> {
        // 检查分词缓�?
        segmentationCache.get(input)?.let { cached ->
            return cached
        }
        
        val schemes = mutableListOf<List<String>>()
        
        try {
            // 1. 使用统一拼音分割器的主要分词结果
            val primarySegments = UnifiedPinyinSplitter.split(input)
            if (primarySegments.isNotEmpty() && primarySegments.size <= MAX_SEGMENT_COUNT) {
                schemes.add(primarySegments)
            }
            
            // 2. 🚀 优化：仅在主要分词结果不理想时才生成替代方案
            if (schemes.isEmpty() || primarySegments.size > 4) {
                val alternativeSchemes = generateSimpleAlternativeSegmentations(input)
                schemes.addAll(alternativeSchemes)
            }
            
            // 3. 限制方案数量
            val limitedSchemes = schemes.take(MAX_SEGMENTATION_SCHEMES)
            
            // 缓存结果
            segmentationCache.put(input, limitedSchemes)
            
            return limitedSchemes
            
        } catch (e: Exception) {
            Timber.e(e, "分词方案生成失败: '$input'")
            // 回退到简单分�?
            val fallbackSegments = listOf(listOf(input))
            segmentationCache.put(input, fallbackSegments)
            return fallbackSegments
        }
    }
    
    /**
     * 🚀 优化：简化替代分词方案生�?
     * 减少动态规划复杂度，使用更简单的分割策略
     */
    private fun generateSimpleAlternativeSegmentations(input: String): List<List<String>> {
        val alternatives = mutableListOf<List<String>>()
        
        // 🚀 优化：使用简单的滑动窗口分割，而不是复杂的动态规�?
        val validSyllables = getCommonSyllables() // 使用更小的常用音节集�?
        
        // 尝试2-3种简单的分割方式
        for (minSegmentLength in 2..3) {
            val segments = mutableListOf<String>()
            var pos = 0
            
            while (pos < input.length) {
                var found = false
                
                // 最长匹配优先，但限制最大长�?
                for (len in minOf(MAX_SEGMENT_LENGTH, input.length - pos) downTo minSegmentLength) {
                    val segment = input.substring(pos, pos + len)
                    if (validSyllables.contains(segment)) {
                        segments.add(segment)
                        pos += len
                        found = true
                        break
                    }
                }
                
                if (!found) {
                    // 添加单个字符
                    segments.add(input.substring(pos, pos + 1))
                    pos++
                }
            }
            
            if (segments.size <= MAX_SEGMENT_COUNT && segments.size >= 2) {
                alternatives.add(segments)
            }
        }
        
        return alternatives.distinctBy { it.joinToString("|") }.take(2) // 最�?种替代方�?
    }
    
    /**
     * 🚀 优化：智能分层查询策�?
     * 减少重复查询，快速返回最佳结�?
     */
    private suspend fun performOptimizedLayeredQuery(
        segmentationSchemes: List<List<String>>, 
        limit: Int
    ): List<List<WordFrequency>> {
        val candidateGroups = mutableListOf<List<WordFrequency>>()
        
        for ((index, segments) in segmentationSchemes.withIndex()) {
            val groupCandidates = mutableListOf<WordFrequency>()
            
            // 🚀 优化：智能查询策略选择
            when {
                segments.size <= 2 -> {
                    // 短分段：优先完整匹配和基础查询
                    val fullPinyin = segments.joinToString("")
                    val fullMatches = queryFullMatch(fullPinyin, 5)
                    groupCandidates.addAll(fullMatches)
                    
                    if (groupCandidates.size < limit / 2) {
                        val segmentMatches = queryOptimizedSegmentMatches(segments, limit - groupCandidates.size)
                        groupCandidates.addAll(segmentMatches)
                    }
                }
                segments.size <= 4 -> {
                    // 中等分段：优先分段匹�?
                    val segmentMatches = queryOptimizedSegmentMatches(segments, limit)
                    groupCandidates.addAll(segmentMatches)
                    
                    if (groupCandidates.size < limit / 3) {
                        val singleCharCombinations = queryOptimizedSingleCharCombinations(segments, limit - groupCandidates.size)
                        groupCandidates.addAll(singleCharCombinations)
                    }
                }
                else -> {
                    // 长分段：仅单字组�?
                    val singleCharCombinations = queryOptimizedSingleCharCombinations(segments, limit)
                    groupCandidates.addAll(singleCharCombinations)
                }
            }
            
            candidateGroups.add(groupCandidates.distinctBy { it.word })
            
            // 🚀 优化：如果已经有足够的结果，提前退�?
            if (candidateGroups.flatten().distinctBy { it.word }.size >= limit) {
                break
            }
        }
        
        return candidateGroups
    }
    
    /**
     * 完整匹配查询
     */
    private suspend fun queryFullMatch(pinyin: String, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        // 🚀 优化：优先查询最可能有结果的Trie
        if (trieManager.isTrieLoaded(TrieType.BASE)) {
            val baseResults = trieManager.searchByPrefix(TrieType.BASE, pinyin, limit)
            results.addAll(baseResults)
        }
        
        if (results.size < limit && trieManager.isTrieLoaded(TrieType.CORRELATION)) {
            val correlationResults = trieManager.searchByPrefix(TrieType.CORRELATION, pinyin, limit - results.size)
            results.addAll(correlationResults)
        }
        
        return results.distinctBy { it.word }.sortedByDescending { it.frequency }
    }
    
    /**
     * 🚀 优化：基于高频词的智能分段匹配查�?
     */
    private suspend fun queryOptimizedSegmentMatches(segments: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        // 🚀 新策略：智能词组识别和高频词优先组合
        val smartCombinations = generateSmartHighFrequencyCombinations(segments, limit)
        results.addAll(smartCombinations)
        
        return results
    }
    
    /**
     * 🚀 新增：基于高频词的智能组合生�?
     */
    private suspend fun generateSmartHighFrequencyCombinations(segments: List<String>, limit: Int): List<WordFrequency> {
        val combinations = mutableListOf<WordFrequency>()
        
        // 1. 🚀 智能词组识别：尝试识别常见词组组�?
        val wordGroupCombinations = tryWordGroupCombinations(segments, limit)
        combinations.addAll(wordGroupCombinations)
        
        // 2. 🚀 高频单字组合：如果词组组合不足，使用高频单字
        if (combinations.size < limit) {
            val highFreqCharCombinations = tryHighFrequencyCharCombinations(segments, limit - combinations.size)
            combinations.addAll(highFreqCharCombinations)
        }
        
        return combinations.distinctBy { it.word }
            .sortedByDescending { it.frequency }
            .take(minOf(limit, 5))
    }
    
    /**
     * 🚀 新增：尝试词组组合（�?知道"�?不是"等常见词组）
     */
    private suspend fun tryWordGroupCombinations(segments: List<String>, limit: Int): List<WordFrequency> {
        val combinations = mutableListOf<WordFrequency>()
        
        // 🚀 智能分段：尝试将相邻分段组合成常见词�?
        val optimizedSegments = optimizeSegmentsForWordGroups(segments)
        
        // 为优化后的分段查询最佳候�?
        val segmentCandidates = mutableListOf<WordFrequency>()
        
        for (segment in optimizedSegments) {
            val bestCandidate = findBestCandidateForSegment(segment)
            if (bestCandidate != null) {
                segmentCandidates.add(bestCandidate)
            }
        }
        
        // 生成组合
        if (segmentCandidates.isNotEmpty()) {
            val combinedWord = segmentCandidates.joinToString("") { it.word }
            val combinedFrequency = segmentCandidates.sumOf { it.frequency }
            
            if (isValidCombination(combinedWord, segmentCandidates.map { it.word })) {
                combinations.add(WordFrequency(combinedWord, combinedFrequency))
            }
        }
        
        return combinations
    }
    
    /**
     * 🚀 新增：优化分段以识别常见词组
     */
    private fun optimizeSegmentsForWordGroups(segments: List<String>): List<String> {
        val optimized = mutableListOf<String>()
        var i = 0
        
        while (i < segments.size) {
            var merged = false
            
            // 🚀 尝试合并相邻分段形成常见词组
            if (i < segments.size - 1) {
                val twoCharGroup = segments[i] + segments[i + 1]
                if (isCommonWordGroup(twoCharGroup)) {
                    optimized.add(twoCharGroup)
                    i += 2
                    merged = true
                }
            }
            
            // 🚀 尝试三字词组
            if (!merged && i < segments.size - 2) {
                val threeCharGroup = segments[i] + segments[i + 1] + segments[i + 2]
                if (isCommonWordGroup(threeCharGroup)) {
                    optimized.add(threeCharGroup)
                    i += 3
                    merged = true
                }
            }
            
            if (!merged) {
                optimized.add(segments[i])
                i++
            }
        }
        
        return optimized
    }
    
    /**
     * 🚀 新增：判断是否为常见词组
     */
    private fun isCommonWordGroup(pinyin: String): Boolean {
        // 🚀 常见词组拼音列表（可以根据需要扩展）
        val commonWordGroups = setOf(
            "zhidao", "zhishi", "shijian", "difang", "dongxi", "yijing", "keyi", "meiyou",
            "xianzai", "yihou", "yiqian", "zenme", "weishenme", "shenme", "nali", "zheli",
            "yiding", "yixia", "yige", "yidian", "yiyang", "yizhi", "yiban", "yisheng",
            "bushi", "bucuo", "buhao", "buyao", "buxing", "buzhidao", "buguoqu",
            "haode", "haokan", "haochi", "haoting", "haoxiang", "haoba",
            "xihuan", "xiang", "xiangxin", "xiangdao", "xiangqi",
            "juede", "juedin", "juexin", "juece",
            "gongzuo", "shenghuo", "xuexi", "wenti", "shiqing", "shihou",
            "pengyou", "jiaren", "laoshi", "xuesheng", "tongxue"
        )
        
        return commonWordGroups.contains(pinyin)
    }
    
    /**
     * 🚀 新增：为分段查找最佳候选词（优先高频词�?
     */
    private suspend fun findBestCandidateForSegment(segment: String): WordFrequency? {
        val candidates = mutableListOf<WordFrequency>()
        
        // 🚀 优先查询词组（如果是常见词组拼音�?
        if (segment.length >= 4 && trieManager.isTrieLoaded(TrieType.BASE)) {
            val wordResults = trieManager.searchByPrefix(TrieType.BASE, segment, 3)
                .filter { it.frequency > 200 } // 高频词组
            candidates.addAll(wordResults)
        }
        
        // 🚀 查询高频单字
        if (trieManager.isTrieLoaded(TrieType.CHARS)) {
            val charResults = trieManager.searchByPrefix(TrieType.CHARS, segment, 3)
                .filter { it.word.length == 1 && it.frequency > 500 } // 更高的频率要�?
            candidates.addAll(charResults)
        }
        
        // 🚀 补充查询一般词�?
        if (candidates.isEmpty() && trieManager.isTrieLoaded(TrieType.BASE)) {
            val wordResults = trieManager.searchByPrefix(TrieType.BASE, segment, 2)
                .filter { it.frequency > 100 }
            candidates.addAll(wordResults)
        }
        
        // 🚀 最后补充一般单�?
        if (candidates.isEmpty() && trieManager.isTrieLoaded(TrieType.CHARS)) {
            val charResults = trieManager.searchByPrefix(TrieType.CHARS, segment, 1)
                .filter { it.word.length == 1 && it.frequency > 50 }
            candidates.addAll(charResults)
        }
        
        return candidates.maxByOrNull { it.frequency }
    }
    
    /**
     * 🚀 新增：高频单字组合（作为备选方案）
     */
    private suspend fun tryHighFrequencyCharCombinations(segments: List<String>, limit: Int): List<WordFrequency> {
        val combinations = mutableListOf<WordFrequency>()
        
        if (!trieManager.isTrieLoaded(TrieType.CHARS)) {
            return combinations
        }
        
        // 为每个分段查询最高频单字
        val highFreqChars = mutableListOf<WordFrequency>()
        
        for (segment in segments) {
            val charResults = trieManager.searchByPrefix(TrieType.CHARS, segment, 1)
                .filter { it.word.length == 1 && it.frequency > 300 } // 高频率要�?
            
            if (charResults.isNotEmpty()) {
                highFreqChars.add(charResults.first())
            }
        }
        
        // 生成组合
        if (highFreqChars.size >= 2) {
            val combinedWord = highFreqChars.joinToString("") { it.word }
            val combinedFrequency = highFreqChars.sumOf { it.frequency }
            
            if (isValidCombination(combinedWord, highFreqChars.map { it.word })) {
                combinations.add(WordFrequency(combinedWord, combinedFrequency))
            }
        }
        
        return combinations
    }
    
    /**
     * 🚀 优化：单字组合查�?
     */
    private suspend fun queryOptimizedSingleCharCombinations(segments: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        if (!trieManager.isTrieLoaded(TrieType.CHARS)) {
            return results
        }
        
        // 为每个分段查询最佳单�?
        val singleChars = mutableListOf<String>()
        
        for (segment in segments) {
            val charResults = trieManager.searchByPrefix(TrieType.CHARS, segment, 1)
                .filter { it.word.length == 1 }
            
            if (charResults.isNotEmpty()) {
                singleChars.add(charResults.first().word)
            } else {
                // 如果没有找到，跳过这个分�?
                continue
            }
        }
        
        // 组合成词�?
        if (singleChars.size >= 2) { // 至少要有2个字符才组合
            val combinedWord = singleChars.joinToString("")
            val frequency = calculateOptimizedCombinationFrequency(singleChars)
            results.add(WordFrequency(combinedWord, frequency))
        }
        
        return results
    }
    
    /**
     * 🚀 优化：生成高质量词语组合（严格限制到5个）
     */
    private fun generateHighQualityWordCombinations(
        segmentCandidates: List<List<WordFrequency>>, 
        limit: Int
    ): List<WordFrequency> {
        val combinations = mutableListOf<WordFrequency>()
        
        if (segmentCandidates.isEmpty() || segmentCandidates.any { it.isEmpty() }) {
            return combinations
        }
        
        // 🚀 优化：严格限制组合数量到5�?
        val maxCombinations = minOf(limit, 5)
        
        // 🚀 优化：由于每个分段最�?个候选，直接生成唯一组合
        val singleCombination = mutableListOf<String>()
        var totalFrequency = 0
        
        for (candidates in segmentCandidates) {
            if (candidates.isNotEmpty()) {
                val bestCandidate = candidates.first()
                singleCombination.add(bestCandidate.word)
                totalFrequency += bestCandidate.frequency
            }
        }
        
        // 生成主要组合
        if (singleCombination.isNotEmpty()) {
            val combinedWord = singleCombination.joinToString("")
            
            // 🚀 优化：质量过�?- 避免生成无意义的组合
            if (isValidCombination(combinedWord, singleCombination)) {
                combinations.add(WordFrequency(combinedWord, totalFrequency))
            }
        }
        
        // 🚀 优化：如果需要更多候选，生成部分组合（前缀组合�?
        if (combinations.size < maxCombinations && singleCombination.size > 2) {
            // 生成�?个字符的组合
            if (singleCombination.size >= 2) {
                val partialWord = singleCombination.take(2).joinToString("")
                val partialFrequency = (totalFrequency * 0.8).toInt()
                if (isValidCombination(partialWord, singleCombination.take(2))) {
                    combinations.add(WordFrequency(partialWord, partialFrequency))
                }
            }
            
            // 生成�?个字符的组合
            if (singleCombination.size >= 3) {
                val partialWord = singleCombination.take(3).joinToString("")
                val partialFrequency = (totalFrequency * 0.9).toInt()
                if (isValidCombination(partialWord, singleCombination.take(3))) {
                    combinations.add(WordFrequency(partialWord, partialFrequency))
                }
            }
        }
        
        return combinations.distinctBy { it.word }
            .sortedByDescending { it.frequency }
            .take(maxCombinations)
    }
    
    /**
     * 🚀 新增：验证组合是否有意义
     */
    private fun isValidCombination(combinedWord: String, components: List<String>): Boolean {
        // 🚀 优化：基本质量检�?
        if (combinedWord.length < 2) return false
        
        // 避免重复字符过多的组�?
        val uniqueChars = combinedWord.toSet().size
        if (uniqueChars < combinedWord.length * 0.5) return false
        
        // 避免明显无意义的组合（可以根据需要扩展）
        val invalidPatterns = setOf("支到", "只到", "支道", "只道", "咋hi", "哈i")
        if (invalidPatterns.any { combinedWord.contains(it) }) return false
        
        return true
    }
    
    /**
     * 🚀 优化：快速生成最佳组�?
     */
    private fun generateOptimizedBestCombinations(
        candidateGroups: List<List<WordFrequency>>, 
        limit: Int
    ): List<WordFrequency> {
        val allCandidates = candidateGroups.flatten()
        
        return allCandidates
            .distinctBy { it.word }
            .sortedWith(compareBy<WordFrequency> { it.word.length }.thenByDescending { it.frequency })
            .take(limit)
    }
    
    /**
     * 🚀 新增：支持取消检查的分词方案生成
     */
    private suspend fun generateOptimizedSegmentationSchemesWithCancellation(input: String): List<List<String>> {
        // 检查分词缓�?
        segmentationCache.get(input)?.let { cached ->
            return cached
        }
        
        val schemes = mutableListOf<List<String>>()
        
        try {
            yield() // 检查是否被取消
            
            // 1. 使用统一拼音分割器的主要分词结果
            val primarySegments = UnifiedPinyinSplitter.split(input)
            if (primarySegments.isNotEmpty() && primarySegments.size <= MAX_SEGMENT_COUNT) {
                schemes.add(primarySegments)
            }
            
            yield() // 检查是否被取消
            
            // 2. 🚀 优化：仅在主要分词结果不理想时才生成替代方案
            if (schemes.isEmpty() || primarySegments.size > 4) {
                val alternativeSchemes = generateSimpleAlternativeSegmentationsWithCancellation(input)
                schemes.addAll(alternativeSchemes)
            }
            
            // 3. 限制方案数量
            val limitedSchemes = schemes.take(MAX_SEGMENTATION_SCHEMES)
            
            // 缓存结果
            segmentationCache.put(input, limitedSchemes)
            
            return limitedSchemes
            
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "分词方案生成失败: '$input'")
            // 回退到简单分�?
            val fallbackSegments = listOf(listOf(input))
            segmentationCache.put(input, fallbackSegments)
            return fallbackSegments
        }
    }
    
    /**
     * 🚀 新增：支持取消检查的替代分词方案生成
     */
    private suspend fun generateSimpleAlternativeSegmentationsWithCancellation(input: String): List<List<String>> {
        val alternatives = mutableListOf<List<String>>()
        
        // 🚀 优化：使用简单的滑动窗口分割，而不是复杂的动态规�?
        val validSyllables = getCommonSyllables() // 使用更小的常用音节集�?
        
        // 尝试2-3种简单的分割方式
        for (minSegmentLength in 2..3) {
            yield() // 检查是否被取消
            
            val segments = mutableListOf<String>()
            var pos = 0
            
            while (pos < input.length) {
                yield() // 检查是否被取消
                
                var found = false
                
                // 最长匹配优先，但限制最大长�?
                for (len in minOf(MAX_SEGMENT_LENGTH, input.length - pos) downTo minSegmentLength) {
                    val segment = input.substring(pos, pos + len)
                    if (validSyllables.contains(segment)) {
                        segments.add(segment)
                        pos += len
                        found = true
                        break
                    }
                }
                
                if (!found) {
                    // 添加单个字符
                    segments.add(input.substring(pos, pos + 1))
                    pos++
                }
            }
            
            if (segments.size <= MAX_SEGMENT_COUNT && segments.size >= 2) {
                alternatives.add(segments)
            }
        }
        
        return alternatives.distinctBy { it.joinToString("|") }.take(2) // 最�?种替代方�?
    }
    
    /**
     * 🚀 新增：支持取消检查的智能分层查询策略
     */
    private suspend fun performOptimizedLayeredQueryWithCancellation(
        segmentationSchemes: List<List<String>>, 
        limit: Int
    ): List<List<WordFrequency>> {
        val candidateGroups = mutableListOf<List<WordFrequency>>()
        
        for ((index, segments) in segmentationSchemes.withIndex()) {
            yield() // 检查是否被取消
            
            val groupCandidates = mutableListOf<WordFrequency>()
            
            // 🚀 优化：智能查询策略选择
            when {
                segments.size <= 2 -> {
                    // 短分段：优先完整匹配和基础查询
                    val fullPinyin = segments.joinToString("")
                    val fullMatches = queryFullMatchWithCancellation(fullPinyin, 5)
                    groupCandidates.addAll(fullMatches)
                    
                    if (groupCandidates.size < limit / 2) {
                        val segmentMatches = queryOptimizedSegmentMatchesWithCancellation(segments, limit - groupCandidates.size)
                        groupCandidates.addAll(segmentMatches)
                    }
                }
                segments.size <= 4 -> {
                    // 中等分段：优先分段匹�?
                    val segmentMatches = queryOptimizedSegmentMatchesWithCancellation(segments, limit)
                    groupCandidates.addAll(segmentMatches)
                    
                    if (groupCandidates.size < limit / 3) {
                        val singleCharCombinations = queryOptimizedSingleCharCombinationsWithCancellation(segments, limit - groupCandidates.size)
                        groupCandidates.addAll(singleCharCombinations)
                    }
                }
                else -> {
                    // 长分段：仅单字组�?
                    val singleCharCombinations = queryOptimizedSingleCharCombinationsWithCancellation(segments, limit)
                    groupCandidates.addAll(singleCharCombinations)
                }
            }
            
            candidateGroups.add(groupCandidates.distinctBy { it.word })
            
            // 🚀 优化：如果已经有足够的结果，提前退�?
            if (candidateGroups.flatten().distinctBy { it.word }.size >= limit) {
                break
            }
        }
        
        return candidateGroups
    }
    
    /**
     * 🚀 新增：支持取消检查的完整匹配查询
     */
    private suspend fun queryFullMatchWithCancellation(pinyin: String, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        yield() // 检查是否被取消
        
        // 🚀 优化：优先查询最可能有结果的Trie
        if (trieManager.isTrieLoaded(TrieType.BASE)) {
            val baseResults = trieManager.searchByPrefix(TrieType.BASE, pinyin, limit)
            results.addAll(baseResults)
        }
        
        yield() // 检查是否被取消
        
        if (results.size < limit && trieManager.isTrieLoaded(TrieType.CORRELATION)) {
            val correlationResults = trieManager.searchByPrefix(TrieType.CORRELATION, pinyin, limit - results.size)
            results.addAll(correlationResults)
        }
        
        return results.distinctBy { it.word }.sortedByDescending { it.frequency }
    }
    
    /**
     * 🚀 新增：支持取消检查的分段匹配查询
     */
    private suspend fun queryOptimizedSegmentMatchesWithCancellation(segments: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        yield() // 检查是否被取消
        
        // 🚀 新策略：智能词组识别和高频词优先组合
        val smartCombinations = generateSmartHighFrequencyCombinationsWithCancellation(segments, limit)
        results.addAll(smartCombinations)
        
        return results
    }
    
    /**
     * 🚀 新增：支持取消检查的智能高频组合生成
     */
    private suspend fun generateSmartHighFrequencyCombinationsWithCancellation(segments: List<String>, limit: Int): List<WordFrequency> {
        val combinations = mutableListOf<WordFrequency>()
        
        yield() // 检查是否被取消
        
        // 🚀 策略1：智能分段合�?- 识别常见词组
        val mergedSegments = smartMergeSegmentsWithCancellation(segments)
        
        yield() // 检查是否被取消
        
        // 🚀 策略2：为合并后的分段查找最佳候选词
        for (segment in mergedSegments) {
            yield() // 检查是否被取消
            
            val bestCandidate = findBestCandidateForSegmentWithCancellation(segment)
            if (bestCandidate != null) {
                combinations.add(bestCandidate)
            }
        }
        
        // 🚀 策略3：生成词组组�?
        if (combinations.size >= 2) {
            val combinedWord = combinations.joinToString("") { it.word }
            val combinedFrequency = combinations.sumOf { it.frequency }
            
            if (isValidCombination(combinedWord, combinations.map { it.word })) {
                return listOf(WordFrequency(combinedWord, combinedFrequency))
            }
        }
        
        // 🚀 策略4：如果组合失败，返回高频单字组合
        if (combinations.isEmpty()) {
            return generateHighFrequencySingleCharCombinationsWithCancellation(segments, limit)
        }
        
        return combinations.take(limit)
    }
    
    /**
     * 🚀 新增：支持取消检查的智能分段合并
     */
    private suspend fun smartMergeSegmentsWithCancellation(segments: List<String>): List<String> {
        val merged = mutableListOf<String>()
        var i = 0
        
        while (i < segments.size) {
            yield() // 检查是否被取消
            
            // 尝试合并相邻分段形成常见词组
            if (i < segments.size - 1) {
                val twoSegmentCombo = segments[i] + segments[i + 1]
                if (isCommonWordGroup(twoSegmentCombo)) {
                    merged.add(twoSegmentCombo)
                    i += 2
                    continue
                }
            }
            
            // 尝试合并三个分段
            if (i < segments.size - 2) {
                val threeSegmentCombo = segments[i] + segments[i + 1] + segments[i + 2]
                if (isCommonWordGroup(threeSegmentCombo)) {
                    merged.add(threeSegmentCombo)
                    i += 3
                    continue
                }
            }
            
            // 无法合并，保持原分段
            merged.add(segments[i])
            i++
        }
        
        return merged
    }
    
    /**
     * 🚀 新增：支持取消检查的分段候选词查找
     */
    private suspend fun findBestCandidateForSegmentWithCancellation(segment: String): WordFrequency? {
        val candidates = mutableListOf<WordFrequency>()
        
        yield() // 检查是否被取消
        
        // 🚀 优先查询词组（如果是常见词组拼音�?
        if (segment.length >= 4 && trieManager.isTrieLoaded(TrieType.BASE)) {
            val wordResults = trieManager.searchByPrefix(TrieType.BASE, segment, 3)
                .filter { it.frequency > 200 } // 高频词组
            candidates.addAll(wordResults)
        }
        
        yield() // 检查是否被取消
        
        // 🚀 查询高频单字
        if (trieManager.isTrieLoaded(TrieType.CHARS)) {
            val charResults = trieManager.searchByPrefix(TrieType.CHARS, segment, 3)
                .filter { it.word.length == 1 && it.frequency > 500 } // 更高的频率要�?
            candidates.addAll(charResults)
        }
        
        // 🚀 补充查询一般词�?
        if (candidates.isEmpty() && trieManager.isTrieLoaded(TrieType.BASE)) {
            val wordResults = trieManager.searchByPrefix(TrieType.BASE, segment, 2)
                .filter { it.frequency > 100 }
            candidates.addAll(wordResults)
        }
        
        // 🚀 最后补充一般单�?
        if (candidates.isEmpty() && trieManager.isTrieLoaded(TrieType.CHARS)) {
            val charResults = trieManager.searchByPrefix(TrieType.CHARS, segment, 1)
                .filter { it.word.length == 1 && it.frequency > 50 }
            candidates.addAll(charResults)
        }
        
        return candidates.maxByOrNull { it.frequency }
    }
    
    /**
     * 🚀 新增：支持取消检查的高频单字组合
     */
    private suspend fun generateHighFrequencySingleCharCombinationsWithCancellation(segments: List<String>, limit: Int): List<WordFrequency> {
        val combinations = mutableListOf<WordFrequency>()
        
        if (!trieManager.isTrieLoaded(TrieType.CHARS)) {
            return combinations
        }
        
        yield() // 检查是否被取消
        
        // 为每个分段查询最高频单字
        val highFreqChars = mutableListOf<WordFrequency>()
        
        for (segment in segments) {
            yield() // 检查是否被取消
            
            val charResults = trieManager.searchByPrefix(TrieType.CHARS, segment, 1)
                .filter { it.word.length == 1 && it.frequency > 300 } // 高频率要求
            
            if (charResults.isNotEmpty()) {
                highFreqChars.add(charResults.first())
            }
        }
        
        // 生成组合
        if (highFreqChars.size >= 2) {
            val combinedWord = highFreqChars.joinToString("") { it.word }
            val combinedFrequency = highFreqChars.sumOf { it.frequency }
            
            if (isValidCombination(combinedWord, highFreqChars.map { it.word })) {
                combinations.add(WordFrequency(combinedWord, combinedFrequency))
            }
        }
        
        return combinations
    }
    
    /**
     * 🚀 新增：支持取消检查的单字组合查询
     */
    private suspend fun queryOptimizedSingleCharCombinationsWithCancellation(segments: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        if (!trieManager.isTrieLoaded(TrieType.CHARS)) {
            return results
        }
        
        yield() // 检查是否被取消
        
        // 为每个分段查询最佳单字
        val singleChars = mutableListOf<String>()
        
        for (segment in segments) {
            yield() // 检查是否被取消
            
            val charResults = trieManager.searchByPrefix(TrieType.CHARS, segment, 1)
                .filter { it.word.length == 1 }
            
            if (charResults.isNotEmpty()) {
                singleChars.add(charResults.first().word)
            } else {
                // 如果没有找到，跳过这个分段
                continue
            }
        }
        
        // 组合成词语
        if (singleChars.size >= 2) { // 至少要有2个字符才组合
            val combinedWord = singleChars.joinToString("")
            val frequency = calculateOptimizedCombinationFrequency(singleChars)
            results.add(WordFrequency(combinedWord, frequency))
        }
        
        return results
    }
    
    /**
     * 🚀 新增：支持取消检查的回退查询
     */
    private suspend fun fallbackToNormalQueryWithCancellation(input: String, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        yield() // 检查是否被取消
        
        // 🚀 优化：简化回退查询
        val trieTypes = listOf(TrieType.BASE, TrieType.CHARS)
        
        for (trieType in trieTypes) {
            yield() // 检查是否被取消
            
            if (trieManager.isTrieLoaded(trieType) && results.size < limit) {
                val trieResults = trieManager.searchByPrefix(trieType, input, limit - results.size)
                results.addAll(trieResults)
            }
        }
        
        return results.distinctBy { it.word }.sortedByDescending { it.frequency }
    }
    
    /**
     * 回退到普通查询（保留原方法以兼容性）
     */
    private suspend fun fallbackToNormalQuery(input: String, limit: Int): List<WordFrequency> {
        return fallbackToNormalQueryWithCancellation(input, limit)
    }
    
    /**
     * 🚀 优化：计算组合频率
     */
    private fun calculateOptimizedCombinationFrequency(chars: List<String>): Int {
        // 简化频率计算
        return maxOf(50 - chars.size * 5, 1)
    }
    
    /**
     * 🚀 优化：获取常用拼音音节集合（减少集合大小）
     */
    private fun getCommonSyllables(): Set<String> {
        return setOf(
            // 最常用的音节（减少到核心音节）
            "a", "e", "o", "ai", "ei", "ao", "ou", "an", "en", "ang", "eng",
            "ba", "pa", "ma", "fa", "da", "ta", "na", "la", "ga", "ka", "ha", "za", "ca", "sa", "ya", "wa",
            "bai", "pai", "mai", "dai", "tai", "lai", "gai", "kai", "hai", "zai", "cai", "sai",
            "ban", "pan", "man", "fan", "dan", "tan", "nan", "lan", "gan", "kan", "han", "zan", "can", "san", "yan", "wan",
            "bang", "pang", "mang", "fang", "dang", "tang", "lang", "gang", "kang", "hang", "zang", "cang", "sang", "yang", "wang",
            "bao", "pao", "mao", "dao", "tao", "lao", "gao", "kao", "hao", "zao", "cao", "sao", "yao",
            "bei", "pei", "mei", "fei", "nei", "lei", "gei", "hei", "zei", "wei",
            "ben", "pen", "men", "fen", "den", "ten", "nen", "len", "gen", "ken", "hen", "zen", "cen", "sen", "wen",
            "beng", "peng", "meng", "feng", "deng", "teng", "neng", "leng", "geng", "keng", "heng", "zeng", "ceng", "seng", "weng",
            "bi", "pi", "mi", "di", "ti", "ni", "li", "ji", "qi", "xi", "zi", "ci", "si", "yi",
            "bian", "pian", "mian", "dian", "tian", "nian", "lian", "jian", "qian", "xian",
            "biao", "piao", "miao", "diao", "tiao", "niao", "liao", "jiao", "qiao", "xiao",
            "bie", "pie", "mie", "die", "tie", "nie", "lie", "jie", "qie", "xie",
            "bin", "pin", "min", "din", "tin", "nin", "lin", "jin", "qin", "xin", "yin",
            "bing", "ping", "ming", "ding", "ting", "ning", "ling", "jing", "qing", "xing", "ying",
            "bo", "po", "mo", "fo", "do", "to", "lo", "go", "ko", "ho", "zo", "co", "so", "wo",
            "bu", "pu", "mu", "fu", "du", "tu", "nu", "lu", "gu", "ku", "hu", "ju", "qu", "xu", "zu", "cu", "su", "wu",
            "chi", "zhi", "shi", "ri", "che", "zhe", "she", "re", "chu", "zhu", "shu", "ru",
            "chai", "zhai", "shai", "chan", "zhan", "shan", "ran", "chang", "zhang", "shang", "rang",
            "chao", "zhao", "shao", "rao", "chen", "zhen", "shen", "ren", "cheng", "zheng", "sheng", "reng",
            "chi", "zhi", "shi", "ri", "chong", "zhong", "shou", "rou", "chou", "zhou", "shou", "rou",
            "chua", "zhua", "shua", "rua", "chuai", "zhuai", "shuai", "chuan", "zhuan", "shuan", "ruan",
            "chuang", "zhuang", "shuang", "chui", "zhui", "shui", "rui", "chun", "zhun", "shun", "run",
            "chuo", "zhuo", "shuo", "ruo",
            "er", "nv", "lv"
        )
    }
    
    /**
     * 获取性能统计
     */
    fun getPerformanceStats(): String {
        val hitRate = if (queryCount.get() > 0) {
            (cacheHits.get() * 100.0 / queryCount.get()).toInt()
        } else 0
        
        val avgSegmentationTime = if (queryCount.get() > 0) {
            segmentationTime.get().toDouble() / queryCount.get()
        } else 0.0
        
        val avgQueryTime = if (queryCount.get() > 0) {
            queryTime.get().toDouble() / queryCount.get()
        } else 0.0
        
        return buildString {
            appendLine("📊 ContinuousPinyinEngine 性能统计 (优化版):")
            appendLine("查询总数: ${queryCount.get()}")
            appendLine("缓存命中: ${cacheHits.get()} (${hitRate}%)")
            appendLine("平均分词耗时: ${String.format("%.1f", avgSegmentationTime)}ms")
            appendLine("平均查询耗时: ${String.format("%.1f", avgQueryTime)}ms")
            appendLine("分词缓存: ${segmentationCache.size()}/300")
            appendLine("查询缓存: ${continuousCache.size()}/200")
        }
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        continuousCache.evictAll()
        segmentationCache.evictAll()
        Timber.d("🧹 连续拼音缓存已清理")
    }
    
    /**
     * 重置性能统计
     */
    fun resetPerformanceStats() {
        queryCount.set(0)
        cacheHits.set(0)
        segmentationTime.set(0)
        queryTime.set(0)
        Timber.d("🔄 连续拼音性能统计已重置")
    }
} 
