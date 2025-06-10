package com.shenji.aikeyboard.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ai.engines.Gemma3nImageAnalysisEngine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * AI分析窗口Activity
 * 显示屏幕截图分析进度和结果
 */
class AIAnalysisActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AIAnalysisActivity"
        const val EXTRA_SCREENSHOT_PATH = "screenshot_path"
        const val REQUEST_CODE_SCREEN_CAPTURE = 1001
    }
    
    // UI组件
    private lateinit var btnClose: Button
    private lateinit var btnCopy: Button
    private lateinit var ivScreenshot: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView
    private lateinit var scrollView: ScrollView
    
    // AI分析引擎
    private lateinit var analysisEngine: Gemma3nImageAnalysisEngine
    
    // 屏幕截图管理器
    private lateinit var screenCaptureManager: ScreenCaptureManager
    
    // 当前状态
    private var isAnalyzing = false
    private var currentBitmap: Bitmap? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置窗口特性
        setupWindowFlags()
        
        // 检查是否从悬浮窗启动
        val fromFloatingWindow = intent.getBooleanExtra("from_floating_window", false)
        if (fromFloatingWindow) {
            // 如果是从悬浮窗启动，先设置为透明并隐藏悬浮窗
            window.decorView.alpha = 0f
            hideFloatingWindow()
        }
        
        setContentView(R.layout.activity_ai_analysis)
        
        // 隐藏ActionBar
        supportActionBar?.hide()
        
        // 初始化组件
        initializeComponents()
        
        // 设置UI
        setupUI()
        
        // 开始截图流程
        startScreenCapture()
        
        Timber.d("$TAG: AIAnalysisActivity created")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 恢复悬浮窗显示
        restoreFloatingWindow()
        // 清理资源
        currentBitmap?.recycle()
        currentBitmap = null
        screenCaptureManager.release()
        Timber.d("$TAG: AIAnalysisActivity destroyed")
    }
    
    /**
     * 设置窗口标志
     */
    private fun setupWindowFlags() {
        try {
            // 设置窗口为全屏，保持状态栏可见
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            
            // 设置状态栏透明
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            
            // 设置系统UI可见性
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to setup window flags")
        }
    }
    
    /**
     * 初始化组件
     */
    private fun initializeComponents() {
        try {
            // 初始化AI分析引擎
            analysisEngine = Gemma3nImageAnalysisEngine(this)
            
            // 初始化屏幕截图管理器
            screenCaptureManager = ScreenCaptureManager.getInstance(this)
            
            Timber.d("$TAG: Components initialized")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize components")
            finish()
        }
    }
    
    /**
     * 设置UI
     */
    private fun setupUI() {
        try {
            btnClose = findViewById(R.id.btn_close)
            btnCopy = findViewById(R.id.btn_copy)
            ivScreenshot = findViewById(R.id.iv_screenshot)
            progressBar = findViewById(R.id.progress_bar)
            tvStatus = findViewById(R.id.tv_status)
            tvResult = findViewById(R.id.tv_result)
            scrollView = findViewById(R.id.scroll_view)
            
            // 设置关闭按钮
            btnClose.setOnClickListener {
                finish()
            }
            
            // 设置复制按钮
            btnCopy.setOnClickListener {
                copyAnalysisResult()
            }
            
            // 初始状态
            showLoadingState("准备截取屏幕...")
            
            Timber.d("$TAG: UI setup completed")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to setup UI")
            finish()
        }
    }
    
    /**
     * 开始屏幕截图
     */
    private fun startScreenCapture() {
        try {
            // 检查是否已有有效的MediaProjection实例
            if (screenCaptureManager.hasActiveMediaProjection()) {
                Timber.d("$TAG: MediaProjection already initialized, starting capture")
                // 直接截图
                captureAndAnalyze()
            } else {
                // 需要请求权限
                val hasStoredPermission = screenCaptureManager.hasStoredPermission()
                val message = if (hasStoredPermission) {
                    "重新确认屏幕录制权限（Android系统要求）"
                } else {
                    "首次使用需要授权屏幕录制权限"
                }
                
                Timber.d("$TAG: $message")
                showLoadingState(message)
                
                val intent = screenCaptureManager.createScreenCaptureIntent()
                startActivityForResult(intent, REQUEST_CODE_SCREEN_CAPTURE)
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to start screen capture")
            showErrorState("启动屏幕截图失败: ${e.message}")
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        Timber.d("$TAG: onActivityResult - requestCode: $requestCode, resultCode: $resultCode")
        
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                try {
                    Timber.d("$TAG: Screen capture permission granted, initializing MediaProjection")
                    showLoadingState("正在初始化屏幕录制...")
                    
                    // 保存权限状态
                    screenCaptureManager.savePermissionStatus(resultCode, data)
                    
                    // 初始化MediaProjection
                    screenCaptureManager.initializeMediaProjection(resultCode, data)
                    
                    Timber.d("$TAG: MediaProjection initialized, hiding window and starting capture")
                    
                    // 检查是否从悬浮窗启动
                    val fromFloatingWindow = intent.getBooleanExtra("from_floating_window", false)
                    if (fromFloatingWindow) {
                        // 隐藏悬浮窗
                        hideFloatingWindow()
                    }
                    
                    // 隐藏当前窗口，延迟截图，确保截取的是目标界面
                    window.decorView.visibility = View.INVISIBLE
                    
                    tvStatus.postDelayed({
                        Timber.d("$TAG: Starting delayed capture and analyze")
                        captureAndAnalyze()
                    }, 1500) // 延长到1.5秒确保用户返回到目标应用
                    
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Failed to initialize MediaProjection")
                    showErrorState("初始化屏幕录制失败: ${e.message}")
                    // 确保窗口可见
                    window.decorView.visibility = View.VISIBLE
                    window.decorView.alpha = 1f
                }
            } else {
                Timber.w("$TAG: Screen capture permission denied - resultCode: $resultCode")
                showErrorState("用户拒绝了屏幕录制权限，请重新授权")
                // 确保窗口可见
                window.decorView.visibility = View.VISIBLE
                window.decorView.alpha = 1f
            }
        }
    }
    
    /**
     * 截图并分析
     */
    private fun captureAndAnalyze() {
        if (isAnalyzing) {
            Timber.w("$TAG: Already analyzing, skipping")
            return
        }
        
        Timber.d("$TAG: captureAndAnalyze() called")
        
        lifecycleScope.launch {
            try {
                isAnalyzing = true
                Timber.d("$TAG: Starting screen capture process")
                
                runOnUiThread {
                    showLoadingState("正在截取屏幕...")
                }
                
                // 截取屏幕
                Timber.d("$TAG: Calling screenCaptureManager.captureScreen()")
                val bitmap = screenCaptureManager.captureScreen()
                currentBitmap = bitmap
                
                Timber.d("$TAG: Screen captured successfully, bitmap size: ${bitmap.width}x${bitmap.height}")
                
                // 恢复窗口显示并显示截图
                runOnUiThread {
                    Timber.d("$TAG: Restoring window and displaying screenshot")
                    window.decorView.alpha = 1f
                    window.decorView.visibility = View.VISIBLE
                    ivScreenshot.setImageBitmap(bitmap)
                    ivScreenshot.visibility = View.VISIBLE
                    
                    // 恢复悬浮窗显示
                    restoreFloatingWindow()
                }
                
                // 开始AI分析
                Timber.d("$TAG: Starting AI analysis")
                startAIAnalysis(bitmap)
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to capture and analyze")
                runOnUiThread {
                    showErrorState("截图分析失败: ${e.message}")
                    // 确保窗口可见
                    window.decorView.alpha = 1f
                    window.decorView.visibility = View.VISIBLE
                }
            } finally {
                isAnalyzing = false
                Timber.d("$TAG: captureAndAnalyze() finished")
            }
        }
    }
    
    /**
     * 开始AI分析
     */
    private fun startAIAnalysis(bitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                showLoadingState("正在初始化AI模型...")
                
                // 确保AI引擎已初始化
                if (!analysisEngine.isInitialized()) {
                    val initialized = analysisEngine.initialize()
                    if (!initialized) {
                        showErrorState("AI模型初始化失败")
                        return@launch
                    }
                }
                
                showLoadingState("AI正在分析图片内容...")
                
                // 开始流式分析
                analysisEngine.analyzeImageStream(bitmap).collectLatest { chunk ->
                    runOnUiThread {
                        appendAnalysisResult(chunk)
                    }
                }
                
                // 分析完成
                runOnUiThread {
                    showCompletedState()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: AI analysis failed")
                showErrorState("AI分析失败: ${e.message}")
            }
        }
    }
    
    /**
     * 显示加载状态
     */
    private fun showLoadingState(message: String) {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            tvStatus.text = message
            tvStatus.visibility = View.VISIBLE
            tvResult.visibility = View.GONE
        }
    }
    
    /**
     * 显示错误状态
     */
    private fun showErrorState(message: String) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            tvStatus.text = "❌ $message"
            tvStatus.visibility = View.VISIBLE
            tvResult.visibility = View.GONE
        }
    }
    
    /**
     * 显示完成状态
     */
    private fun showCompletedState() {
        progressBar.visibility = View.GONE
        tvStatus.text = "✅ 分析完成"
        btnCopy.isEnabled = true  // 启用复制按钮
    }
    
    /**
     * 复制分析结果到剪贴板
     */
    private fun copyAnalysisResult() {
        try {
            val analysisText = tvResult.text.toString()
            if (analysisText.isNotEmpty()) {
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("AI分析结果", analysisText)
                clipboardManager.setPrimaryClip(clipData)
                
                Toast.makeText(this, "分析结果已复制到剪贴板", Toast.LENGTH_SHORT).show()
                Timber.d("$TAG: Analysis result copied to clipboard")
            } else {
                Toast.makeText(this, "没有可复制的内容", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error copying analysis result")
            Toast.makeText(this, "复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 追加分析结果
     */
    private fun appendAnalysisResult(chunk: String) {
        if (tvResult.visibility == View.GONE) {
            tvResult.visibility = View.VISIBLE
            tvResult.text = ""
        }
        
        // 追加文本
        val currentText = tvResult.text.toString()
        tvResult.text = currentText + chunk
        
        // 自动滚动到底部
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
    
    /**
     * 处理已有截图
     */
    private fun handleExistingScreenshot() {
        try {
            // 从TempBitmapHolder获取截图
            val bitmap = TempBitmapHolder.bitmap
            if (bitmap != null) {
                Timber.d("$TAG: Using existing screenshot from service")
                currentBitmap = bitmap
                
                // 显示截图
                window.decorView.alpha = 1f
                window.decorView.visibility = View.VISIBLE
                ivScreenshot.setImageBitmap(bitmap)
                ivScreenshot.visibility = View.VISIBLE
                
                // 恢复悬浮窗显示
                restoreFloatingWindow()
                
                // 开始AI分析
                startAIAnalysis(bitmap)
            } else {
                Timber.e("$TAG: No screenshot found in TempBitmapHolder")
                showErrorState("未找到截图，请重试")
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error handling existing screenshot")
            showErrorState("处理截图失败: ${e.message}")
        }
    }
    
    /**
     * 隐藏悬浮窗
     */
    private fun hideFloatingWindow() {
        try {
            // 通过广播通知悬浮窗服务隐藏
            val intent = Intent("com.shenji.aikeyboard.HIDE_FLOATING_WINDOW")
            sendBroadcast(intent)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error hiding floating window")
        }
    }
    
    /**
     * 恢复悬浮窗显示
     */
    private fun restoreFloatingWindow() {
        try {
            // 通过广播通知悬浮窗服务恢复显示
            val intent = Intent("com.shenji.aikeyboard.RESTORE_FLOATING_WINDOW")
            sendBroadcast(intent)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error restoring floating window")
        }
    }
} 