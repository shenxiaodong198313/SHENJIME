package com.shenji.aikeyboard.keyboard

import com.shenji.aikeyboard.model.WordFrequency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 输入法引擎适配器
 * 
 * 使用CandidateEngine接口，解耦输入法和具体引擎实现
 * 为输入法提供简洁的候选词查询接口
 */
class InputMethodEngineAdapter private constructor() {
    
    // 使用接口而不是具体实现，便于切换引擎
    private val engine: CandidateEngine = SmartPinyinEngine.getInstance()
    
    /**
     * 获取候选词列表
     * 
     * @param input 输入的拼音字符串
     * @param limit 返回候选词数量限制
     * @return 候选词列表，按优先级排序
     */
    suspend fun getCandidates(input: String, limit: Int = 20): List<WordFrequency> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val results = engine.getCandidates(input, limit, 0)
            val endTime = System.currentTimeMillis()
            
            Timber.d("🎯 InputMethodEngine: 查询'$input'返回${results.size}个候选词，耗时${endTime - startTime}ms")
            return@withContext results
            
        } catch (e: Exception) {
            Timber.e(e, "🎯 InputMethodEngine: 查询失败")
            return@withContext emptyList()
        }
    }
    
    /**
     * 获取拼音分段
     * 
     * @param input 输入的拼音字符串
     * @return 分段后的音节列表
     */
    fun getSegments(input: String): List<String> {
        return try {
            engine.getSegments(input)
        } catch (e: Exception) {
            Timber.w(e, "🎯 InputMethodEngine: 分段失败，使用简单分段")
            listOf(input)
        }
    }
    
    /**
     * 获取性能统计信息
     */
    fun getPerformanceStats(): String {
        return engine.getPerformanceStats()
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        engine.clearCache()
        Timber.d("🎯 InputMethodEngine: 缓存已清理")
    }
    
    companion object {
        @Volatile
        private var INSTANCE: InputMethodEngineAdapter? = null
        
        fun getInstance(): InputMethodEngineAdapter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InputMethodEngineAdapter().also { INSTANCE = it }
            }
        }
    }
} 