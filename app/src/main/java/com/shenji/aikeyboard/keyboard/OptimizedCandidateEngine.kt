package com.shenji.aikeyboard.keyboard

import android.util.LruCache
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.trie.TrieType
import com.shenji.aikeyboard.model.WordFrequency
import com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/**
 * 智能候选词引擎
 * 
 * 设计原则：
 * 1. 多策略智能查询：单字、词组、缩写、分段、模糊等
 * 2. 分层缓存机制：查询缓存、策略缓存
 * 3. 按需加载Trie：根据输入类型动态加载
 * 4. 性能优化：并行查询、结果合并、去重排序
 */
class OptimizedCandidateEngine private constructor() {
    
    private val trieManager = TrieManager.instance
    private val inputStrategy = InputStrategy()
    private val queryEngine = IntelligentQueryEngine()
    
    // 多级缓存系统
    private val candidateCache = LruCache<String, List<WordFrequency>>(500)
    private val strategyCache = LruCache<String, List<InputStrategy.QueryStrategy>>(200)
    
    // 性能统计
    private val queryCount = AtomicLong(0)
    private val cacheHits = AtomicLong(0)
    private val strategyHits = AtomicLong(0)
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        @Volatile
        private var INSTANCE: OptimizedCandidateEngine? = null
        
