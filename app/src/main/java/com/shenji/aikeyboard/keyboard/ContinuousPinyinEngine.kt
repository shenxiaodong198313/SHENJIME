package com.shenji.aikeyboard.keyboard

import android.util.LruCache
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.trie.TrieType
import com.shenji.aikeyboard.model.WordFrequency
import com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * 连续拼音处理引擎 - 方案一实现
 * 
 * 核心功能：
 * 1. 动态分词算法：将连续拼音智能分割成多种可能的组合
 * 2. 分层查询策略：完整匹配 -> 分段匹配 -> 单字组合
 * 3. 性能优化：缓存机制、查询限制、智能回退
 * 4. 无损集成：不改变现有数据结构，完全兼容现有系统
 * 
 * 示例：
 * 输入："woshiyigenvhai" 
 * 分词：["wo shi yi ge nv hai", "wo shi yi gen v hai", "wo shi yi ge n v hai"]
 * 输出：[["我", "是", "一", "个", "女", "孩"], ["我是", "一个", "女孩"], ["我是一个女孩"]]
 */
class ContinuousPinyinEngine private constructor() {
    
    private val trieManager = TrieManager.instance
    
    // 连续拼音专用缓存
    private val continuousCache = LruCache<String, List<List<WordFrequency>>>(50)
    private val segmentationCache = LruCache<String, List<List<String>>>(100)
    
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
        
