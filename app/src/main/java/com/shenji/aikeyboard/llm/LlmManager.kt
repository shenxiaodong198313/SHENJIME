package com.shenji.aikeyboard.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * LLM管理器
 * 负责MediaPipe LLM的初始化、推理和资源管理
 * 支持多模型管理和切换
 */
class LlmManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "LlmManager"
        
        // Gemma3-1B-IT模型配置 (保持现有路径不变)
        private const val GEMMA3_MODEL_ASSET_PATH = "llm_models/gemma3-1b-it-int4.task"
        private const val GEMMA3_TOKENIZER_ASSET_PATH = "llm_models/tokenizer.model"
        private const val GEMMA3_MODEL_FILE_NAME = "gemma3-1b-it-int4.task"
        private const val GEMMA3_TOKENIZER_FILE_NAME = "tokenizer.model"
        
        // Gemma3n-E4B-IT模型配置 (使用独立目录)
        private const val GEMMA3N_MODEL_ASSET_PATH = "llm_models/gemma3n-e4b-it/gemma-3n-E4B-it-int4.task"
        private const val GEMMA3N_MODEL_FILE_NAME = "gemma3n-e4b-it-gemma-3n-E4B-it-int4.task"
        
        @Volatile
        private var INSTANCE: LlmManager? = null
        
        fun getInstance(context: Context): LlmManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LlmManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 模型类型枚举
    enum class ModelType {
        GEMMA3_1B_IT,
        GEMMA3N_E4B_IT
    }
    
    private var llmInference: LlmInference? = null
    private var currentModelType: ModelType? = null
    private var isInitialized = false
    private var isInitializing = false
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 获取模型文件路径（优先从外部存储读取，如果不存在则从assets复制）
     */
    private suspend fun getModelFile(assetPath: String, fileName: String, externalPath: String? = null): File = withContext(Dispatchers.IO) {
        // 首先检查外部存储是否有模型文件
        if (externalPath != null) {
            val externalFile = File(externalPath)
            if (externalFile.exists() && externalFile.length() > 0) {
                Log.d(TAG, "使用外部存储的模型文件: ${externalFile.absolutePath}")
                return@withContext externalFile
            }
        }
        
        // 如果外部存储没有，则从assets复制到内部存储
        return@withContext copyAssetToInternalStorage(assetPath, fileName)
    }
    
    /**
     * 从assets复制文件到内部存储
     */
    private suspend fun copyAssetToInternalStorage(assetPath: String, fileName: String): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.filesDir, fileName)
        
        // 如果文件已存在且大小正确，跳过复制
        if (outputFile.exists()) {
            try {
                val assetSize = context.assets.open(assetPath).use { it.available().toLong() }
                if (outputFile.length() == assetSize) {
                    Log.d(TAG, "文件已存在且大小正确，跳过复制: $fileName")
                    return@withContext outputFile
                }
            } catch (e: Exception) {
                Log.w(TAG, "检查文件大小失败，重新复制: $fileName")
            }
        }
        
        Log.d(TAG, "开始复制文件: $assetPath -> ${outputFile.absolutePath}")
        
        try {
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        
                        // 每10MB打印一次进度
                        if (totalBytes % (10 * 1024 * 1024) == 0L) {
                            Log.d(TAG, "复制进度: ${totalBytes / (1024 * 1024)}MB")
                        }
                    }
                    
                    Log.d(TAG, "文件复制完成: $fileName, 总大小: ${totalBytes / (1024 * 1024)}MB")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "复制文件失败: $fileName", e)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw e
        }
        
        outputFile
    }
    
    /**
     * 初始化默认LLM模型 (Gemma3-1B-IT)
     */
    suspend fun initialize(): Boolean = initializeModel(ModelType.GEMMA3_1B_IT)
    
    /**
     * 初始化Gemma3n模型
     */
    suspend fun initializeGemma3n(): Boolean = initializeModel(ModelType.GEMMA3N_E4B_IT)
    
    /**
     * 初始化指定的LLM模型
     */
    private suspend fun initializeModel(modelType: ModelType): Boolean = withContext(Dispatchers.IO) {
        // 如果当前模型已经是目标模型且已初始化，直接返回
        if (isInitialized && currentModelType == modelType) {
            Log.d(TAG, "目标模型已经初始化: $modelType")
            return@withContext true
        }
        
        if (isInitializing) {
            Log.d(TAG, "LLM正在初始化中...")
            return@withContext false
        }
        
        isInitializing = true
        
        try {
            Log.d(TAG, "开始初始化LLM模型: $modelType")
            
            // 释放当前模型资源
            if (isInitialized) {
                Log.d(TAG, "释放当前模型资源: $currentModelType")
                llmInference?.close()
                llmInference = null
                isInitialized = false
                currentModelType = null
            }
            
            when (modelType) {
                ModelType.GEMMA3_1B_IT -> {
                    // 初始化Gemma3-1B-IT模型
                    Log.d(TAG, "获取Gemma3模型文件...")
                    val modelFile = getModelFile(
                        GEMMA3_MODEL_ASSET_PATH, 
                        GEMMA3_MODEL_FILE_NAME,
                        "/sdcard/shenji_models/gemma3-1b-it-int4.task"
                    )
            
                    Log.d(TAG, "获取Gemma3分词器文件...")
                    val tokenizerFile = getModelFile(
                        GEMMA3_TOKENIZER_ASSET_PATH, 
                        GEMMA3_TOKENIZER_FILE_NAME,
                        "/sdcard/shenji_models/tokenizer.model"
                    )
            
            // 验证文件是否存在
            if (!modelFile.exists()) {
                        Log.e(TAG, "Gemma3模型文件不存在: ${modelFile.absolutePath}")
                return@withContext false
            }
            
            if (!tokenizerFile.exists()) {
                        Log.e(TAG, "Gemma3分词器文件不存在: ${tokenizerFile.absolutePath}")
                return@withContext false
            }
            
                    Log.d(TAG, "Gemma3模型文件路径: ${modelFile.absolutePath}")
                    Log.d(TAG, "Gemma3分词器文件路径: ${tokenizerFile.absolutePath}")
            
            // 创建LLM推理选项
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .setMaxTopK(40)
                .build()
            
            // 初始化LLM推理引擎
                    Log.d(TAG, "创建Gemma3 LLM推理引擎...")
                    llmInference = LlmInference.createFromOptions(context, options)
                }
                
                ModelType.GEMMA3N_E4B_IT -> {
                    // 初始化Gemma3n-E4B-IT模型
                    Log.d(TAG, "获取Gemma3n模型文件...")
                    val modelFile = getModelFile(
                        GEMMA3N_MODEL_ASSET_PATH, 
                        GEMMA3N_MODEL_FILE_NAME,
                        "/sdcard/shenji_models/gemma3n-e4b-it/gemma-3n-E4B-it-int4.task"
                    )
                    
                    // 验证文件是否存在
                    if (!modelFile.exists()) {
                        Log.e(TAG, "Gemma3n模型文件不存在: ${modelFile.absolutePath}")
                        return@withContext false
                    }
                    
                    Log.d(TAG, "Gemma3n模型文件路径: ${modelFile.absolutePath}")
                    
                    // 创建MediaPipe LLM推理选项（Gemma3n也使用.task格式）
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(2048)
                        .setMaxTopK(50)
                        .build()
                    
                    // 初始化LLM推理引擎
                    Log.d(TAG, "创建Gemma3n LLM推理引擎...")
            llmInference = LlmInference.createFromOptions(context, options)
                }
            }
            
            currentModelType = modelType
            isInitialized = true
            Log.d(TAG, "LLM模型初始化成功: $modelType")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "LLM初始化失败: $modelType", e)
            false
        } finally {
            isInitializing = false
        }
    }
    
    /**
     * 切换模型
     */
    suspend fun switchModel(modelType: ModelType): Boolean {
        Log.d(TAG, "请求切换模型: $currentModelType -> $modelType")
        return initializeModel(modelType)
    }
    
    /**
     * 获取当前模型类型
     */
    fun getCurrentModelType(): ModelType? = currentModelType
    
    /**
     * 生成文本回复
     */
    suspend fun generateResponse(prompt: String): String? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "LLM未初始化，无法生成回复")
            return@withContext "模型未初始化，请稍后再试"
        }
        
        if (prompt.isBlank()) {
            Log.w(TAG, "输入为空")
            return@withContext "请输入有效的问题"
        }
        
        try {
            Log.d(TAG, "开始生成回复，输入长度: ${prompt.length}")
            Log.d(TAG, "输入内容: $prompt")
            
            val startTime = System.currentTimeMillis()
            val response = llmInference?.generateResponse(prompt)
            val endTime = System.currentTimeMillis()
            
            Log.d(TAG, "LLM推理耗时: ${endTime - startTime}ms")
            Log.d(TAG, "生成回复长度: ${response?.length ?: 0}")
            Log.d(TAG, "生成回复内容: $response")
            
            if (response.isNullOrBlank()) {
                Log.w(TAG, "LLM返回空回复")
                return@withContext "抱歉，我现在无法生成回复，请重新尝试"
            }
            
            response
        } catch (e: Exception) {
            Log.e(TAG, "生成回复失败", e)
            "抱歉，生成回复时发生错误: ${e.message}"
        }
    }
    
    /**
     * 流式生成文本回复
     * @param prompt 输入提示词
     * @param onProgress 流式回调，参数为(部分文本, 是否完成)
     */
    suspend fun generateResponseStream(
        prompt: String, 
        onProgress: (String, Boolean) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "LLM未初始化，无法生成回复")
            onProgress("模型未初始化，请稍后再试", true)
            return@withContext "模型未初始化，请稍后再试"
        }
        
        if (prompt.isBlank()) {
            Log.w(TAG, "输入为空")
            onProgress("请输入有效的问题", true)
            return@withContext "请输入有效的问题"
        }
        
        try {
            Log.d(TAG, "开始流式生成回复，输入长度: ${prompt.length}")
            Log.d(TAG, "输入内容: $prompt")
            
            val startTime = System.currentTimeMillis()
            val responseBuilder = StringBuilder()
            
            // 使用MediaPipe的流式生成（如果支持）
            // 由于MediaPipe LlmInference可能不直接支持流式，我们需要使用MNN的LlmSession
            // 这里我们先实现一个模拟的流式效果，后续可以集成真正的流式API
            
            val response = llmInference?.generateResponse(prompt)
            
            if (response.isNullOrBlank()) {
                Log.w(TAG, "LLM返回空回复")
                onProgress("抱歉，我现在无法生成回复，请重新尝试", true)
                return@withContext "抱歉，我现在无法生成回复，请重新尝试"
            }
            
                         // 模拟流式输出效果 - 按字符逐步显示
             for (i in response.indices) {
                 val partialResponse = response.substring(0, i + 1)
                 responseBuilder.clear()
                 responseBuilder.append(partialResponse)
                 
                 // 调用进度回调
                 withContext(Dispatchers.Main) {
                     onProgress(partialResponse, i == response.length - 1)
                 }
                 
                 // 添加延迟模拟流式效果
                 if (i < response.length - 1) {
                     // 根据字符类型调整延迟时间
                     val delay = when {
                         response[i] in "，。！？；：" -> 200L // 标点符号停顿长一些
                         response[i] == ' ' -> 30L // 空格停顿短一些
                         response[i] == '\n' -> 100L // 换行停顿中等
                         else -> 20L // 普通字符
                     }
                     kotlinx.coroutines.delay(delay)
                 }
             }
            
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "LLM流式推理耗时: ${endTime - startTime}ms")
            Log.d(TAG, "生成回复长度: ${response.length}")
            
            response
        } catch (e: Exception) {
            Log.e(TAG, "流式生成回复失败", e)
            val errorMsg = "抱歉，生成回复时发生错误: ${e.message}"
            onProgress(errorMsg, true)
            errorMsg
        }
    }
    
    /**
     * 检查LLM是否已初始化
     */
    fun isReady(): Boolean = isInitialized
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            llmInference?.close()
            llmInference = null
            isInitialized = false
            isInitializing = false
            scope.cancel()
            Log.d(TAG, "LLM资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放LLM资源失败", e)
        }
    }
} 