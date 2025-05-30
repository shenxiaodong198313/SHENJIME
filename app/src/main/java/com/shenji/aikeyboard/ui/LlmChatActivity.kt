package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.BuildConfig
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.llm.LlmManager
import com.shenji.aikeyboard.adapter.ChatMessagesAdapter
import com.shenji.aikeyboard.model.ChatMessage
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * LLM聊天Activity
 * 使用与插件日志一致的样式设计
 */
class LlmChatActivity : AppCompatActivity() {
    
    private lateinit var chatAdapter: ChatMessagesAdapter
    private lateinit var llmManager: LlmManager
    
    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var tvLoadingStatus: TextView
    private lateinit var tvTypingIndicator: TextView
    private lateinit var btnClearChat: Button
    private lateinit var btnAiSettings: Button
    
    private val chatMessages = mutableListOf<ChatMessage>()
    private var modelId: String = ""
    private var modelName: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏模式
        setupFullScreenMode()
        
        setContentView(R.layout.activity_llm_chat)
        
        // 隐藏ActionBar
        supportActionBar?.hide()
        
        // 获取传递的参数
        modelId = intent.getStringExtra("model_id") ?: ""
        modelName = intent.getStringExtra("model_name") ?: "AI Chat"
        
        setupUI()
        setupRecyclerView()
        setupLlmManager()
        setupClickListeners()
    }
    
    /**
     * 设置全屏模式
     */
    private fun setupFullScreenMode() {
        try {
            // 设置状态栏和导航栏颜色与背景一致
            window.statusBarColor = getColor(R.color.splash_background_color)
            window.navigationBarColor = getColor(R.color.splash_background_color)
            
            // 使用传统的全屏方法，更兼容
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        } catch (e: Exception) {
            Timber.w("设置全屏模式失败: ${e.message}")
        }
    }
    
    private fun setupUI() {
        // 设置返回按钮
        findViewById<Button>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
        
        // 设置页面标题
        findViewById<TextView>(R.id.pageTitle)?.text = modelName
        
        // 获取UI组件
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus)
        tvTypingIndicator = findViewById(R.id.tvTypingIndicator)
        btnClearChat = findViewById(R.id.btnClearChat)
        btnAiSettings = findViewById(R.id.btnAiSettings)
        
        // 显示初始化状态
        showLoadingState(true)
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatMessagesAdapter()
        
        recyclerViewChat.apply {
            layoutManager = LinearLayoutManager(this@LlmChatActivity)
            adapter = chatAdapter
        }
    }
    
    private fun setupLlmManager() {
        llmManager = LlmManager.getInstance(this)
        
        // 重新初始化LLM（确保资源可用）
        lifecycleScope.launch {
            val success = llmManager.initialize()
            
            runOnUiThread {
                showLoadingState(false)
                
                if (success) {
                    Toast.makeText(this@LlmChatActivity, "模型加载成功", Toast.LENGTH_SHORT).show()
                    addWelcomeMessage()
                } else {
                    Toast.makeText(this@LlmChatActivity, "模型加载失败", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        btnSend.setOnClickListener {
            sendMessage()
        }
        
        etMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
        
        btnClearChat.setOnClickListener {
            clearChat()
        }
        
        btnAiSettings.setOnClickListener {
            // TODO: 实现AI设置页面
            Toast.makeText(this, "AI设置功能即将推出", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            content = "你好！我是 $modelName，一个运行在你设备上的AI助手。有什么可以帮助你的吗？",
            isFromUser = false,
            timestamp = System.currentTimeMillis()
        )
        
        chatMessages.add(welcomeMessage)
        chatAdapter.submitList(chatMessages.toList())
        scrollToBottom()
    }
    
    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()
        if (messageText.isEmpty()) return
        
        // 清空输入框
        etMessage.text.clear()
        
        // 添加用户消息
        val userMessage = ChatMessage(
            content = messageText,
            isFromUser = true,
            timestamp = System.currentTimeMillis()
        )
        
        chatMessages.add(userMessage)
        chatAdapter.submitList(chatMessages.toList())
        scrollToBottom()
        
        // 显示正在输入状态
        showTypingIndicator(true)
        
        // 添加"正在思考"的临时消息
        val thinkingMessage = ChatMessage(
            content = "正在思考中...",
            isFromUser = false,
            timestamp = System.currentTimeMillis()
        )
        chatMessages.add(thinkingMessage)
        chatAdapter.submitList(chatMessages.toList())
        scrollToBottom()
        
        // 生成AI回复
        lifecycleScope.launch {
            try {
                Log.d("LlmChat", "开始生成回复，输入: $messageText")
                val startTime = System.currentTimeMillis()
                
                val response = llmManager.generateResponse(messageText)
                
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                Log.d("LlmChat", "生成回复完成，耗时: ${duration}ms, 回复: $response")
                
                runOnUiThread {
                    showTypingIndicator(false)
                    
                    // 移除"正在思考"的临时消息
                    chatMessages.removeLastOrNull()
                    
                    val aiMessage = ChatMessage(
                        content = if (response.isNullOrBlank()) {
                            "抱歉，我现在无法回复。请稍后再试。(生成为空)"
                        } else {
                            response
                        },
                        isFromUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    chatMessages.add(aiMessage)
                    chatAdapter.submitList(chatMessages.toList())
                    scrollToBottom()
                    
                    // 显示性能信息（仅在调试模式）
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(this@LlmChatActivity, 
                            "推理耗时: ${duration}ms", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("LlmChat", "生成回复失败", e)
                
                runOnUiThread {
                    showTypingIndicator(false)
                    
                    // 移除"正在思考"的临时消息
                    chatMessages.removeLastOrNull()
                    
                    val errorMessage = ChatMessage(
                        content = "抱歉，生成回复时发生错误: ${e.message}",
                        isFromUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    chatMessages.add(errorMessage)
                    chatAdapter.submitList(chatMessages.toList())
                    scrollToBottom()
                }
            }
        }
    }
    
    private fun clearChat() {
        chatMessages.clear()
        chatAdapter.submitList(chatMessages.toList())
        addWelcomeMessage()
        Toast.makeText(this, "对话已清除", Toast.LENGTH_SHORT).show()
    }
    
    private fun showLoadingState(isLoading: Boolean) {
        if (isLoading) {
            tvLoadingStatus.visibility = View.VISIBLE
            tvLoadingStatus.text = "正在加载模型..."
            recyclerViewChat.visibility = View.GONE
            findViewById<View>(R.id.layoutInput).visibility = View.GONE
        } else {
            tvLoadingStatus.visibility = View.GONE
            recyclerViewChat.visibility = View.VISIBLE
            findViewById<View>(R.id.layoutInput).visibility = View.VISIBLE
        }
    }
    
    private fun showTypingIndicator(isTyping: Boolean) {
        if (isTyping) {
            tvTypingIndicator.visibility = View.VISIBLE
            tvTypingIndicator.text = "AI正在思考..."
        } else {
            tvTypingIndicator.visibility = View.GONE
        }
    }
    
    private fun scrollToBottom() {
        if (chatMessages.isNotEmpty()) {
            recyclerViewChat.smoothScrollToPosition(chatMessages.size - 1)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 离开对话页面时释放LLM资源
        try {
            llmManager.release()
            Timber.d("LLM资源已释放")
        } catch (e: Exception) {
            Timber.w("释放LLM资源失败: ${e.message}")
        }
    }
} 