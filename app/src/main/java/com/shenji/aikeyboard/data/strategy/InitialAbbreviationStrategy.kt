package com.shenji.aikeyboard.data.strategy

import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.data.WordFrequency
import timber.log.Timber

/**
 * 首字母缩写匹配策略
 * 处理首字母缩写输入，例如"bj"可匹配"beijing"等
 */
class InitialAbbreviationStrategy(private val repository: DictionaryRepository) : CandidateStrategy {
    
    /**
     * 判断是否适用于首字母缩写
     * 条件：
     * 1. 输入长度>=2 (单字母应由SingleCharStrategy处理)
     * 2. 不包含空格
     * 3. 全部是小写字母
     */
    override fun isApplicable(input: String): Boolean {
        val isAbbreviation = input.length >= 2 && 
                            !input.contains(' ') && 
                            input.all { it.isLowerCase() }
        
        if (isAbbreviation) {
            Timber.d("输入'$input'可能是首字母缩写，使用首字母缩写匹配策略")
        }
        
        return isAbbreviation
    }
    
    /**
     * 生成候选词
     * 策略：
     * 1. 查询initialLetters字段完全匹配的词条
     * 2. 如果结果不足，补充查询initialLetters字段包含该输入的词条
     */
    override suspend fun generateCandidates(input: String, limit: Int): List<WordFrequency> {
        Timber.d("首字母缩写策略处理输入: '$input'")
        
        try {
            val startTime = System.currentTimeMillis()
            
            // 查询匹配的首字母缩写词条
            val abbreviationMatches = repository.searchByInitialLetters(
                initials = input,
                limit = limit
            )
            
            val endTime = System.currentTimeMillis()
            Timber.d("首字母缩写'$input'查询耗时: ${endTime - startTime}ms，找到${abbreviationMatches.size}个结果")
            
            // 确保查询结果已完全返回后再记录日志
            if (abbreviationMatches.isNotEmpty()) {
                // 记录找到的候选词
                Timber.d("首字母缩写'$input'查询结果: ${
                    abbreviationMatches.take(5).joinToString { 
                        "${it.word}(${it.frequency})" 
                    }
                }${if (abbreviationMatches.size > 5) "..." else ""}")
            } else {
                // 如果没有找到候选词，记录可能的原因
                Timber.d("首字母缩写'$input'未找到匹配词条，可能原因：1)词库中没有匹配的词条；2)initialLetters索引未完全建立")
                
                // 尝试检查索引字段情况
                val indexStatus = repository.checkInitialLettersIndex(input)
                Timber.d("首字母索引检查: $indexStatus")
            }
            
            return abbreviationMatches
            
        } catch (e: Exception) {
            Timber.e(e, "首字母缩写策略查询异常: ${e.message}")
            return emptyList()
        }
    }
} 