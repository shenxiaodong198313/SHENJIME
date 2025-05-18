package com.shenji.aikeyboard.data.strategy

import com.shenji.aikeyboard.data.WordFrequency

/**
 * 候选词生成策略接口
 * 定义不同输入场景下候选词生成的方法
 */
interface CandidateStrategy {
    /**
     * 根据输入生成候选词列表
     * @param input 用户输入
     * @param limit 候选词数量限制
     * @return 候选词列表
     */
    suspend fun generateCandidates(input: String, limit: Int): List<WordFrequency>
    
    /**
     * 判断该策略是否适用于当前输入
     * @param input 用户输入
     * @return 是否适用
     */
    fun isApplicable(input: String): Boolean
} 