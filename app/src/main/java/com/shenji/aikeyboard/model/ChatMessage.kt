package com.shenji.aikeyboard.model

/**
 * 聊天消息数据类
 */
data class ChatMessage(
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long
) 