        // 性能优化常量
        private const val MAX_INPUT_LENGTH = 30        // 最大输入长度
        private const val MAX_SEGMENT_COUNT = 8        // 最大分段数量
        private const val MAX_SEGMENTATION_SCHEMES = 5 // 最大分词方案数
        private const val MIN_SEGMENT_LENGTH = 1       // 最小分段长度
        private const val MAX_SEGMENT_LENGTH = 6       // 最大分段长度
    }
    
    /**
     * 连续拼音查询结果
     */
    data class ContinuousQueryResult(
        val segmentationSchemes: List<List<String>>,  // 分词方案
        val candidateGroups: List<List<WordFrequency>>, // 候选词组
        val bestCombinations: List<WordFrequency>,    // 最佳组合
        val queryTime: Long,                          // 查询耗时
        val cacheHit: Boolean                         // 是否缓存命中
    )
    
    /**
     * 主要查询接口：处理连续拼音输入
     * 
     * @param input 连续拼音字符串，如 "woshiyigenvhai"
     * @param limit 返回结果数量限制
     * @return 分层候选词结果
     */
    suspend fun queryContinuous(input: String, limit: Int = 20): ContinuousQueryResult = withContext(Dispatchers.IO) {
        if (input.isBlank()) {
            return@withContext ContinuousQueryResult(
                emptyList(), emptyList(), emptyList(), 0, false
            )
        }
        
        val cleanInput = input.trim().lowercase()
        queryCount.incrementAndGet()
        
        // 检查缓存
        val cacheKey = "${cleanInput}_$limit"
        continuousCache.get(cacheKey)?.let { cached ->
            cacheHits.incrementAndGet()
            Timber.d("🚀 连续拼音缓存命中: '$cleanInput'")
            return@withContext ContinuousQueryResult(
                emptyList(), cached, cached.flatten(), 0, true
            )
        }
        
        val startTime = System.currentTimeMillis()
        
        // 输入长度检查
        if (cleanInput.length > MAX_INPUT_LENGTH) {
            Timber.w("⚠️ 输入过长(${cleanInput.length}>${MAX_INPUT_LENGTH})，回退到普通查询")
            val fallbackResult = fallbackToNormalQuery(cleanInput, limit)
            return@withContext ContinuousQueryResult(
                emptyList(), listOf(fallbackResult), fallbackResult, 
                System.currentTimeMillis() - startTime, false
            )
        }
        
        try {
            // 1. 动态分词
            val segmentationStartTime = System.currentTimeMillis()
            val segmentationSchemes = generateSegmentationSchemes(cleanInput)
            val segmentationEndTime = System.currentTimeMillis()
            segmentationTime.addAndGet(segmentationEndTime - segmentationStartTime)
            
            Timber.d("🔧 分词完成: '$cleanInput' -> ${segmentationSchemes.size}种方案")
            segmentationSchemes.forEachIndexed { index, scheme ->
                Timber.d("   方案${index + 1}: ${scheme.joinToString(" + ")}")
            }
            
            // 2. 分层查询
            val queryStartTime = System.currentTimeMillis()
            val candidateGroups = performLayeredQuery(segmentationSchemes, limit)
            val queryEndTime = System.currentTimeMillis()
            queryTime.addAndGet(queryEndTime - queryStartTime)
            
            // 3. 生成最佳组合
            val bestCombinations = generateBestCombinations(candidateGroups, limit)
            
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
            
            Timber.d("✅ 连续拼音查询完成: '$cleanInput' -> ${bestCombinations.size}个结果 (${totalTime}ms)")
            
            return@withContext result
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 连续拼音查询失败: '$cleanInput'")
            val fallbackResult = fallbackToNormalQuery(cleanInput, limit)
            return@withContext ContinuousQueryResult(
                emptyList(), listOf(fallbackResult), fallbackResult,
                System.currentTimeMillis() - startTime, false
            )
        }
    }
    
    /**
     * 生成分词方案
     * 使用动态规划算法生成多种可能的分词组合
     */
    private fun generateSegmentationSchemes(input: String): List<List<String>> {
        // 检查分词缓存
        segmentationCache.get(input)?.let { cached ->
            Timber.d("🚀 分词缓存命中: '$input'")
            return cached
        }
        
        val schemes = mutableListOf<List<String>>()
        
        try {
            // 1. 使用统一拼音分割器的主要分词结果
            val primarySegments = UnifiedPinyinSplitter.split(input)
            if (primarySegments.isNotEmpty() && primarySegments.size <= MAX_SEGMENT_COUNT) {
                schemes.add(primarySegments)
                Timber.d("🎯 主要分词: ${primarySegments.joinToString(" + ")}")
            }
            
            // 2. 生成替代分词方案
            val alternativeSchemes = generateAlternativeSegmentations(input)
            schemes.addAll(alternativeSchemes)
            
            // 3. 限制方案数量
            val limitedSchemes = schemes.take(MAX_SEGMENTATION_SCHEMES)
            
            // 缓存结果
            segmentationCache.put(input, limitedSchemes)
            
            Timber.d("🔧 分词方案生成完成: ${limitedSchemes.size}种方案")
            
            return limitedSchemes
            
        } catch (e: Exception) {
            Timber.e(e, "分词方案生成失败: '$input'")
            // 回退到简单分词
            val fallbackSegments = listOf(listOf(input))
            segmentationCache.put(input, fallbackSegments)
            return fallbackSegments
        }
    }
    
    /**
     * 生成替代分词方案
     * 通过调整分割点生成不同的分词组合
     */
    private fun generateAlternativeSegmentations(input: String): List<List<String>> {
        val alternatives = mutableListOf<List<String>>()
        
        // 基于有效拼音音节的动态规划分词
        val validSyllables = getValidSyllables()
        val dp = Array(input.length + 1) { mutableListOf<List<String>>() }
        dp[0].add(emptyList())
        
        for (i in 1..input.length) {
            for (j in 0 until i) {
                val segment = input.substring(j, i)
                if (segment.length <= MAX_SEGMENT_LENGTH && 
                    (validSyllables.contains(segment) || segment.length == 1)) {
                    
                    for (prevSegmentation in dp[j]) {
                        val newSegmentation = prevSegmentation + segment
                        if (newSegmentation.size <= MAX_SEGMENT_COUNT) {
                            dp[i].add(newSegmentation)
                        }
                    }
                }
            }
        }
        
        // 收集所有有效的分词方案
        val allSegmentations = dp[input.length]
            .filter { it.isNotEmpty() && it.size >= 2 } // 至少要有2个分段
            .distinctBy { it.joinToString("|") }
            .sortedBy { it.size } // 按分段数量排序，优先较少分段
        
        alternatives.addAll(allSegmentations.take(MAX_SEGMENTATION_SCHEMES - 1))
        
        Timber.d("🔄 生成${alternatives.size}种替代分词方案")
        
        return alternatives
    }
    
    /**
     * 分层查询策略
     * 第一层：完整匹配
     * 第二层：分段匹配
     * 第三层：单字组合
     */
    private suspend fun performLayeredQuery(
        segmentationSchemes: List<List<String>>, 
        limit: Int
    ): List<List<WordFrequency>> {
        val candidateGroups = mutableListOf<List<WordFrequency>>()
        
        for ((index, segments) in segmentationSchemes.withIndex()) {
            Timber.d("🔍 查询方案${index + 1}: ${segments.joinToString(" + ")}")
            
            val groupCandidates = mutableListOf<WordFrequency>()
            
            // 第一层：尝试完整匹配
            val fullPinyin = segments.joinToString("")
            val fullMatches = queryFullMatch(fullPinyin, 3)
            if (fullMatches.isNotEmpty()) {
                groupCandidates.addAll(fullMatches)
                Timber.d("   ✅ 完整匹配: ${fullMatches.size}个")
            }
            
            // 第二层：分段匹配
            val segmentMatches = querySegmentMatches(segments, limit - groupCandidates.size)
            groupCandidates.addAll(segmentMatches)
            Timber.d("   ✅ 分段匹配: ${segmentMatches.size}个")
            
            // 第三层：单字组合（如果结果不足）
            if (groupCandidates.size < limit / 2) {
                val singleCharCombinations = querySingleCharCombinations(segments, limit - groupCandidates.size)
                groupCandidates.addAll(singleCharCombinations)
                Timber.d("   ✅ 单字组合: ${singleCharCombinations.size}个")
            }
            
            candidateGroups.add(groupCandidates.distinctBy { it.word })
        }
        
        return candidateGroups
    }
    
    /**
     * 完整匹配查询
     */
    private suspend fun queryFullMatch(pinyin: String, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        // 查询基础词典
        if (trieManager.isTrieLoaded(TrieType.BASE)) {
            val baseResults = trieManager.searchByPrefix(TrieType.BASE, pinyin, limit)
            results.addAll(baseResults)
        }
        
        // 查询关联词典
        if (trieManager.isTrieLoaded(TrieType.CORRELATION) && results.size < limit) {
            val correlationResults = trieManager.searchByPrefix(TrieType.CORRELATION, pinyin, limit - results.size)
            results.addAll(correlationResults)
        }
        
        return results.distinctBy { it.word }.sortedByDescending { it.frequency }
    }
    
    /**
     * 分段匹配查询
     */
    private suspend fun querySegmentMatches(segments: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        // 为每个分段查询候选词
        val segmentCandidates = mutableListOf<List<WordFrequency>>()
        
        for (segment in segments) {
            val candidates = mutableListOf<WordFrequency>()
            
            // 优先查询单字
            if (trieManager.isTrieLoaded(TrieType.CHARS)) {
                val charResults = trieManager.searchByPrefix(TrieType.CHARS, segment, 3)
                    .filter { it.word.length == 1 }
                candidates.addAll(charResults)
            }
            
            // 补充查询词组
            if (trieManager.isTrieLoaded(TrieType.BASE) && candidates.size < 3) {
                val wordResults = trieManager.searchByPrefix(TrieType.BASE, segment, 3 - candidates.size)
                candidates.addAll(wordResults)
            }
            
            segmentCandidates.add(candidates.distinctBy { it.word }.sortedByDescending { it.frequency })
        }
        
        // 生成组合
        val combinations = generateWordCombinations(segmentCandidates, limit)
        results.addAll(combinations)
        
        return results
    }
    
    /**
     * 单字组合查询
     */
    private suspend fun querySingleCharCombinations(segments: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        if (!trieManager.isTrieLoaded(TrieType.CHARS)) {
            return results
        }
        
        // 为每个分段查询最佳单字
        val singleChars = mutableListOf<String>()
        
        for (segment in segments) {
            val charResults = trieManager.searchByPrefix(TrieType.CHARS, segment, 1)
                .filter { it.word.length == 1 }
            
            if (charResults.isNotEmpty()) {
                singleChars.add(charResults.first().word)
            } else {
                // 如果没有找到，使用拼音本身
                singleChars.add(segment)
            }
        }
        
        // 组合成词语
        if (singleChars.size == segments.size) {
            val combinedWord = singleChars.joinToString("")
            val frequency = calculateCombinationFrequency(singleChars)
            results.add(WordFrequency(combinedWord, frequency))
        }
        
        return results
    }
    
    /**
     * 生成词语组合
     */
    private fun generateWordCombinations(
        segmentCandidates: List<List<WordFrequency>>, 
        limit: Int
    ): List<WordFrequency> {
        val combinations = mutableListOf<WordFrequency>()
        
        if (segmentCandidates.isEmpty() || segmentCandidates.any { it.isEmpty() }) {
            return combinations
        }
        
        // 使用递归生成所有可能的组合
        fun generateCombinations(
            index: Int, 
            currentCombination: List<String>, 
            currentFrequency: Int
        ) {
            if (index == segmentCandidates.size) {
                val combinedWord = currentCombination.joinToString("")
                combinations.add(WordFrequency(combinedWord, currentFrequency))
                return
            }
            
            if (combinations.size >= limit) return
            
            for (candidate in segmentCandidates[index].take(2)) { // 每个分段最多取2个候选
                generateCombinations(
                    index + 1,
                    currentCombination + candidate.word,
                    currentFrequency + candidate.frequency
                )
            }
        }
        
        generateCombinations(0, emptyList(), 0)
        
        return combinations.distinctBy { it.word }
            .sortedByDescending { it.frequency }
            .take(limit)
    }
    
    /**
     * 生成最佳组合
     */
    private fun generateBestCombinations(
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
     * 回退到普通查询
     */
    private suspend fun fallbackToNormalQuery(input: String, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        // 尝试各种Trie查询
        val trieTypes = listOf(TrieType.BASE, TrieType.CHARS, TrieType.CORRELATION)
        
        for (trieType in trieTypes) {
            if (trieManager.isTrieLoaded(trieType) && results.size < limit) {
                val trieResults = trieManager.searchByPrefix(trieType, input, limit - results.size)
                results.addAll(trieResults)
            }
        }
        
        return results.distinctBy { it.word }.sortedByDescending { it.frequency }
    }
    
    /**
     * 计算组合频率
     */
    private fun calculateCombinationFrequency(chars: List<String>): Int {
        // 简单的频率计算：基础频率减去长度惩罚
        return maxOf(100 - chars.size * 10, 1)
    }
    
    /**
     * 获取有效拼音音节集合
     */
    private fun getValidSyllables(): Set<String> {
        return setOf(
            // 单韵母
            "a", "e", "o", "i", "u", "v",
            // 复韵母
            "ai", "ei", "ao", "ou", "ia", "ie", "ua", "uo", "ve", "ue",
            // 鼻韵母
            "an", "en", "in", "un", "vn", "ang", "eng", "ing", "ong", "iang", "iong", "uang", "ueng",
            // 声母+韵母组合（常用音节）
            "ba", "pa", "ma", "fa", "da", "ta", "na", "la", "ga", "ka", "ha", "ja", "qa", "xa", "za", "ca", "sa", "ra", "ya", "wa",
            "bai", "pai", "mai", "dai", "tai", "nai", "lai", "gai", "kai", "hai", "zai", "cai", "sai", "rai",
            "ban", "pan", "man", "fan", "dan", "tan", "nan", "lan", "gan", "kan", "han", "jan", "qan", "xan", "zan", "can", "san", "ran", "yan", "wan",
            "bang", "pang", "mang", "fang", "dang", "tang", "nang", "lang", "gang", "kang", "hang", "jang", "qang", "xang", "zang", "cang", "sang", "rang", "yang", "wang",
            "bao", "pao", "mao", "dao", "tao", "nao", "lao", "gao", "kao", "hao", "jao", "qao", "xao", "zao", "cao", "sao", "rao", "yao",
            "bei", "pei", "mei", "fei", "dei", "tei", "nei", "lei", "gei", "kei", "hei", "jei", "qei", "xei", "zei", "cei", "sei", "rei", "wei",
            "ben", "pen", "men", "fen", "den", "ten", "nen", "len", "gen", "ken", "hen", "jen", "qen", "xen", "zen", "cen", "sen", "ren", "wen",
            "beng", "peng", "meng", "feng", "deng", "teng", "neng", "leng", "geng", "keng", "heng", "jeng", "qeng", "xeng", "zeng", "ceng", "seng", "reng", "weng",
            "bi", "pi", "mi", "di", "ti", "ni", "li", "gi", "ki", "hi", "ji", "qi", "xi", "zi", "ci", "si", "ri", "yi",
            "bian", "pian", "mian", "dian", "tian", "nian", "lian", "jian", "qian", "xian", "zian", "cian", "sian", "rian", "yian",
            "biang", "piang", "miang", "diang", "tiang", "niang", "liang", "giang", "kiang", "hiang", "jiang", "qiang", "xiang", "ziang", "ciang", "siang", "riang", "yang",
            "biao", "piao", "miao", "diao", "tiao", "niao", "liao", "jiao", "qiao", "xiao", "ziao", "ciao", "siao", "riao", "yiao",
            "bie", "pie", "mie", "die", "tie", "nie", "lie", "jie", "qie", "xie", "zie", "cie", "sie", "rie", "yie",
            "bin", "pin", "min", "din", "tin", "nin", "lin", "gin", "kin", "hin", "jin", "qin", "xin", "zin", "cin", "sin", "rin", "yin",
            "bing", "ping", "ming", "ding", "ting", "ning", "ling", "ging", "king", "hing", "jing", "qing", "xing", "zing", "cing", "sing", "ring", "ying",
            "bo", "po", "mo", "fo", "do", "to", "no", "lo", "go", "ko", "ho", "jo", "qo", "xo", "zo", "co", "so", "ro", "yo", "wo",
            "bu", "pu", "mu", "fu", "du", "tu", "nu", "lu", "gu", "ku", "hu", "ju", "qu", "xu", "zu", "cu", "su", "ru", "yu", "wu",
            // 特殊音节
            "er", "ng", "hm", "hng"
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
            appendLine("📊 ContinuousPinyinEngine 性能统计:")
            appendLine("查询总数: ${queryCount.get()}")
            appendLine("缓存命中: ${cacheHits.get()} (${hitRate}%)")
            appendLine("平均分词耗时: ${String.format("%.1f", avgSegmentationTime)}ms")
            appendLine("平均查询耗时: ${String.format("%.1f", avgQueryTime)}ms")
            appendLine("分词缓存: ${segmentationCache.size()}/100")
            appendLine("查询缓存: ${continuousCache.size()}/50")
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