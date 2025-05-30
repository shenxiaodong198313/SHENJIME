package com.shenji.aikeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.llm.LlmManager
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Gemma大模型页面
 * 使用与插件日志一致的样式设计
 */
class LlmModelsActivity : AppCompatActivity() {
    
    private lateinit var llmManager: LlmManager
    private lateinit var tvModelStatus: TextView
    private lateinit var btnStartChat: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏模式
        setupFullScreenMode()
        
        setContentView(R.layout.activity_llm_models)
        
        // 隐藏ActionBar
        supportActionBar?.hide()
        
        setupUI()
        initializeLlm()
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
        
        // 获取UI组件
        tvModelStatus = findViewById(R.id.tvModelStatus)
        btnStartChat = findViewById(R.id.btnStartChat)
        
        // 设置开始对话按钮点击事件
        btnStartChat.setOnClickListener {
            startChatActivity()
        }
        
        // 初始状态
        btnStartChat.isEnabled = false
        tvModelStatus.text = "正在初始化模型..."
    }
    
    private fun initializeLlm() {
        llmManager = LlmManager.getInstance(this)
        
        lifecycleScope.launch {
            try {
                val success = llmManager.initialize()
                
                runOnUiThread {
                    if (success) {
                        tvModelStatus.text = "✅ Gemma-3-1B-IT-INT4 模型已就绪\n\n📱 本地运行，保护隐私\n🚀 快速响应，无需网络\n🧠 智能对话，理解上下文"
                        btnStartChat.isEnabled = true
                        btnStartChat.text = "开始对话"
                    } else {
                        tvModelStatus.text = "❌ 模型初始化失败\n\n请检查设备存储空间是否充足\n或重新启动应用重试"
                        btnStartChat.text = "重试"
                        btnStartChat.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvModelStatus.text = "❌ 模型加载出错：${e.message}"
                    btnStartChat.text = "重试"
                    btnStartChat.isEnabled = true
                }
            }
        }
    }
    
    private fun startChatActivity() {
        if (llmManager.isReady()) {
            val intent = Intent(this, LlmChatActivity::class.java)
            intent.putExtra("model_id", "gemma3-1b-it-int4")
            intent.putExtra("model_name", "Gemma-3-1B-IT")
            startActivity(intent)
        } else {
            // 重新初始化
            initializeLlm()
        }
    }
} 