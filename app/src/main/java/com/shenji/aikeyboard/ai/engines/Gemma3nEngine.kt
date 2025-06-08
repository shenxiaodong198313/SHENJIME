package com.shenji.aikeyboard.ai.engines

import android.content.Context
import android.graphics.Bitmap
import com.shenji.aikeyboard.ai.*
import com.shenji.aikeyboard.llm.LlmManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Gemma3n引擎实现
 * 基于LiteRT的Gemma-3n-E4B-IT模型
 */
class Gemma3nEngine(private val context: Context) : AIEngine {
    
    companion object {
        private const val TAG = "Gemma3nEngine"
        private const val ENGINE_ID = "gemma-3n-e4b-it"
        private const val MODEL_ASSET_PATH = "llm_models/gemma3n-e4b-it/gemma-3n-E4B-it-int4.task"
        private const val MODEL_FILE_NAME = "gemma3n-e4b-it-gemma-3n-E4B-it-int4.task"
    }
    
    override val engineInfo = AIEngineInfo(
        name = "Gemma-3n-E4B-IT",
        version = "1.0.0",
        modelSize = 4_410_000_000L, // 约4.41GB
        capabilities = setOf(
            AICapability.PINYIN_CORRECTION,
            AICapability.TEXT_CONTINUATION,
            AICapability.SEMANTIC_ANALYSIS,
            AICapability.MULTIMODAL_ANALYSIS
        ),
        maxContextLength = 2048,
        averageLatency = 250L
    )
    
    private var llmManager: LlmManager? = null
    private var isInitialized = false
    
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Timber.d("$TAG: 开始初始化Gemma3n引擎")
            
            // 创建专用的LlmManager实例用于Gemma3n模型
            llmManager = LlmManager.getInstance(context)
            
            // 初始化Gemma3n模型
            val success = llmManager?.initializeGemma3n() ?: false
            
            isInitialized = success
            
            if (success) {
                Timber.i("$TAG: Gemma3n引擎初始化成功")
            } else {
                Timber.e("$TAG: Gemma3n引擎初始化失败")
            }
            
