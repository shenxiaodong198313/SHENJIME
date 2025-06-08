package com.shenji.aikeyboard.gallery.data

import android.graphics.Bitmap

/**
 * 聊天消息数据类
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val image: Bitmap? = null,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val type: ChatMessageType = ChatMessageType.TEXT
)

/**
 * 消息类型枚举
 */
enum class ChatMessageType {
    TEXT,           // 纯文本消息
    IMAGE,          // 纯图片消息
    MULTIMODAL,     // 图片+文本消息
    LOADING,        // 加载中消息
    ERROR           // 错误消息
}

/**
 * 模型初始化状态枚举
 */
enum class ModelInitializationStatus {
    NOT_INITIALIZED,    // 未初始化
    INITIALIZING,       // 初始化中
    INITIALIZED,        // 已初始化
    FAILED             // 初始化失败
}

/**
 * 聊天UI状态
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val modelInitializationStatus: ModelInitializationStatus = ModelInitializationStatus.NOT_INITIALIZED,
    val initializationProgress: String = ""
) 