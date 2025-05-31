package com.shenji.aikeyboard.ai

import android.view.inputmethod.EditorInfo
import com.shenji.aikeyboard.keyboard.ShenjiInputMethodService
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * 输入法AI适配器
 * 负责连接输入法服务与AI引擎，处理AI功能的调用和结果展示
 */
class InputMethodAIAdapter(
    private val inputMethodService: ShenjiInputMethodService
) {
    private val aiEngineManager = AIEngineManager.getInstance()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * 处理拼音纠错
     */
    fun handlePinyinCorrection(input: String, context: InputContext) {
        if (input.isBlank()) {
            return
        }
        
        coroutineScope.launch {
            try {
                // 显示分析状态
                showAnalyzingState()
                
                // 获取当前引擎
                val currentEngine = aiEngineManager.getCurrentEngine()
                if (currentEngine == null) {
                    Timber.w("没有可用的AI引擎")
                    hideSmartTips()
                    return@launch
                }
                
                // 异步执行AI分析
                val suggestions: List<CorrectionSuggestion> = withContext(Dispatchers.IO) {
                    currentEngine.correctPinyin(input, context)
                }
                
                // 显示结果
                if (suggestions.isNotEmpty()) {
                    showCorrectionSuggestions(suggestions)
                } else {
                    hideSmartTips()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "拼音纠错处理失败: ${e.message}")
                hideSmartTips()
            }
        }
    }
    
    /**
     * 处理文本续写
     */
    fun handleTextContinuation(text: String, context: InputContext) {
        coroutineScope.launch {
            try {
                showAnalyzingState()
                
                // 获取当前引擎
                val currentEngine = aiEngineManager.getCurrentEngine()
                if (currentEngine == null) {
                    Timber.w("没有可用的AI引擎")
                    hideSmartTips()
                    return@launch
                }
                
                val suggestions: List<ContinuationSuggestion> = withContext(Dispatchers.IO) {
                    currentEngine.generateContinuation(text, context)
                }
                
                if (suggestions.isNotEmpty()) {
                    showContinuationSuggestions(suggestions)
                } else {
                    hideSmartTips()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "文本续写处理失败: ${e.message}")
                hideSmartTips()
            }
        }
    }
    
    /**
     * 处理语义分析
     */
    fun handleSemanticAnalysis(text: String, context: InputContext) {
        coroutineScope.launch {
            try {
                // 获取当前引擎
                val currentEngine = aiEngineManager.getCurrentEngine()
                if (currentEngine == null) {
                    Timber.w("没有可用的AI引擎")
                    return@launch
                }
                
                val analysis: SemanticAnalysis = withContext(Dispatchers.IO) {
                    currentEngine.analyzeSemantics(text, context)
                }
                
                // 根据语义分析结果调整输入建议
                processSemanticAnalysis(analysis)
                
            } catch (e: Exception) {
                Timber.e(e, "语义分析处理失败: ${e.message}")
            }
        }
    }
    
    /**
     * 创建输入上下文
     */
    fun createInputContext(
        appPackage: String,
        inputType: Int,
        previousText: String,
        cursorPosition: Int
    ): InputContext {
        return InputContext(
            appPackage = appPackage,
            inputType = inputType,
            previousText = previousText,
            cursorPosition = cursorPosition,
            userPreferences = UserPreferences(),
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 显示智能分析状态
     */
    private fun showAnalyzingState() {
        // 创建一个临时的CorrectionSuggestion用于显示分析状态
        val analyzingSuggestion = CorrectionSuggestion(
            originalInput = "",
            correctedText = "🔍 智能分析中...",
            correctedPinyin = "",
            confidence = 1.0f,
            errorType = ErrorType.UNKNOWN,
            explanation = "分析状态"
        )
        inputMethodService.showSmartTips(analyzingSuggestion)
    }
    
    /**
     * 显示纠错建议
     */
    private fun showCorrectionSuggestions(suggestions: List<CorrectionSuggestion>) {
        val topSuggestion = suggestions.maxByOrNull { it.confidence }
        
        if (topSuggestion != null && topSuggestion.confidence > 0.6f) {
            val confidenceStars = getConfidenceStars(topSuggestion.confidence)
            val enhancedSuggestion = topSuggestion.copy(
                explanation = "${topSuggestion.explanation ?: ""} $confidenceStars"
            )
            inputMethodService.showSmartTips(enhancedSuggestion)
        } else {
            hideSmartTips()
        }
    }
    
    /**
     * 显示续写建议
     */
    private fun showContinuationSuggestions(suggestions: List<ContinuationSuggestion>) {
        val topSuggestion = suggestions.maxByOrNull { it.confidence }
        
        if (topSuggestion != null && topSuggestion.confidence > 0.5f) {
            val correctionSuggestion = CorrectionSuggestion(
                originalInput = "",
                correctedText = "💡 ${topSuggestion.text}",
                correctedPinyin = "",
                confidence = topSuggestion.confidence,
                errorType = ErrorType.UNKNOWN,
                explanation = "智能续写建议"
            )
            inputMethodService.showSmartTips(correctionSuggestion)
        } else {
            hideSmartTips()
        }
    }
    
    /**
     * 处理语义分析结果
     */
    private fun processSemanticAnalysis(analysis: SemanticAnalysis) {
        // 根据语义分析调整输入策略
        when (analysis.intent) {
            "email" -> {
                // 邮件场景，提供正式用词建议
                Timber.d("🎯 检测到邮件场景，启用正式用词模式")
            }
            "chat" -> {
                // 聊天场景，提供轻松用词建议
                Timber.d("🎯 检测到聊天场景，启用轻松用词模式")
            }
            "document" -> {
                // 文档场景，提供专业用词建议
                Timber.d("🎯 检测到文档场景，启用专业用词模式")
            }
        }
    }
    
    /**
     * 隐藏智能提示
     */
    private fun hideSmartTips() {
        inputMethodService.hideSmartTips()
    }
    
    /**
     * 获取置信度星级显示
     */
    private fun getConfidenceStars(confidence: Float): String {
        val stars = (confidence * 5).toInt()
        return "★".repeat(stars) + "☆".repeat(5 - stars)
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        coroutineScope.cancel()
    }
}

/**
 * 扩展ShenjiInputMethodService以支持AI功能
 */
fun ShenjiInputMethodService.getCurrentAppPackage(): String {
    return try {
        // 获取当前应用包名的逻辑
        currentInputConnection?.let { ic ->
            // 这里需要通过其他方式获取包名，因为InputConnection没有直接方法
            "unknown.app"
        } ?: "unknown.app"
    } catch (e: Exception) {
        "unknown.app"
    }
}

fun ShenjiInputMethodService.getCurrentInputType(): Int {
    return try {
        currentInputEditorInfo?.inputType ?: EditorInfo.TYPE_CLASS_TEXT
    } catch (e: Exception) {
        EditorInfo.TYPE_CLASS_TEXT
    }
}

fun ShenjiInputMethodService.getPreviousText(maxLength: Int = 50): String {
    return try {
        currentInputConnection?.getTextBeforeCursor(maxLength, 0)?.toString() ?: ""
    } catch (e: Exception) {
        ""
    }
}

fun ShenjiInputMethodService.getCursorPosition(): Int {
    return try {
        // 获取光标位置的逻辑
        0 // 简化实现
    } catch (e: Exception) {
        0
    }
} 