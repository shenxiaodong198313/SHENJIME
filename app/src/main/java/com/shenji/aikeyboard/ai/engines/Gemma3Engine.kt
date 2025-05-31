package com.shenji.aikeyboard.ai.engines

import android.content.Context
import com.shenji.aikeyboard.ai.*
import com.shenji.aikeyboard.llm.LlmManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Gemma3引擎实现
 * 基于MediaPipe LLM的Gemma3-1B-IT模型
 */
class Gemma3Engine(private val context: Context) : AIEngine {
    
    companion object {
        private const val TAG = "Gemma3Engine"
        private const val ENGINE_ID = "gemma3-1b-it"
    }
    
    override val engineInfo = AIEngineInfo(
        name = "Gemma3-1B-IT",
        version = "1.0.0",
        modelSize = 1_000_000_000L, // 约1GB
        capabilities = setOf(
            AICapability.PINYIN_CORRECTION,
            AICapability.TEXT_CONTINUATION,
            AICapability.SEMANTIC_ANALYSIS
        ),
        maxContextLength = 1280,
        averageLatency = 300L
    )
    
    private var llmManager: LlmManager? = null
    private var isInitialized = false
    
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Timber.d("$TAG: 开始初始化Gemma3引擎")
            
            llmManager = LlmManager.getInstance(context)
            val success = llmManager?.initialize() ?: false
            
            isInitialized = success
            
            if (success) {
                Timber.i("$TAG: Gemma3引擎初始化成功")
            } else {
                Timber.e("$TAG: Gemma3引擎初始化失败")
            }
            
