package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ai.*
import com.shenji.aikeyboard.ai.engines.Gemma3Engine
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * AI功能测试Activity
 * 用于测试和演示AI拼音纠错、续写等功能
 */
class AITestActivity : AppCompatActivity() {
    
    private lateinit var etInput: EditText
    private lateinit var btnTestCorrection: Button
    private lateinit var btnTestContinuation: Button
    private lateinit var btnTestSemantic: Button
    private lateinit var tvResult: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    
    private lateinit var aiEngineManager: AIEngineManager
    private var isAIInitialized = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_test)
        
        setupUI()
        initializeAI()
    }
    
    private fun setupUI() {
        // 设置返回按钮
        findViewById<Button>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
        
        // 设置页面标题
        findViewById<TextView>(R.id.pageTitle)?.text = "AI功能测试"
        
        etInput = findViewById(R.id.etInput)
        btnTestCorrection = findViewById(R.id.btnTestCorrection)
        btnTestContinuation = findViewById(R.id.btnTestContinuation)
        btnTestSemantic = findViewById(R.id.btnTestSemantic)
        tvResult = findViewById(R.id.tvResult)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        
        // 设置点击事件
        btnTestCorrection.setOnClickListener { testPinyinCorrection() }
        btnTestContinuation.setOnClickListener { testTextContinuation() }
        btnTestSemantic.setOnClickListener { testSemanticAnalysis() }
        
        // 初始状态
        updateButtonsState(false)
        tvStatus.text = "正在初始化AI引擎..."
        showProgress(true)
    }
    
    private fun initializeAI() {
        lifecycleScope.launch {
            try {
                Timber.d("🤖 开始初始化AI引擎")
                
                // 初始化AI引擎管理器
                aiEngineManager = AIEngineManager.getInstance()
                
                // 注册Gemma3引擎
                val gemma3Engine = Gemma3Engine(this@AITestActivity)
                aiEngineManager.registerEngine("gemma3-1b-it", gemma3Engine)
                
                // 切换到Gemma3引擎
                val switchSuccess = aiEngineManager.switchEngine("gemma3-1b-it")
                
                if (switchSuccess) {
                    isAIInitialized = true
                    tvStatus.text = "✅ AI引擎初始化成功 - Gemma3-1B-IT"
                    updateButtonsState(true)
                    showTestInstructions()
                    Timber.i("🎉 AI引擎初始化成功")
                } else {
                    tvStatus.text = "❌ AI引擎初始化失败"
                    tvResult.text = "AI引擎初始化失败，请检查模型文件是否存在"
                    Timber.e("❌ AI引擎初始化失败")
                }
                
            } catch (e: Exception) {
                tvStatus.text = "❌ AI引擎初始化异常"
                tvResult.text = "初始化异常: ${e.message}"
                Timber.e(e, "AI引擎初始化异常")
            } finally {
                showProgress(false)
            }
        }
    }
    
    private fun testPinyinCorrection() {
        val input = etInput.text.toString().trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入拼音", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isAIInitialized) {
            Toast.makeText(this, "AI引擎未初始化", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                showProgress(true)
                tvStatus.text = "🔍 正在分析拼音..."
                
                val currentEngine = aiEngineManager.getCurrentEngine()
                if (currentEngine == null) {
                    tvResult.text = "❌ 没有可用的AI引擎"
                    return@launch
                }
                
                // 创建输入上下文
                val context = InputContext(
                    appPackage = packageName,
                    inputType = android.view.inputmethod.EditorInfo.TYPE_CLASS_TEXT,
                    previousText = "",
                    cursorPosition = 0,
                    userPreferences = UserPreferences(),
                    timestamp = System.currentTimeMillis()
                )
                
                val startTime = System.currentTimeMillis()
                val suggestions = currentEngine.correctPinyin(input, context)
                val endTime = System.currentTimeMillis()
                
                val resultText = buildString {
                    appendLine("🎯 拼音纠错结果 (耗时: ${endTime - startTime}ms)")
                    appendLine("=".repeat(50))
                    appendLine("📝 输入拼音: \"$input\"")
                    appendLine("🔍 分析结果: ${suggestions.size}个建议")
                    appendLine()
                    
                    if (suggestions.isNotEmpty()) {
                        suggestions.forEachIndexed { index, suggestion ->
                            appendLine("${index + 1}. ${suggestion.correctedText}")
                            appendLine("   拼音: ${suggestion.correctedPinyin}")
                            appendLine("   置信度: ${(suggestion.confidence * 100).toInt()}%")
                            appendLine("   错误类型: ${suggestion.errorType}")
                            if (!suggestion.explanation.isNullOrBlank()) {
                                appendLine("   说明: ${suggestion.explanation}")
                            }
                            appendLine()
                        }
                    } else {
                        appendLine("❌ 未找到纠错建议")
                        appendLine("可能原因:")
                        appendLine("- 输入的拼音是正确的")
                        appendLine("- AI模型无法识别此错误")
                        appendLine("- 模型响应为空")
                    }
                }
                
                tvResult.text = resultText
                tvStatus.text = "✅ 拼音纠错完成"
                
            } catch (e: Exception) {
                tvResult.text = "❌ 拼音纠错失败: ${e.message}"
                tvStatus.text = "❌ 处理失败"
                Timber.e(e, "拼音纠错测试失败")
            } finally {
                showProgress(false)
            }
        }
    }
    
    private fun testTextContinuation() {
        val input = etInput.text.toString().trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入文本", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isAIInitialized) {
            Toast.makeText(this, "AI引擎未初始化", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                showProgress(true)
                tvStatus.text = "💡 正在生成续写..."
                
                val currentEngine = aiEngineManager.getCurrentEngine()
                if (currentEngine == null) {
                    tvResult.text = "❌ 没有可用的AI引擎"
                    return@launch
                }
                
                val context = InputContext(
                    appPackage = packageName,
                    inputType = android.view.inputmethod.EditorInfo.TYPE_CLASS_TEXT,
                    previousText = "",
                    cursorPosition = input.length,
                    userPreferences = UserPreferences(),
                    timestamp = System.currentTimeMillis()
                )
                
                val startTime = System.currentTimeMillis()
                val suggestions = currentEngine.generateContinuation(input, context)
                val endTime = System.currentTimeMillis()
                
                val resultText = buildString {
                    appendLine("💡 文本续写结果 (耗时: ${endTime - startTime}ms)")
                    appendLine("=".repeat(50))
                    appendLine("📝 输入文本: \"$input\"")
                    appendLine("🔍 续写建议: ${suggestions.size}个")
                    appendLine()
                    
                    if (suggestions.isNotEmpty()) {
                        suggestions.forEachIndexed { index, suggestion ->
                            appendLine("${index + 1}. ${suggestion.text}")
                            appendLine("   置信度: ${(suggestion.confidence * 100).toInt()}%")
                            appendLine("   类型: ${suggestion.type}")
                            appendLine()
                        }
                    } else {
                        appendLine("❌ 未生成续写建议")
                    }
                }
                
                tvResult.text = resultText
                tvStatus.text = "✅ 文本续写完成"
                
            } catch (e: Exception) {
                tvResult.text = "❌ 文本续写失败: ${e.message}"
                tvStatus.text = "❌ 处理失败"
                Timber.e(e, "文本续写测试失败")
            } finally {
                showProgress(false)
            }
        }
    }
    
    private fun testSemanticAnalysis() {
        val input = etInput.text.toString().trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入文本", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isAIInitialized) {
            Toast.makeText(this, "AI引擎未初始化", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                showProgress(true)
                tvStatus.text = "🧠 正在分析语义..."
                
                val currentEngine = aiEngineManager.getCurrentEngine()
                if (currentEngine == null) {
                    tvResult.text = "❌ 没有可用的AI引擎"
                    return@launch
                }
                
                val context = InputContext(
                    appPackage = packageName,
                    inputType = android.view.inputmethod.EditorInfo.TYPE_CLASS_TEXT,
                    previousText = "",
                    cursorPosition = 0,
                    userPreferences = UserPreferences(),
                    timestamp = System.currentTimeMillis()
                )
                
                val startTime = System.currentTimeMillis()
                val analysis = currentEngine.analyzeSemantics(input, context)
                val endTime = System.currentTimeMillis()
                
                val resultText = buildString {
                    appendLine("🧠 语义分析结果 (耗时: ${endTime - startTime}ms)")
                    appendLine("=".repeat(50))
                    appendLine("📝 输入文本: \"$input\"")
                    appendLine()
                    appendLine("🎯 用户意图: ${analysis.intent}")
                    appendLine("😊 情感倾向: ${analysis.sentiment}")
                    appendLine("🏷️ 主题标签: ${analysis.topics.joinToString(", ")}")
                    appendLine("📊 分析置信度: ${(analysis.confidence * 100).toInt()}%")
                }
                
                tvResult.text = resultText
                tvStatus.text = "✅ 语义分析完成"
                
            } catch (e: Exception) {
                tvResult.text = "❌ 语义分析失败: ${e.message}"
                tvStatus.text = "❌ 处理失败"
                Timber.e(e, "语义分析测试失败")
            } finally {
                showProgress(false)
            }
        }
    }
    
    private fun showTestInstructions() {
        val instructions = """
            🎯 AI功能测试说明:
            
            1. 拼音纠错测试:
               输入错误拼音，如: "ni hoa" (你好)
               AI会分析并提供正确的词语建议
            
            2. 文本续写测试:
               输入不完整的句子，如: "今天天气"
               AI会提供可能的续写内容
            
            3. 语义分析测试:
               输入任意文本，如: "我想订一张机票"
               AI会分析用户意图和情感倾向
            
            💡 测试建议:
            - 尝试不同类型的输入
            - 观察AI的响应时间和准确性
            - 检查日志输出了解详细信息
        """.trimIndent()
        
        tvResult.text = instructions
    }
    
    private fun updateButtonsState(enabled: Boolean) {
        btnTestCorrection.isEnabled = enabled
        btnTestContinuation.isEnabled = enabled
        btnTestSemantic.isEnabled = enabled
    }
    
    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 清理AI资源
        if (isAIInitialized) {
            lifecycleScope.launch {
                try {
                    aiEngineManager.releaseAll()
                    Timber.d("🧹 AI资源已清理")
                } catch (e: Exception) {
                    Timber.e(e, "清理AI资源失败")
                }
            }
        }
    }
} 