package com.shenji.aikeyboard.ai

/**
 * AI引擎标准接口
 * 支持多种AI模型的统一接入
 */
interface AIEngine {
    
    /**
     * 引擎基本信息
     */
    val engineInfo: AIEngineInfo
    
    /**
     * 初始化引擎
     */
    suspend fun initialize(): Boolean
    
    /**
     * 拼音纠错
     * @param input 用户输入的拼音
     * @param context 上下文信息
     * @return 纠错建议列表
     */
    suspend fun correctPinyin(
        input: String, 
        context: InputContext
    ): List<CorrectionSuggestion>
    
    /**
     * 智能续写
     * @param text 已输入文本
     * @param context 上下文信息
     * @return 续写建议列表
     */
    suspend fun generateContinuation(
        text: String,
        context: InputContext
    ): List<ContinuationSuggestion>
    
    /**
     * 语义理解
     * @param input 用户输入
     * @param context 上下文信息
     * @return 语义分析结果
     */
    suspend fun analyzeSemantics(
        input: String,
        context: InputContext
    ): SemanticAnalysis
    
    /**
     * 释放资源
     */
    suspend fun release()
} 