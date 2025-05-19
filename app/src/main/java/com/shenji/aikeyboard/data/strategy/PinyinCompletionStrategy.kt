package com.shenji.aikeyboard.data.strategy

import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.data.WordFrequency
import com.shenji.aikeyboard.utils.PinyinSegmenterOptimized
import timber.log.Timber

/**
 * 拼音补全策略
 * 处理输入长度>1且符合拼音音节的场景
 */
class PinyinCompletionStrategy(private val repository: DictionaryRepository) : CandidateStrategy {
    
    /**
     * 判断是否适用于拼音补全策略
     * 条件：输入长度>1，且输入内容完全符合拼音音节
     */
    override fun isApplicable(input: String): Boolean {
        if (input.length <= 1) return false
        
        // 尝试使用优化版分词器进行分词
        val syllables = PinyinSegmenterOptimized.cut(input)
        val isValid = syllables.size > 1 || (syllables.size == 1 && syllables[0] != input)
        
        if (isValid) {
            val splitResult = syllables.joinToString(" ")
            Timber.d("检测到拼音输入: '$input' -> '$splitResult'，使用拼音补全策略")
        }
        
        return isValid
    }
    
    /**
     * 生成候选词
     * 采用分阶段查询：
     * 1. 根据输入长度确定查询策略
     * 2. 查询不同词典并按权重排序
     */
    override suspend fun generateCandidates(input: String, limit: Int): List<WordFrequency> {
        // 使用优化版分词器规范化拼音
        val syllables = PinyinSegmenterOptimized.cut(input)
        val normalizedPinyin = syllables.joinToString(" ")
        Timber.d("拼音补全策略处理输入: '$input' -> '$normalizedPinyin'")
        
        val results = mutableListOf<WordFrequency>()
        
        try {
            // 根据输入长度应用不同的查询策略
            when {
                // 2-3个字符，优先查询单字和基础词典
                input.length <= 3 -> {
                    val basicResults = repository.searchBasicEntries(
                        pinyin = normalizedPinyin,
                        limit = limit,
                        excludeTypes = listOf("associational", "poetry")  // 排除大词典
                    )
                    results.addAll(basicResults)
                }
                
                // 4-5个字符，优先查询基础和关联词典
                input.length <= 5 -> {
                    val midResults = repository.searchBasicEntries(
                        pinyin = normalizedPinyin,
                        limit = limit,
                        excludeTypes = listOf("poetry")  // 排除诗词词典
                    )
                    results.addAll(midResults)
                }
                
                // 超过5个字符，查询所有词典
                else -> {
                    val fullResults = repository.searchBasicEntries(
                        pinyin = normalizedPinyin,
                        limit = limit
                    )
                    results.addAll(fullResults)
                }
            }
            
            Timber.d("拼音补全'$normalizedPinyin'查询结果: ${
                results.take(5).joinToString { it.word }
            }${if (results.size > 5) "..." else ""}")
            
        } catch (e: Exception) {
            Timber.e(e, "拼音补全策略查询异常: ${e.message}")
        }
        
        // 按词频排序
        return results.sortedByDescending { it.frequency }
    }
} 