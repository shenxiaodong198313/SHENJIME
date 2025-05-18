package com.shenji.aikeyboard.data.strategy

import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.data.PinyinSplitter
import com.shenji.aikeyboard.data.WordFrequency
import timber.log.Timber

/**
 * 精确音节匹配策略
 * 处理输入完全匹配标准音节的场景，如"ta"、"hao"等
 */
class ExactSyllableStrategy(private val repository: DictionaryRepository) : CandidateStrategy {
    
    // 拼音分词器，用于获取和验证拼音音节
    private val pinyinSplitter = PinyinSplitter()
    
    /**
     * 判断输入是否为完整的标准音节
     */
    override fun isApplicable(input: String): Boolean {
        // 获取所有有效音节
        val validSyllables = pinyinSplitter.getPinyinSyllables()
        val isExactSyllable = input in validSyllables
        
        if (isExactSyllable) {
            Timber.d("输入'$input'是完整音节，使用精确音节匹配策略")
        }
        
        return isExactSyllable
    }
    
    /**
     * 生成候选词
     * 1. 查询该精确音节的单字（最多取前3个）
     * 2. 查询以该音节开头的常用词组（最多取前10个）
     */
    override suspend fun generateCandidates(input: String, limit: Int): List<WordFrequency> {
        Timber.d("精确音节匹配策略处理输入: '$input'")
        
        val results = mutableListOf<WordFrequency>()
        
        try {
            val startTime = System.currentTimeMillis()
            
            // 1. 查询精确匹配该音节的单字（仅限chars词典）
            val singleChars = repository.searchSingleCharsForSyllable(
                syllable = input,
                maxResults = 3  // 每个音节最多3个最高频单字
            )
            
            results.addAll(singleChars)
            Timber.d("音节'$input'单字查询结果: ${singleChars.joinToString { it.word }}")
            
            // 2. 查询以该音节开头的词组
            val combinedWords = repository.searchCombinedWordsForSyllable(
                syllable = input,
                maxResults = limit - results.size
            )
            
            results.addAll(combinedWords)
            Timber.d("音节'$input'词组查询结果: ${combinedWords.take(5).joinToString { it.word }}${if (combinedWords.size > 5) "..." else ""}")
            
            val endTime = System.currentTimeMillis()
            Timber.d("精确音节匹配查询耗时: ${endTime - startTime}ms，找到${results.size}个结果")
            
        } catch (e: Exception) {
            Timber.e(e, "精确音节匹配策略查询异常: ${e.message}")
        }
        
        return results
    }
} 