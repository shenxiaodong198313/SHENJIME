package com.shenji.aikeyboard.ui

import android.view.accessibility.AccessibilityNodeInfo
import com.shenji.aikeyboard.utils.ScreenOCRHelper
import com.ven.assists.AssistsCore
import kotlinx.coroutines.runBlocking
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
        val timestamp: String = "",
        val isFromOther: Boolean = true // true=对方消息, false=我的消息
    )
    
    /**
     * 过滤后的微信对话数据
     */
    data class FilteredWeChatData(
        val packageName: String,
        val conversationMessages: List<ConversationMessage>,
        val totalNodes: Int,
        val textNodes: Int,
        val otherPersonName: String = "" // 对方的昵称（从顶部导航栏获取）
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
            
            // 获取对方昵称（从顶部导航栏）
            val otherPersonName = extractOtherPersonName(allNodes)
            Timber.d("$TAG: 对方昵称: $otherPersonName")
            
            // 提取对话消息
            val messages = extractConversationMessages(allNodes, otherPersonName)
            
            // 统计文本节点数量
            val textNodeCount = allNodes.count { node ->
                !node.text.isNullOrEmpty() || !node.contentDescription.isNullOrEmpty()
            }
            
            return FilteredWeChatData(
                packageName = packageName,
                conversationMessages = messages,
                totalNodes = allNodes.size,
                textNodes = textNodeCount,
                otherPersonName = otherPersonName
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
     * 从顶部导航栏提取对方昵称
     * 优先使用OCR识别，如果失败则回退到无障碍服务方式
     */
    private fun extractOtherPersonName(nodes: List<AccessibilityNodeInfo>): String {
        return try {
            // 首先尝试使用OCR识别顶部菜单栏文本
            Timber.d("$TAG: 尝试使用OCR识别顶部菜单栏文本")
            val ocrResult = runBlocking {
                try {
                    ScreenOCRHelper.getTopAreaText(0.15f) // 识别顶部15%区域
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: OCR识别异常")
                    null
                }
            }
            
            if (!ocrResult.isNullOrBlank()) {
                Timber.d("$TAG: OCR识别成功，获取到昵称: $ocrResult")
                return ocrResult
            }
            
            Timber.w("$TAG: OCR识别失败，回退到无障碍服务方式")
            
            // 回退到原有的无障碍服务方式
            val allTitleCandidates = nodes.filter { node ->
                val text = node.text?.toString() ?: ""
                val className = node.className?.toString() ?: ""
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                
                // 基础筛选条件
                className.contains("TextView") && 
                text.isNotEmpty() && 
                text.length > 1 &&
                bounds.top < 500 && // 扩大顶部区域范围
                !isTimestamp(text)
            }
            
            Timber.d("$TAG: 找到 ${allTitleCandidates.size} 个所有标题候选节点")
            allTitleCandidates.forEach { node ->
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                Timber.d("$TAG: 所有候选: '${node.text}' at (${bounds.centerX()}, ${bounds.top}) class=${node.className}")
            }
            
            // 查找顶部导航栏的标题文本（严格条件）
            val titleNodes = allTitleCandidates.filter { node ->
                val text = node.text?.toString() ?: ""
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                
                // 严格的标题节点筛选条件
                text.length < 20 && // 标题通常不会太长
                bounds.top < 300 && // 必须在屏幕顶部区域
                bounds.centerX() > 200 && bounds.centerX() < 800 && // 必须在屏幕中央区域
                !isSystemUIText(text) &&
                !text.contains("聊天") &&
                !text.contains("消息") &&
                !text.contains("发送") &&
                !text.contains("输入") &&
                !text.contains("你好") && // 排除常见的问候语
                !text.contains("hello") &&
                !text.contains("在吗") &&
                !text.contains("😊") &&
                !text.contains("😄") &&
                !text.matches(Regex(".*[。！？!?].*")) // 排除包含句号、感叹号、问号的文本
            }
            
            // 优先选择位置最靠上且最居中的节点
            val topTitleNode = titleNodes.minByOrNull { node ->
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                bounds.top + Math.abs(bounds.centerX() - 540) // 综合考虑高度和居中程度
            }
            
            val otherName = topTitleNode?.text?.toString()?.trim() ?: ""
            
            Timber.d("$TAG: 找到 ${titleNodes.size} 个严格标题候选节点")
            titleNodes.forEach { node ->
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                Timber.d("$TAG: 严格候选: '${node.text}' at (${bounds.centerX()}, ${bounds.top})")
            }
            
            if (otherName.isNotEmpty() && otherName != "你好啊" && otherName != "hello在吗") {
                Timber.d("$TAG: 无障碍服务提取到对方昵称: $otherName")
                otherName
            } else {
                Timber.w("$TAG: 未能提取到有效的对方昵称，使用默认值")
                "未获取到"
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 提取对方昵称时出错")
            "获取失败"
        }
    }
    
    /**
     * 从节点列表中提取对话消息
     */
    private fun extractConversationMessages(nodes: List<AccessibilityNodeInfo>, otherPersonName: String): List<ConversationMessage> {
        val messages = mutableListOf<ConversationMessage>()
        val processedTexts = mutableSetOf<String>() // 避免重复消息
        
        try {
            // 查找消息文本节点和对应的发送者信息
            val messageNodes = nodes.filter { node ->
                val text = node.text?.toString() ?: ""
                val className = node.className?.toString() ?: ""
                
                // 过滤出可能是消息内容的文本节点
                text.isNotEmpty() && 
                className.contains("TextView") &&
                !isSystemUIText(text) &&
                !isTimestamp(text) &&
                !isSystemMessage(text) &&
                text.length > 1 && // 排除单个字符
                text != otherPersonName // 排除昵称本身
            }
            
            Timber.d("$TAG: 找到 ${messageNodes.size} 个消息节点")
            Timber.d("$TAG: OCR获取的对方昵称: '$otherPersonName'")
            
            // 处理消息节点，简化逻辑：根据消息位置判断发送者
            messageNodes.forEach { node ->
                val text = node.text?.toString() ?: ""
                
                // 避免重复消息
                if (text in processedTexts) return@forEach
                processedTexts.add(text)
                
                // 获取消息位置
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                
                // 简化判断逻辑：根据消息气泡位置判断发送者
                // 微信中：左侧消息=对方，右侧消息=我
                val screenWidth = 1080 // 默认屏幕宽度，可以动态获取
                val isMyMessage = bounds.centerX() > screenWidth * 0.5 // 消息在屏幕右半部分
                
                val isFromOther = !isMyMessage
                val displaySenderName = if (isFromOther) otherPersonName else "我"
                
                messages.add(ConversationMessage(
                    senderName = displaySenderName,
                    content = text,
                    isFromOther = isFromOther
                ))
                
                Timber.d("$TAG: 消息 '$text' - 位置X: ${bounds.centerX()}, 屏幕宽度: $screenWidth, 是我的消息: $isMyMessage, 显示发送者: $displaySenderName")
            }
            
            Timber.d("$TAG: 提取到 ${messages.size} 条对话消息")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 提取对话消息时出错")
        }
        
        return messages.take(20) // 限制显示最近20条消息
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
            // 首先显示获取到的对话对象信息
            append("获取对话对象：${data.otherPersonName}\n\n")
            
            if (data.conversationMessages.isEmpty()) {
                append("暂无对话消息")
            } else {
                data.conversationMessages.forEach { message ->
                    val prefix = if (message.isFromOther) "👤 ${message.senderName}" else "🤖 ${message.senderName}"
                    append("$prefix: ${message.content}\n\n")
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