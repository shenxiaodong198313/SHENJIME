package com.shenji.aikeyboard.gallery.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * MediaPipe多模态引擎
 * 基于谷歌官方Gallery的LlmChatModelHelper实现真实的多模态推理
 */
class MediaPipeMultimodalEngine {
    
    companion object {
        private const val TAG = "MediaPipeMultimodalEngine"
        private const val DEFAULT_MAX_TOKENS = 1024
        private const val DEFAULT_TOPK = 40
        private const val DEFAULT_TOPP = 0.95f
        private const val DEFAULT_TEMPERATURE = 0.8f
    }
    
    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null
    private var isInitialized = false
    private var context: Context? = null
    
    /**
     * 初始化引擎
     */
    suspend fun initialize(context: Context? = null) = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) return@withContext
            
            this@MediaPipeMultimodalEngine.context = context
            
            if (context == null) {
                throw Exception("Context 不能为空")
            }
            
            // 获取 Gemma3n 模型文件路径
            val modelPath = getGemma3nModelPath(context)
            if (modelPath == null) {
                throw Exception("无法找到 Gemma3n 模型文件")
            }
            
            Timber.d("$TAG: 使用模型路径: $modelPath")
            
            // 创建 MediaPipe LLM 推理选项，支持多模态
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(DEFAULT_MAX_TOKENS)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .setMaxNumImages(1) // 支持图像输入
                .build()
            
            // 创建 LLM 推理引擎
            llmInference = LlmInference.createFromOptions(context, options)
            
            // 创建推理会话，启用视觉模态
            llmSession = LlmInferenceSession.createFromOptions(
                llmInference!!,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(DEFAULT_TOPK)
                    .setTopP(DEFAULT_TOPP)
                    .setTemperature(DEFAULT_TEMPERATURE)
                    .setGraphOptions(
                        GraphOptions.builder()
                            .setEnableVisionModality(true) // 启用视觉模态
                            .build()
                    )
                    .build()
            )
            
            isInitialized = true
            Timber.d("$TAG: MediaPipe多模态引擎初始化成功（真实推理模式）")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: MediaPipe多模态引擎初始化失败")
            throw e
        }
    }
    
    /**
     * 获取 Gemma3n 模型文件路径
     */
    private fun getGemma3nModelPath(context: Context): String? {
        // 优先检查外部存储
        val externalPath = "/sdcard/shenji_models/gemma3n-e4b-it/gemma-3n-E4B-it-int4.task"
        val externalFile = java.io.File(externalPath)
        if (externalFile.exists() && externalFile.length() > 0) {
            Timber.d("$TAG: 使用外部存储的模型文件: $externalPath")
            return externalPath
        }
        
        // 检查内部存储
        val internalFile = java.io.File(context.filesDir, "gemma3n-e4b-it-gemma-3n-E4B-it-int4.task")
        if (internalFile.exists() && internalFile.length() > 0) {
            Timber.d("$TAG: 使用内部存储的模型文件: ${internalFile.absolutePath}")
            return internalFile.absolutePath
        }
        
        Timber.e("$TAG: 未找到 Gemma3n 模型文件")
        return null
    }
    
    /**
     * 分析图片内容
     */
    suspend fun analyzeImage(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            throw IllegalStateException("引擎未初始化")
        }
        
        return@withContext runInference("请详细描述这张图片的内容。", bitmap)
    }
    
    /**
     * 分析图片并回答特定问题
     */
    suspend fun analyzeImageWithText(bitmap: Bitmap, question: String): String = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            throw IllegalStateException("引擎未初始化")
        }
        
        return@withContext runInference(question, bitmap)
    }
    
    /**
     * 生成纯文本回复
     */
    suspend fun generateTextResponse(text: String): String = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            throw IllegalStateException("引擎未初始化")
        }
        
        return@withContext runInference(text, null)
    }
    
    /**
     * 流式分析图片，使用Channel实现真正的流式输出
     */
    fun analyzeImageStream(bitmap: Bitmap, question: String): Flow<String> = 
        channelFlow {
            try {
                if (!isInitialized) {
                    send("❌ AI引擎未初始化")
                    return@channelFlow
                }
                
                val session = llmSession ?: throw IllegalStateException("推理会话未初始化")
                
                Timber.d("$TAG: 开始流式推理 - 文本: '$question', 图片: ${bitmap.width}x${bitmap.height}")
                
                // 添加查询文本
                session.addQueryChunk(question)
                
                // 添加图片
                try {
                    val mpImage = BitmapImageBuilder(bitmap).build()
                    session.addImage(mpImage)
                    Timber.d("$TAG: 成功添加图片到流式推理")
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: 添加图片到流式推理失败")
                    send("❌ 图片处理失败: ${e.message}")
                    return@channelFlow
                }
                
                // 使用suspendCancellableCoroutine包装异步推理
                suspendCancellableCoroutine<Unit> { continuation ->
                    session.generateResponseAsync { partialResult, done ->
                        try {
                            if (partialResult != null && partialResult.isNotEmpty()) {
                                // 使用trySend安全地发送数据
                                val result = trySend(partialResult)
                                if (result.isFailure) {
                                    Timber.w("$TAG: 发送流式数据失败: ${result.exceptionOrNull()}")
                                }
                            }
                            
                            if (done) {
                                Timber.d("$TAG: 流式推理完成")
                                if (continuation.isActive) {
                                    continuation.resume(Unit)
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "$TAG: 处理流式推理结果时出错")
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.failure(e))
                            }
                        }
                    }
                    
                    // 设置取消回调
                    continuation.invokeOnCancellation {
                        Timber.d("$TAG: 流式推理被取消")
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 启动流式推理失败")
                send("❌ 启动流式推理失败: ${e.message}")
            }
        }.flowOn(Dispatchers.IO)
    
    /**
     * 流式分析图片，使用回调方式实现
     */
    fun analyzeImageStreamWithCallback(bitmap: Bitmap, prompt: String, callback: (String) -> Unit) {
        try {
            if (!isInitialized) {
                callback("❌ AI引擎未初始化")
                return
            }
            
            val session = llmSession ?: throw IllegalStateException("推理会话未初始化")
            
            Timber.d("$TAG: 开始流式推理 - 文本: '$prompt', 图片: ${bitmap.width}x${bitmap.height}")
            
            // 添加查询文本
            session.addQueryChunk(prompt)
            
            // 添加图片
            try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                session.addImage(mpImage)
                Timber.d("$TAG: 成功添加图片到流式推理")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 添加图片到流式推理失败")
                callback("❌ 图片处理失败: ${e.message}")
                return
            }
            
            // 开始异步推理
            session.generateResponseAsync { partialResult, done ->
                try {
                    if (partialResult != null && partialResult.isNotEmpty()) {
                        callback(partialResult)
                    }
                    
                    if (done) {
                        Timber.d("$TAG: 流式推理完成")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: 处理流式推理结果时出错")
                    callback("❌ 处理推理结果时出错: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 启动流式推理失败")
            callback("❌ 启动流式推理失败: ${e.message}")
        }
    }
    
    /**
     * 执行推理（支持真正的多模态）
     */
    private suspend fun runInference(input: String, image: Bitmap?): String = suspendCancellableCoroutine { continuation ->
        try {
            val session = llmSession ?: throw IllegalStateException("推理会话未初始化")
            
            val resultBuilder = StringBuilder()
            
            Timber.d("$TAG: 开始推理 - 文本: '$input', 图片: ${image != null}")
            
            // 根据官方文档，文本应该在图片之前添加
            session.addQueryChunk(input)
            
            // 如果有图片，添加图片（使用正确的BitmapImageBuilder）
            if (image != null) {
                try {
                    val mpImage = BitmapImageBuilder(image).build()
                    session.addImage(mpImage)
                    Timber.d("$TAG: 成功添加图片，尺寸: ${image.width}x${image.height}")
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: 添加图片失败")
                    // 如果图片处理失败，继续进行文本推理
                }
            }
            
            // 开始异步推理
            session.generateResponseAsync { partialResult, done ->
                try {
                    if (partialResult != null) {
                        resultBuilder.append(partialResult)
                    }
                    
                    if (done) {
                        val finalResult = resultBuilder.toString().trim()
                        Timber.d("$TAG: 推理完成，结果长度: ${finalResult.length}")
                        
                        if (continuation.isActive) {
                            if (finalResult.isNotEmpty()) {
                                continuation.resume(finalResult)
                            } else {
                                continuation.resume("抱歉，没有生成有效的回复。")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: 处理推理结果时出错")
                    if (continuation.isActive) {
                        continuation.resume("抱歉，处理回复时出现错误：${e.message}")
                    }
                }
            }
            
            // 设置取消回调
            continuation.invokeOnCancellation {
                try {
                    Timber.d("$TAG: 推理被取消")
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: 取消推理时出错")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 启动推理失败")
            if (continuation.isActive) {
                continuation.resume("抱歉，启动推理时出现错误：${e.message}")
            }
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            llmSession?.close()
            llmSession = null
            
            llmInference?.close()
            llmInference = null
            
            isInitialized = false
            context = null
            
            Timber.d("$TAG: MediaPipe多模态引擎资源清理完成")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 清理MediaPipe引擎资源失败")
        }
    }
} 