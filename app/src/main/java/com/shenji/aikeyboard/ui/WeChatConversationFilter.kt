package com.shenji.aikeyboard.ui

import android.view.accessibility.AccessibilityNodeInfo
import com.ven.assists.AssistsCore
import timber.log.Timber

/**
 * 微信对话数据过滤器
 * 从assists框架获取的节点信息中提取微信对话内容
 */
object WeChatConversationFilter {
    
    private const val TAG = "WeChatConversationFilter"
    
    /**
     * 对话消息数据类
     */
    data class ConversationMessage(
        val senderName: String,
        val content: String,
        val timestamp: String = ""
    )
    
    /**
     * 过滤后的微信对话数据
     */
    data class FilteredWeChatData(
        val packageName: String,
        val conversationMessages: List<ConversationMessage>,
        val totalNodes: Int,
        val textNodes: Int
    )
    
    /**
     * 从assists节点数据中过滤微信对话内容
     */
    fun filterWeChatConversation(): FilteredWeChatData {
        try {
            // 检查是否在微信应用中
            val packageName = AssistsCore.getPackageName()
            if (packageName != "com.tencent.mm") {
                Timber.w("$TAG: 当前不在微信应用中，包名: $packageName")
                return FilteredWeChatData(
                    packageName = packageName,
                    conversationMessages = listOf(
                        ConversationMessage("系统提示", "请先打开微信聊天界面")
                    ),
                    totalNodes = 0,
                    textNodes = 0
                )
            }
            
            // 获取所有节点
            val allNodes = AssistsCore.getAllNodes()
            Timber.d("$TAG: 获取到 ${allNodes.size} 个节点")
            
            // 提取对话消息
            val messages = extractConversationMessages(allNodes)
            
            // 统计文本节点数量
            val textNodeCount = allNodes.count { node ->
                !node.text.isNullOrEmpty() || !node.contentDescription.isNullOrEmpty()
            }
            
            return FilteredWeChatData(
                packageName = packageName,
                conversationMessages = messages,
                totalNodes = allNodes.size,
                textNodes = textNodeCount
            )
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 过滤微信对话数据时出错")
            return FilteredWeChatData(
                packageName = "error",
                conversationMessages = listOf(
                    ConversationMessage("错误", "获取对话数据失败: ${e.message}")
                ),
                totalNodes = 0,
                textNodes = 0
            )
        }
    }
    
    /**
     * 从节点列表中提取对话消息
     */
    private fun extractConversationMessages(nodes: List<AccessibilityNodeInfo>): List<ConversationMessage> {
        val messages = mutableListOf<ConversationMessage>()
        val processedTexts = mutableSetOf<String>() // 避免重复消息
        
        try {
            // 查找头像描述节点，用于提取发送者姓名
            val avatarNodes = nodes.filter { node ->
                val description = node.contentDescription?.toString() ?: ""
                description.contains("头像") && description.contains("描述:")
            }
            
            // 查找消息文本节点
            val messageNodes = nodes.filter { node ->
                val text = node.text?.toString() ?: ""
                val className = node.className?.toString() ?: ""
                
                // 过滤出可能是消息内容的文本节点
                text.isNotEmpty() && 
                className.contains("TextView") &&
                !isSystemUIText(text) &&
                text.length > 1 // 排除单个字符
            }
            
            Timber.d("$TAG: 找到 ${avatarNodes.size} 个头像节点，${messageNodes.size} 个消息节点")
            
            // 处理头像节点，提取发送者姓名
            val senderNames = mutableMapOf<String, String>()
            avatarNodes.forEach { node ->
                val description = node.contentDescription?.toString() ?: ""
                val senderName = extractSenderName(description)
                if (senderName.isNotEmpty()) {
                    senderNames[node.toString()] = senderName
                }
            }
            
            // 处理消息节点
            messageNodes.forEach { node ->
                val text = node.text?.toString() ?: ""
                
                // 避免重复消息
                if (text in processedTexts) return@forEach
                processedTexts.add(text)
                
                // 尝试匹配发送者
                val senderName = findSenderForMessage(node, senderNames, nodes) ?: "未知用户"
                
                // 过滤时间戳等无关信息
                if (!isTimestamp(text) && !isSystemMessage(text)) {
                    messages.add(ConversationMessage(
                        senderName = senderName,
                        content = text
                    ))
                }
            }
            
            Timber.d("$TAG: 提取到 ${messages.size} 条对话消息")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 提取对话消息时出错")
        }
        
        return messages.take(20) // 限制显示最近20条消息
    }
    
