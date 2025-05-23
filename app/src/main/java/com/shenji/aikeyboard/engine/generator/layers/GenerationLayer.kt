package com.shenji.aikeyboard.engine.generator.layers

import com.shenji.aikeyboard.engine.analyzer.InputAnalysis
import com.shenji.aikeyboard.model.Candidate

/**
 * 生成层接口
 * 
 * 定义候选词生成层的基本契约
 */
interface GenerationLayer {
    /**
     * 生成候选词
     * 
     * @param analysis 输入分析结果
     * @param limit 候选词数量限制
     * @return 候选词列表
     */
    suspend fun generate(analysis: InputAnalysis, limit: Int): List<Candidate>
} 