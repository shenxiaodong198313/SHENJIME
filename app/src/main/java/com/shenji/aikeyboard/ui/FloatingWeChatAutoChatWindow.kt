package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.assists.AssistsManager
import com.shenji.aikeyboard.llm.LlmManager
import com.shenji.aikeyboard.utils.ScreenOCRHelper
import com.ven.assists.AssistsCore
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 微信AI自动聊天悬浮窗
 * 使用assists框架显示过滤后的微信对话内容，并集成GEMMA3N-4b模型生成AI回复
 */
class FloatingWeChatAutoChatWindow(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    
    companion object {
        private const val TAG = "FloatingWeChatAutoChatWindow"
        
        // AI聊天提示词模板
        private const val CHAT_PROMPT_TEMPLATE = """
你是一个智能聊天助手，请根据以下微信对话内容，生成一个合适的回复。

对话内容：
%s

要求：
1. 回复要自然、友好、符合对话语境
2. 回复长度适中，不要太长或太短
3. 根据对话内容的情感色彩调整回复语调
4. 如果是问题，请给出有用的回答
5. 如果是日常聊天，请保持轻松愉快的语调

请直接给出回复内容，不要包含任何解释或格式标记：
        """
    }
    
    // UI组件
    private var contentView: View? = null
    private var tvChatTarget: TextView? = null
    private var tvStatusContent: TextView? = null
    private var scrollViewStatus: ScrollView? = null
    
    // Assists窗口包装器
    private var windowWrapper: AssistsWindowWrapper? = null
    
    // AI模型管理器
    private var llmManager: LlmManager? = null
    
    // 当前状态
    private var analysisJob: Job? = null
    private var monitoringJob: Job? = null
    private var currentConversationText = ""
    private var currentConversationData = ""
    private var isAIProcessing = false
    private var lastProcessedMessages = mutableSetOf<String>() // 记录已处理的消息
    private var isMonitoring = false
    // 添加消息检测相关的状态变量
    private var lastMessageCount = 0 // 记录上次检测到的消息总数
    private var lastOtherPersonMessageCount = 0 // 记录上次检测到的对方消息数量
    private var lastDetectedMessageContent = "" // 记录上次检测到的最新消息内容
    private var aiReplyCount = 0 // 记录AI回复次数，用于调试
    
    /**
     * 初始化窗口
     */
    fun initializeWindow() {
        try {
            createContentView()
            setupAssistsWindow()
            setupUI()
            initializeAIModel()
            Timber.d("$TAG: Window initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize window")
        }
    }
    
    /**
     * 初始化AI模型
     */
    private fun initializeAIModel() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Starting AI model initialization")
                llmManager = LlmManager.getInstance(context)
                
                // 尝试初始化GEMMA3N-4b模型，如果失败则尝试默认模型
                var success = llmManager?.initializeGemma3n() ?: false
                
                if (!success) {
                    Timber.w("$TAG: GEMMA3N-4b initialization failed, trying default model")
                    success = llmManager?.initialize() ?: false
                }
                
                if (success) {
                    Timber.d("$TAG: AI model initialized successfully")
                } else {
                    Timber.e("$TAG: Failed to initialize any AI model")
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error initializing AI model")
            }
        }
    }
    
    /**
     * 显示窗口并开始分析
     */
    fun showAndAnalyze() {
        try {
            // 检查是否已经有窗口在显示
            windowWrapper?.let { wrapper ->
                if (AssistsWindowManager.contains(wrapper)) {
                    Timber.d("$TAG: Window already exists and is visible, not creating duplicate")
                    return
                }
            }
            
            // 如果没有窗口或窗口不存在，创建新窗口
            createContentView()
            setupAssistsWindow()
            setupUI()
            
            windowWrapper?.let { wrapper ->
                // 延迟一下再添加窗口，确保Service完全启动
                coroutineScope.launch {
                    try {
                        kotlinx.coroutines.delay(100) // 延迟100ms
                        withContext(Dispatchers.Main) {
                            AssistsWindowManager.add(wrapper)
                            
                            // 隐藏悬浮按钮
                            hideFloatingButton()
                            
                            // 开始持续监控模式
                            startContinuousMonitoring()
                            
                            // 自动点击输入框并启用AI回复模式
                            autoClickInputBoxAndEnableAIMode()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: Failed to add window with delay")
                        // 如果还是失败，尝试重新创建窗口
                        withContext(Dispatchers.Main) {
                            recreateWindow()
                        }
                    }
                }
            } ?: run {
                Timber.w("$TAG: WindowWrapper is null after setup")
                recreateWindow()
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to show window")
            recreateWindow()
        }
    }
    
    /**
     * 重新创建窗口
     */
    private fun recreateWindow() {
        try {
            Timber.d("$TAG: Recreating window")
            
            // 清理旧窗口
            windowWrapper?.let { wrapper ->
                try {
                    if (AssistsWindowManager.contains(wrapper)) {
                        AssistsWindowManager.removeView(wrapper.getView())
                    }
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: Error removing old window")
                }
            }
            
            // 重新初始化
            createContentView()
            setupAssistsWindow()
            setupUI()
            
            // 延迟添加新窗口
            coroutineScope.launch {
                try {
                    kotlinx.coroutines.delay(200) // 延迟200ms
                    withContext(Dispatchers.Main) {
                        windowWrapper?.let { wrapper ->
                            AssistsWindowManager.add(wrapper)
                            startContinuousMonitoring()
                            autoClickInputBoxAndEnableAIMode()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Failed to recreate window")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "窗口创建失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error in recreateWindow")
        }
    }
    
    /**
     * 关闭窗口
     */
    fun close() {
        try {
            // 停止所有任务
            analysisJob?.cancel()
            monitoringJob?.cancel()
            isMonitoring = false
            
            // 恢复悬浮按钮显示
            showFloatingButton()
            
            // 退出AI回复模式
            disableAIReplyMode()
            
            windowWrapper?.let { wrapper ->
                try {
                    if (AssistsWindowManager.contains(wrapper)) {
                        AssistsWindowManager.removeView(wrapper.getView())
                    }
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: Error removing window view, window may already be removed")
                }
            }
            
            // 延迟清理窗口引用，确保关闭操作完成
            Handler(android.os.Looper.getMainLooper()).postDelayed({
                windowWrapper = null
                Timber.d("$TAG: Window reference cleared")
            }, 500)
            
            Timber.d("$TAG: Window closed")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error closing window")
        }
    }
    
    /**
     * 创建内容视图
     */
    private fun createContentView() {
        contentView = LayoutInflater.from(context).inflate(R.layout.content_wechat_auto_chat, null)
    }
    
    /**
     * 设置Assists窗口
     */
    private fun setupAssistsWindow() {
        contentView?.let { view ->
            windowWrapper = AssistsWindowWrapper(
                view = view,
                wmLayoutParams = AssistsWindowManager.createLayoutParams().apply {
                    width = (context.resources.displayMetrics.widthPixels * 0.6).toInt() // 减少宽度到60%
                    height = (context.resources.displayMetrics.heightPixels * 0.28).toInt() // 减少高度30% (0.4 * 0.7 = 0.28)
                    // 设置默认位置在右上角
                    x = (context.resources.displayMetrics.widthPixels * 0.3).toInt()
                    y = (context.resources.displayMetrics.heightPixels * 0.1).toInt()
                },
                onClose = { parent ->
                    // 当用户点击关闭按钮时的回调
                    Timber.d("$TAG: User clicked close button, restoring floating button")
                    
                    // 恢复悬浮按钮显示
                    showFloatingButton()
                    
                    // 退出AI回复模式
                    disableAIReplyMode()
                    
                    // 停止监控任务
                    analysisJob?.cancel()
                    monitoringJob?.cancel()
                    isMonitoring = false
                    
                    // 延迟清理窗口引用，确保AssistsWindowWrapper完成关闭操作
                    Handler(android.os.Looper.getMainLooper()).postDelayed({
                        windowWrapper = null
                        Timber.d("$TAG: Window reference cleared by user close")
                    }, 500)
                    
                    Timber.d("$TAG: Window closed by user")
                }
            ).apply {
                showOption = true
                showBackground = true
                initialCenter = false // 不居中，使用自定义位置
            }
        }
    }
    
    /**
     * 设置UI组件
     */
    private fun setupUI() {
        contentView?.let { view ->
            try {
                tvChatTarget = view.findViewById(R.id.tv_chat_target)
                tvStatusContent = view.findViewById(R.id.tv_status_content)
                scrollViewStatus = view.findViewById(R.id.scroll_view_status)
                
                // 初始显示
                showInitialState()
                
                Timber.d("$TAG: UI setup completed")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to setup UI")
            }
        }
    }
    
    /**
     * 开始持续监控模式
     */
    private fun startContinuousMonitoring() {
        if (isMonitoring) {
            Timber.w("$TAG: Monitoring already active")
            return
        }
        
        // 重置监控状态
        resetMonitoringState()
        
        isMonitoring = true
        updateStatusContent("🤖 AI-GEMMA3N-4B模型加载成功\n\n⏳ 未检测到有新的聊天消息")
        
        monitoringJob = coroutineScope.launch {
            while (isMonitoring) {
                try {
                    // 检查新消息
                    checkForNewMessages()
                    
                    // 等待5秒
                    kotlinx.coroutines.delay(5000)
                    
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Error in monitoring loop")
                    withContext(Dispatchers.Main) {
                        updateStatusContent("❌ 监控过程中出现错误：${e.message}")
                    }
                    kotlinx.coroutines.delay(5000) // 错误后也等待5秒再重试
                }
            }
        }
    }
    
    /**
     * 重置监控状态
     */
    private fun resetMonitoringState() {
        Timber.d("$TAG: Resetting monitoring state")
        lastProcessedMessages.clear()
        lastMessageCount = 0
        lastOtherPersonMessageCount = 0
        lastDetectedMessageContent = ""
        aiReplyCount = 0
        isAIProcessing = false
    }
    
    /**
     * 检查新消息
     */
    private suspend fun checkForNewMessages() {
        if (isAIProcessing) {
            Timber.d("$TAG: AI is processing, skip this check")
            return
        }
        
        try {
            // 检查assists服务状态
            if (!AssistsManager.isAccessibilityServiceEnabled()) {
                withContext(Dispatchers.Main) {
                    updateStatusContent("❌ Assists无障碍服务未启用")
                }
                return
            }
            
            // 在IO线程中执行数据过滤
            val filteredData = withContext(Dispatchers.IO) {
                WeChatConversationFilter.filterWeChatConversation()
            }
            
            // 更新对话对象显示
            withContext(Dispatchers.Main) {
                val targetName = if (filteredData.otherPersonName.isNotEmpty()) {
                    filteredData.otherPersonName
                } else {
                    "未获取到"
                }
                updateChatTarget(targetName)
            }
            
            // 检查是否有新消息
            val newMessages = checkForNewConversationMessages(filteredData)
            
            // 统计当前消息状态
            val currentMessageCount = filteredData.conversationMessages.size
            val currentOtherPersonMessages = filteredData.conversationMessages.filter { it.isFromOther }
            val currentOtherPersonMessageCount = currentOtherPersonMessages.size
            val currentLatestOtherMessage = currentOtherPersonMessages.lastOrNull()?.content ?: ""
            
            // 添加详细调试信息
            Timber.d("$TAG: === 消息检测状态 ===")
            Timber.d("$TAG: 总消息数: $currentMessageCount (上次: $lastMessageCount)")
            Timber.d("$TAG: 对方消息数: $currentOtherPersonMessageCount (上次: $lastOtherPersonMessageCount)")
            Timber.d("$TAG: 最新对方消息: '$currentLatestOtherMessage' (上次: '$lastDetectedMessageContent')")
            Timber.d("$TAG: 新消息数量: ${newMessages.size}")
            Timber.d("$TAG: AI回复次数: $aiReplyCount")
            Timber.d("$TAG: 已处理消息数: ${lastProcessedMessages.size}")
            
            if (newMessages.isNotEmpty()) {
                Timber.d("$TAG: Found ${newMessages.size} new messages")
                
                // 显示检测到新消息的状态
                val latestMessage = newMessages.last()
                withContext(Dispatchers.Main) {
                    updateStatusContent("✅ 检测到有新的聊天消息\n\n最新消息：\n${latestMessage.senderName}: ${latestMessage.content}")
                }
                
                // 更新检测状态
                lastMessageCount = currentMessageCount
                lastOtherPersonMessageCount = currentOtherPersonMessageCount
                lastDetectedMessageContent = latestMessage.content
                
                // 延迟一下让用户看到消息
                kotlinx.coroutines.delay(1000)
                
                // 准备AI分析的上下文（最多5条消息）
                val contextMessages = getRecentMessagesForAI(filteredData, 5)
                currentConversationData = contextMessages
                
                // 显示正在发送给AI的状态
                withContext(Dispatchers.Main) {
                    updateStatusContent("📤 正在把最新的聊天消息发送给AI_GEMMA3N-4B进行分析\n\n发送内容：\n${latestMessage.content}")
                }
                
                // 生成AI回复
                generateAIReply()
                
            } else {
                // 没有新消息，显示等待状态
                withContext(Dispatchers.Main) {
                    val statusText = if (aiReplyCount > 0) {
                        "⏳ 未检测到有新的聊天消息\n\n(已自动回复 $aiReplyCount 次，每5秒检查一次)"
                    } else {
                        "⏳ 未检测到有新的聊天消息"
                    }
                    updateStatusContent(statusText)
                }
                
                // 更新当前状态（即使没有新消息也要更新，用于下次比较）
                lastMessageCount = currentMessageCount
                lastOtherPersonMessageCount = currentOtherPersonMessageCount
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error checking for new messages")
            withContext(Dispatchers.Main) {
                updateStatusContent("❌ 检查新消息时出错：${e.message}")
            }
        }
    }
    
    /**
     * 检查是否有新的对话消息（只检查对方的消息）
     * 优化后的检测逻辑：基于消息数量和内容变化来判断新消息
     */
    private fun checkForNewConversationMessages(data: WeChatConversationFilter.FilteredWeChatData): List<WeChatConversationFilter.ConversationMessage> {
        val newMessages = mutableListOf<WeChatConversationFilter.ConversationMessage>()
        
        // 获取所有对方的消息
        val otherPersonMessages = data.conversationMessages.filter { it.isFromOther }
        
        Timber.d("$TAG: === 新消息检测详情 ===")
        Timber.d("$TAG: 当前对方消息数: ${otherPersonMessages.size}")
        Timber.d("$TAG: 上次对方消息数: $lastOtherPersonMessageCount")
        
        // 方法1：基于消息数量变化检测新消息
        if (otherPersonMessages.size > lastOtherPersonMessageCount) {
            // 有新的对方消息
            val newCount = otherPersonMessages.size - lastOtherPersonMessageCount
            val latestMessages = otherPersonMessages.takeLast(newCount)
            
            Timber.d("$TAG: 检测到 $newCount 条新的对方消息（基于数量变化）")
            latestMessages.forEach { message ->
                Timber.d("$TAG: 新消息: ${message.senderName}: ${message.content}")
            }
            
            newMessages.addAll(latestMessages)
        }
        // 方法2：基于最新消息内容变化检测（防止消息数量相同但内容变化的情况）
        else if (otherPersonMessages.isNotEmpty()) {
            val currentLatestMessage = otherPersonMessages.last()
            
            if (currentLatestMessage.content != lastDetectedMessageContent && 
                lastDetectedMessageContent.isNotEmpty()) {
                
                Timber.d("$TAG: 检测到消息内容变化（基于内容比较）")
                Timber.d("$TAG: 当前最新: '${currentLatestMessage.content}'")
                Timber.d("$TAG: 上次记录: '$lastDetectedMessageContent'")
                
                newMessages.add(currentLatestMessage)
            }
        }
        
        // 方法3：如果是第一次检测且有对方消息，也算作新消息
        if (lastOtherPersonMessageCount == 0 && otherPersonMessages.isNotEmpty()) {
            Timber.d("$TAG: 首次检测到对方消息")
            newMessages.addAll(otherPersonMessages.takeLast(1)) // 只取最新的一条
        }
        
        // 更新已处理消息记录（使用更精确的去重策略）
        for (message in data.conversationMessages) {
            val messageKey = "${message.senderName}:${message.content}:${message.isFromOther}"
            lastProcessedMessages.add(messageKey)
        }
        
        // 清理过旧的已处理消息记录（保持最多100条）
        if (lastProcessedMessages.size > 100) {
            val messagesToRemove = lastProcessedMessages.take(lastProcessedMessages.size - 100)
            lastProcessedMessages.removeAll(messagesToRemove.toSet())
        }
        
        Timber.d("$TAG: 最终检测到 ${newMessages.size} 条新消息")
        
        return newMessages
    }
    
    /**
     * 获取最近的消息用于AI分析
     */
    private fun getRecentMessagesForAI(data: WeChatConversationFilter.FilteredWeChatData, maxCount: Int): String {
        val recentMessages = data.conversationMessages.takeLast(maxCount)
        
        return buildString {
            if (recentMessages.isEmpty()) {
                append("没有检测到对话内容")
            } else {
                append("最近的对话记录：\n")
                recentMessages.forEach { message ->
                    append("${message.senderName}: ${message.content}\n")
                }
                
                append("\n[注意：请根据最新的消息生成合适的回复]")
            }
        }.trim()
    }
    
    /**
     * 生成AI回复
     */
    private fun generateAIReply() {
        if (isAIProcessing) {
            Timber.w("$TAG: AI is already processing")
            return
        }
        
        coroutineScope.launch {
            try {
                isAIProcessing = true
                
                // 阶段1：检查AI模型状态
                withContext(Dispatchers.Main) {
                    updateStatusContent("🤖 AI_GEMMA3N-4B正在生成回复中...")
                }
                
                var modelReady = false
                
                // 检查AI模型是否已初始化
                if (llmManager == null) {
                    withContext(Dispatchers.Main) {
                        updateStatusContent("⚠️ AI模型未初始化，开始初始化GEMMA3N-4b模型...")
                    }
                    
                    // 重新初始化AI模型
                    val initSuccess = withContext(Dispatchers.IO) {
                        try {
                            llmManager = LlmManager.getInstance(context)
                            val gemma3nSuccess = llmManager?.initializeGemma3n() ?: false
                            
                            if (gemma3nSuccess) {
                                Timber.d("$TAG: GEMMA3N-4b model initialized successfully")
                                true
                            } else {
                                Timber.w("$TAG: GEMMA3N-4b failed, trying default model")
                                llmManager?.initialize() ?: false
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "$TAG: Error initializing AI model")
                            false
                        }
                    }
                    
                    if (initSuccess) {
                        withContext(Dispatchers.Main) {
                            updateStatusContent("✅ AI模型初始化成功！")
                        }
                        modelReady = true
                        kotlinx.coroutines.delay(1000) // 让用户看到成功信息
                    } else {
                        withContext(Dispatchers.Main) {
                            updateStatusContent("❌ AI模型初始化失败，无法生成回复")
                        }
                        return@launch
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateStatusContent("🤖 AI_GEMMA3N-4B正在生成回复中...")
                    }
                    modelReady = true
                    kotlinx.coroutines.delay(500)
                }
                
                if (!modelReady) {
                    return@launch
                }
                
                // 阶段2：准备发送消息给AI模型
                withContext(Dispatchers.Main) {
                    updateStatusContent("🤖 AI_GEMMA3N-4B正在生成回复中...")
                }
                
                // 构建AI提示词
                val prompt = String.format(CHAT_PROMPT_TEMPLATE, currentConversationData)
                
                kotlinx.coroutines.delay(1000) // 让用户看到发送信息
                
                // 阶段3：AI模型生成回复中
                withContext(Dispatchers.Main) {
                    updateStatusContent("🤖 AI_GEMMA3N-4B已生成回复，正在调用神迹进行自动回复")
                }
                
                // 在IO线程中调用AI模型生成回复
                val aiReply = withContext(Dispatchers.IO) {
                    try {
                        val startTime = System.currentTimeMillis()
                        val response = llmManager?.generateResponse(prompt) ?: ""
                        val endTime = System.currentTimeMillis()
                        val duration = endTime - startTime
                        
                        Timber.d("$TAG: AI response generated in ${duration}ms: $response")
                        response
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: Error generating AI response")
                        ""
                    }
                }
                
                if (aiReply.isNotEmpty()) {
                    // 清理AI回复内容
                    val cleanedReply = cleanAIReply(aiReply)
                    
                    // 验证AI回复是否有效（基础验证）
                    if (isValidAIReply(cleanedReply)) {
                        // 阶段4：AI模型生成结果
                        withContext(Dispatchers.Main) {
                            updateStatusContent("🤖 AI_GEMMA3N-4B已生成回复，正在调用神迹进行自动回复\n\n生成的回复：\n「$cleanedReply」")
                        }
                        
                        kotlinx.coroutines.delay(2000) // 让用户看到生成的回复
                        
                        // 自动填充到输入框并发送
                        autoFillAndSendMessage(cleanedReply)
                    } else {
                        withContext(Dispatchers.Main) {
                            updateStatusContent("❌ AI生成的回复内容无效：\n「$cleanedReply」\n\n停止自动发送")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateStatusContent("❌ AI模型未能生成回复\n\n可能原因：\n• 模型处理超时\n• 输入内容过长\n• 模型内部错误")
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error in AI reply generation")
                withContext(Dispatchers.Main) {
                    updateStatusContent("❌ AI处理过程中发生异常：\n${e.message}\n\n请重试或手动回复")
                }
            } finally {
                isAIProcessing = false
            }
        }
    }
    
    /**
     * 清理AI回复内容
     */
    private fun cleanAIReply(reply: String): String {
        return reply.trim()
            .removePrefix("回复：")
            .removePrefix("回复:")
            .removePrefix("答：")
            .removePrefix("答:")
            .removePrefix("USER_REVIEW_SUB_PROMPT:")
            .removePrefix("模型未初始化")
            .removePrefix("模型没有初始化")
            .removePrefix("请稍后再试")
            .trim()
            .take(200) // 限制长度避免过长
    }
    
    /**
     * 验证AI回复是否有效（基础验证）
     */
    private fun isValidAIReply(reply: String): Boolean {
        // 基础验证：确保不是错误信息
        val errorPhrases = listOf(
            "模型未初始化",
            "模型没有初始化", 
            "请稍后再试",
            "初始化失败",
            "ERROR",
            "Exception",
            "FATAL"
        )
        
        return reply.isNotEmpty() && 
               reply.length >= 2 && 
               !errorPhrases.any { reply.contains(it, ignoreCase = true) }
    }
    
    /**
     * 自动填充消息到输入框并发送
     */
    private fun autoFillAndSendMessage(message: String) {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                // 验证消息内容
                if (!isValidAIReply(message)) {
                    updateStatusContent("❌ AI回复内容无效，停止自动发送")
                    return@launch
                }
                
                // 延迟确保UI更新完成
                kotlinx.coroutines.delay(1500)
                
                // 检查窗口是否仍然有效
                if (windowWrapper == null || !AssistsWindowManager.contains(windowWrapper!!)) {
                    Timber.w("$TAG: Window is no longer valid, cannot auto-send message")
                    return@launch
                }
                
                // 检查是否在微信中
                val packageName = AssistsCore.getPackageName()
                if (packageName != "com.tencent.mm") {
                    Timber.w("$TAG: Not in WeChat app, cannot auto-fill message")
                    updateStatusContent("⚠️ 请切换到微信应用")
                    return@launch
                }
                
                // 先点击输入框，然后填充内容并发送
                updateStatusContent("📝 正在点击输入框...")
                val inputClicked = clickWeChatInputField()
                
                if (inputClicked) {
                    // 延迟一下确保输入框获得焦点
                    kotlinx.coroutines.delay(300)
                    
                    updateStatusContent("📝 正在填充AI回复内容...")
                    
                    // 使用输入法服务直接填充并发送
                    val inputMethodFilled = fillTextViaInputMethod(message)
                    
                    if (inputMethodFilled) {
                        // 增加AI回复计数
                        aiReplyCount++
                        
                        updateStatusContent("✅ AI回复发送成功！\n\n⏳ 未检测到有新的聊天消息\n\n(已自动回复 $aiReplyCount 次，每5秒检查一次)")
                        Toast.makeText(context, "✅ AI回复已自动发送", Toast.LENGTH_SHORT).show()
                        Timber.d("$TAG: AI reply sent successfully via input method, count: $aiReplyCount")
                        
                        // 发送成功后继续监控，不关闭窗口
                        kotlinx.coroutines.delay(2000)
                        
                        // 重新开始监控状态显示
                        updateStatusContent("⏳ 未检测到有新的聊天消息\n\n(已自动回复 $aiReplyCount 次，每5秒检查一次)")
                    } else {
                        // 如果输入法方式失败，回退到传统方式
                        updateStatusContent("⚠️ 输入法填充失败，尝试传统方式...")
                        
                        val inputFilled = fillWeChatInputField(message)
                        
                        if (inputFilled) {
                            updateStatusContent("📝 消息已填充，正在发送...")
                            
                            // 延迟后自动点击发送按钮
                            kotlinx.coroutines.delay(800)
                            val sent = clickWeChatSendButton()
                            
                            if (sent) {
                                // 增加AI回复计数
                                aiReplyCount++
                                
                                updateStatusContent("✅ AI回复发送成功！\n\n⏳ 未检测到有新的聊天消息\n\n(已自动回复 $aiReplyCount 次，每5秒检查一次)")
                                Toast.makeText(context, "✅ AI回复已自动发送", Toast.LENGTH_SHORT).show()
                                Timber.d("$TAG: AI reply sent successfully, count: $aiReplyCount")
                                
                                // 发送成功后继续监控，不关闭窗口
                                kotlinx.coroutines.delay(2000)
                                
                                // 重新开始监控状态显示
                                updateStatusContent("⏳ 未检测到有新的聊天消息\n\n(已自动回复 $aiReplyCount 次，每5秒检查一次)")
                            } else {
                                updateStatusContent("⚠️ 消息已填充，请手动点击发送\n将继续监控新消息")
                                Toast.makeText(context, "⚠️ 消息已填充，请手动点击发送", Toast.LENGTH_SHORT).show()
                                
                                // 即使发送失败也继续监控
                                kotlinx.coroutines.delay(3000)
                                val statusText = if (aiReplyCount > 0) {
                                    "⏳ 未检测到有新的聊天消息\n\n(已自动回复 $aiReplyCount 次，每5秒检查一次)"
                                } else {
                                    "⏳ 未检测到有新的聊天消息\n\n(每5秒检查一次)"
                                }
                                updateStatusContent(statusText)
                            }
                        } else {
                            updateStatusContent("❌ 无法找到输入框，请手动输入")
                            Toast.makeText(context, "❌ 无法自动填充消息，请手动输入", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    updateStatusContent("❌ 无法点击输入框，请手动操作")
                    Toast.makeText(context, "❌ 无法点击输入框，请手动操作", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error auto-filling and sending message")
                updateStatusContent("❌ 自动发送失败，请手动操作")
                Toast.makeText(context, "自动发送失败，请手动操作", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 通过输入法服务填充文本并发送
     */
    private fun fillTextViaInputMethod(text: String): Boolean {
        return try {
            // 获取输入法服务实例
            val inputMethodService = com.shenji.aikeyboard.keyboard.ShenjiInputMethodService.instance
            
            if (inputMethodService != null) {
                // 调用输入法服务的填充方法
                inputMethodService.fillTextAndSend(text)
                Timber.d("$TAG: Text filled via input method service")
                return true
            } else {
                Timber.w("$TAG: Input method service instance is null")
                return false
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error filling text via input method")
            false
        }
    }
    
    /**
     * 填充微信输入框
     */
    private fun fillWeChatInputField(message: String): Boolean {
        return try {
            val allNodes = AssistsCore.getAllNodes()
            val inputNodes = allNodes.filter { node ->
                val className = node.className?.toString() ?: ""
                val description = node.contentDescription?.toString() ?: ""
                
                // 查找输入框节点
                (className.contains("EditText") || 
                 description.contains("输入") || 
                 node.isEditable) &&
                node.isEnabled
            }
            
            Timber.d("$TAG: Found ${inputNodes.size} input nodes for filling")
            
            if (inputNodes.isNotEmpty()) {
                val inputNode = inputNodes.first()
                
                // 先点击输入框获得焦点
                inputNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                
                // 清空现有内容（先选择全部文本）
                val selectAllBundle = android.os.Bundle()
                selectAllBundle.putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                selectAllBundle.putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, inputNode.text?.length ?: 0)
                inputNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION, selectAllBundle)
                
                // 填充新内容
                val arguments = android.os.Bundle()
                arguments.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
                val filled = inputNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                
                if (filled) {
                    Timber.d("$TAG: Successfully filled input field with message")
                    return true
                } else {
                    Timber.w("$TAG: Failed to fill input field")
                }
            } else {
                Timber.w("$TAG: No input field found for filling")
            }
            
            false
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error filling WeChat input field")
            false
        }
    }
    
    /**
     * 点击微信发送按钮
     */
    private fun clickWeChatSendButton(): Boolean {
        return try {
            val allNodes = AssistsCore.getAllNodes()
            val sendNodes = allNodes.filter { node ->
                val text = node.text?.toString() ?: ""
                val description = node.contentDescription?.toString() ?: ""
                val className = node.className?.toString() ?: ""
                
                // 查找发送按钮
                (text.contains("发送") || 
                 description.contains("发送") ||
                 description.contains("Send") ||
                 (className.contains("Button") && node.isClickable && 
                  (text.isEmpty() || text.length < 5))) && // 发送按钮通常文字很短或为空
                node.isEnabled && node.isClickable
            }
            
            Timber.d("$TAG: Found ${sendNodes.size} potential send buttons")
            
            if (sendNodes.isNotEmpty()) {
                // 优先选择包含"发送"文字的按钮
                val sendButton = sendNodes.find { 
                    val text = it.text?.toString() ?: ""
                    val desc = it.contentDescription?.toString() ?: ""
                    text.contains("发送") || desc.contains("发送")
                } ?: sendNodes.first()
                
                val clicked = sendButton.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                
                if (clicked) {
                    Timber.d("$TAG: Successfully clicked send button")
                    return true
                } else {
                    Timber.w("$TAG: Failed to click send button")
                }
            } else {
                Timber.w("$TAG: No send button found")
            }
            
            false
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error clicking WeChat send button")
            false
        }
    }
    
    /**
     * 显示初始状态
     */
    private fun showInitialState() {
        updateChatTarget("获取中...")
        updateStatusContent("🤖 AI-GEMMA3N-4B模型初始化中...")
    }
    
    /**
     * 显示加载状态
     */
    private fun showLoadingState() {
        updateStatusContent("📡 正在获取对话数据...\n\n请确保：\n1. 已开启Assists无障碍服务\n2. 当前在微信聊天界面\n3. 聊天界面有对话内容")
    }
    
    /**
     * 显示错误状态
     */
    private fun showErrorState(message: String) {
        updateStatusContent("❌ $message")
    }
    
    /**
     * 更新对话对象显示
     */
    private fun updateChatTarget(targetName: String) {
        // 更新窗口标题栏显示对话对象昵称
        windowWrapper?.viewBinding?.tvTitle?.text = targetName
        // 同时更新内容区域的显示（备用）
        tvChatTarget?.text = targetName
    }
    
    /**
     * 更新状态内容显示
     */
    private fun updateStatusContent(content: String) {
        tvStatusContent?.text = content
        
        // 自动滚动到底部显示最新状态
        scrollViewStatus?.post {
            scrollViewStatus?.fullScroll(View.FOCUS_DOWN)
        }
    }
    
    /**
     * 显示对话内容（已废弃，现在合并到状态显示中）
     */
    private fun showConversationContent(content: String) {
        // 不再单独显示对话内容，而是合并到状态显示中
        currentConversationText = content
    }
    
    /**
     * 更新AI状态显示（已废弃，使用updateStatusContent替代）
     */
    private fun updateAIStatus(status: String, colorHex: String) {
        updateStatusContent(status)
    }
    
    /**
     * 启用AI回复模式（不自动点击输入框）
     */
    private fun autoClickInputBoxAndEnableAIMode() {
        coroutineScope.launch {
            try {
                kotlinx.coroutines.delay(500) // 延迟500ms，确保窗口完全显示
                
                withContext(Dispatchers.Main) {
                    updateStatusContent("🤖 AI回复模式已启用\n\n⏳ 等待检测新消息...")
                    
                    // 启用AI回复模式（不自动点击输入框）
                    enableAIReplyMode()
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error enabling AI mode")
                withContext(Dispatchers.Main) {
                    updateStatusContent("❌ 启用AI模式时出错：${e.message}")
                }
            }
        }
    }
    
    /**
     * 隐藏悬浮按钮
     */
    private fun hideFloatingButton() {
        try {
            val intent = android.content.Intent("com.shenji.aikeyboard.HIDE_FLOATING_WINDOW")
            intent.setPackage(context.packageName) // 确保广播只发送给本应用
            context.sendBroadcast(intent)
            Timber.d("$TAG: Sent hide floating button broadcast with package: ${context.packageName}")
            
            // 额外的调试：直接尝试通过服务引用隐藏
            try {
                val serviceIntent = android.content.Intent(context, FloatingWindowService::class.java)
                serviceIntent.action = "HIDE_FLOATING_WINDOW"
                context.startService(serviceIntent)
                Timber.d("$TAG: Also sent hide command via service intent")
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to send via service intent")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error hiding floating button")
        }
    }
    
    /**
     * 显示悬浮按钮
     */
    private fun showFloatingButton() {
        try {
            val intent = android.content.Intent("com.shenji.aikeyboard.RESTORE_FLOATING_WINDOW")
            intent.setPackage(context.packageName) // 确保广播只发送给本应用
            context.sendBroadcast(intent)
            Timber.d("$TAG: Sent show floating button broadcast with package: ${context.packageName}")
            
            // 额外的调试：直接尝试通过服务引用显示
            try {
                val serviceIntent = android.content.Intent(context, FloatingWindowService::class.java)
                serviceIntent.action = "RESTORE_FLOATING_WINDOW"
                context.startService(serviceIntent)
                Timber.d("$TAG: Also sent show command via service intent")
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to send via service intent")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error showing floating button")
        }
    }
    
    /**
     * 启用AI回复模式
     */
    private fun enableAIReplyMode() {
        try {
            val intent = android.content.Intent("com.shenji.aikeyboard.ENABLE_AI_REPLY_MODE")
            context.sendBroadcast(intent)
            Timber.d("$TAG: Sent enable AI reply mode broadcast")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error enabling AI reply mode")
        }
    }
    
    /**
     * 禁用AI回复模式
     */
    private fun disableAIReplyMode() {
        try {
            val intent = android.content.Intent("com.shenji.aikeyboard.DISABLE_AI_REPLY_MODE")
            context.sendBroadcast(intent)
            Timber.d("$TAG: Sent disable AI reply mode broadcast")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error disabling AI reply mode")
        }
    }
    
    /**
     * 通过无障碍服务点击微信输入框
     */
    private fun clickWeChatInputField(): Boolean {
        return try {
            // 检查是否在微信中
            val packageName = AssistsCore.getPackageName()
            if (packageName != "com.tencent.mm") {
                Timber.w("$TAG: Not in WeChat app, package: $packageName")
                return false
            }
            
            // 查找微信输入框
            val allNodes = AssistsCore.getAllNodes()
            val inputNodes = allNodes.filter { node ->
                val className = node.className?.toString() ?: ""
                val text = node.text?.toString() ?: ""
                val description = node.contentDescription?.toString() ?: ""
                
                // 查找可能的输入框节点
                (className.contains("EditText") || 
                 description.contains("输入") || 
                 text.isEmpty() && node.isEditable) &&
                node.isClickable
            }
            
            Timber.d("$TAG: Found ${inputNodes.size} potential input nodes")
            
            // 尝试点击第一个找到的输入框
            if (inputNodes.isNotEmpty()) {
                val inputNode = inputNodes.first()
                val clicked = inputNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                
                if (clicked) {
                    Timber.d("$TAG: Successfully clicked WeChat input field")
                    Toast.makeText(context, "已自动点击输入框，键盘应该显示", Toast.LENGTH_SHORT).show()
                    return true
                } else {
                    Timber.w("$TAG: Failed to click input field")
                }
            } else {
                Timber.w("$TAG: No input field found in WeChat")
                Toast.makeText(context, "未找到输入框，请手动点击输入框", Toast.LENGTH_SHORT).show()
            }
            
            false
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error clicking WeChat input field")
            false
        }
    }
    
    /**
     * 复制分析结果
     */
    private fun copyAnalysisResult() {
        try {
            if (currentConversationText.isNotEmpty()) {
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("微信对话分析结果", currentConversationText)
                clipboardManager.setPrimaryClip(clipData)
                
                Toast.makeText(context, "对话内容已复制到剪贴板", Toast.LENGTH_SHORT).show()
                Timber.d("$TAG: Conversation content copied to clipboard")
            } else {
                Toast.makeText(context, "没有可复制的内容", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error copying conversation content")
            Toast.makeText(context, "复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
} 