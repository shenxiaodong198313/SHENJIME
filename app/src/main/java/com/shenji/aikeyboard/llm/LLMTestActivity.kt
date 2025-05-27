package com.shenji.aikeyboard.llm

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.databinding.ActivityLlmTestBinding
import kotlinx.coroutines.launch

/**
 * LLM测试活动
 * 用于测试大模型功能
 */
class LLMTestActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLlmTestBinding
    private val llmService = ShenjiApplication.llmService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLlmTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        updateStatus()
    }
    
    private fun setupUI() {
        // 设置标题栏
        supportActionBar?.title = "LLM大模型测试"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 设置按钮点击事件
        binding.btnGenerate.setOnClickListener {
            generateText()
        }
        
        binding.btnComplete.setOnClickListener {
            completeText()
        }
        
        binding.btnCorrect.setOnClickListener {
            correctText()
        }
        
        binding.btnRewrite.setOnClickListener {
            rewriteText()
        }
        
        binding.btnReset.setOnClickListener {
            resetSession()
        }
        
        binding.btnRefreshStatus.setOnClickListener {
            updateStatus()
        }
    }
    
    private fun generateText() {
        val input = binding.etInput.text.toString().trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入文本", Toast.LENGTH_SHORT).show()
            return
        }
        
        setLoading(true)
        lifecycleScope.launch {
            try {
                val result = llmService.generateSentence(input)
                binding.tvResult.text = if (result.isNotEmpty()) {
                    "生成结果：\n$result"
                } else {
                    "生成失败或服务未就绪"
                }
            } catch (e: Exception) {
                binding.tvResult.text = "生成异常：${e.message}"
            } finally {
                setLoading(false)
            }
        }
    }
    
    private fun completeText() {
        val input = binding.etInput.text.toString().trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入文本", Toast.LENGTH_SHORT).show()
            return
        }
        
        setLoading(true)
        lifecycleScope.launch {
            try {
                val results = llmService.completeText(input)
                binding.tvResult.text = if (results.isNotEmpty()) {
                    "补全结果：\n${results.joinToString("\n")}"
                } else {
                    "补全失败或服务未就绪"
                }
            } catch (e: Exception) {
                binding.tvResult.text = "补全异常：${e.message}"
            } finally {
                setLoading(false)
            }
        }
    }
    
    private fun correctText() {
        val input = binding.etInput.text.toString().trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入文本", Toast.LENGTH_SHORT).show()
            return
        }
        
        setLoading(true)
        lifecycleScope.launch {
            try {
                val result = llmService.correctText(input)
                binding.tvResult.text = "纠错结果：\n$result"
            } catch (e: Exception) {
                binding.tvResult.text = "纠错异常：${e.message}"
            } finally {
                setLoading(false)
            }
        }
    }
    
    private fun rewriteText() {
        val input = binding.etInput.text.toString().trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入文本", Toast.LENGTH_SHORT).show()
            return
        }
        
        setLoading(true)
        lifecycleScope.launch {
            try {
                val result = llmService.rewriteText(input)
                binding.tvResult.text = "改写结果：\n$result"
            } catch (e: Exception) {
                binding.tvResult.text = "改写异常：${e.message}"
            } finally {
                setLoading(false)
            }
        }
    }
    
    private fun resetSession() {
        llmService.resetSession()
        binding.tvResult.text = "会话已重置"
        Toast.makeText(this, "会话已重置", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateStatus() {
        val isReady = llmService.isServiceReady()
        val modelInfo = llmService.getModelInfo()
        
        binding.tvStatus.text = """
            服务状态：${if (isReady) "✅ 就绪" else "❌ 未就绪"}
            
            模型信息：
            $modelInfo
        """.trimIndent()
    }
    
    private fun setLoading(loading: Boolean) {
        binding.btnGenerate.isEnabled = !loading
        binding.btnComplete.isEnabled = !loading
        binding.btnCorrect.isEnabled = !loading
        binding.btnRewrite.isEnabled = !loading
        
        if (loading) {
            binding.tvResult.text = "正在处理中..."
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 