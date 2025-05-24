package com.shenji.aikeyboard.keyboard

import com.shenji.aikeyboard.model.WordFrequency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 简化的拼音输入法适配器
 * 
 * 设计原则：
 * 1. 只使用OptimizedCandidateEngine
 * 2. 删除所有复杂的回退逻辑
 * 3. 简单直接的接口
 */
class PinyinIMEAdapter private constructor() {
    
    private val optimizedEngine = OptimizedCandidateEngine.getInstance()
    
    /**
     * 获取候选词列表
     */
    suspend fun getCandidates(input: String, limit: Int = 20): List<WordFrequency> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val results = optimizedEngine.getCandidates(input, limit)
            val endTime = System.currentTimeMillis()
            
            Timber.d("PinyinIMEAdapter: 查询'$input'返回${results.size}个候选词，耗时${endTime - startTime}ms")
            return@withContext results
            
        } catch (e: Exception) {
            Timber.e(e, "PinyinIMEAdapter: 查询失败")
            return@withContext emptyList()
        }
    }
    
    /**
     * 拼音分词
     */
    fun splitPinyin(input: String): List<String> {
        return optimizedEngine.splitPinyin(input)
    }
    
    /**
     * 获取性能统计信息
     */
    fun getPerformanceStats(): String {
        return optimizedEngine.getPerformanceStats()
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        optimizedEngine.clearCache()
        Timber.d("PinyinIMEAdapter: 缓存已清理")
    }
    
    companion object {
        @Volatile
        private var INSTANCE: PinyinIMEAdapter? = null
        
        fun getInstance(): PinyinIMEAdapter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PinyinIMEAdapter().also { INSTANCE = it }
            }
        }
    }
} 