    /**
     * 从头像描述中提取发送者姓名
     */
    private fun extractSenderName(description: String): String {
        return try {
            // 格式: "描述: xxx头像"
            if (description.startsWith("描述:") && description.endsWith("头像")) {
                description.removePrefix("描述:").removeSuffix("头像").trim()
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 为消息节点查找对应的发送者
     */
    private fun findSenderForMessage(
        messageNode: AccessibilityNodeInfo,
        senderNames: Map<String, String>,
        allNodes: List<AccessibilityNodeInfo>
    ): String? {
        return try {
            // 简单策略：查找附近的头像节点
            // 这里可以根据实际的微信界面结构进行优化
            senderNames.values.firstOrNull() ?: "联系人"
        } catch (e: Exception) {
            "未知用户"
        }
    }
    
    /**
     * 判断是否为系统UI文本
     */
    private fun isSystemUIText(text: String): Boolean {
        val systemTexts = listOf(
            "聊天信息", "切换到按住说话", "表情", "更多功能按钮",
            "发送", "输入", "语音", "视频通话", "音频通话"
        )
        return systemTexts.any { text.contains(it) }
    }
    
    /**
     * 判断是否为时间戳
     */
    private fun isTimestamp(text: String): Boolean {
        return text.matches(Regex("\\d{1,2}:\\d{2}")) || 
               text.matches(Regex("\\d{4}-\\d{1,2}-\\d{1,2}")) ||
               text.contains("昨天") || text.contains("今天")
    }
    
    /**
     * 判断是否为系统消息
     */
    private fun isSystemMessage(text: String): Boolean {
        return text.contains("撤回了一条消息") ||
               text.contains("开启了朋友验证") ||
               text.contains("你们已经是好友")
    }
    
    /**
     * 格式化对话数据为显示文本
     */
    fun formatConversationForDisplay(data: FilteredWeChatData): String {
        return buildString {
            append("=== 基础信息 ===\n")
            append("无障碍服务状态: 已启用\n")
            append("根节点: 已获取\n")
            append("当前包名: ${data.packageName}\n\n")
            
            append("=== 节点统计 ===\n")
            append("节点总数: ${data.totalNodes}\n")
            append("文本节点数: ${data.textNodes}\n\n")
            
            append("=== 微信对话内容 ===\n")
            if (data.conversationMessages.isEmpty()) {
                append("未检测到对话消息\n")
            } else {
                append("对话消息数: ${data.conversationMessages.size}\n\n")
                data.conversationMessages.forEach { message ->
                    append("${message.senderName}: ${message.content}\n\n")
                }
            }
        }
    }
    
    /**
     * 格式化对话数据为简洁显示文本（仅显示对话内容）
     */
    fun formatConversationForSimpleDisplay(data: FilteredWeChatData): String {
        return buildString {
            if (data.conversationMessages.isEmpty()) {
                append("暂无对话消息")
            } else {
                data.conversationMessages.forEach { message ->
                    append("${message.senderName}: ${message.content}\n\n")
                }
            }
        }.trim()
    }
    
    /**
     * 格式化对话数据为AI模型输入格式
     * 专门用于AI模型理解和生成回复
     */
    fun formatConversationForAI(data: FilteredWeChatData): String {
        return buildString {
            if (data.conversationMessages.isEmpty()) {
                append("没有检测到对话内容")
            } else {
                // 只保留最近的对话消息，按时间顺序排列
                val recentMessages = data.conversationMessages.takeLast(10)
                
                recentMessages.forEach { message ->
                    append("${message.senderName}: ${message.content}\n")
                }
                
                // 如果消息太少，添加上下文提示
                if (recentMessages.size < 3) {
                    append("\n[注意：对话内容较少，请生成友好的回复]")
                }
            }
        }.trim()
    }
} 