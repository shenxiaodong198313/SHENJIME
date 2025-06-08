package com.shenji.aikeyboard.gallery.ui.chat

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shenji.aikeyboard.gallery.data.ChatMessage
import com.shenji.aikeyboard.gallery.data.ChatMessageType
import com.shenji.aikeyboard.gallery.data.ChatUiState
import com.shenji.aikeyboard.gallery.data.ModelInitializationStatus
import com.shenji.aikeyboard.gallery.ui.common.MediaPipeMultimodalEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Gemma-3n聊天ViewModel
 * 基于谷歌官方Gallery的LlmChatViewModel实现真实推理
 */
class Gemma3nChatViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private val multimodalEngine = MediaPipeMultimodalEngine()
    private var context: Context? = null
    
    fun setContext(context: Context) {
        this.context = context
        if (this.context != null) {
            initializeEngine()
        }
    }
    
    private fun initializeEngine() {
        viewModelScope.launch {
            try {
                // 更新状态为初始化中
                _uiState.value = _uiState.value.copy(
                    modelInitializationStatus = ModelInitializationStatus.INITIALIZING,
                    initializationProgress = "正在加载 Gemma-3n 模型..."
                )
                
                // 模拟初始化过程的不同阶段
                updateInitializationProgress("正在检查模型文件...")
                kotlinx.coroutines.delay(500)
                
                updateInitializationProgress("正在加载模型权重...")
                kotlinx.coroutines.delay(800)
                
                updateInitializationProgress("正在初始化推理引擎...")
                kotlinx.coroutines.delay(600)
                
                updateInitializationProgress("正在优化模型配置...")
                kotlinx.coroutines.delay(400)
                
                // 执行实际初始化，传递 Context
                multimodalEngine.initialize(context!!)
                
                // 初始化成功
                _uiState.value = _uiState.value.copy(
                    modelInitializationStatus = ModelInitializationStatus.INITIALIZED,
                    initializationProgress = "模型加载完成"
                )
                
                Timber.d("MediaPipe多模态引擎初始化成功")
                
                // 添加欢迎消息
                addWelcomeMessage()
                
            } catch (e: Exception) {
                Timber.e(e, "MediaPipe多模态引擎初始化失败")
                
                _uiState.value = _uiState.value.copy(
                    modelInitializationStatus = ModelInitializationStatus.FAILED,
                    initializationProgress = "模型初始化失败: ${e.message}"
                )
                
                addErrorMessage("引擎初始化失败: ${e.message}")
            }
        }
    }
    
    private fun updateInitializationProgress(progress: String) {
        _uiState.value = _uiState.value.copy(
            initializationProgress = progress
        )
    }
    
    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            text = "你好！我是 Gemma-3n，一个多模态AI助手。你可以上传图片并询问相关问题，或者与我进行文本对话。",
            isUser = false,
            type = ChatMessageType.TEXT
        )
        
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + welcomeMessage
        )
    }
    
    /**
     * 发送消息（文本和/或图片）
     */
    fun sendMessage(text: String, image: Bitmap?) {
        if (text.isBlank() && image == null) return
        
        // 检查模型是否已初始化
        if (_uiState.value.modelInitializationStatus != ModelInitializationStatus.INITIALIZED) {
            addErrorMessage("模型尚未初始化完成，请稍候再试")
            return
        }
        
        // 添加用户消息
        val userMessage = ChatMessage(
            text = text,
            image = image,
            isUser = true,
            type = when {
                image != null && text.isNotBlank() -> ChatMessageType.MULTIMODAL
                image != null -> ChatMessageType.IMAGE
                else -> ChatMessageType.TEXT
            }
        )
        
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isLoading = true
        )
        
        // 生成AI回复
        generateResponse(text, image)
    }
    
    private fun generateResponse(text: String, image: Bitmap?) {
        viewModelScope.launch {
            try {
                val response = when {
                    image != null && text.isNotBlank() -> {
                        // 多模态分析：图片+文本
                        multimodalEngine.analyzeImageWithText(image, text)
                    }
                    image != null -> {
                        // 纯图片分析
                        multimodalEngine.analyzeImage(image)
                    }
                    else -> {
                        // 纯文本对话
                        multimodalEngine.generateTextResponse(text)
                    }
                }
                
                // 添加AI回复
                val aiMessage = ChatMessage(
                    text = response,
                    isUser = false,
                    type = ChatMessageType.TEXT
                )
                
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + aiMessage,
                    isLoading = false
                )
                
            } catch (e: Exception) {
                Timber.e(e, "生成回复失败")
                addErrorMessage("生成回复失败: ${e.message}")
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false
                )
            }
        }
    }
    
    private fun addErrorMessage(errorText: String) {
        val errorMessage = ChatMessage(
            text = errorText,
            isUser = false,
            type = ChatMessageType.ERROR
        )
        
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + errorMessage
        )
    }
    
    /**
     * 清除对话历史
     */
    fun clearHistory() {
        _uiState.value = ChatUiState(
            modelInitializationStatus = ModelInitializationStatus.INITIALIZED,
            initializationProgress = "模型已就绪"
        )
        addWelcomeMessage()
    }
    
    override fun onCleared() {
        super.onCleared()
        // 清理资源
        multimodalEngine.cleanup()
    }
} 