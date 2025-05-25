package com.shenji.aikeyboard.keyboard

import android.util.LruCache
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.trie.TrieType
import com.shenji.aikeyboard.model.WordFrequency
// import com.shenji.aikeyboard.data.repository.DictionaryRepository
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * 智能拼音引擎 - 项目中唯一的候选词查询引擎
 * 
 * 设计原则：
 * 1. 只处理当前正在输入的拼音部分（不包括已确认文本）
 * 2. 根据输入长度和状态选择最优查询策略
 * 3. 利用Trie加载状态进行智能路由
 * 4. 渐进式查询，避免重复计算
 * 5. 统一的音节匹配、拆分、查询方法
 */
class SmartPinyinEngine private constructor() {
    
    private val trieManager = TrieManager.instance
    // private val dictionaryRepository = DictionaryRepository.getInstance()
    
    // 渐进式缓存 - 缓存用户输入过程中的结果
    private val progressiveCache = LruCache<String, CachedResult>(200)
    
    // 性能统计
    private val queryCount = AtomicLong(0)
    private val cacheHits = AtomicLong(0)
    private val totalQueryTime = AtomicLong(0)
    
    companion object {
        @Volatile
        private var INSTANCE: SmartPinyinEngine? = null
        
        fun getInstance(): SmartPinyinEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartPinyinEngine().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 缓存结果数据类
     */
    private data class CachedResult(
        val candidates: List<WordFrequency>,
        val analysis: QueryAnalysis,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 查询分析数据类
     */
    data class QueryAnalysis(
        val inputType: InputType,
        val queryStrategy: QueryStrategy,
        val segmentations: List<String>,
        val trieStatus: String,
        val queryTime: Long,
        val resultCount: Int,
        val cacheHit: Boolean
    )
    
    /**
     * 输入类型枚举
     */
    enum class InputType {
        SINGLE_CHAR,        // 单字符: "n", "w"
        SHORT_INPUT,        // 短输入: "ni", "wo", "nh"
        MEDIUM_INPUT,       // 中等输入: "nihao", "nhao"
        LONG_INPUT,         // 长输入: "woshibeijingren"
        MIXED_INPUT         // 混合输入: "wodepy", "woshibjr"
    }
    
    /**
     * 查询策略枚举
     */
    enum class QueryStrategy {
        CHAR_TRIE_ONLY,     // 仅查询单字Trie
        TRIE_PRIORITY,      // Trie优先
        HYBRID_QUERY,       // 混合查询
        DATABASE_PRIORITY,  // 数据库优先
        PROGRESSIVE_FILTER, // 渐进式过滤
        ABBREVIATION_MATCH  // 缩写匹配
    }
    
    /**
     * 主要查询接口 - 获取候选词（支持智能分层懒加载）
     */
    suspend fun getCandidates(currentPinyin: String, limit: Int = 25, offset: Int = 0): List<WordFrequency> {
        if (currentPinyin.isBlank()) return emptyList()
        
        val cleanInput = currentPinyin.trim().lowercase()
        queryCount.incrementAndGet()
        
        var queryTime = 0L
        val result = measureTimeMillis {
            // 1. 检查渐进式缓存
            val cachedResult = checkProgressiveCache(cleanInput)
            if (cachedResult != null) {
                cacheHits.incrementAndGet()
                // 支持智能分层分页返回
                return getLayeredCandidates(cleanInput, cachedResult.candidates, limit, offset)
            }
            
            // 2. 分析输入类型
            val inputType = analyzeInputType(cleanInput)
            
            // 3. 选择查询策略
            val strategy = selectQueryStrategy(inputType, cleanInput)
            
            // 4. 执行全量查询（为分层懒加载准备）
            val candidates = executeFullQuery(cleanInput, strategy)
            
            // 5. 缓存结果
            val analysis = QueryAnalysis(
                inputType = inputType,
                queryStrategy = strategy,
                segmentations = generateSegmentations(cleanInput),
                trieStatus = getTrieStatus(),
                queryTime = queryTime,
                resultCount = candidates.size,
                cacheHit = false
            )
            
            progressiveCache.put(cleanInput, CachedResult(candidates, analysis))
            
        }.let { queryTime = it; progressiveCache.get(cleanInput)?.candidates ?: emptyList() }
        
        totalQueryTime.addAndGet(queryTime)
        
        // 返回智能分层结果
        return getLayeredCandidates(cleanInput, result, limit, offset)
    }
    
    /**
     * 智能分层候选词获取
     * 
     * 分层策略：
     * 1. 第1层：与音节数相同的词组（按词频排序）
     * 2. 第2层：第1个音节的单字候选
     * 3. 第3层：第2个音节的单字候选
     * 4. 第N层：第N个音节的单字候选
     */
    private suspend fun getLayeredCandidates(
        input: String, 
        allCandidates: List<WordFrequency>, 
        limit: Int, 
        offset: Int
    ): List<WordFrequency> {
        // 获取音节分割
        val segments = intelligentSegmentation(input)
        val segmentCount = segments.size
        
        // 构建分层候选词
        val layeredCandidates = buildLayeredCandidates(input, segments, allCandidates)
        
        // 分页返回
        val startIndex = offset
        val endIndex = minOf(offset + limit, layeredCandidates.size)
        return if (startIndex < layeredCandidates.size) {
            layeredCandidates.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }
    
    /**
     * 构建分层候选词列表
     */
    private suspend fun buildLayeredCandidates(
        input: String,
        segments: List<String>,
        allCandidates: List<WordFrequency>
    ): List<WordFrequency> {
        val layeredResults = mutableListOf<WordFrequency>()
        val segmentCount = segments.size
        
        Timber.d("构建分层候选词: $input -> ${segments.joinToString(" ")} (${segmentCount}个音节)")
        
        // 第1层：与音节数相同的词组
        if (segmentCount >= 2) {
            val matchingLengthWords = allCandidates.filter { it.word.length == segmentCount }
                .sortedByDescending { it.frequency }
            layeredResults.addAll(matchingLengthWords)
            Timber.d("第1层(${segmentCount}字词): ${matchingLengthWords.size}个")
        } else {
            // 单音节输入，直接添加所有候选
            layeredResults.addAll(allCandidates.sortedByDescending { it.frequency })
        }
        
        // 第2-N层：按音节顺序的单字候选
        if (segmentCount >= 2) {
            for ((index, segment) in segments.withIndex()) {
                val singleCharCandidates = getSingleCharCandidates(segment)
                    .filter { candidate -> 
                        // 避免重复添加已有的候选词
                        !layeredResults.any { it.word == candidate.word }
                    }
                    .sortedByDescending { it.frequency }
                
                layeredResults.addAll(singleCharCandidates)
                Timber.d("第${index + 2}层(${segment}单字): ${singleCharCandidates.size}个")
            }
        }
        
        return layeredResults.distinctBy { it.word }
    }
    
    /**
     * 获取单个音节的单字候选
     */
    private suspend fun getSingleCharCandidates(segment: String): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        // 查询单字Trie
        if (trieManager.isTrieLoaded(TrieType.CHARS)) {
            val charResults = trieManager.searchByPrefix(TrieType.CHARS, segment, 20)
            results.addAll(charResults)
        }
        
        // 查询BASE Trie中的单字
        if (trieManager.isTrieLoaded(TrieType.BASE)) {
            val baseResults = trieManager.searchByPrefix(TrieType.BASE, segment, 15)
                .filter { it.word.length == 1 }
            results.addAll(baseResults)
        }
        
        return results.distinctBy { it.word }
    }
    
    /**
     * 执行全量查询（为分层懒加载准备所有候选词）
     */
    private suspend fun executeFullQuery(input: String, strategy: QueryStrategy): List<WordFrequency> {
        return when (strategy) {
            QueryStrategy.CHAR_TRIE_ONLY -> queryCharTrieOnly(input, 100)
            QueryStrategy.TRIE_PRIORITY -> queryTriePriority(input, 200)
            QueryStrategy.HYBRID_QUERY -> queryHybridFull(input)
            QueryStrategy.DATABASE_PRIORITY -> queryDatabasePriority(input, 300)
            QueryStrategy.PROGRESSIVE_FILTER -> queryProgressiveFilter(input, 200)
            QueryStrategy.ABBREVIATION_MATCH -> queryAbbreviationMatchFull(input)
        }
    }
    
    /**
     * 混合查询 - 全量版本（为懒加载准备）
     */
    private suspend fun queryHybridFull(input: String): List<WordFrequency> {
        return withContext(Dispatchers.Default) {
            val results = mutableListOf<WordFrequency>()
            
            // 1. 获取所有可能的分割方案
            val segmentations = generateSegmentations(input)
            
            // 2. 对每种分割方案进行全量查询
            for (segmentation in segmentations) {
                val segments = if (segmentation.contains(" ")) {
                    segmentation.split(" ")
                } else {
                    listOf(segmentation)
                }
                
                if (segments.size > 1) {
                    // 多音节：查询所有可能的词组
                    val phraseResults = queryAllPhrases(segments)
                    results.addAll(phraseResults)
                } else {
                    // 单音节：查询所有相关候选
                    val singleResults = querySingleSegment(segments[0], 100)
                    results.addAll(singleResults)
                }
            }
            
            results.distinctBy { it.word }
                .sortedByDescending { it.frequency }
        }
    }
    
    /**
     * 查询所有可能的词组
     */
    private suspend fun queryAllPhrases(segments: List<String>): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        val segmentCount = segments.size
        
        // 查询与音节数相同的词组
        val fullPhrase = segments.joinToString("")
        if (trieManager.isTrieLoaded(TrieType.BASE)) {
            val fullPhraseResults = trieManager.searchByPrefix(TrieType.BASE, fullPhrase, 100)
                .filter { it.word.length == segmentCount }
            results.addAll(fullPhraseResults)
        }
        
        // 查询较短的词组组合
        for (phraseLength in (segmentCount - 1) downTo 2) {
            for (startIndex in 0..(segmentCount - phraseLength)) {
                val phraseSegments = segments.subList(startIndex, startIndex + phraseLength)
                val phrase = phraseSegments.joinToString("")
                
                if (trieManager.isTrieLoaded(TrieType.BASE)) {
                    val phraseResults = trieManager.searchByPrefix(TrieType.BASE, phrase, 50)
                        .filter { it.word.length == phraseLength }
                    results.addAll(phraseResults)
                }
            }
        }
        
        return results
    }
    
    /**
     * 缩写匹配 - 全量版本
     */
    private suspend fun queryAbbreviationMatchFull(input: String): List<WordFrequency> {
        return withContext(Dispatchers.Default) {
            val results = mutableListOf<WordFrequency>()
            
            // 查询所有相关的词组和单字
            val firstChar = input.first().toString()
            
            // 查询词组
            if (trieManager.isTrieLoaded(TrieType.BASE)) {
                val phraseResults = trieManager.searchByPrefix(TrieType.BASE, firstChar, 200)
                results.addAll(phraseResults)
                
                if (input.length > 1) {
                    val fullResults = trieManager.searchByPrefix(TrieType.BASE, input, 100)
                    results.addAll(fullResults)
                }
            }
            
            // 查询单字
            if (trieManager.isTrieLoaded(TrieType.CHARS)) {
                val charResults = trieManager.searchByPrefix(TrieType.CHARS, firstChar, 50)
                results.addAll(charResults)
            }
            
            results.distinctBy { it.word }
                .sortedByDescending { it.frequency }
        }
    }
    
    /**
     * 获取更多候选词（懒加载接口）
     */
    suspend fun getMoreCandidates(currentPinyin: String, currentCount: Int, batchSize: Int = 25): List<WordFrequency> {
        return getCandidates(currentPinyin, batchSize, currentCount)
    }
    
    /**
     * 检查是否还有更多候选词
     */
    suspend fun hasMoreCandidates(currentPinyin: String, currentCount: Int): Boolean {
        if (currentPinyin.isBlank()) return false
        
        val cleanInput = currentPinyin.trim().lowercase()
        val cachedResult = checkProgressiveCache(cleanInput)
        
        return if (cachedResult != null) {
            currentCount < cachedResult.candidates.size
        } else {
            // 如果没有缓存，假设可能有更多（需要实际查询验证）
            true
        }
    }
    
    /**
     * 获取查询分析信息
     */
    suspend fun getQueryAnalysis(currentPinyin: String): QueryAnalysis {
        if (currentPinyin.isBlank()) {
            return QueryAnalysis(
                InputType.SINGLE_CHAR, QueryStrategy.CHAR_TRIE_ONLY, 
                emptyList(), getTrieStatus(), 0, 0, false
            )
        }
        
        val cleanInput = currentPinyin.trim().lowercase()
        
        // 检查缓存
        val cached = progressiveCache.get(cleanInput)
        if (cached != null) {
            return cached.analysis.copy(cacheHit = true)
        }
        
        // 重新分析
        val inputType = analyzeInputType(cleanInput)
        val strategy = selectQueryStrategy(inputType, cleanInput)
        
        return QueryAnalysis(
            inputType = inputType,
            queryStrategy = strategy,
            segmentations = generateSegmentations(cleanInput),
            trieStatus = getTrieStatus(),
            queryTime = 0,
            resultCount = 0,
            cacheHit = false
        )
    }
    
    /**
     * 分析输入类型
     */
    private fun analyzeInputType(input: String): InputType {
        return when {
            input.length == 1 -> InputType.SINGLE_CHAR
            input.length <= 3 -> {
                // 检查是否为缩写模式
                if (isAbbreviationPattern(input)) {
                    InputType.SHORT_INPUT
                } else {
                    InputType.SHORT_INPUT
                }
            }
            input.length <= 6 -> {
                // 检查是否为混合输入（缩写+音节）
                if (isMixedInput(input)) {
                    InputType.MIXED_INPUT
                } else {
                    InputType.MEDIUM_INPUT
                }
            }
            input.length <= 12 -> InputType.LONG_INPUT
            else -> InputType.MIXED_INPUT
        }
    }
    
    /**
     * 判断是否为混合输入（缩写+音节组合）
     */
    private fun isMixedInput(input: String): Boolean {
        // 尝试找到有效的拼音音节分割
        val segments = intelligentSegmentation(input)
        return segments.size > 1 && segments.any { it.length == 1 } && segments.any { it.length > 1 }
    }
    
    /**
     * 选择查询策略
     */
    private fun selectQueryStrategy(inputType: InputType, input: String): QueryStrategy {
        return when (inputType) {
            InputType.SINGLE_CHAR -> {
                QueryStrategy.CHAR_TRIE_ONLY
            }
            InputType.SHORT_INPUT -> {
                // 检查是否为缩写模式（2-4个辅音字母）
                if (isAbbreviationPattern(input)) {
                    QueryStrategy.ABBREVIATION_MATCH
                } else if (trieManager.isTrieLoaded(TrieType.BASE)) {
                    QueryStrategy.TRIE_PRIORITY
                } else {
                    QueryStrategy.DATABASE_PRIORITY
                }
            }
            InputType.MEDIUM_INPUT -> {
                // 检查是否有前缀缓存可以利用
                if (hasProgressivePrefix(input)) {
                    QueryStrategy.PROGRESSIVE_FILTER
                } else {
                    QueryStrategy.HYBRID_QUERY
                }
            }
            InputType.MIXED_INPUT -> {
                QueryStrategy.HYBRID_QUERY
            }
            InputType.LONG_INPUT -> {
                QueryStrategy.DATABASE_PRIORITY
            }
        }
    }
    
    /**
     * 执行查询
     */
    private suspend fun executeQuery(input: String, strategy: QueryStrategy, limit: Int): List<WordFrequency> {
        return when (strategy) {
            QueryStrategy.CHAR_TRIE_ONLY -> queryCharTrieOnly(input, limit)
            QueryStrategy.TRIE_PRIORITY -> queryTriePriority(input, limit)
            QueryStrategy.HYBRID_QUERY -> queryHybrid(input, limit)
            QueryStrategy.DATABASE_PRIORITY -> queryDatabasePriority(input, limit)
            QueryStrategy.PROGRESSIVE_FILTER -> queryProgressiveFilter(input, limit)
            QueryStrategy.ABBREVIATION_MATCH -> queryAbbreviationMatch(input, limit)
        }
    }
    
    /**
     * 仅查询单字Trie
     */
    private suspend fun queryCharTrieOnly(input: String, limit: Int): List<WordFrequency> {
        return withContext(Dispatchers.Default) {
            if (trieManager.isTrieLoaded(TrieType.CHARS)) {
                trieManager.searchByPrefix(TrieType.CHARS, input, limit)
            } else {
                // 如果单字Trie未加载，返回空列表
                emptyList()
            }
        }
    }
    
    /**
     * Trie优先查询
     */
    private suspend fun queryTriePriority(input: String, limit: Int): List<WordFrequency> {
        return withContext(Dispatchers.Default) {
            val results = mutableListOf<WordFrequency>()
            
            // 1. 查询单字Trie
            if (trieManager.isTrieLoaded(TrieType.CHARS)) {
                results.addAll(trieManager.searchByPrefix(TrieType.CHARS, input, 3))
            }
            
            // 2. 查询基础Trie
            if (trieManager.isTrieLoaded(TrieType.BASE) && results.size < limit) {
                results.addAll(trieManager.searchByPrefix(TrieType.BASE, input, limit - results.size))
            }
            
            // 3. 如果结果不足，暂时跳过数据库查询
            // if (results.size < limit / 2) {
            //     results.addAll(dictionaryRepository.searchByPinyin(input, limit - results.size))
            // }
            
            results.distinctBy { it.word }.take(limit)
        }
    }
    
    /**
     * 混合查询 - 实现分层候选词策略，支持声母歧义性
     */
    private suspend fun queryHybrid(input: String, limit: Int): List<WordFrequency> {
        return withContext(Dispatchers.Default) {
            val results = mutableListOf<WordFrequency>()
            
            // 1. 获取所有可能的分割方案
            val segmentations = generateSegmentations(input)
            
            // 2. 对每种分割方案进行查询
            for (segmentation in segmentations) {
                if (results.size >= limit) break
                
                val segments = if (segmentation.contains(" ")) {
                    segmentation.split(" ")
                } else {
                    listOf(segmentation)
                }
                
                if (segments.size > 1) {
                    // 多音节：使用分层查询策略
                    val layeredResults = queryLayeredCandidates(segments, limit / segmentations.size)
                    results.addAll(layeredResults)
                } else {
                    // 单音节：直接查询
                    val singleResults = querySingleSegment(segments[0], limit / segmentations.size)
                    results.addAll(singleResults)
                }
            }
            
            results.distinctBy { it.word }
                .sortedByDescending { it.frequency }
                .take(limit)
        }
    }
    
    /**
     * 查询单个音节
     */
    private suspend fun querySingleSegment(segment: String, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        // 查询BASE Trie
        if (trieManager.isTrieLoaded(TrieType.BASE)) {
            results.addAll(trieManager.searchByPrefix(TrieType.BASE, segment, limit))
        }
        
        // 查询单字Trie
        if (trieManager.isTrieLoaded(TrieType.CHARS)) {
            results.addAll(trieManager.searchByPrefix(TrieType.CHARS, segment, limit / 2))
        }
        
        return results.take(limit)
    }
    
    /**
     * 分层候选词查询策略 - 优化版
     * 
     * 策略说明：
     * 1. 严格按音节数优先：优先查询与分割音节数相同的完整词组
     * 2. 穷尽同长度词组后，才查询较短词组
     * 3. 最后提供单字候选词作为备选
     * 
     * 例如：["wei", "xin"] (2个音节)
     * - 第1层：查询2字词组 (weixin) - 微信、维新、威信等
     * - 第2层：单字候选 (wei开头的字、xin开头的字) - 仅在2字词组不足时
     */
    private suspend fun queryLayeredCandidates(segments: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        val segmentCount = segments.size
        
        Timber.d("开始分层查询: ${segments.joinToString(" ")} (${segmentCount}个音节)")
        
        // 第1层：优先查询与音节数相同的完整词组
        if (segmentCount >= 2) {
            val fullPhrase = segments.joinToString("")
            val fullPhraseResults = queryPhraseByLength(fullPhrase, segmentCount, limit * 2) // 增加查询数量
            results.addAll(fullPhraseResults)
            Timber.d("第1层(${segmentCount}字词): $fullPhrase -> ${fullPhraseResults.size}个结果")
            
            // 如果同长度词组已经足够，直接返回
            if (results.size >= limit) {
                return results.take(limit)
            }
        }
        
        // 第2层：只有在同长度词组不足时，才查询较短词组
        if (results.size < limit && segmentCount > 2) {
            val remainingLimit = limit - results.size
            
            for (phraseLength in (segmentCount - 1) downTo 2) {
                if (results.size >= limit) break
                
                val layerResults = mutableListOf<WordFrequency>()
                val currentLayerLimit = remainingLimit / (segmentCount - 1)
                
                // 生成该长度的所有可能组合
                for (startIndex in 0..(segmentCount - phraseLength)) {
                    val phraseSegments = segments.subList(startIndex, startIndex + phraseLength)
                    val phrase = phraseSegments.joinToString("")
                    
                    val phraseResults = queryPhraseByLength(phrase, phraseLength, currentLayerLimit)
                    layerResults.addAll(phraseResults)
                    
                    if (layerResults.size >= currentLayerLimit) break
                }
                
                results.addAll(layerResults.take(currentLayerLimit))
                Timber.d("第${segmentCount - phraseLength + 1}层(${phraseLength}字词): ${layerResults.size}个结果")
            }
        }
        
        // 最后一层：单字候选词（仅在词组不足时提供）
        if (results.size < limit) {
            val remainingLimit = limit - results.size
            val singleCharResults = mutableListOf<WordFrequency>()
            
            for (segment in segments) {
                if (singleCharResults.size >= remainingLimit) break
                
                // 查询单字Trie
                if (trieManager.isTrieLoaded(TrieType.CHARS)) {
                    val charResults = trieManager.searchByPrefix(TrieType.CHARS, segment, 5)
                    singleCharResults.addAll(charResults)
                }
                
                // 查询BASE Trie中的单字
                if (trieManager.isTrieLoaded(TrieType.BASE)) {
                    val baseResults = trieManager.searchByPrefix(TrieType.BASE, segment, 3)
                        .filter { it.word.length == 1 }
                    singleCharResults.addAll(baseResults)
                }
            }
            
            results.addAll(singleCharResults.distinctBy { it.word }.take(remainingLimit))
            Timber.d("最后一层(单字): ${singleCharResults.size}个结果")
        }
        
        return results
    }
    
    /**
     * 按词长查询短语
     */
    private suspend fun queryPhraseByLength(phrase: String, expectedLength: Int, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        if (trieManager.isTrieLoaded(TrieType.BASE)) {
            val baseResults = trieManager.searchByPrefix(TrieType.BASE, phrase, limit * 2)
                .filter { it.word.length == expectedLength }
            results.addAll(baseResults)
        }
        
        return results.take(limit)
    }
    
    /**
     * 计算各层的候选词数量分配
     */
    private fun calculateLayerLimits(segmentCount: Int, totalLimit: Int): List<Int> {
        val layers = segmentCount // 总层数
        val limits = mutableListOf<Int>()
        
        when {
            segmentCount <= 2 -> {
                // 简单情况：平均分配
                limits.add(totalLimit / 2)
                limits.add(totalLimit / 2)
            }
            segmentCount <= 4 -> {
                // 中等复杂度：完整词组优先
                limits.add((totalLimit * 0.4).toInt()) // 完整词组
                limits.add((totalLimit * 0.3).toInt()) // 次长词组
                limits.add((totalLimit * 0.2).toInt()) // 短词组
                limits.add((totalLimit * 0.1).toInt()) // 单字
            }
            else -> {
                // 高复杂度：更多层级
                limits.add((totalLimit * 0.3).toInt()) // 完整词组
                limits.add((totalLimit * 0.25).toInt()) // 次长词组
                limits.add((totalLimit * 0.2).toInt()) // 中等词组
                limits.add((totalLimit * 0.15).toInt()) // 短词组
                limits.add((totalLimit * 0.1).toInt()) // 单字
            }
        }
        
        // 确保至少每层有1个候选
        return limits.map { maxOf(1, it) }
    }
    
    /**
     * 数据库优先查询
     */
    private suspend fun queryDatabasePriority(input: String, limit: Int): List<WordFrequency> {
        return withContext(Dispatchers.Default) {
            // 暂时返回空列表，后续实现数据库查询
            emptyList()
        }
    }
    
    /**
     * 渐进式过滤查询
     */
    private suspend fun queryProgressiveFilter(input: String, limit: Int): List<WordFrequency> {
        return withContext(Dispatchers.Default) {
            // 查找最长的前缀缓存
            val prefixResult = findLongestPrefixCache(input)
            if (prefixResult != null) {
                // 基于前缀结果过滤
                prefixResult.candidates.filter { candidate ->
                    matchesPinyinPattern(candidate.word, input)
                }.take(limit)
            } else {
                // 回退到混合查询
                queryHybrid(input, limit)
            }
        }
    }
    
    /**
     * 智能分割查询
     */
    private suspend fun queryWithSegmentation(input: String, limit: Int): List<WordFrequency> {
        return withContext(Dispatchers.Default) {
            val segmentations = generateSegmentations(input)
            val results = mutableListOf<WordFrequency>()
            
            for (segmentation in segmentations.take(3)) { // 限制分割数量
                // 暂时跳过数据库查询，使用Trie查询
                if (trieManager.isTrieLoaded(TrieType.BASE)) {
                    val segmentResults = trieManager.searchByPrefix(TrieType.BASE, segmentation, limit / 3)
                    results.addAll(segmentResults)
                    if (results.size >= limit) break
                }
            }
            
            results.distinctBy { it.word }.take(limit)
        }
    }
    
    /**
     * 生成可能的分割方案 - 支持声母歧义性处理
     */
    private fun generateSegmentations(input: String): List<String> {
        val segmentations = mutableListOf<String>()
        
        // 添加原始输入
        segmentations.add(input)
        
        // 处理复辅音声母的歧义性（zh, ch, sh）
        val ambiguousSegmentations = handleInitialAmbiguity(input)
        segmentations.addAll(ambiguousSegmentations)
        
        // 对于长输入，进行智能分割
        if (input.length > 3) {
            val intelligentSegments = intelligentSegmentation(input)
            if (intelligentSegments.size > 1) {
                segmentations.add(intelligentSegments.joinToString(" "))
                
                // 检查是否为完美分割
                val isValidSyllables = getValidSyllables()
                val isPerfectSegmentation = intelligentSegments.all { it in isValidSyllables }
                
                // 只有当智能分割不完美时，才添加简单分割作为备选
                if (!isPerfectSegmentation) {
                    for (i in 2..input.length-2 step 2) {
                        val part1 = input.substring(0, i)
                        val part2 = input.substring(i)
                        segmentations.add("$part1 $part2")
                    }
                }
            } else {
                // 智能分割失败，使用简单分割
                for (i in 2..input.length-2 step 2) {
                    val part1 = input.substring(0, i)
                    val part2 = input.substring(i)
                    segmentations.add("$part1 $part2")
                }
            }
        }
        
        return segmentations.distinct()
    }
    
    /**
     * 处理复辅音声母的歧义性
     * 
     * 复辅音声母：zh, ch, sh
     * 这些可能被理解为：
     * 1. 完整声母（如 "sh" → 查找以sh开头的音节）
     * 2. 分离字母（如 "sh" → "s" + "h"）
     */
    private fun handleInitialAmbiguity(input: String): List<String> {
        val ambiguousSegmentations = mutableListOf<String>()
        
        // 复辅音声母列表
        val compoundInitials = setOf("zh", "ch", "sh")
        
        when (input.length) {
            2 -> {
                // 对于2字符输入，检查是否为复辅音声母
                if (input in compoundInitials) {
                    // 方案1：作为完整声母
                    // 已经在原始输入中包含
                    
                    // 方案2：作为分离字母
                    ambiguousSegmentations.add("${input[0]} ${input[1]}")
                }
            }
            3 -> {
                // 对于3字符输入，检查前2个字符是否为复辅音声母
                val firstTwo = input.substring(0, 2)
                if (firstTwo in compoundInitials) {
                    // 方案1：复辅音声母 + 单字符
                    ambiguousSegmentations.add("$firstTwo ${input[2]}")
                    
                    // 方案2：单字符 + 单字符 + 单字符
                    ambiguousSegmentations.add("${input[0]} ${input[1]} ${input[2]}")
                }
                
                // 检查后2个字符是否为复辅音声母
                val lastTwo = input.substring(1, 3)
                if (lastTwo in compoundInitials) {
                    // 方案3：单字符 + 复辅音声母
                    ambiguousSegmentations.add("${input[0]} $lastTwo")
                }
            }
            4 -> {
                // 对于4字符输入，检查各种复辅音声母组合
                val firstTwo = input.substring(0, 2)
                val lastTwo = input.substring(2, 4)
                
                if (firstTwo in compoundInitials && lastTwo in compoundInitials) {
                    // 方案1：复辅音声母 + 复辅音声母
                    ambiguousSegmentations.add("$firstTwo $lastTwo")
                }
                
                if (firstTwo in compoundInitials) {
                    // 方案2：复辅音声母 + 单字符 + 单字符
                    ambiguousSegmentations.add("$firstTwo ${input[2]} ${input[3]}")
                }
                
                if (lastTwo in compoundInitials) {
                    // 方案3：单字符 + 单字符 + 复辅音声母
                    ambiguousSegmentations.add("${input[0]} ${input[1]} $lastTwo")
                }
            }
        }
        
        return ambiguousSegmentations
    }
    
    /**
     * 获取有效音节集合
     */
    private fun getValidSyllables(): Set<String> {
        return setOf(
            "a", "e", "o", "ai", "ei", "ao", "ou", "an", "en", "ang", "eng", "er",
            "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biao", "bie", "bin", "bing", "bo", "bu",
            "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo",
            "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng", "di", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo",
            "fa", "fan", "fang", "fei", "fen", "feng", "fo", "fou", "fu",
            "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
            "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
            "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu", "ju", "juan", "jue", "jun",
            "ka", "kai", "kan", "kang", "kao", "ke", "ken", "keng", "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
            "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian", "liang", "liao", "lie", "lin", "ling", "liu", "long", "lou", "lu", "luan", "lue", "lun", "luo", "lv",
            "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
            "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ni", "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu", "nuan", "nue", "nuo", "nv",
            "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu",
            "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu", "qu", "quan", "que", "qun",
            "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "ruan", "rui", "run", "ruo",
            "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "si", "song", "sou", "su", "suan", "sui", "sun", "suo",
            "ta", "tai", "tan", "tang", "tao", "te", "teng", "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo",
            "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
            "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu", "xu", "xuan", "xue", "xun",
            "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you", "yu", "yuan", "yue", "yun",
            "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo",
            "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo",
            "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo",
            "sha", "shai", "shan", "shang", "shao", "she", "shei", "shen", "sheng", "shi", "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo"
        )
    }
    
    /**
     * 智能分割输入为有效的拼音音节
     */
    private fun intelligentSegmentation(input: String): List<String> {
        val segments = mutableListOf<String>()
        var i = 0
        
        // 常见拼音音节模式（完整音节，不包括单独的声母）
        val validSyllables = setOf(
            // 单韵母（可以独立成字）
            "a", "e", "o", "ai", "ei", "ao", "ou", "an", "en", "ang", "eng", "er",
            // b系列
            "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biao", "bie", "bin", "bing", "bo", "bu",
            // c系列  
            "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo",
            // d系列
            "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng", "di", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo",
            // f系列
            "fa", "fan", "fang", "fei", "fen", "feng", "fo", "fou", "fu",
            // g系列
            "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
            // h系列
            "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
            // j系列
            "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu", "ju", "juan", "jue", "jun",
            // k系列
            "ka", "kai", "kan", "kang", "kao", "ke", "ken", "keng", "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
            // l系列
            "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian", "liang", "liao", "lie", "lin", "ling", "liu", "long", "lou", "lu", "luan", "lue", "lun", "luo", "lv",
            // m系列
            "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
            // n系列
            "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ni", "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu", "nuan", "nue", "nuo", "nv",
            // p系列
            "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu",
            // q系列
            "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu", "qu", "quan", "que", "qun",
            // r系列
            "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "ruan", "rui", "run", "ruo",
            // s系列
            "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "si", "song", "sou", "su", "suan", "sui", "sun", "suo",
            // t系列
            "ta", "tai", "tan", "tang", "tao", "te", "teng", "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo",
            // w系列
            "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
            // x系列
            "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu", "xu", "xuan", "xue", "xun",
            // y系列
            "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you", "yu", "yuan", "yue", "yun",
            // z系列
            "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo",
            // zh系列
            "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo",
            // ch系列
            "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo",
            // sh系列
            "sha", "shai", "shan", "shang", "shao", "she", "shei", "shen", "sheng", "shi", "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo"
        )
        
        // 特殊处理的单字母（可以作为缩写）
        val validSingleChars = setOf("b", "c", "d", "f", "g", "h", "j", "k", "l", "m", "n", "p", "q", "r", "s", "t", "w", "x", "y", "z")
        
        while (i < input.length) {
            var found = false
            
            // 尝试匹配最长的有效音节（从长到短，但优先完整音节）
            for (len in minOf(6, input.length - i) downTo 2) {
                val candidate = input.substring(i, i + len)
                if (validSyllables.contains(candidate)) {
                    segments.add(candidate)
                    i += len
                    found = true
                    break
                }
            }
            
            // 如果没有找到长音节，检查单字符
            if (!found) {
                val singleChar = input.substring(i, i + 1)
                if (validSyllables.contains(singleChar) || validSingleChars.contains(singleChar)) {
                    segments.add(singleChar)
                    i++
                    found = true
                }
            }
            
            // 如果还是没找到，添加单个字符（作为无效输入）
            if (!found) {
                segments.add(input.substring(i, i + 1))
                i++
            }
        }
        
        return segments
    }
    
    /**
     * 检查渐进式缓存
     */
    private fun checkProgressiveCache(input: String): CachedResult? {
        return progressiveCache.get(input)
    }
    
    /**
     * 检查是否有渐进式前缀
     */
    private fun hasProgressivePrefix(input: String): Boolean {
        for (i in input.length - 1 downTo 1) {
            val prefix = input.substring(0, i)
            if (progressiveCache.get(prefix) != null) {
                return true
            }
        }
        return false
    }
    
    /**
     * 查找最长的前缀缓存
     */
    private fun findLongestPrefixCache(input: String): CachedResult? {
        for (i in input.length - 1 downTo 1) {
            val prefix = input.substring(0, i)
            val cached = progressiveCache.get(prefix)
            if (cached != null) {
                return cached
            }
        }
        return null
    }
    
    /**
     * 缩写匹配查询
     */
    private suspend fun queryAbbreviationMatch(input: String, limit: Int): List<WordFrequency> {
        return withContext(Dispatchers.Default) {
            val results = mutableListOf<WordFrequency>()
            
            // 1. 首先查询单字母开头的词语（如"w"开头的词）
            val firstChar = input.first().toString()
            if (trieManager.isTrieLoaded(TrieType.BASE)) {
                val firstCharResults = trieManager.searchByPrefix(TrieType.BASE, firstChar, limit * 2)
                results.addAll(firstCharResults)
            }
            
            // 2. 查询单字Trie中首字母匹配的字
            if (trieManager.isTrieLoaded(TrieType.CHARS)) {
                val charResults = trieManager.searchByPrefix(TrieType.CHARS, firstChar, limit)
                results.addAll(charResults)
            }
            
            // 3. 尝试查询完整缩写匹配（如果输入长度>1）
            if (input.length > 1) {
                // 尝试直接查询缩写模式
                if (trieManager.isTrieLoaded(TrieType.BASE)) {
                    val abbreviationResults = trieManager.searchByPrefix(TrieType.BASE, input, limit)
                    results.addAll(abbreviationResults)
                }
            }
            
            // 按频率排序并去重
            results.distinctBy { it.word }
                .sortedByDescending { it.frequency }
                .take(limit)
        }
    }
    
    /**
     * 判断是否为缩写模式
     */
    private fun isAbbreviationPattern(input: String): Boolean {
        // 2-4个字符，全部是辅音字母
        val consonants = "bcdfghjklmnpqrstvwxyz"
        return input.length in 2..4 && input.all { it in consonants }
    }
    

    
    /**
     * 拼音模式匹配
     */
    private fun matchesPinyinPattern(word: String, pinyin: String): Boolean {
        // 简化的拼音匹配逻辑
        // 这里应该实现更复杂的拼音匹配算法
        return true // 临时返回true，后续完善
    }
    
    /**
     * 获取Trie状态
     */
    private fun getTrieStatus(): String {
        return buildString {
            append("CHARS: ${if (trieManager.isTrieLoaded(TrieType.CHARS)) "已加载" else "未加载"}")
            append(", BASE: ${if (trieManager.isTrieLoaded(TrieType.BASE)) "已加载" else "未加载"}")
        }
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        progressiveCache.evictAll()
        Timber.d("SmartPinyinEngine: 缓存已清理")
    }
    
    /**
     * 获取性能统计
     */
    fun getPerformanceStats(): String {
        val avgTime = if (queryCount.get() > 0) {
            totalQueryTime.get() / queryCount.get()
        } else 0
        
        val hitRate = if (queryCount.get() > 0) {
            (cacheHits.get() * 100.0 / queryCount.get()).toInt()
        } else 0
        
        return buildString {
            appendLine("📊 SmartPinyinEngine 性能统计:")
            appendLine("查询总数: ${queryCount.get()}")
            appendLine("缓存命中: ${cacheHits.get()} (${hitRate}%)")
            appendLine("平均耗时: ${avgTime}ms")
            appendLine("缓存大小: ${progressiveCache.size()}/200")
        }
    }
} 