package com.shenji.aikeyboard.ai.engines

import android.content.Context
import android.graphics.Bitmap
import com.shenji.aikeyboard.gallery.ui.common.MediaPipeMultimodalEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Gemma3n图片分析引擎
 * 专门用于悬浮窗AI分析功能，复用现有的MediaPipe多模态引擎
 */
class Gemma3nImageAnalysisEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "Gemma3nImageAnalysisEngine"
        
        // 图片分析专用提示词
        private val IMAGE_ANALYSIS_PROMPT = """
请详细分析这张图片的内容，包括：

1. 主要对象和场景
2. 文字内容（如果有的话）
3. 界面元素和布局（如果是应用界面）
4. 重要信息提取

请用简洁、精炼的语言进行总结，突出重点信息。
        """.trimIndent()
    }
    
    private var multimodalEngine: MediaPipeMultimodalEngine? = null
    private var isInitialized = false
    
    /**
     * 初始化分析引擎
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                Timber.d("$TAG: Engine already initialized")
                return@withContext true
            }
            
            Timber.d("$TAG: Initializing Gemma3n image analysis engine")
            
            // 创建MediaPipe多模态引擎实例
            multimodalEngine = MediaPipeMultimodalEngine()
            
            // 初始化引擎
            multimodalEngine?.initialize(context)
            
            isInitialized = true
            Timber.i("$TAG: Gemma3n image analysis engine initialized successfully")
            
            return@withContext true
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize Gemma3n image analysis engine")
            isInitialized = false
            multimodalEngine = null
            return@withContext false
        }
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean {
        return isInitialized && multimodalEngine != null
    }
    
    /**
     * 流式分析图片
     * 返回Flow以支持实时显示分析结果
     */
    fun analyzeImageStream(bitmap: Bitmap): Flow<String> = flow {
        try {
            if (!isInitialized || multimodalEngine == null) {
                emit("❌ AI引擎未初始化")
                return@flow
            }
            
            Timber.d("$TAG: Starting real-time streaming image analysis")
            
            // 使用真实的流式分析
            runRealTimeInference(bitmap, IMAGE_ANALYSIS_PROMPT)
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error during streaming image analysis")
            emit("❌ 分析过程中出现错误: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 执行真实时间推理，利用MediaPipe的流式输出
     */
    private suspend fun FlowCollector<String>.runRealTimeInference(bitmap: Bitmap, prompt: String) {
        try {
            Timber.d("$TAG: Starting real-time inference with MediaPipe")
            
            // 使用MediaPipe的真实流式推理，直接收集Flow
            multimodalEngine!!.analyzeImageStream(bitmap, prompt).collect { chunk ->
                emit(chunk)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error during real-time inference")
            emit("❌ 实时推理过程中出现错误: ${e.message}")
        }
    }
    
    /**
     * 将文本分割成适合流式输出的段落
     */
    private fun splitTextIntoSegments(text: String): List<String> {
        val segments = mutableListOf<String>()
        
        try {
            // 首先按段落分割
            val paragraphs = text.split("\n\n", "\n")
            
            for (paragraph in paragraphs) {
                if (paragraph.trim().isEmpty()) continue
                
                // 如果段落太长，按句子分割
                if (paragraph.length > 100) {
                    val sentences = paragraph.split("。", "！", "？", ". ", "! ", "? ")
                    
                    for (i in sentences.indices) {
                        val sentence = sentences[i].trim()
                        if (sentence.isNotEmpty()) {
                            // 添加标点符号（除了最后一个）
                            val segmentText = if (i < sentences.size - 1) {
                                when {
                                    paragraph.contains("。") -> "$sentence。"
                                    paragraph.contains("！") -> "$sentence！"
                                    paragraph.contains("？") -> "$sentence？"
                                    else -> "$sentence"
                                }
                            } else {
                                sentence
                            }
                            
                            segments.add(segmentText)
                        }
                    }
                } else {
                    segments.add(paragraph)
                }
                
                // 在段落之间添加换行
                segments.add("\n")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error splitting text into segments")
            // 如果分割失败，返回原始文本
            segments.clear()
            segments.add(text)
        }
        
        return segments.filter { it.isNotBlank() }
    }
    
    /**
     * 单次图片分析（非流式）
     */
    suspend fun analyzeImage(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized || multimodalEngine == null) {
                return@withContext "AI引擎未初始化"
            }
            
            Timber.d("$TAG: Starting single image analysis")
            
            val result = multimodalEngine!!.analyzeImageWithText(
                bitmap = bitmap,
                question = IMAGE_ANALYSIS_PROMPT
            )
            
            return@withContext if (result.isNotEmpty()) {
                result
            } else {
                "AI分析未返回结果"
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error during image analysis")
            return@withContext "分析失败: ${e.message}"
        }
    }
    
    /**
     * 自定义提示词分析图片
     */
    suspend fun analyzeImageWithPrompt(bitmap: Bitmap, customPrompt: String): String = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized || multimodalEngine == null) {
                return@withContext "AI引擎未初始化"
            }
            
            Timber.d("$TAG: Starting custom prompt image analysis")
            
            val result = multimodalEngine!!.analyzeImageWithText(
                bitmap = bitmap,
                question = customPrompt
            )
            
            return@withContext if (result.isNotEmpty()) {
                result
            } else {
                "AI分析未返回结果"
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error during custom prompt analysis")
            return@withContext "分析失败: ${e.message}"
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            multimodalEngine?.cleanup()
            multimodalEngine = null
            isInitialized = false
            Timber.d("$TAG: Gemma3n image analysis engine released")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error releasing engine")
        }
    }
} 