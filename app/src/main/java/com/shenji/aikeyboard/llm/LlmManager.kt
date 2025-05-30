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
 */
class LlmManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "LlmManager"
        private const val MODEL_ASSET_PATH = "llm_models/gemma3-1b-it-int4.task"
        private const val TOKENIZER_ASSET_PATH = "llm_models/tokenizer.model"
        private const val MODEL_FILE_NAME = "gemma3-1b-it-int4.task"
        private const val TOKENIZER_FILE_NAME = "tokenizer.model"
        
        @Volatile
        private var INSTANCE: LlmManager? = null
        
        fun getInstance(context: Context): LlmManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LlmManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private var llmInference: LlmInference? = null
    private var isInitialized = false
    private var isInitializing = false
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
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
     * 初始化LLM模型
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d(TAG, "LLM已经初始化")
            return@withContext true
        }
        
        if (isInitializing) {
            Log.d(TAG, "LLM正在初始化中...")
            return@withContext false
        }
        
        isInitializing = true
        
        try {
            Log.d(TAG, "开始初始化LLM模型...")
            
            // 从assets复制模型文件到内部存储
            Log.d(TAG, "复制模型文件...")
            val modelFile = copyAssetToInternalStorage(MODEL_ASSET_PATH, MODEL_FILE_NAME)
            
            Log.d(TAG, "复制分词器文件...")
            val tokenizerFile = copyAssetToInternalStorage(TOKENIZER_ASSET_PATH, TOKENIZER_FILE_NAME)
            
            // 验证文件是否存在
            if (!modelFile.exists()) {
                Log.e(TAG, "模型文件不存在: ${modelFile.absolutePath}")
                return@withContext false
            }
            
            if (!tokenizerFile.exists()) {
                Log.e(TAG, "分词器文件不存在: ${tokenizerFile.absolutePath}")
                return@withContext false
            }
            
            Log.d(TAG, "模型文件路径: ${modelFile.absolutePath}")
            Log.d(TAG, "分词器文件路径: ${tokenizerFile.absolutePath}")
            
            // 创建LLM推理选项
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .setMaxTopK(40)
                .build()
            
            // 初始化LLM推理引擎
            Log.d(TAG, "创建LLM推理引擎...")
            llmInference = LlmInference.createFromOptions(context, options)
            
            isInitialized = true
            Log.d(TAG, "LLM模型初始化成功")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "LLM初始化失败", e)
            false
        } finally {
            isInitializing = false
        }
    }
    
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