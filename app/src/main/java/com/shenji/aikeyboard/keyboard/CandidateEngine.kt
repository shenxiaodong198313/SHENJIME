package com.shenji.aikeyboard.keyboard

import com.shenji.aikeyboard.model.WordFrequency

/**
 * 候选词引擎接口
 * 
 * 统一的候选词查询接口，解耦输入法和具体引擎实现
 * 支持分页查询、拼音分段、性能统计等核心功能
 */
interface CandidateEngine {
    
    /**
     * 获取候选词列表
     * 
     * @param input 输入的拼音字符串
     * @param limit 返回候选词数量限制
     * @param offset 分页偏移量，用于懒加载
     * @return 候选词列表，按优先级排序
     */
    suspend fun getCandidates(
        input: String, 
        limit: Int = 20, 
        offset: Int = 0
    ): List<WordFrequency>
    
    /**
     * 获取拼音分段结果
     * 
     * @param input 输入的拼音字符串
     * @return 分段后的音节列表
     */
    fun getSegments(input: String): List<String>
    
    /**
     * 清理缓存
     */
    fun clearCache()
    
    /**
     * 获取性能统计信息
     * 
     * @return 性能统计字符串
     */
    fun getPerformanceStats(): String
} 