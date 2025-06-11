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
import com.shenji.aikeyboard.ai.engines.Gemma3nWeChatAnalysisEngine
import com.shenji.aikeyboard.ai.engines.WeChatAnalysisResult
import com.shenji.aikeyboard.utils.AutofillAccessibilityService
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.os.Build
import timber.log.Timber

/**
 * 微信对话分析窗口Activity
 * 专门用于分析微信对话内容，提取对话摘要和建议回复
 */
class WeChatAnalysisActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "WeChatAnalysisActivity"
        const val REQUEST_CODE_SCREEN_CAPTURE = 2001
    }
    
    // UI组件
    private lateinit var btnClose: Button
    private lateinit var btnCopy: Button
    private lateinit var ivScreenshot: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvContactName: TextView
    private lateinit var tvSummary: TextView
    private lateinit var tvSuggestion: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var sendReplyButton: Button
    
    // AI分析引擎
    private lateinit var analysisEngine: Gemma3nWeChatAnalysisEngine
    
    // 屏幕截图管理器
    private lateinit var screenCaptureManager: ScreenCaptureManager
    
    // 当前状态
    private var isAnalyzing = false
    private var currentBitmap: Bitmap? = null
    private var currentResult = WeChatAnalysisResult()
    private var fullResponseText = StringBuilder()
    
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
        
        setContentView(R.layout.activity_wechat_analysis)
        
        // 隐藏ActionBar
        supportActionBar?.hide()
        
        // 初始化组件
        initializeComponents()
        
        // 设置UI
        setupUI()
        
        // 开始截图流程
        startScreenCapture()
        
        Timber.d("$TAG: WeChatAnalysisActivity created")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 恢复悬浮窗显示
        restoreFloatingWindow()
        // 清理资源
        currentBitmap?.recycle()
        currentBitmap = null
        screenCaptureManager.release()
        Timber.d("$TAG: WeChatAnalysisActivity destroyed")
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
            analysisEngine = Gemma3nWeChatAnalysisEngine()
            
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
            tvContactName = findViewById(R.id.tv_contact_name)
            tvSummary = findViewById(R.id.tv_summary)
            tvSuggestion = findViewById(R.id.tv_suggestion)
            scrollView = findViewById(R.id.scroll_view)
            sendReplyButton = findViewById(R.id.send_reply_button)
            
            // 设置关闭按钮
            btnClose.setOnClickListener {
                finish()
            }
            
            // 设置复制按钮
            btnCopy.setOnClickListener {
                copyAnalysisResult()
            }
            
            // 设置发送回复建议按钮
            sendReplyButton.setOnClickListener {
                sendReplySuggestion()
            }
            
            // 初始状态
            showLoadingState("准备截取微信对话...")
            
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
            // 检查是否从悬浮窗启动
            val fromFloatingWindow = intent.getBooleanExtra("from_floating_window", false)
            
            // 检查无障碍服务是否可用（Android 11+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && AutofillAccessibilityService.isServiceEnabled(this)) {
                Timber.d("$TAG: Using accessibility service for screenshot")
                showLoadingState("正在通过无障碍服务截取微信对话...")
                
                // 检查是否从悬浮窗启动
                if (fromFloatingWindow) {
                    hideFloatingWindow()
                }
                
                // 隐藏当前窗口
                window.decorView.visibility = View.INVISIBLE
                
                // 延迟截图，确保截取的是微信界面
                tvStatus.postDelayed({
                    AutofillAccessibilityService.takeScreenshotViaAccessibility { bitmap ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            if (bitmap != null) {
                                currentBitmap = bitmap
                                
                                // 恢复窗口显示并显示截图
                                window.decorView.alpha = 1f
                                window.decorView.visibility = View.VISIBLE
                                ivScreenshot.setImageBitmap(bitmap)
                                ivScreenshot.visibility = View.VISIBLE
                                
                                // 恢复悬浮窗显示
                                restoreFloatingWindow()
                                
                                // 开始微信对话分析
                                startWeChatAnalysis(bitmap)
                            } else {
                                Timber.w("$TAG: Accessibility screenshot failed, falling back to MediaProjection")
                                showErrorState("无障碍截图失败")
                                // 确保窗口可见
                                window.decorView.alpha = 1f
                                window.decorView.visibility = View.VISIBLE
                                restoreFloatingWindow()
                            }
                        }
                    }
                }, 1500) // 延迟1.5秒确保用户返回到微信
                
                return
            }
            
            // 降级到MediaProjection方式
            Timber.d("$TAG: Using MediaProjection for screenshot")
            
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
                    
                    // 隐藏悬浮窗和当前窗口
                    hideFloatingWindow()
                    window.decorView.visibility = View.INVISIBLE
                    
                    tvStatus.postDelayed({
                        Timber.d("$TAG: Starting delayed capture and analyze")
                        captureAndAnalyze()
                    }, 1500) // 延长到1.5秒确保用户返回到微信界面
                    
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Failed to initialize MediaProjection")
                    showErrorState("初始化屏幕录制失败: ${e.message}")
                    window.decorView.visibility = View.VISIBLE
                    window.decorView.alpha = 1f
                }
            } else {
                Timber.w("$TAG: Screen capture permission denied - resultCode: $resultCode")
                showErrorState("用户拒绝了屏幕录制权限，请重新授权")
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
                    showLoadingState("正在截取微信对话...")
                }
                
                // 截取屏幕
                val bitmap = screenCaptureManager.captureScreen()
                currentBitmap = bitmap
                
                Timber.d("$TAG: Screen captured successfully, bitmap size: ${bitmap.width}x${bitmap.height}")
                
                // 恢复窗口显示并显示截图
                runOnUiThread {
                    window.decorView.alpha = 1f
                    window.decorView.visibility = View.VISIBLE
                    ivScreenshot.setImageBitmap(bitmap)
                    ivScreenshot.visibility = View.VISIBLE
                    
                    // 恢复悬浮窗显示
                    restoreFloatingWindow()
                }
                
                // 开始AI分析
                startWeChatAnalysis(bitmap)
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to capture and analyze")
                runOnUiThread {
                    showErrorState("截图分析失败: ${e.message}")
                    window.decorView.alpha = 1f
                    window.decorView.visibility = View.VISIBLE
                }
            } finally {
                isAnalyzing = false
            }
        }
    }
    
    /**
     * 开始微信对话分析
     */
    private fun startWeChatAnalysis(bitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                showLoadingState("正在初始化AI模型...")
                
                // 初始化AI引擎
                if (!analysisEngine.isInitialized()) {
                    val initialized = analysisEngine.initialize(this@WeChatAnalysisActivity)
                    if (!initialized) {
                        showErrorState("AI模型初始化失败")
                        return@launch
                    }
                }
                
                showLoadingState("AI正在分析微信对话...")
                
                // 开始流式分析
                analysisEngine.analyzeWeChatConversationStream(bitmap).collect { chunk ->
                    fullResponseText.append(chunk)
                    
                    // 实时解析并更新UI
                    val parsedResult = analysisEngine.parseAnalysisResult(fullResponseText.toString())
                    runOnUiThread {
                        updateAnalysisResult(parsedResult)
                        
                        // 检查是否分析完成
                        if (isAnalysisComplete(parsedResult)) {
                            enableButtons()
                        }
                    }
                }
                
                // 分析完成
                runOnUiThread {
                    showCompletedState()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: WeChat analysis failed")
                showErrorState("微信对话分析失败: ${e.message}")
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
        }
    }
    
    /**
     * 显示完成状态
     */
    private fun showCompletedState() {
        progressBar.visibility = View.GONE
        tvStatus.text = "✅ 分析完成"
        btnCopy.isEnabled = true
        sendReplyButton.isEnabled = true
    }
    
    /**
     * 更新分析结果
     */
    private fun updateAnalysisResult(result: WeChatAnalysisResult) {
        currentResult = result
        
        // 更新联系人名称
        if (result.contactName.isNotEmpty() && result.contactName != "联系人") {
            tvContactName.text = result.contactName
            tvContactName.visibility = View.VISIBLE
        }
        
        // 更新对话摘要
        if (result.summary.isNotEmpty()) {
            tvSummary.text = result.summary
            tvSummary.visibility = View.VISIBLE
        }
        
        // 更新建议回复
        if (result.suggestion.isNotEmpty()) {
            tvSuggestion.text = result.suggestion
            tvSuggestion.visibility = View.VISIBLE
        }
        
        // 自动滚动到底部
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
    
    /**
     * 检查分析是否完成
     */
    private fun isAnalysisComplete(result: WeChatAnalysisResult): Boolean {
        return result.contactName.isNotEmpty() && 
               result.summary.isNotEmpty() && 
               !result.summary.contains("正在分析") &&
               result.suggestion.isNotEmpty() && 
               !result.suggestion.contains("正在生成")
    }
    
    /**
     * 启用按钮
     */
    private fun enableButtons() {
        btnCopy.isEnabled = true
        sendReplyButton.isEnabled = true
    }
    
    /**
     * 复制分析结果到剪贴板
     */
    private fun copyAnalysisResult() {
        try {
            val contactName = tvContactName.text.toString()
            val summary = tvSummary.text.toString()
            val suggestion = tvSuggestion.text.toString()
            
            val fullResult = buildString {
                if (contactName.isNotEmpty()) {
                    append("联系人：$contactName\n\n")
                }
                if (summary.isNotEmpty()) {
                    append("对话摘要：\n$summary\n\n")
                }
                if (suggestion.isNotEmpty()) {
                    append("建议回复：\n$suggestion")
                }
            }
            
            if (fullResult.isNotEmpty()) {
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("微信对话分析结果", fullResult)
                clipboardManager.setPrimaryClip(clipData)
                
                Toast.makeText(this, "分析结果已复制到剪贴板", Toast.LENGTH_SHORT).show()
                Timber.d("$TAG: WeChat analysis result copied to clipboard")
            } else {
                Toast.makeText(this, "没有可复制的内容", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error copying analysis result")
            Toast.makeText(this, "复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 发送回复建议（只复制回复内容）
     */
    private fun sendReplySuggestion() {
        if (currentResult.suggestion.isNotEmpty() && !currentResult.suggestion.contains("正在生成")) {
            copyToClipboard("回复建议", currentResult.suggestion)
            Toast.makeText(this, "已复制回复建议，可直接发送", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "回复建议尚未生成完成", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 复制文本到剪贴板
     */
    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }
    
    /**
     * 隐藏悬浮窗
     */
    private fun hideFloatingWindow() {
        try {
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
            val intent = Intent("com.shenji.aikeyboard.RESTORE_FLOATING_WINDOW")
            sendBroadcast(intent)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error restoring floating window")
        }
    }
}

 