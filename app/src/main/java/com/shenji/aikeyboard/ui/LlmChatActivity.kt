package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shenji.aikeyboard.BuildConfig
import com.shenji.aikeyboard.databinding.ActivityLlmChatBinding
import com.shenji.aikeyboard.llm.LlmManager
import com.shenji.aikeyboard.adapter.ChatMessagesAdapter
import com.shenji.aikeyboard.model.ChatMessage
import kotlinx.coroutines.launch

/**
 * LLM聊天Activity
 * 提供与AI模型的对话界面
 */
class LlmChatActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLlmChatBinding
    private lateinit var chatAdapter: ChatMessagesAdapter
    private lateinit var llmManager: LlmManager
    
    private val chatMessages = mutableListOf<ChatMessage>()
    private var modelId: String = ""
    private var modelName: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLlmChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取传递的参数
        modelId = intent.getStringExtra("model_id") ?: ""
        modelName = intent.getStringExtra("model_name") ?: "AI Chat"
        
        setupUI()
        setupRecyclerView()
        setupLlmManager()
        setupClickListeners()
    }
    
    private fun setupUI() {
        // 设置工具栏
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = modelName
        }
        
        // 显示初始化状态
        showLoadingState(true)
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatMessagesAdapter()
        
        binding.recyclerViewChat.apply {
            layoutManager = LinearLayoutManager(this@LlmChatActivity)
            adapter = chatAdapter
        }
    }
    
    private fun setupLlmManager() {
        llmManager = LlmManager.getInstance(this)
        
        // 初始化LLM
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
        binding.btnSend.setOnClickListener {
            sendMessage()
        }
        
        binding.etMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
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
        val messageText = binding.etMessage.text.toString().trim()
        if (messageText.isEmpty()) return
        
        // 清空输入框
        binding.etMessage.text.clear()
        
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
    
    private fun showLoadingState(isLoading: Boolean) {
        binding.apply {
            if (isLoading) {
                progressBar.visibility = View.VISIBLE
                tvLoadingStatus.visibility = View.VISIBLE
                tvLoadingStatus.text = "正在加载模型..."
                layoutInput.visibility = View.GONE
            } else {
                progressBar.visibility = View.GONE
                tvLoadingStatus.visibility = View.GONE
                layoutInput.visibility = View.VISIBLE
            }
        }
    }
    
    private fun showTypingIndicator(isTyping: Boolean) {
        binding.apply {
            if (isTyping) {
                tvTypingIndicator.visibility = View.VISIBLE
                tvTypingIndicator.text = "AI正在思考..."
            } else {
                tvTypingIndicator.visibility = View.GONE
            }
        }
    }
    
    private fun scrollToBottom() {
        if (chatMessages.isNotEmpty()) {
            binding.recyclerViewChat.smoothScrollToPosition(chatMessages.size - 1)
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 注意：不要在这里释放LlmManager，因为它是单例
        // llmManager.release()
    }
} 