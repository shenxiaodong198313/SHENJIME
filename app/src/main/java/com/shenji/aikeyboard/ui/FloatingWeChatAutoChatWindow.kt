package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
    private var tvConversationContent: TextView? = null
    private var tvAIStatus: TextView? = null
    private var scrollViewConversation: ScrollView? = null
    private var scrollViewAIStatus: ScrollView? = null
    
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
            windowWrapper?.let { wrapper ->
                if (!AssistsWindowManager.contains(wrapper)) {
                    AssistsWindowManager.add(wrapper)
                }
                
                // 开始持续监控模式
                startContinuousMonitoring()
                
                // 自动显示输入法键盘
                showInputMethodKeyboard()
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to show window")
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
            
            windowWrapper?.let { wrapper ->
                try {
                    if (AssistsWindowManager.contains(wrapper)) {
                        AssistsWindowManager.removeView(wrapper.getView())
                    }
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: Error removing window view, window may already be removed")
                }
            }
            windowWrapper = null
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
                    height = (context.resources.displayMetrics.heightPixels * 0.4).toInt()
                    // 设置默认位置在右上角
                    x = (context.resources.displayMetrics.widthPixels * 0.3).toInt()
                    y = (context.resources.displayMetrics.heightPixels * 0.1).toInt()
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
                tvConversationContent = view.findViewById(R.id.tv_conversation_content)
                tvAIStatus = view.findViewById(R.id.tv_ai_status)
                scrollViewConversation = view.findViewById(R.id.scroll_view_conversation)
                scrollViewAIStatus = view.findViewById(R.id.scroll_view_ai_status)
                
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
        
        isMonitoring = true
        updateAIStatus("🔄 开始持续监控模式...\n每5秒检查新消息", "#FF2196F3")
        
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
                        updateAIStatus("❌ 监控过程中出现错误：${e.message}", "#FFF44336")
                    }
                    kotlinx.coroutines.delay(5000) // 错误后也等待5秒再重试
                }
            }
        }
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
                    updateAIStatus("❌ Assists无障碍服务未启用", "#FFF44336")
                }
                return
            }
            
            // 在IO线程中执行数据过滤
            val filteredData = withContext(Dispatchers.IO) {
                WeChatConversationFilter.filterWeChatConversation()
            }
            
            // 检查是否有新消息
            val newMessages = checkForNewConversationMessages(filteredData)
            
            // 添加详细调试信息
            Timber.d("$TAG: Total messages: ${filteredData.conversationMessages.size}, New messages: ${newMessages.size}")
            Timber.d("$TAG: All messages: ${filteredData.conversationMessages.map { "${it.senderName}: ${it.content}" }}")
            Timber.d("$TAG: Processed messages count: ${lastProcessedMessages.size}")
            
            if (newMessages.isNotEmpty()) {
                Timber.d("$TAG: Found ${newMessages.size} new messages")
                
                // 更新显示内容（仅显示对话内容）
                val displayText = WeChatConversationFilter.formatConversationForSimpleDisplay(filteredData)
                withContext(Dispatchers.Main) {
                    showConversationContent(displayText)
                    updateAIStatus("📨 检测到新消息，准备AI分析...", "#FFFF9800")
                }
                
                // 准备AI分析的上下文（最多5条消息）
                val contextMessages = getRecentMessagesForAI(filteredData, 5)
                currentConversationData = contextMessages
                
                // 生成AI回复
                generateAIReply()
                
            } else {
                // 没有新消息，更新状态（仅显示对话内容）
                val displayText = WeChatConversationFilter.formatConversationForSimpleDisplay(filteredData)
                withContext(Dispatchers.Main) {
                    showConversationContent(displayText)
                    updateAIStatus("👁️ 持续监控中...\n等待新消息 (每5秒检查)", "#FF81C784")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error checking for new messages")
            withContext(Dispatchers.Main) {
                updateAIStatus("❌ 检查新消息时出错：${e.message}", "#FFF44336")
            }
        }
    }
    
    /**
     * 检查是否有新的对话消息
     */
    private fun checkForNewConversationMessages(data: WeChatConversationFilter.FilteredWeChatData): List<WeChatConversationFilter.ConversationMessage> {
        val newMessages = mutableListOf<WeChatConversationFilter.ConversationMessage>()
        
        for (message in data.conversationMessages) {
            val messageKey = "${message.senderName}:${message.content}"
            
            // 只跳过已经处理过的消息
            if (lastProcessedMessages.contains(messageKey)) {
                continue
            }
            
            // 这是新消息
            newMessages.add(message)
            lastProcessedMessages.add(messageKey)
            
            // 添加调试日志
            Timber.d("$TAG: New message detected - Sender: ${message.senderName}, Content: ${message.content}")
        }
        
        // 清理过旧的已处理消息记录（保持最多50条）
        if (lastProcessedMessages.size > 50) {
            val messagesToRemove = lastProcessedMessages.take(lastProcessedMessages.size - 50)
            lastProcessedMessages.removeAll(messagesToRemove.toSet())
        }
        
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
                    updateAIStatus("🔍 检查AI模型状态...", "#FF2196F3")
                }
                
                var modelReady = false
                
                // 检查AI模型是否已初始化
                if (llmManager == null) {
                    withContext(Dispatchers.Main) {
                        updateAIStatus("⚠️ AI模型未初始化，开始初始化GEMMA3N-4b模型...", "#FFFF9800")
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
                            updateAIStatus("✅ AI模型初始化成功！", "#FF4CAF50")
                        }
                        modelReady = true
                        kotlinx.coroutines.delay(1000) // 让用户看到成功信息
                    } else {
                        withContext(Dispatchers.Main) {
                            updateAIStatus("❌ AI模型初始化失败，无法生成回复", "#FFF44336")
                        }
                        return@launch
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateAIStatus("✅ AI模型已就绪", "#FF4CAF50")
                    }
                    modelReady = true
                    kotlinx.coroutines.delay(500)
                }
                
                if (!modelReady) {
                    return@launch
                }
                
                // 阶段2：准备发送消息给AI模型
                withContext(Dispatchers.Main) {
                    updateAIStatus("📤 正在将对话内容发送给AI模型...\n\n对话内容：\n$currentConversationData", "#FF2196F3")
                }
                
                // 构建AI提示词
                val prompt = String.format(CHAT_PROMPT_TEMPLATE, currentConversationData)
                
                kotlinx.coroutines.delay(1000) // 让用户看到发送信息
                
                // 阶段3：AI模型生成回复中
                withContext(Dispatchers.Main) {
                    updateAIStatus("🤖 AI模型正在分析对话并生成回复...\n\n请稍候，这可能需要几秒钟时间", "#FF9C27B0")
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
                            updateAIStatus("✅ AI模型生成回复成功！\n\n生成的回复：\n「$cleanedReply」\n\n⏳ 准备自动发送...", "#FF4CAF50")
                        }
                        
                        kotlinx.coroutines.delay(2000) // 让用户看到生成的回复
                        
                        // 自动填充到输入框并发送
                        autoFillAndSendMessage(cleanedReply)
                    } else {
                        withContext(Dispatchers.Main) {
                            updateAIStatus("❌ AI生成的回复内容无效：\n「$cleanedReply」\n\n停止自动发送", "#FFF44336")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateAIStatus("❌ AI模型未能生成回复\n\n可能原因：\n• 模型处理超时\n• 输入内容过长\n• 模型内部错误", "#FFF44336")
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error in AI reply generation")
                withContext(Dispatchers.Main) {
                    updateAIStatus("❌ AI处理过程中发生异常：\n${e.message}\n\n请重试或手动回复", "#FFF44336")
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
                    updateAIStatus("❌ AI回复内容无效，停止自动发送", "#FFF44336")
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
                    updateAIStatus("⚠️ 请切换到微信应用", "#FFFF9800")
                    return@launch
                }
                
                // 查找输入框并填充内容
                val inputFilled = fillWeChatInputField(message)
                
                if (inputFilled) {
                    updateAIStatus("📝 消息已填充，正在发送...", "#FF2196F3")
                    
                    // 延迟后自动点击发送按钮
                    kotlinx.coroutines.delay(800)
                    val sent = clickWeChatSendButton()
                    
                    if (sent) {
                        updateAIStatus("✅ AI回复发送成功！\n继续监控新消息...", "#FF4CAF50")
                        Toast.makeText(context, "✅ AI回复已自动发送", Toast.LENGTH_SHORT).show()
                        Timber.d("$TAG: AI reply sent successfully")
                        
                        // 发送成功后继续监控，不关闭窗口
                        kotlinx.coroutines.delay(2000)
                        
                        // 重新开始监控状态显示
                        updateAIStatus("👁️ 持续监控中...\n等待新消息 (每5秒检查)", "#FF81C784")
                    } else {
                        updateAIStatus("⚠️ 消息已填充，请手动点击发送\n将继续监控新消息", "#FFFF9800")
                        Toast.makeText(context, "⚠️ 消息已填充，请手动点击发送", Toast.LENGTH_SHORT).show()
                        
                        // 即使发送失败也继续监控
                        kotlinx.coroutines.delay(3000)
                        updateAIStatus("👁️ 持续监控中...\n等待新消息 (每5秒检查)", "#FF81C784")
                    }
                } else {
                    updateAIStatus("❌ 无法找到输入框，请手动输入", "#FFF44336")
                    Toast.makeText(context, "❌ 无法自动填充消息，请手动输入", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error auto-filling and sending message")
                updateAIStatus("❌ 自动发送失败，请手动操作", "#FFF44336")
                Toast.makeText(context, "自动发送失败，请手动操作", Toast.LENGTH_SHORT).show()
            }
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
        currentConversationText = "正在获取微信对话内容..."
        tvConversationContent?.text = currentConversationText
        updateAIStatus("🤖 AI系统准备中...", "#FF81C784")
    }
    
    /**
     * 显示加载状态
     */
    private fun showLoadingState() {
        currentConversationText = "正在分析微信对话内容...\n\n请确保：\n1. 已开启Assists无障碍服务\n2. 当前在微信聊天界面\n3. 聊天界面有对话内容"
        tvConversationContent?.text = currentConversationText
        updateAIStatus("📡 正在获取对话数据...", "#FF2196F3")
    }
    
    /**
     * 显示错误状态
     */
    private fun showErrorState(message: String) {
        currentConversationText = "❌ $message"
        tvConversationContent?.text = currentConversationText
        updateAIStatus("❌ 获取对话失败", "#FFF44336")
    }
    
    /**
     * 显示对话内容
     */
    private fun showConversationContent(content: String) {
        currentConversationText = content
        tvConversationContent?.text = content
        
        // 自动滚动到顶部
        scrollViewConversation?.post {
            scrollViewConversation?.scrollTo(0, 0)
        }
    }
    
    /**
     * 更新AI状态显示
     */
    private fun updateAIStatus(status: String, colorHex: String) {
        tvAIStatus?.text = status
        try {
            tvAIStatus?.setTextColor(android.graphics.Color.parseColor(colorHex))
        } catch (e: Exception) {
            // 如果颜色解析失败，使用默认颜色
            tvAIStatus?.setTextColor(android.graphics.Color.parseColor("#FF81C784"))
        }
        
        // 自动滚动到底部显示最新状态
        scrollViewAIStatus?.post {
            scrollViewAIStatus?.fullScroll(View.FOCUS_DOWN)
        }
    }
    
    /**
     * 自动显示输入法键盘
     */
    private fun showInputMethodKeyboard() {
        try {
            coroutineScope.launch(Dispatchers.Main) {
                // 延迟一小段时间确保窗口已完全显示
                kotlinx.coroutines.delay(500)
                
                // 方法1：尝试通过无障碍服务点击微信输入框
                val success = clickWeChatInputField()
                
                if (!success) {
                    // 方法2：显示输入法选择器
                    val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    
                    try {
                        inputMethodManager.showInputMethodPicker()
                        Timber.d("$TAG: Input method picker shown")
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG: Failed to show input method picker, trying alternative method")
                        
                        // 方法3：检查并提示切换到我们的输入法
                        try {
                            // 获取当前输入法
                            val currentInputMethod = android.provider.Settings.Secure.getString(
                                context.contentResolver,
                                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
                            )
                            
                            Timber.d("$TAG: Current input method: $currentInputMethod")
                            
                            // 如果当前不是我们的输入法，提示用户切换
                            if (!currentInputMethod.contains("com.shenji.aikeyboard")) {
                                Toast.makeText(context, "请切换到神迹AI键盘以使用自动聊天功能", Toast.LENGTH_LONG).show()
                                
                                // 显示输入法设置
                                val intent = android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "神迹AI键盘已激活，请点击输入框开始聊天", Toast.LENGTH_SHORT).show()
                            }
                            
                        } catch (e2: Exception) {
                            Timber.e(e2, "$TAG: Failed to check/switch input method")
                            Toast.makeText(context, "请手动点击输入框并切换到神迹AI键盘", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error showing input method keyboard")
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