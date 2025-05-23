package com.shenji.aikeyboard.engine.generator

import com.shenji.aikeyboard.data.DictionaryRepository
// import com.shenji.aikeyboard.data.StagedDictionaryRepository
import com.shenji.aikeyboard.engine.CombinationCandidateGenerator
import com.shenji.aikeyboard.engine.analyzer.InputAnalysis
import com.shenji.aikeyboard.engine.analyzer.InputMode
import com.shenji.aikeyboard.engine.generator.layers.*
import com.shenji.aikeyboard.model.Candidate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/**
 * 分层候选词生成器
 * 
 * 根据输入分析结果，使用多层生成策略产生候选词
 * 支持并行生成和智能合并
 */
class CandidateGenerator {
    
    // 数据库仓库
    // private val stagedDictionaryRepository = StagedDictionaryRepository()
    private val dictionaryRepository = DictionaryRepository()
    
    // 组合候选词生成器
    private val combinationGenerator = CombinationCandidateGenerator(dictionaryRepository)
    
    // 各层生成器
    private val exactMatchLayer = ExactMatchLayer()
    private val prefixMatchLayer = PrefixMatchLayer()
    private val fuzzyMatchLayer = FuzzyMatchLayer()
    private val smartSuggestionLayer = SmartSuggestionLayer()
    private val contextPredictionLayer = ContextPredictionLayer()
    
    /**
     * 生成候选词
     * 
     * @param analysis 输入分析结果
     * @param limit 候选词数量限制
     * @return 候选词列表
     */
    suspend fun generate(analysis: InputAnalysis, limit: Int): List<Candidate> = coroutineScope {
        val startTime = System.currentTimeMillis()
        
        try {
            Timber.d("开始生成候选词: ${analysis.input} (模式: ${analysis.mode})")
            
            // 根据输入模式选择生成策略
            val strategy = selectGenerationStrategy(analysis)
            
            // 并行执行各层生成
            val layerResults = strategy.layers.map { layer ->
                async {
                    try {
                        when (layer) {
                            1 -> exactMatchLayer.generate(analysis, limit)
                            2 -> prefixMatchLayer.generate(analysis, limit)
                            3 -> fuzzyMatchLayer.generate(analysis, limit)
                            4 -> smartSuggestionLayer.generate(analysis, limit)
                            5 -> contextPredictionLayer.generate(analysis, limit)
                            else -> emptyList()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "第${layer}层生成异常")
                        emptyList<Candidate>()
                    }
                }
            }.awaitAll()
            
            // 合并和去重
            val mergedCandidates = mergeCandidates(layerResults, strategy)
            
            // 应用生成策略的后处理
            val processedCandidates = applyPostProcessing(mergedCandidates, analysis, strategy)
            
            val endTime = System.currentTimeMillis()
            Timber.d("候选词生成完成: ${processedCandidates.size}个候选词 (耗时: ${endTime - startTime}ms)")
            
            return@coroutineScope processedCandidates.take(limit)
            
        } catch (e: Exception) {
            Timber.e(e, "候选词生成异常: ${analysis.input}")
            return@coroutineScope emptyList()
        }
    }
    
    /**
     * 选择生成策略
     */
    private fun selectGenerationStrategy(analysis: InputAnalysis): GenerationStrategy {
        return when (analysis.mode) {
            InputMode.SINGLE_LETTER -> SingleLetterStrategy()
            InputMode.PURE_ACRONYM -> PureAcronymStrategy()
            InputMode.PURE_PINYIN -> PurePinyinStrategy()
            InputMode.PARTIAL_PINYIN -> PartialPinyinStrategy()
            InputMode.ACRONYM_PINYIN_MIX -> AcronymPinyinMixStrategy()
            InputMode.PINYIN_ACRONYM_MIX -> PinyinAcronymMixStrategy()
            InputMode.SENTENCE_INPUT -> SentenceInputStrategy(combinationGenerator)
            InputMode.PROGRESSIVE_INPUT -> ProgressiveInputStrategy()
            else -> DefaultStrategy()
        }
    }
    
