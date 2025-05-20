package com.shenji.aikeyboard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

// 调试信息类型别名，方便外部引用
typealias DebugInfo = StagedDictionaryRepository.DebugInfo

/**
 * 候选词管理器
 * 负责协调候选词生成和查询
 */
class CandidateManager(private val repository: DictionaryRepository) {
    
    // 分阶段查询仓库
    private val stagedRepository = StagedDictionaryRepository()
    
    // 调试信息
    val debugInfo: DebugInfo get() = stagedRepository.debugInfo
    
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
        
        // 使用分阶段查询生成候选词
        val startTime = System.currentTimeMillis()
        val results = stagedRepository.queryCandidates(normalizedInput, limit)
        val endTime = System.currentTimeMillis()
        
        // 记录查询时间
        val resultSize = results.size
        Timber.d("使用分阶段查询，耗时${endTime - startTime}ms，生成${resultSize}个候选词")
        
        return@withContext results
    }
} 