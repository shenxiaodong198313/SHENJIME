package com.shenji.aikeyboard.data.strategy

import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.data.WordFrequency
import timber.log.Timber

/**
 * 单字母输入策略
 * 处理输入长度为1的场景，直接查询以该字母开头的拼音的单字
 */
class SingleCharStrategy(private val repository: DictionaryRepository) : CandidateStrategy {
    
    /**
     * 判断是否适用于单字母输入
     */
    override fun isApplicable(input: String): Boolean {
        val isValid = input.length == 1 && input.matches(Regex("[a-z]"))
        if (isValid) {
            Timber.d("检测到单字母输入: '$input'，使用单字母策略")
        }
        return isValid
    }
    
    /**
     * 生成候选词
     * 优化逻辑：
     * 1. 直接查询以该字母开头的拼音，无需先匹配音节表
     * 2. 仅查询chars词典中的单字
     * 3. 返回最高词频的单字
     */
    override suspend fun generateCandidates(input: String, limit: Int): List<WordFrequency> {
        Timber.d("单字母策略处理输入: '$input'")
        
        try {
            // 直接查询以该字母开头的拼音的单字
            val startTime = System.currentTimeMillis()
            
            val singleChars = repository.searchDirectlyByInitial(
                initial = input,
                limit = limit,
                onlyChars = true  // 只查询单字词典
            )
            
            val endTime = System.currentTimeMillis()
            Timber.d("单字母'$input'查询耗时: ${endTime - startTime}ms，找到${singleChars.size}个候选词")
            
            return singleChars
            
        } catch (e: Exception) {
            Timber.e(e, "单字母策略查询异常: ${e.message}")
            return emptyList()
        }
    }
} 