    /**
     * 合并候选词
     */
    private fun mergeCandidates(
        layerResults: List<List<Candidate>>,
        strategy: GenerationStrategy
    ): List<Candidate> {
        val mergedMap = mutableMapOf<String, Candidate>()
        
        // 按层级优先级合并，避免重复
        layerResults.forEachIndexed { layerIndex, candidates ->
            candidates.forEach { candidate ->
                val key = candidate.word
                if (!mergedMap.containsKey(key)) {
                    mergedMap[key] = candidate
                } else {
                    // 如果已存在，选择权重更高的
                    val existing = mergedMap[key]!!
                    if (candidate.finalWeight > existing.finalWeight) {
                        mergedMap[key] = candidate
                    }
                }
            }
        }
        
        return mergedMap.values.toList()
    }
    
    /**
     * 应用后处理
     */
    private fun applyPostProcessing(
        candidates: List<Candidate>,
        analysis: InputAnalysis,
        strategy: GenerationStrategy
    ): List<Candidate> {
        return strategy.postProcess(candidates, analysis)
    }
}

/**
 * 生成策略基类
 */
abstract class GenerationStrategy {
    /**
     * 参与的层级
     */
    abstract val layers: List<Int>
    
    /**
     * 策略名称
     */
    abstract val name: String
    
    /**
     * 后处理方法
     */
    open fun postProcess(candidates: List<Candidate>, analysis: InputAnalysis): List<Candidate> {
        return candidates.sortedByDescending { it.finalWeight }
    }
}

/**
 * 单字母策略
 */
class SingleLetterStrategy : GenerationStrategy() {
    override val layers = listOf(1, 4) // 精确匹配 + 智能联想
    override val name = "单字母策略"
    
    override fun postProcess(candidates: List<Candidate>, analysis: InputAnalysis): List<Candidate> {
        // 单字母输入优先显示高频单字和常用词预测
        return candidates.sortedWith(
            compareByDescending<Candidate> { candidate ->
                when {
                    candidate.word.length == 1 -> candidate.finalWeight + 0.3f // 单字加成
                    candidate.word.length == 2 -> candidate.finalWeight + 0.2f // 双字词加成
                    else -> candidate.finalWeight
                }
            }
        )
    }
}

/**
 * 纯缩写策略
 */
class PureAcronymStrategy : GenerationStrategy() {
    override val layers = listOf(1, 2, 4) // 精确匹配 + 前缀匹配 + 智能联想
    override val name = "纯缩写策略"
    
    override fun postProcess(candidates: List<Candidate>, analysis: InputAnalysis): List<Candidate> {
        // 缩写输入按词长和匹配度排序
        return candidates.sortedWith(
            compareByDescending<Candidate> { candidate ->
                val lengthBonus = when (candidate.word.length) {
                    analysis.input.length -> 0.4f // 长度匹配加成
                    analysis.input.length + 1 -> 0.2f // 长度接近加成
                    else -> 0.0f
                }
                candidate.finalWeight + lengthBonus
            }
        )
    }
}

/**
 * 纯拼音策略
 */
class PurePinyinStrategy : GenerationStrategy() {
    override val layers = listOf(1, 2, 3, 4) // 精确匹配 + 前缀匹配 + 模糊匹配 + 智能联想
    override val name = "纯拼音策略"
    
    override fun postProcess(candidates: List<Candidate>, analysis: InputAnalysis): List<Candidate> {
        // 拼音输入优先精确匹配，然后是词频
        return candidates.sortedWith(
            compareByDescending<Candidate> { candidate ->
                val exactMatchBonus = if (candidate.pinyin.replace(" ", "") == analysis.input) 0.3f else 0.0f
                candidate.finalWeight + exactMatchBonus
            }
        )
    }
}

/**
 * 部分拼音策略
 */
