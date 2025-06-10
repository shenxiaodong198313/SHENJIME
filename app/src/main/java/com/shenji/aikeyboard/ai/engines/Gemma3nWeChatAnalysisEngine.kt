package com.shenji.aikeyboard.ai.engines

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.shenji.aikeyboard.gallery.ui.common.MediaPipeMultimodalEngine

/**
 * 微信对话分析结果数据类
 */
data class WeChatAnalysisResult(
    val contactName: String = "",
    val summary: String = "",
    val suggestion: String = ""
)

/**
 * Gemma3n微信对话分析引擎
 */
class Gemma3nWeChatAnalysisEngine {
    
    companion object {
        private const val TAG = "Gemma3nWeChatAnalysisEngine"
        
        // 微信对话分析提示词
        private const val WECHAT_ANALYSIS_PROMPT = """
你是一个专业的微信对话分析助手。请仔细分析这张微信聊天截图，并按照以下要求提供分析：

**重要说明**：
- 左侧的消息气泡（通常是灰色/白色背景）是对方发送的消息
- 右侧的消息气泡（通常是绿色背景）是用户自己已经发送的回复
- 你只需要分析和总结左侧对方的消息内容
- 右侧的绿色消息是用户已经回复的内容，不要把它当作对方的话

请按照以下格式回答：

联系人名称：[从聊天界面顶部提取联系人姓名，如果看不清楚就写"联系人"]

对话摘要：[只总结左侧对方发送的消息内容，不要包含右侧用户已发送的回复。概括对方想表达的主要意思、情绪、需求或问题]

回复建议：[基于对方的消息内容，提供一个简洁、合适的回复建议。这个建议应该是可以直接发送给对方的完整回复内容，语气要自然友好]

请确保回复建议是完整的句子，可以直接复制发送，不要包含"可以回复"等提示性文字。
"""
    }
    
    private var mediaPipeEngine: MediaPipeMultimodalEngine? = null
    private var isInitialized = false
    
    /**
     * 初始化引擎
     */
    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) return@withContext true
            
            mediaPipeEngine = MediaPipeMultimodalEngine()
            mediaPipeEngine!!.initialize(context)
            isInitialized = true
            
            Timber.d("$TAG: Engine initialized successfully")
            return@withContext true
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize engine")
            return@withContext false
        }
    }
    
    /**
     * 分析微信对话截图（流式）
     */
    fun analyzeWeChatConversationStream(bitmap: Bitmap): Flow<String> = callbackFlow {
        try {
            if (!isInitialized || mediaPipeEngine == null) {
                trySend("❌ AI引擎未初始化")
                return@callbackFlow
            }
            
            Timber.d("$TAG: Starting WeChat conversation analysis")
            
            mediaPipeEngine!!.analyzeImageStreamWithCallback(
                bitmap = bitmap,
                prompt = WECHAT_ANALYSIS_PROMPT
            ) { result ->
                trySend(result)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error in WeChat conversation analysis")
            trySend("分析过程中出现错误: ${e.message}")
        }
        
        awaitClose { }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 解析AI响应为结构化数据
     */
    fun parseAnalysisResult(response: String): WeChatAnalysisResult {
        try {
            val lines = response.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            
            var contactName = ""
            var summary = ""
            var suggestion = ""
            
            for (line in lines) {
                when {
                    line.startsWith("联系人名称：") || line.startsWith("联系人:") -> {
                        contactName = line.substringAfter("：").substringAfter(":").trim()
                    }
                    line.startsWith("对话摘要：") || line.startsWith("对话摘要:") -> {
                        summary = line.substringAfter("：").substringAfter(":").trim()
                    }
                    line.startsWith("回复建议：") || line.startsWith("回复建议:") -> {
                        suggestion = line.substringAfter("：").substringAfter(":").trim()
                    }
                }
            }
            
            // 如果某些字段为空，尝试从响应中提取
            if (contactName.isEmpty()) {
                contactName = "联系人"
            }
            
            if (summary.isEmpty() && response.contains("摘要")) {
                val summaryStart = response.indexOf("摘要")
                val summaryEnd = response.indexOf("建议", summaryStart)
                if (summaryStart != -1) {
                    summary = if (summaryEnd != -1) {
                        response.substring(summaryStart, summaryEnd)
                    } else {
                        response.substring(summaryStart)
                    }.replace("摘要", "").replace("：", "").replace(":", "").trim()
                }
            }
            
            if (suggestion.isEmpty() && response.contains("建议")) {
                val suggestionStart = response.indexOf("建议")
                if (suggestionStart != -1) {
                    suggestion = response.substring(suggestionStart)
                        .replace("建议", "").replace("：", "").replace(":", "").trim()
                }
            }
            
            return WeChatAnalysisResult(
                contactName = contactName.ifEmpty { "联系人" },
                summary = summary.ifEmpty { "正在分析对话内容..." },
                suggestion = suggestion.ifEmpty { "正在生成回复建议..." }
            )
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error parsing analysis result")
            return WeChatAnalysisResult(
                contactName = "联系人",
                summary = "解析分析结果时出现错误",
                suggestion = "无法生成回复建议"
            )
        }
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            mediaPipeEngine?.cleanup()
            mediaPipeEngine = null
            isInitialized = false
            Timber.d("$TAG: Engine released")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error releasing engine")
        }
    }
} 