            return@withContext success
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Gemma3引擎初始化异常")
            isInitialized = false
            return@withContext false
        }
    }
    
    override suspend fun correctPinyin(
        input: String, 
        context: InputContext
    ): List<CorrectionSuggestion> = withContext(Dispatchers.IO) {
        
        if (!isInitialized || llmManager == null) {
            Timber.w("$TAG: 引擎未初始化，无法进行拼音纠错")
            return@withContext emptyList()
        }
        
        try {
            Timber.d("$TAG: 开始拼音纠错 - 输入: '$input'")
            
            val prompt = buildCorrectionPrompt(input, context)
            val response = llmManager?.generateResponse(prompt)
            
            if (response.isNullOrBlank()) {
                Timber.w("$TAG: AI响应为空")
                return@withContext emptyList()
            }
            
            val suggestions = parseCorrectionResponse(response, input)
            Timber.d("$TAG: 拼音纠错完成 - 生成${suggestions.size}个建议")
            
            return@withContext suggestions
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 拼音纠错异常")
            return@withContext emptyList()
        }
    }
    
    override suspend fun generateContinuation(
        text: String,
        context: InputContext
    ): List<ContinuationSuggestion> = withContext(Dispatchers.IO) {
        
        if (!isInitialized || llmManager == null) {
            Timber.w("$TAG: 引擎未初始化，无法进行续写")
            return@withContext emptyList()
        }
        
        try {
            Timber.d("$TAG: 开始文本续写 - 输入: '$text'")
            
            val prompt = buildContinuationPrompt(text, context)
            val response = llmManager?.generateResponse(prompt)
            
            if (response.isNullOrBlank()) {
                Timber.w("$TAG: AI续写响应为空")
                return@withContext emptyList()
            }
            
            val suggestions = parseContinuationResponse(response)
            Timber.d("$TAG: 文本续写完成 - 生成${suggestions.size}个建议")
            
            return@withContext suggestions
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 文本续写异常")
            return@withContext emptyList()
        }
    }
    
    override suspend fun analyzeSemantics(
        input: String,
        context: InputContext
    ): SemanticAnalysis = withContext(Dispatchers.IO) {
        
        if (!isInitialized || llmManager == null) {
            Timber.w("$TAG: 引擎未初始化，无法进行语义分析")
            return@withContext SemanticAnalysis(
                intent = "unknown",
                sentiment = Sentiment.NEUTRAL,
                topics = emptyList(),
                confidence = 0.0f
            )
        }
        
        try {
            Timber.d("$TAG: 开始语义分析 - 输入: '$input'")
            
            val prompt = buildSemanticPrompt(input, context)
            val response = llmManager?.generateResponse(prompt)
            
            if (response.isNullOrBlank()) {
                Timber.w("$TAG: AI语义分析响应为空")
                return@withContext SemanticAnalysis(
                    intent = "unknown",
                    sentiment = Sentiment.NEUTRAL,
                    topics = emptyList(),
                    confidence = 0.0f
                )
            }
            
            val analysis = parseSemanticResponse(response)
            Timber.d("$TAG: 语义分析完成 - 意图: ${analysis.intent}")
            
            return@withContext analysis
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 语义分析异常")
            return@withContext SemanticAnalysis(
                intent = "unknown",
                sentiment = Sentiment.NEUTRAL,
                topics = emptyList(),
                confidence = 0.0f
            )
        }
    }
    
    override suspend fun release() = withContext(Dispatchers.IO) {
        try {
            Timber.d("$TAG: 释放Gemma3引擎资源")
            llmManager?.release()
            llmManager = null
            isInitialized = false
            Timber.i("$TAG: Gemma3引擎资源已释放")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 释放Gemma3引擎资源异常")
        }
    }
    
    /**
     * 构建拼音纠错提示词
     */
    private fun buildCorrectionPrompt(input: String, context: InputContext): String {
        return """
你是一个中文拼音纠错专家。用户输入了拼音"$input"，但可能有错误。

上下文信息：
- 前文：${context.previousText}
- 应用：${context.appPackage}

请分析这个拼音是否有错误，如果有错误请提供正确的词语建议。
常见错误类型：
1. 声母混淆：zh/z, ch/c, sh/s, n/l
2. 韵母错误：an/ang, en/eng, in/ing
3. 音调缺失
4. 打字错误

请按以下格式返回（每行一个建议）：
[词语]|[正确拼音]|[置信度0-1]|[错误类型]

示例：
你好|nǐhǎo|0.9|TONE_MISSING
        """.trimIndent()
    }
    
    /**
     * 构建续写提示词
     */
    private fun buildContinuationPrompt(text: String, context: InputContext): String {
        return """
请为以下文本提供3个合适的续写建议：

文本：$text
上下文：${context.previousText}

要求：
1. 续写要自然流畅
2. 符合上下文语境
3. 长度适中（5-15字）

请按以下格式返回：
[续写文本]|[置信度0-1]|[类型]

类型包括：WORD_COMPLETION, SENTENCE_COMPLETION, PARAGRAPH_CONTINUATION
        """.trimIndent()
    }
    
    /**
     * 构建语义分析提示词
     */
    private fun buildSemanticPrompt(input: String, context: InputContext): String {
        return """
请分析以下文本的语义：

文本：$input
上下文：${context.previousText}

请分析：
1. 用户意图
2. 情感倾向（POSITIVE/NEGATIVE/NEUTRAL）
3. 主题标签
4. 分析置信度

请按以下格式返回：
[意图]|[情感]|[主题1,主题2,主题3]|[置信度0-1]
        """.trimIndent()
    }
    
    /**
     * 解析拼音纠错响应
     */
    private fun parseCorrectionResponse(response: String, originalInput: String): List<CorrectionSuggestion> {
        val suggestions = mutableListOf<CorrectionSuggestion>()
        
        try {
            response.lines().forEach { line ->
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val word = parts[0].trim()
                    val pinyin = parts[1].trim()
                    val confidence = parts[2].trim().toFloatOrNull() ?: 0.5f
                    val errorTypeStr = parts[3].trim()
                    
                    val errorType = try {
                        ErrorType.valueOf(errorTypeStr)
                    } catch (e: Exception) {
                        ErrorType.UNKNOWN
                    }
                    
                    if (word.isNotBlank() && pinyin.isNotBlank()) {
                        suggestions.add(
                            CorrectionSuggestion(
                                originalInput = originalInput,
                                correctedText = word,
                                correctedPinyin = pinyin,
                                confidence = confidence.coerceIn(0.0f, 1.0f),
                                errorType = errorType,
                                explanation = null
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 解析纠错响应异常")
        }
        
        return suggestions.sortedByDescending { it.confidence }.take(5)
    }
    
    /**
     * 解析续写响应
     */
    private fun parseContinuationResponse(response: String): List<ContinuationSuggestion> {
        val suggestions = mutableListOf<ContinuationSuggestion>()
        
        try {
            response.lines().forEach { line ->
                val parts = line.split("|")
                if (parts.size >= 3) {
                    val text = parts[0].trim()
                    val confidence = parts[1].trim().toFloatOrNull() ?: 0.5f
                    val typeStr = parts[2].trim()
                    
                    val type = try {
                        ContinuationType.valueOf(typeStr)
                    } catch (e: Exception) {
                        ContinuationType.WORD_COMPLETION
                    }
                    
                    if (text.isNotBlank()) {
                        suggestions.add(
                            ContinuationSuggestion(
                                text = text,
                                confidence = confidence.coerceIn(0.0f, 1.0f),
                                type = type
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 解析续写响应异常")
        }
        
        return suggestions.sortedByDescending { it.confidence }.take(3)
    }
    
    /**
     * 解析语义分析响应
     */
    private fun parseSemanticResponse(response: String): SemanticAnalysis {
        try {
            val parts = response.trim().split("|")
            if (parts.size >= 4) {
                val intent = parts[0].trim()
                val sentimentStr = parts[1].trim()
                val topicsStr = parts[2].trim()
                val confidence = parts[3].trim().toFloatOrNull() ?: 0.5f
                
                val sentiment = try {
                    Sentiment.valueOf(sentimentStr)
                } catch (e: Exception) {
                    Sentiment.NEUTRAL
                }
                
                val topics = topicsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                
                return SemanticAnalysis(
                    intent = intent,
                    sentiment = sentiment,
                    topics = topics,
                    confidence = confidence.coerceIn(0.0f, 1.0f)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 解析语义分析响应异常")
        }
        
        return SemanticAnalysis(
            intent = "unknown",
            sentiment = Sentiment.NEUTRAL,
            topics = emptyList(),
            confidence = 0.0f
        )
    }
} 