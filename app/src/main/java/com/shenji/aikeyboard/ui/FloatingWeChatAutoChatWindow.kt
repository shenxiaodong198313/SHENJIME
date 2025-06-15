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
        
        // AI聊天提示词模板 - 简化版本，提高响应速度
        private const val CHAT_PROMPT_TEMPLATE = """
你是一个年轻朋友，正在微信聊天。

规则：
1. 回复1-2句话，不超过30字
2. 语气轻松自然，像朋友聊天
3. 可以用简单表情如😂👍👀

对话内容：
%s

请直接回复，不要解释：
        """
    }
    
    // UI组件
    private var contentView: View? = null
    private var tvChatTarget: TextView? = null
    private var tvStatusContent: TextView? = null
    private var scrollViewStatus: ScrollView? = null
    private var tvReplyCount: TextView? = null
    private var btnSendImmediately: android.widget.Button? = null
    private var btnCancelSending: android.widget.Button? = null
    private var layoutDelayControls: android.widget.LinearLayout? = null
    
    // 流式显示相关
    private var currentStreamingText = ""
    private var isStreaming = false
    
    // 延迟发送相关
    private var isDelayedSending = false
    private var delayJob: kotlinx.coroutines.Job? = null
    private var currentPendingMessage = ""
    
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
            Timber.d("$TAG: Starting showAndAnalyze")
            
            // 检查是否已经有窗口在显示
            windowWrapper?.let { wrapper ->
                if (AssistsWindowManager.contains(wrapper)) {
                    Timber.d("$TAG: Window already exists and is visible, not creating duplicate")
                    return
                } else {
                    // 窗口引用存在但不在管理器中，清理引用
                    Timber.w("$TAG: Window reference exists but not in manager, cleaning up")
                    windowWrapper = null
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
            
            // 停止所有任务
            analysisJob?.cancel()
            monitoringJob?.cancel()
            isMonitoring = false
            stopStreaming()
            
            // 清理旧窗口
            windowWrapper?.let { wrapper ->
                try {
                    if (AssistsWindowManager.contains(wrapper)) {
                        AssistsWindowManager.removeView(wrapper.getView())
                        Timber.d("$TAG: Old window removed during recreation")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: Error removing old window during recreation")
                }
            }
            
            // 清理旧引用
            windowWrapper = null
            
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
                            Timber.d("$TAG: Window recreated successfully")
                        } ?: run {
                            Timber.e("$TAG: Failed to recreate window - windowWrapper is null")
                            Toast.makeText(context, "窗口重建失败，请重试", Toast.LENGTH_SHORT).show()
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
            Timber.d("$TAG: Closing window programmatically")
            
            // 停止所有任务
            analysisJob?.cancel()
            monitoringJob?.cancel()
            delayJob?.cancel()
            isMonitoring = false
            isDelayedSending = false
            currentPendingMessage = ""
            
            // 停止流式显示
            stopStreaming()
            
            // 恢复悬浮按钮显示
            showFloatingButton()
            
            // 退出AI回复模式
            disableAIReplyMode()
            
            // 移除窗口视图
            windowWrapper?.let { wrapper ->
                try {
                    if (AssistsWindowManager.contains(wrapper)) {
                        AssistsWindowManager.removeView(wrapper.getView())
                        Timber.d("$TAG: Window view removed from AssistsWindowManager")
                    } else {
                        Timber.w("$TAG: Window wrapper not found in AssistsWindowManager")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: Error removing window view, window may already be removed")
                }
            } ?: run {
                Timber.w("$TAG: WindowWrapper is null, cannot remove window")
            }
            
            // 清理窗口引用
            windowWrapper = null
            
            Timber.d("$TAG: Window closed and reference cleared")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error closing window")
            // 即使出错也要尝试清理引用
            windowWrapper = null
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
                    // 使用协程确保关闭操作不阻塞UI，即使在AI处理时也能关闭
                    coroutineScope.launch(Dispatchers.Main) {
                        try {
                            // 当用户点击关闭按钮时的回调
                            Timber.d("$TAG: User clicked close button, starting cleanup")
                            
                            // 立即停止所有任务，包括AI处理
                            analysisJob?.cancel()
                            monitoringJob?.cancel()
                            delayJob?.cancel()
                            isMonitoring = false
                            isAIProcessing = false // 重要：立即停止AI处理
                            isDelayedSending = false
                            currentPendingMessage = ""
                            
                            // 停止流式显示
                            stopStreaming()
                            
                            // 恢复悬浮按钮显示
                            showFloatingButton()
                            
                            // 退出AI回复模式
                            disableAIReplyMode()
                            
                            // 实际移除窗口视图
                            windowWrapper?.let { wrapper ->
                                try {
                                    if (AssistsWindowManager.contains(wrapper)) {
                                        AssistsWindowManager.removeView(wrapper.getView())
                                    }
                                } catch (e: Exception) {
                                    Timber.w(e, "$TAG: Error removing window view in onClose callback")
                                }
                            }
                            
                            // 清理窗口引用
                            windowWrapper = null
                            
                            Timber.d("$TAG: Window closed by user, cleanup completed")
                        } catch (e: Exception) {
                            Timber.e(e, "$TAG: Error in onClose callback")
                            // 即使出错也要尝试移除窗口
                            try {
                                AssistsWindowManager.removeView(parent)
                            } catch (removeError: Exception) {
                                Timber.e(removeError, "$TAG: Failed to remove window in error recovery")
                            }
                        }
                    }
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
                tvReplyCount = view.findViewById(R.id.tv_reply_count)
                
                // 创建延迟发送控制按钮
                setupDelayControls(view)
                
                // 初始显示
                showInitialState()
                
                Timber.d("$TAG: UI setup completed")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to setup UI")
            }
        }
    }
    
    /**
     * 设置延迟发送控制按钮
     */
    private fun setupDelayControls(parentView: View) {
        try {
            // 直接使用父视图作为容器
            val mainContainer = parentView as? android.view.ViewGroup
            
            if (mainContainer != null) {
                // 创建延迟控制按钮容器
                layoutDelayControls = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                    visibility = View.GONE // 初始隐藏
                    
                    val layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(16, 8, 16, 8)
                    }
                    this.layoutParams = layoutParams
                }
                
                // 创建立即发送按钮
                btnSendImmediately = android.widget.Button(context).apply {
                    text = "立即发送"
                    setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 14f
                    
                    val layoutParams = android.widget.LinearLayout.LayoutParams(
                        0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        setMargins(0, 0, 8, 0)
                    }
                    this.layoutParams = layoutParams
                    
                    setOnClickListener {
                        handleImmediateSend()
                    }
                }
                
                // 创建取消发送按钮
                btnCancelSending = android.widget.Button(context).apply {
                    text = "取消发送"
                    setBackgroundColor(android.graphics.Color.parseColor("#F44336"))
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 14f
                    
                    val layoutParams = android.widget.LinearLayout.LayoutParams(
                        0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        setMargins(8, 0, 0, 0)
                    }
                    this.layoutParams = layoutParams
                    
                    setOnClickListener {
                        handleCancelSend()
                    }
                }
                
                // 添加按钮到容器
                layoutDelayControls?.addView(btnSendImmediately)
                layoutDelayControls?.addView(btnCancelSending)
                
                // 添加容器到主布局
                mainContainer.addView(layoutDelayControls)
                
                Timber.d("$TAG: Delay control buttons created successfully")
            } else {
                Timber.w("$TAG: Could not find suitable container for delay controls")
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to setup delay controls")
        }
    }
    
    /**
     * 显示延迟控制按钮
     */
    private fun showDelayControls() {
        layoutDelayControls?.visibility = View.VISIBLE
    }
    
    /**
     * 隐藏延迟控制按钮
     */
    private fun hideDelayControls() {
        layoutDelayControls?.visibility = View.GONE
    }
    
    /**
     * 处理立即发送
     */
    private fun handleImmediateSend() {
        if (isDelayedSending && currentPendingMessage.isNotEmpty()) {
            sendMessageImmediately(currentPendingMessage)
            hideDelayControls()
        }
    }
    
    /**
     * 处理取消发送
     */
    private fun handleCancelSend() {
        if (isDelayedSending) {
            cancelSending()
            hideDelayControls()
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
        stopStreaming()
        updateReplyCountDisplay()
    }
    
    /**
     * 停止流式显示
     */
    private fun stopStreaming() {
        isStreaming = false
        currentStreamingText = ""
    }
    
    /**
     * 更新回复次数显示
     */
    private fun updateReplyCountDisplay() {
        tvReplyCount?.let { textView ->
            if (aiReplyCount > 0) {
                textView.text = "已回复 $aiReplyCount 次"
                textView.visibility = View.VISIBLE
            } else {
                textView.visibility = View.GONE
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
                    updateStatusContent("⏳ 未检测到有新的聊天消息")
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
        
        // 简化已处理消息记录逻辑，避免影响消息检测
        // 只在确实处理了新消息后才更新记录
        if (newMessages.isNotEmpty()) {
            for (message in newMessages) {
                val messageKey = "${message.senderName}:${message.content}:${message.isFromOther}"
                lastProcessedMessages.add(messageKey)
            }
            
            // 清理过旧的已处理消息记录（保持最多50条）
            if (lastProcessedMessages.size > 50) {
                val messagesToRemove = lastProcessedMessages.take(lastProcessedMessages.size - 50)
                lastProcessedMessages.removeAll(messagesToRemove.toSet())
            }
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
     * 格式化对话数据为简化聊天输入格式
     */
    private fun formatConversationForHumanizedChat(data: WeChatConversationFilter.FilteredWeChatData): String {
        val recentMessages = data.conversationMessages.takeLast(3) // 只取最近3条，减少token消耗
        
        return buildString {
            if (recentMessages.isEmpty()) {
                append("没有对话内容")
            } else {
                // 简化格式化消息记录
                recentMessages.forEach { message ->
                    val prefix = if (message.isFromOther) "朋友" else "我"
                    append("$prefix: ${message.content}\n")
                }
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
        
        // 使用独立的协程，确保不阻塞UI操作
        coroutineScope.launch(Dispatchers.IO) {
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
                
                // 构建AI提示词 - 使用拟人化格式
                // 重新获取当前对话数据用于拟人化格式化
                val currentFilteredData = WeChatConversationFilter.filterWeChatConversation()
                val humanizedConversationData = if (currentFilteredData != null) {
                    formatConversationForHumanizedChat(currentFilteredData)
                } else {
                    currentConversationData // 回退到原始数据
                }
                val prompt = String.format(CHAT_PROMPT_TEMPLATE, humanizedConversationData)
                
                kotlinx.coroutines.delay(1000) // 让用户看到发送信息
                
                // 阶段3：AI模型生成回复中
                withContext(Dispatchers.Main) {
                    updateStatusContent("🤖 AI_GEMMA3N-4B正在生成回复中...")
                }
                
                // 使用流式生成AI回复 - 添加超时机制
                var finalAiReply = ""
                isStreaming = true
                currentStreamingText = ""
                
                try {
                    // 添加30秒超时
                    finalAiReply = withContext(Dispatchers.IO) {
                        kotlinx.coroutines.withTimeoutOrNull(30000) {
                            llmManager?.generateResponseStream(prompt) { partialText, isComplete ->
                                // 检查是否仍在处理中（用户可能已关闭窗口）
                                if (!isAIProcessing) return@generateResponseStream
                                
                                // 在主线程更新UI
                                coroutineScope.launch(Dispatchers.Main) {
                                    if (isStreaming && isAIProcessing) {
                                        currentStreamingText = partialText
                                        val cleanedPartialText = cleanAIReply(partialText)
                                        
                                        // 1. 实时更新窗口显示内容
                                        updateStatusContent("🤖 AI_GEMMA3N-4B正在生成回复中...\n\n生成的回复：\n「$cleanedPartialText」")
                                        
                                        // 自动滚动到底部
                                        scrollViewStatus?.post {
                                            scrollViewStatus?.fullScroll(View.FOCUS_DOWN)
                                        }
                                    }
                                }
                            }
                        } ?: ""
                    }
                    
                    if (finalAiReply.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            updateStatusContent("⚠️ AI回复生成超时，请重试")
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Error in streaming AI response")
                    finalAiReply = ""
                    withContext(Dispatchers.Main) {
                        updateStatusContent("❌ AI回复生成失败：${e.message}")
                    }
                } finally {
                    isStreaming = false
                }
                
                val aiReply = finalAiReply
                
                if (aiReply.isNotEmpty()) {
                    // 清理AI回复内容
                    val cleanedReply = cleanAIReply(aiReply)
                    
                    // 验证AI回复是否有效（基础验证）
                    if (isValidAIReply(cleanedReply)) {
                        // 阶段4：AI模型生成完毕，使用智能延迟发送
                        withContext(Dispatchers.Main) {
                            updateStatusContent("🤖 AI_GEMMA3N-4B生成完毕！\n\n生成的回复：\n「$cleanedReply」\n\n⏰ 正在计算最佳发送时机...")
                        }
                        
                        kotlinx.coroutines.delay(500) // 短暂显示生成完毕状态
                        
                        // 使用智能延迟发送，让聊天更自然
                        withContext(Dispatchers.Main) {
                            sendMessageWithDelay(cleanedReply, currentFilteredData)
                        }
                    } else {
                        // AI回复无效时，尝试生成简单的拟人化回复
                        val fallbackReply = if (currentFilteredData != null) {
                            generateFallbackReply(currentFilteredData)
                        } else {
                            ""
                        }
                        if (fallbackReply.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                updateStatusContent("⚠️ AI回复不够自然，使用备用回复：\n「$fallbackReply」\n\n⏰ 正在计算发送时机...")
                            }
                            
                            kotlinx.coroutines.delay(300)
                            
                            withContext(Dispatchers.Main) {
                                sendMessageWithDelay(fallbackReply, currentFilteredData)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                updateStatusContent("❌ AI生成的回复内容无效：\n「$cleanedReply」\n\n停止自动发送")
                            }
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
     * 清理AI回复内容 - 针对拟人化聊天机器人优化
     */
    private fun cleanAIReply(reply: String): String {
        return reply.trim()
            // 移除常见的回复前缀
            .removePrefix("回复：")
            .removePrefix("回复:")
            .removePrefix("答：")
            .removePrefix("答:")
            .removePrefix("我回复：")
            .removePrefix("我说：")
            .removePrefix("USER_REVIEW_SUB_PROMPT:")
            // 移除错误信息
            .removePrefix("模型未初始化")
            .removePrefix("模型没有初始化")
            .removePrefix("请稍后再试")
            // 移除可能的格式标记
            .removePrefix("【回复】")
            .removePrefix("[回复]")
            .removePrefix("回复内容：")
            // 移除多余的引号
            .removePrefix("\"")
            .removeSuffix("\"")
            .removePrefix("'")
            .removeSuffix("'")
            // 移除换行符，保持单行回复
            .replace("\n", " ")
            .replace("\r", "")
            .trim()
            .take(100) // 限制长度，符合微信聊天习惯
    }
    
    /**
     * 验证AI回复是否有效（针对拟人化聊天优化）
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
            "FATAL",
            "抱歉",
            "无法",
            "不能",
            "系统",
            "AI助手",
            "智能助手"
        )
        
        // 不合适的回复模式
        val inappropriatePatterns = listOf(
            "作为一个",
            "我是一个",
            "根据您的",
            "很高兴为您",
            "如果您需要",
            "请问还有什么",
            "希望我的回答"
        )
        
        // 检查是否包含过多的格式标记
        val formatMarkers = listOf("【", "】", "[", "]", "##", "**")
        val hasFormatMarkers = formatMarkers.any { reply.contains(it) }
        
        return reply.isNotEmpty() && 
               reply.length >= 1 && 
               reply.length <= 100 && // 符合微信聊天长度
               !errorPhrases.any { reply.contains(it, ignoreCase = true) } &&
               !inappropriatePatterns.any { reply.contains(it, ignoreCase = true) } &&
               !hasFormatMarkers && // 不包含格式标记
               !reply.startsWith("根据") && // 避免过于正式的开头
               !reply.startsWith("基于") &&
               !reply.contains("您") // 避免过于正式的称谓
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
                        updateReplyCountDisplay()
                        
                        updateStatusContent("✅ AI回复发送成功！\n\n⏳ 未检测到有新的聊天消息")
                        Toast.makeText(context, "✅ AI回复已自动发送", Toast.LENGTH_SHORT).show()
                        Timber.d("$TAG: AI reply sent successfully via input method, count: $aiReplyCount")
                        
                        // 发送成功后继续监控，不关闭窗口
                        kotlinx.coroutines.delay(2000)
                        
                        // 重新开始监控状态显示
                        updateStatusContent("⏳ 未检测到有新的聊天消息")
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
                                updateReplyCountDisplay()
                                
                                updateStatusContent("✅ AI回复发送成功！\n\n⏳ 未检测到有新的聊天消息")
                                Toast.makeText(context, "✅ AI回复已自动发送", Toast.LENGTH_SHORT).show()
                                Timber.d("$TAG: AI reply sent successfully, count: $aiReplyCount")
                                
                                // 发送成功后继续监控，不关闭窗口
                                kotlinx.coroutines.delay(2000)
                                
                                // 重新开始监控状态显示
                                updateStatusContent("⏳ 未检测到有新的聊天消息")
                            } else {
                                updateStatusContent("⚠️ 消息已填充，请手动点击发送\n将继续监控新消息")
                                Toast.makeText(context, "⚠️ 消息已填充，请手动点击发送", Toast.LENGTH_SHORT).show()
                                
                                // 即使发送失败也继续监控
                                kotlinx.coroutines.delay(3000)
                                updateStatusContent("⏳ 未检测到有新的聊天消息")
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
    
    /**
     * 流式填充输入框内容
     * 实时更新输入框中的文本内容
     */
    private fun fillInputFieldStreamingly(text: String) {
        try {
            // 方法1：通过输入法服务流式填充
            val inputMethodService = com.shenji.aikeyboard.keyboard.ShenjiInputMethodService.instance
            if (inputMethodService != null) {
                inputMethodService.autoFillText(text)
                return
            }
            
            // 方法2：通过无障碍服务流式填充
            val allNodes = AssistsCore.getAllNodes()
            val inputNodes = allNodes.filter { node ->
                val className = node.className?.toString() ?: ""
                val description = node.contentDescription?.toString() ?: ""
                
                (className.contains("EditText") || 
                 description.contains("输入") || 
                 node.isEditable) &&
                node.isEnabled
            }
            
            if (inputNodes.isNotEmpty()) {
                val inputNode = inputNodes.first()
                
                // 流式填充文本
                val arguments = android.os.Bundle()
                arguments.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                inputNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error in streaming fill")
        }
    }
    
    /**
     * 直接发送消息（不需要再填充内容）
     * 用于流式填充完成后直接发送
     */
    private fun sendMessageDirectly(): Boolean {
        return try {
            // 方法1：通过输入法服务发送
            val inputMethodService = com.shenji.aikeyboard.keyboard.ShenjiInputMethodService.instance
            if (inputMethodService != null) {
                // 发送回车键
                val inputConnection = inputMethodService.currentInputConnection
                if (inputConnection != null) {
                    val downEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)
                    val upEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER)
                    
                    val sendDown = inputConnection.sendKeyEvent(downEvent)
                    val sendUp = inputConnection.sendKeyEvent(upEvent)
                    
                    if (sendDown && sendUp) {
                        Timber.d("$TAG: Message sent via input method service")
                        return true
                    }
                }
            }
            
            // 方法2：通过无障碍服务点击发送按钮
            clickWeChatSendButton()
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error sending message directly")
            false
        }
    }
    
    /**
     * 计算智能延迟时间（秒）
     * 根据回复内容和聊天情况计算合适的延迟时间，模拟真实聊天节奏
     */
    private fun calculateSmartDelay(reply: String, conversationData: WeChatConversationFilter.FilteredWeChatData?): Int {
        // 基础延迟时间（秒）
        var delaySeconds = 2
        
        // 根据回复长度调整延迟
        when {
            reply.length <= 5 -> delaySeconds = kotlin.random.Random.nextInt(1, 3) // 短回复：1-2秒
            reply.length <= 15 -> delaySeconds = kotlin.random.Random.nextInt(2, 5) // 中等回复：2-4秒
            reply.length <= 30 -> delaySeconds = kotlin.random.Random.nextInt(3, 7) // 长回复：3-6秒
            else -> delaySeconds = kotlin.random.Random.nextInt(5, 10) // 很长回复：5-9秒
        }
        
        // 根据回复类型调整延迟
        when {
            // 简单确认类回复，延迟较短
            reply in listOf("好的", "嗯嗯", "哈哈", "是的", "👀", "😂") -> {
                delaySeconds = kotlin.random.Random.nextInt(1, 3)
            }
            
            // 问题回复，需要"思考"时间
            reply.contains("?") || reply.contains("？") -> {
                delaySeconds += kotlin.random.Random.nextInt(2, 4)
            }
            
            // 复杂回复，需要更多"打字"时间
            reply.contains("因为") || reply.contains("所以") || reply.contains("不过") -> {
                delaySeconds += kotlin.random.Random.nextInt(1, 3)
            }
            
            // 情感回复，稍微快一点
            reply.contains("哈哈") || reply.contains("😂") || reply.contains("🤣") -> {
                delaySeconds = maxOf(1, delaySeconds - 1)
            }
        }
        
        // 根据对话频率调整（如果对方刚发了很多消息，回复可以快一点）
        conversationData?.let { data ->
            val recentMessages = data.conversationMessages.takeLast(5)
            val recentOtherMessages = recentMessages.filter { it.isFromOther }
            
            if (recentOtherMessages.size >= 3) {
                // 对方连续发了多条消息，回复快一点
                delaySeconds = maxOf(1, delaySeconds - 2)
            }
        }
        
        // 限制延迟范围：1-15秒
        return delaySeconds.coerceIn(1, 15)
    }
    
    /**
     * 获取延迟原因说明
     */
    private fun getDelayReason(reply: String, delaySeconds: Int): String {
        return when {
            delaySeconds <= 2 -> "快速回复"
            reply.length <= 5 -> "简短回复"
            reply.length <= 15 -> "正在打字"
            reply.length <= 30 -> "组织语言中"
            reply.contains("?") || reply.contains("？") -> "思考问题中"
            reply.contains("因为") || reply.contains("所以") -> "整理思路中"
            else -> "认真回复中"
        }
    }
    
    /**
     * 延迟发送消息
     */
    private fun sendMessageWithDelay(message: String, conversationData: WeChatConversationFilter.FilteredWeChatData?) {
        // 取消之前的延迟任务
        delayJob?.cancel()
        
        val delaySeconds = calculateSmartDelay(message, conversationData)
        val delayReason = getDelayReason(message, delaySeconds)
        
        isDelayedSending = true
        currentPendingMessage = message
        
        // 显示延迟控制按钮
        showDelayControls()
        
        // 显示延迟状态
        updateStatusContent("✅ AI回复已生成，为了更自然的聊天体验\n\n📝 回复内容：「$message」\n\n⏰ $delayReason，需要等待 $delaySeconds 秒后发送\n\n💡 点击下方按钮可立即发送或取消")
        
        // 启动延迟任务
        delayJob = coroutineScope.launch {
            try {
                // 倒计时显示
                for (remainingSeconds in delaySeconds downTo 1) {
                    if (!isDelayedSending) break // 如果被取消，退出循环
                    
                    withContext(Dispatchers.Main) {
                        updateStatusContent("✅ AI回复已生成，为了更自然的聊天体验\n\n📝 回复内容：「$message」\n\n⏰ $delayReason，还需等待 $remainingSeconds 秒后发送\n\n💡 点击下方按钮可立即发送或取消")
                    }
                    
                    kotlinx.coroutines.delay(1000) // 等待1秒
                }
                
                // 延迟结束，发送消息
                if (isDelayedSending) {
                    withContext(Dispatchers.Main) {
                        updateStatusContent("📤 延迟时间到，正在发送回复...")
                    }
                    
                    kotlinx.coroutines.delay(500) // 短暂延迟显示发送状态
                    
                    // 实际发送消息
                    autoFillAndSendMessage(message)
                    val sent = true // autoFillAndSendMessage内部处理成功状态
                    
                    if (sent) {
                        // 增加AI回复计数
                        aiReplyCount++
                        updateReplyCountDisplay()
                        
                        withContext(Dispatchers.Main) {
                            updateStatusContent("✅ AI回复发送成功！\n\n⏳ 未检测到有新的聊天消息")
                            Toast.makeText(context, "✅ AI回复已自动发送", Toast.LENGTH_SHORT).show()
                        }
                        
                        Timber.d("$TAG: Delayed AI reply sent successfully, count: $aiReplyCount")
                        
                        // 发送成功后继续监控
                        kotlinx.coroutines.delay(2000)
                        withContext(Dispatchers.Main) {
                            updateStatusContent("⏳ 未检测到有新的聊天消息")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            updateStatusContent("❌ 延迟发送失败，请手动发送")
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Timber.d("$TAG: Delayed sending was cancelled")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error in delayed sending")
                withContext(Dispatchers.Main) {
                    updateStatusContent("❌ 延迟发送过程中出错：${e.message}")
                }
                            } finally {
                    isDelayedSending = false
                    currentPendingMessage = ""
                    withContext(Dispatchers.Main) {
                        hideDelayControls()
                    }
                }
        }
    }
    
    /**
     * 立即发送消息（取消延迟）
     */
    private fun sendMessageImmediately(message: String) {
        // 取消延迟任务
        delayJob?.cancel()
        isDelayedSending = false
        currentPendingMessage = ""
        
        hideDelayControls()
        updateStatusContent("📤 立即发送回复...")
        
        coroutineScope.launch {
            kotlinx.coroutines.delay(300)
            
            autoFillAndSendMessage(message)
            val sent = true // autoFillAndSendMessage内部处理成功状态
            
            if (sent) {
                aiReplyCount++
                updateReplyCountDisplay()
                
                withContext(Dispatchers.Main) {
                    updateStatusContent("✅ AI回复立即发送成功！\n\n⏳ 未检测到有新的聊天消息")
                    Toast.makeText(context, "✅ AI回复已立即发送", Toast.LENGTH_SHORT).show()
                }
                
                Timber.d("$TAG: AI reply sent immediately, count: $aiReplyCount")
                
                kotlinx.coroutines.delay(2000)
                withContext(Dispatchers.Main) {
                    updateStatusContent("⏳ 未检测到有新的聊天消息")
                }
            } else {
                withContext(Dispatchers.Main) {
                    updateStatusContent("❌ 立即发送失败，请手动发送")
                }
            }
        }
    }
    
    /**
     * 取消发送
     */
    private fun cancelSending() {
        delayJob?.cancel()
        isDelayedSending = false
        currentPendingMessage = ""
        
        hideDelayControls()
        updateStatusContent("❌ 已取消发送\n\n⏳ 未检测到有新的聊天消息")
        Toast.makeText(context, "已取消发送", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 生成备用的拟人化回复
     */
    private fun generateFallbackReply(data: WeChatConversationFilter.FilteredWeChatData): String {
        val lastMessage = data.conversationMessages.lastOrNull()
        val lastContent = lastMessage?.content?.lowercase() ?: ""
        
        // 根据最后一条消息的内容特征生成合适的回复
        return when {
            // 问候类
            lastContent.contains("你好") || lastContent.contains("hi") || lastContent.contains("hello") -> {
                listOf("嗨！", "你好呀👋", "哈喽～").random()
            }
            
            // 问题类
            lastContent.contains("?") || lastContent.contains("？") -> {
                listOf("让我想想🤔", "这个问题有点意思", "emmm...").random()
            }
            
            // 感叹类
            lastContent.contains("!") || lastContent.contains("！") -> {
                listOf("哇塞！", "真的假的", "牛啊👌").random()
            }
            
            // 表达累/忙
            lastContent.contains("累") || lastContent.contains("忙") -> {
                listOf("辛苦了😮‍💨", "打工人不容易", "要注意休息哦").random()
            }
            
            // 表达开心
            lastContent.contains("开心") || lastContent.contains("高兴") || lastContent.contains("哈哈") -> {
                listOf("哈哈哈🤣", "看你这么开心我也开心", "笑死我了").random()
            }
            
            // 吃饭相关
            lastContent.contains("吃") || lastContent.contains("饭") || lastContent.contains("饿") -> {
                listOf("我也饿了", "吃什么好呢🤔", "干饭人干饭魂！").random()
            }
            
            // 工作相关
            lastContent.contains("工作") || lastContent.contains("上班") -> {
                listOf("打工人加油💪", "工作顺利！", "搬砖不易啊").random()
            }
            
            // 默认通用回复
            else -> {
                listOf(
                    "哈哈",
                    "是的呢",
                    "确实",
                    "有道理",
                    "懂了",
                    "👀",
                    "嗯嗯",
                    "真的",
                    "哇",
                    "好的"
                ).random()
            }
        }
    }
    
    /**
     * 测试关闭功能 - 用于调试关闭按钮问题
     * 这个方法可以通过外部调用来测试窗口关闭是否正常工作
     */
    fun testCloseFunction(): String {
        val debugInfo = StringBuilder()
        
        try {
            debugInfo.append("=== 窗口关闭功能测试 ===\n")
            
            // 检查窗口状态
            windowWrapper?.let { wrapper ->
                val isContained = AssistsWindowManager.contains(wrapper)
                debugInfo.append("窗口是否在管理器中: $isContained\n")
                
                val view = wrapper.getView()
                debugInfo.append("窗口视图是否为空: ${view == null}\n")
                
                if (view != null) {
                    debugInfo.append("窗口视图可见性: ${view.visibility}\n")
                    debugInfo.append("窗口视图是否附加到窗口: ${view.isAttachedToWindow}\n")
                }
                
                // 测试关闭操作
                debugInfo.append("\n--- 执行关闭操作 ---\n")
                close()
                
                // 检查关闭后状态
                kotlinx.coroutines.GlobalScope.launch {
                    kotlinx.coroutines.delay(1000) // 等待1秒
                    
                    val isStillContained = AssistsWindowManager.contains(wrapper)
                    debugInfo.append("关闭后窗口是否仍在管理器中: $isStillContained\n")
                    
                    val currentWrapper = windowWrapper
                    debugInfo.append("窗口引用是否已清理: ${currentWrapper == null}\n")
                    
                    Timber.d("$TAG: Close test result:\n$debugInfo")
                }
                
            } ?: run {
                debugInfo.append("窗口引用为空，无法测试\n")
            }
            
            debugInfo.append("=== 测试完成 ===\n")
            
        } catch (e: Exception) {
            debugInfo.append("测试过程中出现异常: ${e.message}\n")
            Timber.e(e, "$TAG: Error in close function test")
        }
        
        return debugInfo.toString()
    }
    
    /**
     * 获取窗口状态信息 - 用于调试
     */
    fun getWindowStatus(): String {
        val status = StringBuilder()
        
        try {
            status.append("=== 窗口状态信息 ===\n")
            
            windowWrapper?.let { wrapper ->
                status.append("窗口包装器: 存在\n")
                status.append("是否在管理器中: ${AssistsWindowManager.contains(wrapper)}\n")
                
                val view = wrapper.getView()
                status.append("视图: ${if (view != null) "存在" else "不存在"}\n")
                
                if (view != null) {
                    status.append("视图可见性: ${view.visibility}\n")
                    status.append("视图是否附加: ${view.isAttachedToWindow}\n")
                    status.append("视图宽度: ${view.width}\n")
                    status.append("视图高度: ${view.height}\n")
                }
                
            } ?: run {
                status.append("窗口包装器: 不存在\n")
            }
            
            status.append("监控状态: $isMonitoring\n")
            status.append("AI处理状态: $isAIProcessing\n")
            status.append("流式显示状态: $isStreaming\n")
            
            status.append("=== 状态信息结束 ===\n")
            
        } catch (e: Exception) {
            status.append("获取状态信息时出现异常: ${e.message}\n")
            Timber.e(e, "$TAG: Error getting window status")
        }
        
        return status.toString()
    }
} 