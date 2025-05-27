package com.shenji.aikeyboard.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * LLM服务类
 * 为输入法提供智能文本生成功能
 */
class LLMService private constructor() {
    
    companion object {
        private const val TAG = "LLMService"
        
        @Volatile
        private var INSTANCE: LLMService? = null
        
        fun getInstance(): LLMService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LLMService().also { INSTANCE = it }
            }
        }
    }
    
    private val llmEngine = ShenjiLLMEngine.getInstance()
    private var isReady = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * 初始化服务
     */
    suspend fun initialize(context: Context): Boolean {
        return try {
            Log.i(TAG, "Initializing LLM service...")
            isReady = llmEngine.initialize(context)
            if (isReady) {
                Log.i(TAG, "LLM service ready")
            } else {
                Log.e(TAG, "Failed to initialize LLM service")
            }
            isReady
        } catch (e: Exception) {
            Log.e(TAG, "Exception during service initialization", e)
            false
        }
    }
    
    /**
     * 智能文本补全
     * 根据用户输入的文本，生成可能的补全内容
     */
    suspend fun completeText(input: String): List<String> {
        if (!isReady) {
            Log.w(TAG, "LLM service not ready")
            return emptyList()
        }
        
        return try {
            val prompt = "请补全以下文本：$input"
            val result = llmEngine.generateText(prompt, 64)
            
            if (result.isNotEmpty()) {
                // 简单处理生成的文本，提取可能的补全选项
                listOf(result.trim())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during text completion", e)
            emptyList()
        }
    }
    
    /**
     * 智能句子生成
     * 根据关键词生成完整句子
     */
    suspend fun generateSentence(keywords: String): String {
        if (!isReady) {
            Log.w(TAG, "LLM service not ready")
            return ""
        }
        
        return try {
            val prompt = "根据关键词\"$keywords\"生成一个完整的句子："
            llmEngine.generateText(prompt, 128)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during sentence generation", e)
            ""
        }
    }
    
    /**
     * 智能纠错
     * 检查并纠正文本中的错误
     */
    suspend fun correctText(input: String): String {
        if (!isReady) {
            Log.w(TAG, "LLM service not ready")
            return input
        }
        
        return try {
            val prompt = "请纠正以下文本中的错误：$input"
            val result = llmEngine.generateText(prompt, 128)
            
            if (result.isNotEmpty()) {
                result.trim()
            } else {
                input
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during text correction", e)
            input
        }
    }
    
    /**
     * 智能改写
     * 将文本改写为更好的表达
     */
    suspend fun rewriteText(input: String): String {
        if (!isReady) {
            Log.w(TAG, "LLM service not ready")
            return input
        }
        
        return try {
            val prompt = "请将以下文本改写得更加优雅：$input"
            val result = llmEngine.generateText(prompt, 128)
            
            if (result.isNotEmpty()) {
                result.trim()
            } else {
                input
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during text rewriting", e)
            input
        }
    }
    
    /**
     * 获取服务状态
     */
    fun isServiceReady(): Boolean = isReady
    
    /**
     * 获取模型信息
     */
    fun getModelInfo(): String = llmEngine.getModelInfo()
    
    /**
     * 重置会话
     */
    fun resetSession() {
        llmEngine.resetSession()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        isReady = false
        llmEngine.release()
        scope.cancel()
        Log.i(TAG, "LLM service released")
    }
} 