package com.shenji.aikeyboard.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ai.*
import com.shenji.aikeyboard.ai.engines.Gemma3Engine
import com.shenji.aikeyboard.ai.engines.Gemma3nEngine
import com.shenji.aikeyboard.llm.LlmManager
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.InputStream

/**
 * AI功能测试Activity
 * 用于测试和演示AI拼音纠错、续写等功能
 */
class AITestActivity : AppCompatActivity() {
    
    companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1001
        private const val MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 1002
    }
    
    private lateinit var etInput: EditText
    private lateinit var btnTestCorrection: Button
    private lateinit var btnTestContinuation: Button
    private lateinit var btnTestSemantic: Button
    private lateinit var btnTestMultimodal: Button
    private lateinit var btnSwitchModel: Button
    private lateinit var tvResult: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    
    // 多模态相关UI组件
    private lateinit var multimodalLayout: LinearLayout
    private lateinit var ivImagePreview: ImageView
    private lateinit var btnSelectImage: Button
    private lateinit var btnClearImage: Button
    
    private lateinit var aiEngineManager: AIEngineManager
    private var isAIInitialized = false
    private var currentModelType = LlmManager.ModelType.GEMMA3_1B_IT
    private var selectedImageBitmap: Bitmap? = null
    
    // 图像选择器
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageSelection(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_test)
        
        setupUI()
        checkStoragePermissionAndInitialize()
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
        btnTestMultimodal = findViewById(R.id.btnTestMultimodal)
        btnSwitchModel = findViewById(R.id.btnSwitchModel)
        tvResult = findViewById(R.id.tvResult)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        
        // 多模态UI组件
        multimodalLayout = findViewById(R.id.multimodalLayout)
        ivImagePreview = findViewById(R.id.ivImagePreview)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnClearImage = findViewById(R.id.btnClearImage)
        
        // 设置点击事件
        btnTestCorrection.setOnClickListener { testPinyinCorrection() }
        btnTestContinuation.setOnClickListener { testTextContinuation() }
        btnTestSemantic.setOnClickListener { testSemanticAnalysis() }
        btnTestMultimodal.setOnClickListener { testMultimodalAnalysis() }
        btnSwitchModel.setOnClickListener { showModelSwitchDialog() }
        
        // 多模态功能点击事件
        btnSelectImage.setOnClickListener { selectImage() }
        btnClearImage.setOnClickListener { clearImage() }
        
        // 初始状态
        updateButtonsState(false)
        updateMultimodalUI()
        tvStatus.text = "正在初始化AI引擎..."
        showProgress(true)
    }
    
    /**
     * 检查存储权限并初始化AI
     */
    private fun checkStoragePermissionAndInitialize() {
        if (hasStoragePermission()) {
            initializeAI()
        } else {
            requestStoragePermission()
        }
    }
    
    /**
     * 检查是否有存储权限
     */
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要MANAGE_EXTERNAL_STORAGE权限
            Environment.isExternalStorageManager()
        } else {
            // Android 10及以下需要READ_EXTERNAL_STORAGE权限
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 请求存储权限
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 请求MANAGE_EXTERNAL_STORAGE权限
            showManageStoragePermissionDialog()
        } else {
            // Android 10及以下请求READ_EXTERNAL_STORAGE权限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    /**
     * 显示管理外部存储权限对话框
     */
    private fun showManageStoragePermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要存储权限")
            .setMessage("为了读取模型文件，需要授予存储管理权限。点击确定前往设置页面。")
            .setPositiveButton("确定") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE)
                } catch (e: Exception) {
                    // 如果无法打开特定应用的设置页面，打开通用设置页面
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE)
                }
            }
            .setNegativeButton("取消") { _, _ ->
                tvStatus.text = "❌ 需要存储权限才能使用AI功能"
                tvResult.text = "请授予存储权限以访问模型文件"
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 处理权限请求结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initializeAI()
                } else {
                    tvStatus.text = "❌ 需要存储权限才能使用AI功能"
                    tvResult.text = "请授予存储权限以访问模型文件"
                }
            }
        }
    }
    
    /**
     * 处理Activity结果
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            MANAGE_EXTERNAL_STORAGE_REQUEST_CODE -> {
                if (hasStoragePermission()) {
                    initializeAI()
                } else {
                    tvStatus.text = "❌ 需要存储权限才能使用AI功能"
                    tvResult.text = "请授予存储权限以访问模型文件"
                }
            }
        }
    }
    
    private fun initializeAI() {
        lifecycleScope.launch {
            try {
                Timber.d("🤖 开始初始化AI引擎")
                
                // 初始化AI引擎管理器
                aiEngineManager = AIEngineManager.getInstance()
                
                // 注册两个AI引擎
                val gemma3Engine = Gemma3Engine(this@AITestActivity)
                val gemma3nEngine = Gemma3nEngine(this@AITestActivity)
                
                aiEngineManager.registerEngine("gemma3-1b-it", gemma3Engine)
                aiEngineManager.registerEngine("gemma3n-e4b-it", gemma3nEngine)
                
                // 默认切换到Gemma3引擎
                val switchSuccess = aiEngineManager.switchEngine("gemma3-1b-it")
                
                if (switchSuccess) {
                    isAIInitialized = true
                    currentModelType = LlmManager.ModelType.GEMMA3_1B_IT
                    updateStatusText()
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
        btnTestMultimodal.isEnabled = enabled
    }
    
    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }
    
    /**
     * 显示模型切换对话框
     */
    private fun showModelSwitchDialog() {
        val models = arrayOf(
            "Gemma3-1B-IT (默认模型)",
            "Gemma-3n-E4B-IT (新模型)"
        )
        
        val currentSelection = when (currentModelType) {
            LlmManager.ModelType.GEMMA3_1B_IT -> 0
            LlmManager.ModelType.GEMMA3N_E4B_IT -> 1
        }
        
        AlertDialog.Builder(this)
            .setTitle("🔄 选择AI模型")
            .setSingleChoiceItems(models, currentSelection) { dialog, which ->
                val targetModelType = when (which) {
                    0 -> LlmManager.ModelType.GEMMA3_1B_IT
                    1 -> LlmManager.ModelType.GEMMA3N_E4B_IT
                    else -> LlmManager.ModelType.GEMMA3_1B_IT
                }
                
                if (targetModelType != currentModelType) {
                    switchToModel(targetModelType)
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 切换到指定模型
     */
    private fun switchToModel(targetModelType: LlmManager.ModelType) {
        lifecycleScope.launch {
            try {
                showProgress(true)
                updateButtonsState(false)
                tvStatus.text = "🔄 正在切换模型..."
                
                val targetEngineId = when (targetModelType) {
                    LlmManager.ModelType.GEMMA3_1B_IT -> "gemma3-1b-it"
                    LlmManager.ModelType.GEMMA3N_E4B_IT -> "gemma3n-e4b-it"
                }
                
                Timber.d("🔄 开始切换模型: $currentModelType -> $targetModelType")
                
                // 切换引擎
                val switchSuccess = aiEngineManager.switchEngine(targetEngineId)
                
                if (switchSuccess) {
                    currentModelType = targetModelType
                    updateStatusText()
                    updateButtonsState(true)
                    updateMultimodalUI()
                    
                    // 清除之前选择的图像（如果有）
                    if (selectedImageBitmap != null) {
                        clearImage()
                    }
                    
                    // 显示切换成功信息
                    val modelName = when (targetModelType) {
                        LlmManager.ModelType.GEMMA3_1B_IT -> "Gemma3-1B-IT"
                        LlmManager.ModelType.GEMMA3N_E4B_IT -> "Gemma-3n-E4B-IT"
                    }
                    
                    tvResult.text = """
                        🎉 模型切换成功！
                        
                        当前使用模型: $modelName
                        
                        模型特性:
                        ${getModelDescription(targetModelType)}
                        
                        现在可以开始测试新模型的AI功能了！
                    """.trimIndent()
                    
                    Toast.makeText(this@AITestActivity, "已切换到 $modelName", Toast.LENGTH_SHORT).show()
                    Timber.i("🎉 模型切换成功: $targetModelType")
                    
                } else {
                    tvStatus.text = "❌ 模型切换失败"
                    tvResult.text = "模型切换失败，请检查模型文件是否存在"
                    updateButtonsState(true) // 恢复按钮状态
                    Timber.e("❌ 模型切换失败: $targetModelType")
                }
                
            } catch (e: Exception) {
                tvStatus.text = "❌ 模型切换异常"
                tvResult.text = "切换异常: ${e.message}"
                updateButtonsState(true) // 恢复按钮状态
                Timber.e(e, "模型切换异常")
            } finally {
                showProgress(false)
            }
        }
    }
    
    /**
     * 更新状态文本
     */
    private fun updateStatusText() {
        val modelName = when (currentModelType) {
            LlmManager.ModelType.GEMMA3_1B_IT -> "Gemma3-1B-IT"
            LlmManager.ModelType.GEMMA3N_E4B_IT -> "Gemma-3n-E4B-IT"
        }
        tvStatus.text = "✅ AI引擎就绪 - $modelName"
    }
    
    /**
     * 获取模型描述
     */
    private fun getModelDescription(modelType: LlmManager.ModelType): String {
        return when (modelType) {
            LlmManager.ModelType.GEMMA3_1B_IT -> """
                • 模型大小: ~1GB
                • 上下文长度: 1280 tokens
                • 平均延迟: ~300ms
                • 适用场景: 轻量级任务，快速响应
            """.trimIndent()
            
            LlmManager.ModelType.GEMMA3N_E4B_IT -> """
                • 模型大小: ~4.41GB
                • 上下文长度: 2048 tokens
                • 平均延迟: ~250ms
                • 适用场景: 复杂任务，更高精度，多模态支持
                • 技术特性: INT4量化，移动端优化，支持图像输入
            """.trimIndent()
        }
    }
    
    /**
     * 更新多模态UI显示状态
     */
    private fun updateMultimodalUI() {
        val isMultimodal = currentModelType == LlmManager.ModelType.GEMMA3N_E4B_IT
        multimodalLayout.visibility = if (isMultimodal) android.view.View.VISIBLE else android.view.View.GONE
        btnTestMultimodal.visibility = if (isMultimodal) android.view.View.VISIBLE else android.view.View.GONE
        
        // 更新输入提示
        if (isMultimodal) {
            etInput.hint = "输入文本提示词，如：图片上有什么？"
        } else {
            etInput.hint = "输入拼音或文本进行测试..."
        }
    }
    
    /**
     * 选择图像
     */
    private fun selectImage() {
        try {
            imagePickerLauncher.launch("image/*")
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开图像选择器: ${e.message}", Toast.LENGTH_SHORT).show()
            Timber.e(e, "打开图像选择器失败")
        }
    }
    
    /**
     * 处理图像选择结果
     */
    private fun handleImageSelection(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap != null) {
                // 调整图像大小以适应模型要求 (256x256, 512x512, 768x768)
                val resizedBitmap = resizeImageForModel(bitmap)
                selectedImageBitmap = resizedBitmap
                
                // 显示预览
                ivImagePreview.setImageBitmap(resizedBitmap)
                ivImagePreview.visibility = android.view.View.VISIBLE
                btnClearImage.visibility = android.view.View.VISIBLE
                
                Toast.makeText(this, "图像已选择", Toast.LENGTH_SHORT).show()
                Timber.d("图像选择成功，尺寸: ${resizedBitmap.width}x${resizedBitmap.height}")
            } else {
                Toast.makeText(this, "无法加载图像", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "图像处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
            Timber.e(e, "图像处理失败")
        }
    }
    
    /**
     * 调整图像大小以适应模型要求
     */
    private fun resizeImageForModel(bitmap: Bitmap): Bitmap {
        val targetSizes = listOf(256, 512, 768)
        val originalSize = maxOf(bitmap.width, bitmap.height)
        
        // 选择最接近的目标尺寸
        val targetSize = targetSizes.minByOrNull { kotlin.math.abs(it - originalSize) } ?: 512
        
        return Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
    }
    
    /**
     * 清除选择的图像
     */
    private fun clearImage() {
        selectedImageBitmap?.recycle()
        selectedImageBitmap = null
        ivImagePreview.setImageBitmap(null)
        ivImagePreview.visibility = android.view.View.GONE
        btnClearImage.visibility = android.view.View.GONE
        Toast.makeText(this, "图像已清除", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 测试多模态分析（图像+文本）
     */
    private fun testMultimodalAnalysis() {
        val textInput = etInput.text.toString().trim()
        
        if (textInput.isEmpty()) {
            Toast.makeText(this, "请输入文本提示词", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedImageBitmap == null) {
            Toast.makeText(this, "请先选择一张图像", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isAIInitialized) {
            Toast.makeText(this, "AI引擎未初始化", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentModelType != LlmManager.ModelType.GEMMA3N_E4B_IT) {
            Toast.makeText(this, "多模态功能需要Gemma-3n-E4B模型", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                showProgress(true)
                tvStatus.text = "🖼️ 正在分析图像和文本..."
                
                val currentEngine = aiEngineManager.getCurrentEngine()
                if (currentEngine == null) {
                    tvResult.text = "❌ 没有可用的AI引擎"
                    return@launch
                }
                
                // 检查引擎是否支持多模态
                if (currentEngine !is Gemma3nEngine) {
                    tvResult.text = "❌ 当前引擎不支持多模态功能"
                    return@launch
                }
                
                val startTime = System.currentTimeMillis()
                
                // 调用多模态分析
                val response = currentEngine.analyzeImageWithText(selectedImageBitmap!!, textInput)
                
                val endTime = System.currentTimeMillis()
                
                val resultText = buildString {
                    appendLine("🖼️ 多模态分析结果 (耗时: ${endTime - startTime}ms)")
                    appendLine("=".repeat(50))
                    appendLine("📝 文本提示: \"$textInput\"")
                    appendLine("🖼️ 图像尺寸: ${selectedImageBitmap!!.width}x${selectedImageBitmap!!.height}")
                    appendLine()
                    appendLine("🤖 AI分析结果:")
                    appendLine(response)
                }
                
                tvResult.text = resultText
                tvStatus.text = "✅ 多模态分析完成"
                
            } catch (e: Exception) {
                tvResult.text = "❌ 多模态分析失败: ${e.message}"
                tvStatus.text = "❌ 处理失败"
                Timber.e(e, "多模态分析失败")
            } finally {
                showProgress(false)
            }
        }
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