            return@withContext success
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Gemma3n引擎初始化异常")
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
                Timber.w("$TAG: AI纠错响应为空")
                return@withContext emptyList()
            }
            
            val suggestions = parseCorrectionResponse(response)
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
            Timber.d("$TAG: 释放Gemma3n引擎资源")
            llmManager?.release()
            llmManager = null
            isInitialized = false
            Timber.i("$TAG: Gemma3n引擎资源已释放")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 释放Gemma3n引擎资源异常")
        }
    }
    
    /**
     * 构建拼音纠错提示词
     */
    private fun buildCorrectionPrompt(input: String, context: InputContext): String {
        return """
你是一个专业的中文拼音纠错助手。用户输入了拼音"$input"，请分析是否有错误并提供纠正建议。

上下文信息：
- 前文：${context.previousText}
- 应用：${context.appPackage}

常见错误类型：
1. 声母混淆：zh/z, ch/c, sh/s, n/l
2. 韵母错误：an/ang, en/eng, in/ing
3. 音调缺失或错误
4. 键盘输入错误

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
请为以下文本提供3个自然流畅的续写建议：

文本：$text
上下文：${context.previousText}

要求：
1. 续写要符合语境和逻辑
2. 长度适中（3-12字）
3. 语言自然流畅
4. 考虑不同的续写方向

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
请分析以下文本的语义信息：

文本：$input
上下文：${context.previousText}

请从以下维度进行分析：
1. 用户意图（如：询问、请求、表达情感等）
2. 情感倾向（积极、消极、中性）
3. 主题标签（最多3个关键词）
4. 置信度评估

请按以下JSON格式返回：
{
  "intent": "用户意图",
  "sentiment": "POSITIVE/NEGATIVE/NEUTRAL",
  "topics": ["主题1", "主题2", "主题3"],
  "confidence": 0.85
}
        """.trimIndent()
    }
    
    /**
     * 解析纠错响应
     */
    private fun parseCorrectionResponse(response: String): List<CorrectionSuggestion> {
        val suggestions = mutableListOf<CorrectionSuggestion>()
        
        try {
            response.lines().forEach { line ->
                if (line.contains("|")) {
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        val word = parts[0].trim()
                        val pinyin = parts[1].trim()
                        val confidence = parts[2].trim().toFloatOrNull() ?: 0.5f
                        val errorType = when (parts[3].trim()) {
                            "CONSONANT_CONFUSION" -> ErrorType.CONSONANT_CONFUSION
                            "VOWEL_ERROR" -> ErrorType.VOWEL_ERROR
                            "TONE_MISSING" -> ErrorType.TONE_MISSING
                            "TYPO" -> ErrorType.TYPO
                            else -> ErrorType.UNKNOWN
                        }
                        
                        suggestions.add(
                            CorrectionSuggestion(
                                originalInput = "",
                                correctedText = word,
                                correctedPinyin = pinyin,
                                confidence = confidence,
                                errorType = errorType,
                                explanation = null
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: 解析纠错响应失败")
        }
        
        return suggestions
    }
    
    /**
     * 解析续写响应
     */
    private fun parseContinuationResponse(response: String): List<ContinuationSuggestion> {
        val suggestions = mutableListOf<ContinuationSuggestion>()
        
        try {
            response.lines().forEach { line ->
                if (line.contains("|")) {
                    val parts = line.split("|")
                    if (parts.size >= 3) {
                        val text = parts[0].trim()
                        val confidence = parts[1].trim().toFloatOrNull() ?: 0.5f
                        val type = when (parts[2].trim()) {
                            "WORD_COMPLETION" -> ContinuationType.WORD_COMPLETION
                            "SENTENCE_COMPLETION" -> ContinuationType.SENTENCE_COMPLETION
                            "PARAGRAPH_CONTINUATION" -> ContinuationType.PARAGRAPH_CONTINUATION
                            else -> ContinuationType.WORD_COMPLETION
                        }
                        
                        suggestions.add(
                            ContinuationSuggestion(
                                text = text,
                                confidence = confidence,
                                type = type
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: 解析续写响应失败")
        }
        
        return suggestions
    }
    
    /**
     * 解析语义分析响应
     */
    private fun parseSemanticResponse(response: String): SemanticAnalysis {
        return try {
            // 简单的JSON解析（实际项目中应使用Gson等库）
            val intent = extractJsonValue(response, "intent") ?: "unknown"
            val sentimentStr = extractJsonValue(response, "sentiment") ?: "NEUTRAL"
            val sentiment = when (sentimentStr) {
                "POSITIVE" -> Sentiment.POSITIVE
                "NEGATIVE" -> Sentiment.NEGATIVE
                else -> Sentiment.NEUTRAL
            }
            val confidence = extractJsonValue(response, "confidence")?.toFloatOrNull() ?: 0.5f
            
            // 提取主题（简化处理）
            val topics = mutableListOf<String>()
            val topicsMatch = Regex("\"topics\":\\s*\\[(.*?)\\]").find(response)
            topicsMatch?.groupValues?.get(1)?.let { topicsStr ->
                topicsStr.split(",").forEach { topic ->
                    val cleanTopic = topic.trim().removeSurrounding("\"")
                    if (cleanTopic.isNotBlank()) {
                        topics.add(cleanTopic)
                    }
                }
            }
            
            SemanticAnalysis(
                intent = intent,
                sentiment = sentiment,
                topics = topics,
                confidence = confidence
            )
        } catch (e: Exception) {
            Timber.w(e, "$TAG: 解析语义分析响应失败")
            SemanticAnalysis(
                intent = "unknown",
                sentiment = Sentiment.NEUTRAL,
                topics = emptyList(),
                confidence = 0.0f
            )
        }
    }
    
    /**
     * 从JSON字符串中提取值（简化实现）
     */
    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\":\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)
    }
    
    /**
     * 多模态分析：图像+文本
     */
    suspend fun analyzeImageWithText(image: Bitmap, textPrompt: String): String = withContext(Dispatchers.IO) {
        if (!isInitialized || llmManager == null) {
            Timber.w("$TAG: 引擎未初始化，无法进行多模态分析")
            return@withContext "❌ AI引擎未初始化"
        }
        
        try {
            Timber.d("$TAG: 开始多模态分析 - 图像尺寸: ${image.width}x${image.height}, 提示: '$textPrompt'")
            
            // 提取图像特征信息
            val imageFeatures = extractImageFeatures(image)
            
            // 构建多模态提示词
            val prompt = buildMultimodalPrompt(textPrompt, imageFeatures)
            
            // 调用LLM生成响应
            val response = llmManager?.generateResponse(prompt)
            
            if (response.isNullOrBlank()) {
                Timber.w("$TAG: 多模态分析响应为空")
                return@withContext "❌ AI分析响应为空，请重试"
            }
            
            Timber.d("$TAG: 多模态分析完成")
            return@withContext response
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 多模态分析异常")
            return@withContext "❌ 分析过程中发生错误: ${e.message}"
        }
    }
    
    /**
     * 提取图像特征信息
     */
    private fun extractImageFeatures(image: Bitmap): ImageFeatures {
        // 基本图像信息
        val width = image.width
        val height = image.height
        val aspectRatio = width.toFloat() / height.toFloat()
        
        // 分析主要颜色
        val dominantColors = analyzeDominantColors(image)
        
        // 分析亮度
        val brightness = analyzeBrightness(image)
        
        // 判断图像类型
        val imageType = determineImageType(aspectRatio, width, height)
        
        return ImageFeatures(
            width = width,
            height = height,
            aspectRatio = aspectRatio,
            dominantColors = dominantColors,
            brightness = brightness,
            imageType = imageType
        )
    }
    
    /**
     * 分析图像主要颜色
     */
    private fun analyzeDominantColors(image: Bitmap): List<String> {
        val colors = mutableListOf<String>()
        
        try {
            // 缩小图像以提高处理速度
            val smallImage = Bitmap.createScaledBitmap(image, 50, 50, false)
            
            val colorCounts = mutableMapOf<Int, Int>()
            
            // 统计颜色频率
            for (x in 0 until smallImage.width) {
                for (y in 0 until smallImage.height) {
                    val pixel = smallImage.getPixel(x, y)
                    val color = simplifyColor(pixel)
                    colorCounts[color] = colorCounts.getOrDefault(color, 0) + 1
                }
            }
            
            // 获取最常见的颜色
            val sortedColors = colorCounts.toList().sortedByDescending { it.second }.take(3)
            
            sortedColors.forEach { (color, _) ->
                colors.add(getColorName(color))
            }
            
            smallImage.recycle()
        } catch (e: Exception) {
            Timber.w(e, "分析颜色失败")
            colors.add("未知颜色")
        }
        
        return colors.ifEmpty { listOf("未知颜色") }
    }
    
    /**
     * 简化颜色值
     */
    private fun simplifyColor(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        
        // 将颜色值简化到较少的区间
        val simplifiedR = (r / 64) * 64
        val simplifiedG = (g / 64) * 64
        val simplifiedB = (b / 64) * 64
        
        return (simplifiedR shl 16) or (simplifiedG shl 8) or simplifiedB
    }
    
    /**
     * 获取颜色名称
     */
    private fun getColorName(color: Int): String {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        
        return when {
            r > 200 && g > 200 && b > 200 -> "白色"
            r < 50 && g < 50 && b < 50 -> "黑色"
            r > 150 && g < 100 && b < 100 -> "红色"
            r < 100 && g > 150 && b < 100 -> "绿色"
            r < 100 && g < 100 && b > 150 -> "蓝色"
            r > 150 && g > 150 && b < 100 -> "黄色"
            r > 150 && g < 100 && b > 150 -> "紫色"
            r > 150 && g > 100 && b < 100 -> "橙色"
            r > 100 && g > 100 && b > 100 -> "灰色"
            else -> "混合色"
        }
    }
    
    /**
     * 分析图像亮度
     */
    private fun analyzeBrightness(image: Bitmap): String {
        try {
            val smallImage = Bitmap.createScaledBitmap(image, 20, 20, false)
            var totalBrightness = 0
            val pixelCount = smallImage.width * smallImage.height
            
            for (x in 0 until smallImage.width) {
                for (y in 0 until smallImage.height) {
                    val pixel = smallImage.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    
                    // 计算亮度 (使用标准公式)
                    val brightness = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    totalBrightness += brightness
                }
            }
            
            val averageBrightness = totalBrightness / pixelCount
            smallImage.recycle()
            
            return when {
                averageBrightness > 180 -> "明亮"
                averageBrightness > 100 -> "适中"
                else -> "较暗"
            }
        } catch (e: Exception) {
            Timber.w(e, "分析亮度失败")
            return "未知亮度"
        }
    }
    
    /**
     * 判断图像类型
     */
    private fun determineImageType(aspectRatio: Float, width: Int, height: Int): String {
        return when {
            aspectRatio > 1.5 -> "横向图像"
            aspectRatio < 0.67 -> "纵向图像"
            kotlin.math.abs(aspectRatio - 1.0f) < 0.1f -> "正方形图像"
            width < 500 || height < 500 -> "小尺寸图像"
            width > 2000 || height > 2000 -> "高分辨率图像"
            else -> "标准图像"
        }
    }
    
    /**
     * 构建多模态提示词
     */
    private fun buildMultimodalPrompt(textPrompt: String, imageFeatures: ImageFeatures): String {
        return """
你是一个专业的图像分析助手。用户上传了一张图像并提出了问题。

用户问题：$textPrompt

图像特征分析：
- 尺寸：${imageFeatures.width} x ${imageFeatures.height} 像素
- 宽高比：${String.format("%.2f", imageFeatures.aspectRatio)}
- 图像类型：${imageFeatures.imageType}
- 主要颜色：${imageFeatures.dominantColors.joinToString("、")}
- 亮度：${imageFeatures.brightness}

基于以上图像特征信息，请回答用户的问题。

回答要求：
1. 根据图像特征推测可能的内容
2. 结合用户的具体问题进行分析
3. 如果特征信息不足以完全回答问题，请说明限制
4. 用中文回答，语言自然流畅
5. 可以根据颜色、尺寸、类型等特征进行合理推测

请开始分析：
        """.trimIndent()
    }
    
    /**
     * 图像特征数据类
     */
    private data class ImageFeatures(
        val width: Int,
        val height: Int,
        val aspectRatio: Float,
        val dominantColors: List<String>,
        val brightness: String,
        val imageType: String
    )
} 