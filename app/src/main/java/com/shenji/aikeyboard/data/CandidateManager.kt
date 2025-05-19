package com.shenji.aikeyboard.data

import com.shenji.aikeyboard.data.strategy.*
import com.shenji.aikeyboard.utils.PinyinSegmenterOptimized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 候选词管理器
 * 负责协调不同的候选词生成策略，根据输入类型选择合适的策略
 */
class CandidateManager(private val repository: DictionaryRepository) {
    
    // 所有策略列表，按照优先级顺序排列
    private val strategies = listOf(
        SingleCharStrategy(repository),               // 单字母输入策略
        ExactSyllableStrategy(repository),            // 精确音节匹配策略
        PinyinCompletionStrategy(repository),         // 拼音补全策略（完整拼音分词成功的情况）
        InitialAbbreviationStrategy(repository),      // 首字母缩写策略
        SyllableSplitStrategyOptimized(repository)    // 优化版音节拆分策略（最后兜底方案）
    )
    
    /**
     * 根据输入生成候选词
     * @param input 用户输入
     * @param limit 候选词数量限制
     * @return 候选词列表
     */
    suspend fun generateCandidates(input: String, limit: Int): List<WordFrequency> = withContext(Dispatchers.IO) {
        // 规范化输入
        val normalizedInput = input.trim().lowercase()
        
        if (normalizedInput.isEmpty()) {
            Timber.d("输入为空，返回空候选词列表")
            return@withContext emptyList()
        }
        
        Timber.d("生成'$normalizedInput'的候选词")
        
        // 选择适用的策略
        val applicableStrategy = selectStrategy(normalizedInput)
        
        // 使用选定的策略生成候选词
        val startTime = System.currentTimeMillis()
        // 确保在IO线程完成候选词查询
        val results = withContext(Dispatchers.IO) {
            applicableStrategy.generateCandidates(normalizedInput, limit)
        }
        val endTime = System.currentTimeMillis()
        
        // 查询完成后记录日志
        val resultSize = results.size
        Timber.d("使用${applicableStrategy.javaClass.simpleName}策略，耗时${endTime - startTime}ms，生成${resultSize}个候选词")
        
        // 如果结果为空或少于预期，尝试使用其他策略
        if (resultSize < 5 && applicableStrategy !is SyllableSplitStrategyOptimized) {
            Timber.d("主策略结果较少(${resultSize})，尝试使用优化版音节拆分策略")
            val backupStrategy = strategies.last() as SyllableSplitStrategyOptimized
            
            // 确保在IO线程完成备选查询
            val backupResults = withContext(Dispatchers.IO) {
                backupStrategy.generateCandidates(normalizedInput, limit - resultSize)
            }
            
            val combinedResults = (results + backupResults)
                .distinctBy { it.word }
                .sortedByDescending { it.frequency }
                .take(limit)
                
            val combinedSize = combinedResults.size
            Timber.d("合并结果后共${combinedSize}个候选词")
            
            // 记录日志输出
            if (combinedSize > 0) {
                val sampleResults = combinedResults.take(kotlin.math.min(3, combinedSize))
                Timber.d("生成候选词样本: ${sampleResults.joinToString { "${it.word}(${it.frequency})" }}")
            } else {
                Timber.d("合并后仍未生成任何候选词")
            }
            
            return@withContext combinedResults
        }
        
        // 记录日志输出
        if (resultSize > 0) {
            val sampleResults = results.take(kotlin.math.min(3, resultSize))
            Timber.d("生成候选词样本: ${sampleResults.joinToString { "${it.word}(${it.frequency})" }}")
        } else {
            Timber.d("未生成任何候选词")
        }
        
        return@withContext results
    }
    
    /**
     * 根据输入选择适用的策略
     */
    private fun selectStrategy(input: String): CandidateStrategy {
        // 预处理：尝试对输入进行优化版智能拆分
        val splitParts = PinyinSegmenterOptimized.cut(input)
        val hasSyllables = splitParts.size > 1 || (splitParts.size == 1 && splitParts[0] != input)
        
        // 记录拆分结果
        if (splitParts.isNotEmpty() && splitParts[0] != input) {
            Timber.d("输入'$input'优化拆分结果: ${splitParts.joinToString(", ")}")
        }
        
        // 特殊处理：检查是否可以完整拼音分词
        val canCompleteSplit = splitParts.size > 1 || (splitParts.size == 1 && splitParts[0] != input)
        
        // 1. 如果是多个音节的完整拼音，直接使用拼音补全策略
        if (canCompleteSplit && input.length > 1) {
            Timber.d("输入'$input'可以完整分词为拼音音节，使用拼音补全策略")
            return strategies[2] // PinyinCompletionStrategy
        }
        
        // 2. 如果包含有效的音节+字母组合，优先使用优化版音节拆分策略
        if (!canCompleteSplit && hasSyllables && splitParts.size > 1) {
            Timber.d("输入'$input'包含音节+字母组合，优先使用优化版音节拆分策略")
            return strategies.last() // SyllableSplitStrategyOptimized 
        }
        
        // 3. 尝试每种策略
        for (strategy in strategies) {
            if (strategy.isApplicable(input)) {
                Timber.d("选择${strategy.javaClass.simpleName}作为'$input'的处理策略")
                return strategy
            }
        }
        
        // 4. 默认使用优化版音节拆分策略（作为兜底方案）
        Timber.d("没有找到适用的策略，使用优化版音节拆分策略作为默认处理'$input'")
        return strategies.last() // SyllableSplitStrategyOptimized
    }
} 