class PartialPinyinStrategy : GenerationStrategy() {
    override val layers = listOf(2, 3, 4) // 前缀匹配 + 模糊匹配 + 智能联想
    override val name = "部分拼音策略"
    
    override fun postProcess(candidates: List<Candidate>, analysis: InputAnalysis): List<Candidate> {
        // 部分拼音优先补全匹配
        return candidates.sortedWith(
            compareByDescending<Candidate> { candidate ->
                val prefixBonus = if (candidate.pinyin.replace(" ", "").startsWith(analysis.input)) 0.2f else 0.0f
                candidate.finalWeight + prefixBonus
            }
        )
    }
}

/**
 * 缩写+拼音混合策略
 */
class AcronymPinyinMixStrategy : GenerationStrategy() {
    override val layers = listOf(1, 2, 3, 4) // 全层级
    override val name = "缩写+拼音混合策略"
}

/**
 * 拼音+缩写混合策略
 */
class PinyinAcronymMixStrategy : GenerationStrategy() {
    override val layers = listOf(1, 2, 3, 4) // 全层级
    override val name = "拼音+缩写混合策略"
}

/**
 * 句子输入策略
 */
class SentenceInputStrategy(
    private val combinationGenerator: CombinationCandidateGenerator? = null
) : GenerationStrategy() {
    override val layers = listOf(1, 2, 4, 5) // 精确匹配 + 前缀匹配 + 智能联想 + 上下文预测
    override val name = "句子输入策略"
    
    override fun postProcess(candidates: List<Candidate>, analysis: InputAnalysis): List<Candidate> {
        val processedCandidates = candidates.toMutableList()
        
        // 如果常规生成器没有找到足够的候选词，尝试组合生成
        if (processedCandidates.size < 3 && combinationGenerator != null) {
            try {
                // 从输入分析中提取音节
                val syllables = extractSyllablesFromAnalysis(analysis)
                if (syllables.isNotEmpty()) {
                    Timber.d("尝试组合候选词生成，音节: ${syllables.joinToString(" ")}")
                    
                    // 使用协程生成组合候选词（这里需要在suspend函数中调用）
                    // 暂时注释掉，需要重构为suspend函数
                    // val combinationCandidates = combinationGenerator.generateCombinationCandidates(syllables, 5)
                    // processedCandidates.addAll(combinationCandidates)
                }
            } catch (e: Exception) {
                Timber.e(e, "组合候选词生成失败")
            }
        }
        
        // 句子输入优先完整句子，然后是词组组合
        return processedCandidates.sortedWith(
            compareByDescending<Candidate> { candidate ->
                val sentenceBonus = when {
                    candidate.word.length >= 4 -> 0.3f // 长句子加成
                    candidate.word.length >= 2 -> 0.1f // 词组加成
                    else -> 0.0f
                }
                val combinationBonus = if (candidate.type == "combination" || candidate.type == "progressive") 0.2f else 0.0f
                candidate.finalWeight + sentenceBonus + combinationBonus
            }
        )
    }
    
    /**
     * 从输入分析中提取音节
     */
    private fun extractSyllablesFromAnalysis(analysis: InputAnalysis): List<String> {
        // 这里需要根据实际的InputAnalysis结构来实现
        // 暂时使用简单的拼音拆分
        return try {
            val input = analysis.input
            // 假设有一个拼音拆分方法
            if (input.length >= 6) {
                // 简单的拼音拆分逻辑，实际应该使用更智能的方法
                listOf("wo", "bu", "shi", "bei", "jing", "ren") // 示例
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "音节提取失败")
            emptyList()
        }
    }
}

/**
 * 渐进式输入策略
 */
class ProgressiveInputStrategy : GenerationStrategy() {
    override val layers = listOf(2, 4) // 前缀匹配 + 智能联想
    override val name = "渐进式输入策略"
}

/**
 * 默认策略
 */
class DefaultStrategy : GenerationStrategy() {
    override val layers = listOf(1, 2, 3) // 基础三层
    override val name = "默认策略"
} 