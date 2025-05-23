package com.shenji.aikeyboard.engine

import com.shenji.aikeyboard.engine.analyzer.InputAnalyzer
import com.shenji.aikeyboard.engine.generator.CandidateGenerator
import com.shenji.aikeyboard.engine.RankingEngine
import com.shenji.aikeyboard.engine.CacheManager
import com.shenji.aikeyboard.engine.UserLearner
import com.shenji.aikeyboard.engine.PerformanceMonitor
import com.shenji.aikeyboard.engine.ContextManager
import com.shenji.aikeyboard.model.Candidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 现代输入法引擎核心类
 * 
 * 负责协调各个组件，提供统一的输入处理接口
 * 
 * 架构设计：
 * - InputAnalyzer: 输入分析和模式识别
 * - CandidateGenerator: 分层候选词生成
 * - RankingEngine: 智能排序和权重计算
 * - CacheManager: 多级缓存管理
 * - UserLearner: 用户行为学习
 * - PerformanceMonitor: 性能监控
 * - ContextManager: 上下文管理
 */
class InputMethodEngine {
    
    // 核心组件
    private val inputAnalyzer = InputAnalyzer()
    private val candidateGenerator = CandidateGenerator()
    private val rankingEngine = RankingEngine()
    private val cacheManager = CacheManager()
    private val userLearner = UserLearner()
    private val performanceMonitor = PerformanceMonitor()
    private val contextManager = ContextManager()
    
    /**
     * 主要接口：根据用户输入生成候选词
     * 
     * @param input 用户输入的拼音字符串
     * @param limit 候选词数量限制
     * @param context 上下文信息（可选）
     * @return 排序后的候选词列表
     */
    suspend fun generateCandidates(
        input: String, 
        limit: Int = 10,
        context: String? = null
    ): List<Candidate> = withContext(Dispatchers.IO) {
        
        val startTime = System.currentTimeMillis()
        
        try {
            // 1. 输入预处理和验证
            val normalizedInput = preprocessInput(input)
            if (normalizedInput.isEmpty()) {
                Timber.d("输入为空，返回空候选词列表")
                return@withContext emptyList()
            }
            
            // 2. 检查缓存
            val cachedResult = cacheManager.getCandidates(normalizedInput, limit)
            if (cachedResult != null) {
                Timber.d("缓存命中: '$normalizedInput'")
                performanceMonitor.recordCacheHit(normalizedInput)
                return@withContext cachedResult
            }
            
            // 3. 输入分析和模式识别
            val analysis = inputAnalyzer.analyze(normalizedInput)
            Timber.d("输入分析完成: $analysis")
            
            // 4. 更新上下文
            context?.let { contextManager.updateContext(it) }
            
            // 5. 分层候选词生成
            val rawCandidates = candidateGenerator.generate(analysis, limit * 3) // 生成更多候选词用于排序
            
            // 6. 智能排序
            val rankedCandidates = rankingEngine.rank(
                candidates = rawCandidates,
                analysis = analysis,
                userProfile = userLearner.getUserProfile(),
                context = contextManager.getCurrentContext(),
                limit = limit
            )
            
            // 7. 缓存结果
            cacheManager.putCandidates(normalizedInput, rankedCandidates, limit)
            
            // 8. 性能监控
            val endTime = System.currentTimeMillis()
            performanceMonitor.recordQuery(normalizedInput, endTime - startTime, rankedCandidates.size)
            
            Timber.d("候选词生成完成: '$normalizedInput' -> ${rankedCandidates.size}个候选词 (耗时: ${endTime - startTime}ms)")
            
            return@withContext rankedCandidates
            
        } catch (e: Exception) {
            Timber.e(e, "候选词生成异常: '$input'")
            performanceMonitor.recordError(input, e)
            return@withContext emptyList()
        }
    }
    
    /**
     * 记录用户选择，用于学习优化
     */
    fun recordUserSelection(
        input: String,
        selected: Candidate,
        alternatives: List<Candidate>,
        position: Int
    ) {
        userLearner.recordSelection(input, selected, alternatives, position)
        performanceMonitor.recordSelection(input, selected, position)
    }
    
    /**
     * 获取性能统计信息
     */
    fun getPerformanceStats() = performanceMonitor.getStats()
    
    /**
     * 清理缓存和重置统计
     */
    fun cleanup() {
        cacheManager.clear()
        performanceMonitor.reset()
        userLearner.cleanup()
    }
    
    /**
     * 输入预处理
     */
    private fun preprocessInput(input: String): String {
        return input.trim().lowercase().replace(Regex("\\s+"), "")
    }
    
    /**
     * 获取调试信息
     */
    fun getDebugInfo(): String {
        return """
            |输入法引擎调试信息:
            |${performanceMonitor.getStats()}
            |${cacheManager.getStats()}
            |${userLearner.getStats()}
        """.trimMargin()
    }
} 