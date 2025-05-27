package com.shenji.aikeyboard.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

/**
 * 神迹输入法LLM引擎
 * 基于MNN框架的本地大模型推理引擎
 */
class ShenjiLLMEngine private constructor() {
    
    companion object {
        private const val TAG = "ShenjiLLMEngine"
        
        @Volatile
        private var INSTANCE: ShenjiLLMEngine? = null
        
        fun getInstance(): ShenjiLLMEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ShenjiLLMEngine().also { INSTANCE = it }
            }
        }
        
        // 加载本地库
        init {
            try {
                System.loadLibrary("shenjillm")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }
    
    private var sessionPtr: Long = 0
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 初始化LLM引擎
     */
    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing LLM engine...")
            
            // 获取模型文件路径
            val modelDir = getModelDirectory(context)
            val configPath = File(modelDir, "llm_config.json").absolutePath
            
            if (!File(configPath).exists()) {
                Log.e(TAG, "Config file not found: $configPath")
                return@withContext false
            }
            
            // 调用native方法初始化
            sessionPtr = nativeInit(configPath, modelDir.absolutePath)
            isInitialized = sessionPtr != 0L
            
            if (isInitialized) {
                Log.i(TAG, "LLM engine initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize LLM engine")
            }
            
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Exception during initialization", e)
            false
        }
    }
    
    /**
     * 生成文本
     */
    suspend fun generateText(input: String, maxTokens: Int = 128): String = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "LLM engine not initialized")
            return@withContext ""
        }
        
        try {
            Log.d(TAG, "Generating text for: $input")
            val result = nativeGenerate(sessionPtr, input, maxTokens)
            Log.d(TAG, "Generated: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Exception during text generation", e)
            ""
        }
    }
    
    /**
     * 重置会话
     */
    fun resetSession() {
        if (isInitialized) {
            try {
                nativeReset(sessionPtr)
                Log.i(TAG, "Session reset")
            } catch (e: Exception) {
                Log.e(TAG, "Exception during session reset", e)
            }
        }
    }
    
    /**
     * 检查是否已加载
     */
    fun isLoaded(): Boolean {
        return if (isInitialized) {
            try {
                nativeIsLoaded(sessionPtr)
            } catch (e: Exception) {
                Log.e(TAG, "Exception checking load status", e)
                false
            }
        } else {
            false
        }
    }
    
    /**
     * 获取模型信息
     */
    fun getModelInfo(): String {
        return if (isInitialized) {
            try {
                nativeGetModelInfo(sessionPtr)
            } catch (e: Exception) {
                Log.e(TAG, "Exception getting model info", e)
                "Error getting model info"
            }
        } else {
            "Engine not initialized"
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        if (isInitialized) {
            try {
                nativeRelease(sessionPtr)
                sessionPtr = 0
                isInitialized = false
                scope.cancel()
                Log.i(TAG, "LLM engine released")
            } catch (e: Exception) {
                Log.e(TAG, "Exception during release", e)
            }
        }
    }
    
    /**
     * 获取模型目录
     */
    private fun getModelDirectory(context: Context): File {
        // 首先尝试从assets复制到内部存储
        val internalModelDir = File(context.filesDir, "models/Qwen3-0.6B-MNN")
        if (!internalModelDir.exists()) {
            internalModelDir.mkdirs()
        }
        
        // 检查是否需要复制模型文件
        val configFile = File(internalModelDir, "llm_config.json")
        if (!configFile.exists()) {
            copyAssetsToInternal(context, internalModelDir)
        }
        
        return internalModelDir
    }
    
    /**
     * 从assets复制模型文件到内部存储
     */
    private fun copyAssetsToInternal(context: Context, targetDir: File) {
        try {
            Log.i(TAG, "Copying model files from assets...")
            val assetManager = context.assets
            val modelFiles = assetManager.list("models/Qwen3-0.6B-MNN") ?: return
            
            for (fileName in modelFiles) {
                val inputStream = assetManager.open("models/Qwen3-0.6B-MNN/$fileName")
                val outputFile = File(targetDir, fileName)
                
                outputFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                
                Log.d(TAG, "Copied: $fileName")
            }
            
            Log.i(TAG, "Model files copied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model files", e)
        }
    }
    
    // Native方法声明
    private external fun nativeInit(configPath: String, modelDir: String): Long
    private external fun nativeGenerate(sessionPtr: Long, input: String, maxTokens: Int): String
    private external fun nativeReset(sessionPtr: Long)
    private external fun nativeRelease(sessionPtr: Long)
    private external fun nativeIsLoaded(sessionPtr: Long): Boolean
    private external fun nativeGetModelInfo(sessionPtr: Long): String
} 