        fun getInstance(): OptimizedCandidateEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OptimizedCandidateEngine().also { 
                    INSTANCE = it
                    it.preloadCoreTries()
                }
            }
        }
    }
    
    /**
     * 获取候选词 - 智能多策略查询
     */
    suspend fun getCandidates(input: String, limit: Int = 20): List<WordFrequency> = withContext(Dispatchers.IO) {
        val normalizedInput = input.trim().lowercase()
        if (normalizedInput.isEmpty()) return@withContext emptyList()
        
        val startTime = System.currentTimeMillis()
        queryCount.incrementAndGet()
        
        Timber.d("🔍 智能查询开始: '$normalizedInput', 限制: $limit")
        
        // 1. 检查候选词缓存
        candidateCache.get(normalizedInput)?.let { cached ->
            cacheHits.incrementAndGet()
            val endTime = System.currentTimeMillis()
            Timber.d("✅ 缓存命中，返回${cached.size}个候选词，耗时${endTime - startTime}ms")
            return@withContext cached.take(limit)
        }
        
        // 2. 分析输入策略
        val strategies = getQueryStrategies(normalizedInput)
        Timber.d("📋 生成${strategies.size}个查询策略")
        
        // 3. 执行多策略查询
        val results = executeMultiStrategyQuery(strategies, normalizedInput, limit)
        
        // 4. 缓存结果
        if (results.isNotEmpty()) {
            candidateCache.put(normalizedInput, results)
        }
        
        val endTime = System.currentTimeMillis()
        Timber.d("🎯 智能查询完成，返回${results.size}个候选词，耗时${endTime - startTime}ms")
        
        return@withContext results.take(limit)
    }
    
    /**
     * 获取查询策略（带缓存）
     */
    private fun getQueryStrategies(input: String): List<InputStrategy.QueryStrategy> {
        // 检查策略缓存
        strategyCache.get(input)?.let { cached ->
            strategyHits.incrementAndGet()
            Timber.d("📋 策略缓存命中")
            return cached
        }
        
        // 分析输入生成策略
        val strategies = inputStrategy.analyzeInput(input)
        
        // 缓存策略
        if (strategies.isNotEmpty()) {
            strategyCache.put(input, strategies)
        }
        
        return strategies
    }
    
    /**
     * 执行多策略查询
     */
    private suspend fun executeMultiStrategyQuery(
        strategies: List<InputStrategy.QueryStrategy>,
        input: String,
        limit: Int
    ): List<WordFrequency> {
        val allResults = mutableListOf<WordFrequency>()
        var processedStrategies = 0
        
        for (strategy in strategies) {
            if (allResults.size >= limit * 2) break // 收集足够的候选词后停止
            
            try {
                val strategyResults = queryEngine.executeQuery(strategy, input, limit)
                
                if (strategyResults.isNotEmpty()) {
                    // 为不同策略的结果添加权重调整
                    val weightedResults = applyStrategyWeight(strategyResults, strategy)
                    allResults.addAll(weightedResults)
                    
                    Timber.d("✨ ${strategy.description}: ${strategyResults.size}个结果")
                }
                
                processedStrategies++
                
                // 如果已经有足够的高质量结果，可以提前结束
                if (allResults.size >= limit && processedStrategies >= 2) {
                    Timber.d("🚀 提前结束查询，已获得足够结果")
                    break
                }
                
            } catch (e: Exception) {
                Timber.w(e, "策略查询失败: ${strategy.description}")
            }
        }
        
        // 合并、去重、排序
        return mergeAndRankResults(allResults, limit)
    }
    
    /**
     * 应用策略权重
     */
    private fun applyStrategyWeight(
        results: List<WordFrequency>,
        strategy: InputStrategy.QueryStrategy
    ): List<WordFrequency> {
        val weightMultiplier = when (strategy.inputType) {
            InputStrategy.InputType.SINGLE_CHAR -> 1.2f      // 单字优先
            InputStrategy.InputType.WORD_PINYIN -> 1.1f       // 词组次之
            InputStrategy.InputType.ABBREVIATION -> 0.9f      // 缩写降权
            InputStrategy.InputType.LONG_SENTENCE -> 0.8f     // 长句降权
            InputStrategy.InputType.FUZZY_PINYIN -> 0.7f      // 模糊降权
            else -> 1.0f
        }
        
        return results.map { wordFreq ->
            val adjustedFrequency = (wordFreq.frequency * weightMultiplier).toInt()
            WordFrequency(
                word = wordFreq.word,
                frequency = adjustedFrequency,
                source = "${wordFreq.source}(${strategy.inputType})"
            )
        }
    }
    
    /**
     * 合并和排序结果
     */
    private fun mergeAndRankResults(
        allResults: List<WordFrequency>,
        limit: Int
    ): List<WordFrequency> {
        // 按词语去重，保留频率最高的
        val wordMap = mutableMapOf<String, WordFrequency>()
        
        for (result in allResults) {
            val existing = wordMap[result.word]
            if (existing == null || result.frequency > existing.frequency) {
                wordMap[result.word] = result
            }
        }
        
        // 按频率排序并限制数量
        val finalResults = wordMap.values
            .sortedWith(compareByDescending<WordFrequency> { it.frequency }
                .thenBy { it.word.length }) // 同频率时优先短词
            .take(limit)
        
        Timber.d("🔄 结果合并：${allResults.size} -> ${wordMap.size} -> ${finalResults.size}")
        return finalResults
    }
    
    /**
     * 预加载核心Trie
     */
    private fun preloadCoreTries() {
        scope.launch {
            try {
                val coreTries = listOf(
                    TrieType.CHARS,
                    TrieType.BASE
                )
                
                for (trieType in coreTries) {
                    if (!trieManager.isTrieLoaded(trieType)) {
                        val startTime = System.currentTimeMillis()
                        val success = trieManager.loadTrieToMemory(trieType)
                        val loadTime = System.currentTimeMillis() - startTime
                        
                        if (success) {
                            Timber.d("🚀 预加载${trieType}成功，耗时${loadTime}ms")
                        } else {
                            Timber.w("⚠️ 预加载${trieType}失败")
                        }
                        
                        delay(100) // 避免同时加载过多
                    }
                }
                
                Timber.d("✅ 核心Trie预加载完成")
            } catch (e: Exception) {
                Timber.e(e, "❌ 预加载Trie失败")
            }
        }
    }
    
    /**
     * 拼音拆分（兼容性方法）
     */
    fun splitPinyin(input: String): List<String> {
        return try {
            UnifiedPinyinSplitter.split(input)
        } catch (e: Exception) {
            Timber.w(e, "拼音拆分失败，使用简单分段")
            inputStrategy.segmentPinyin(input).firstOrNull() ?: listOf(input)
        }
    }
    
    /**
     * 获取输入分析信息
     */
    fun getInputAnalysis(input: String): String {
        val strategies = inputStrategy.analyzeInput(input)
        val segments = inputStrategy.segmentPinyin(input)
        val fuzzyVariants = inputStrategy.generateFuzzyVariants(input)
        
        return buildString {
            appendLine("输入分析: '$input'")
            appendLine("策略数量: ${strategies.size}")
            strategies.forEachIndexed { index, strategy ->
                appendLine("  ${index + 1}. ${strategy.description} (优先级${strategy.priority})")
            }
            appendLine("分段方案: ${segments.size}种")
            segments.take(3).forEachIndexed { index, segment ->
                appendLine("  ${index + 1}. ${segment.joinToString(" + ")}")
            }
            if (fuzzyVariants.size > 1) {
                appendLine("模糊变体: ${fuzzyVariants.joinToString(", ")}")
            }
        }
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        candidateCache.evictAll()
        strategyCache.evictAll()
        Timber.d("🧹 缓存已清理")
    }
    
    /**
     * 获取性能统计
     */
    fun getPerformanceStats(): String {
        val totalQueries = queryCount.get()
        val candidateHitRate = if (totalQueries > 0) {
            (cacheHits.get().toFloat() / totalQueries * 100).toInt()
        } else 0
        
        val strategyHitRate = if (totalQueries > 0) {
            (strategyHits.get().toFloat() / totalQueries * 100).toInt()
        } else 0
        
        return """
            |📊 性能统计:
            |查询次数: $totalQueries
            |候选词缓存: ${cacheHits.get()}次命中 (${candidateHitRate}%)
            |策略缓存: ${strategyHits.get()}次命中 (${strategyHitRate}%)
            |候选词缓存大小: ${candidateCache.size()}/500
            |策略缓存大小: ${strategyCache.size()}/200
            |已加载Trie: ${trieManager.getLoadedTrieTypes().joinToString(", ")}
        """.trimMargin()
    }
    
    /**
     * 获取缓存统计
     */
    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "queryCount" to queryCount.get(),
            "cacheHits" to cacheHits.get(),
            "strategyHits" to strategyHits.get(),
            "candidateCacheSize" to candidateCache.size(),
            "strategyCacheSize" to strategyCache.size(),
            "loadedTries" to trieManager.getLoadedTrieTypes().size
        )
    }
    
    /**
     * 释放资源
     */
    fun cleanup() {
        scope.cancel()
        clearCache()
        Timber.d("🧹 OptimizedCandidateEngine资源已释放")
    }
} 