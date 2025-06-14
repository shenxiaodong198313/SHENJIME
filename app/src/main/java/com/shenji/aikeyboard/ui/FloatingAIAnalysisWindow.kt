package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ai.engines.Gemma3nImageAnalysisEngine
import com.shenji.aikeyboard.assists.service.EnhancedAssistsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * AI屏幕分析悬浮窗口
 * 基于DraggableFloatingWindow实现可拖拽的AI屏幕分析功能
 */
class FloatingAIAnalysisWindow(
    context: Context,
    private val coroutineScope: CoroutineScope
) : DraggableFloatingWindow(context) {
    
    companion object {
        private const val TAG = "FloatingAIAnalysisWindow"
    }
    
    // UI组件
    private var btnCopy: Button? = null
    private var ivScreenshot: ImageView? = null
    private var progressBar: ProgressBar? = null
    private var tvStatus: TextView? = null
    private var tvResult: TextView? = null
    private var scrollView: ScrollView? = null
    
    // AI分析引擎
    private var analysisEngine: Gemma3nImageAnalysisEngine? = null
    
    // 当前状态
    private var isAnalyzing = false
    private var currentBitmap: Bitmap? = null
    private var analysisJob: Job? = null
    
    /**
     * 初始化窗口
     */
    fun initializeWindow() {
        initialize()
        setupContentView()
        initializeComponents()
        setupUI()
    }
    
    /**
     * 开始AI屏幕分析
     */
    fun startAnalysis() {
        show()
        startScreenCapture()
    }
    
    /**
     * 设置内容视图
     */
    private fun setupContentView() {
        setContentView(R.layout.content_ai_analysis)
    }
    
    /**
     * 初始化组件
     */
    private fun initializeComponents() {
        try {
            analysisEngine = Gemma3nImageAnalysisEngine(context)
            Timber.d("$TAG: Components initialized")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize components")
        }
    }
    
    /**
     * 设置UI
     */
    private fun setupUI() {
        val contentView = getContentView() ?: return
        
        try {
            btnCopy = contentView.findViewById(R.id.btn_copy)
            ivScreenshot = contentView.findViewById(R.id.iv_screenshot)
            progressBar = contentView.findViewById(R.id.progress_bar)
            tvStatus = contentView.findViewById(R.id.tv_status)
            tvResult = contentView.findViewById(R.id.tv_result)
            scrollView = contentView.findViewById(R.id.scroll_view)
            
            // 设置复制按钮
            btnCopy?.setOnClickListener {
                copyAnalysisResult()
            }
            
            // 初始状态
            showLoadingState("准备截取屏幕...")
            
            Timber.d("$TAG: UI setup completed")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to setup UI")
        }
    }
    
    /**
     * 开始屏幕截图
     */
    private fun startScreenCapture() {
        try {
            // 检查无障碍服务是否可用（Android 11+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && EnhancedAssistsService.isServiceEnabled()) {
                Timber.d("$TAG: Using accessibility service for screenshot")
                showLoadingState("正在通过无障碍服务截取屏幕...")
                
                // 隐藏当前窗口
                hide()
                
                // 延迟截图，确保截取的是目标界面
                coroutineScope.launch {
                    kotlinx.coroutines.delay(1500) // 延迟1.5秒确保用户返回到目标界面
                    
                    EnhancedAssistsService.takeScreenshotViaAccessibility { bitmap ->
                        coroutineScope.launch(Dispatchers.Main) {
                            if (bitmap != null) {
                                currentBitmap = bitmap
                                
                                // 恢复窗口显示并显示截图
                                show()
                                ivScreenshot?.setImageBitmap(bitmap)
                                ivScreenshot?.visibility = View.VISIBLE
                                
                                // 开始AI分析
                                startAIAnalysis(bitmap)
                            } else {
                                Timber.w("$TAG: Accessibility screenshot failed")
                                show()
                                showErrorState("无障碍截图失败")
                            }
                        }
                    }
                }
                
                return
            }
            
            // 如果无障碍服务不可用，显示错误信息
            showErrorState("需要开启无障碍服务才能使用截图功能")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error in startScreenCapture")
            showErrorState("截图功能启动失败: ${e.message}")
        }
    }
    
    /**
     * 开始AI分析
     */
    private fun startAIAnalysis(bitmap: Bitmap) {
        if (isAnalyzing) {
            Timber.w("$TAG: Already analyzing, skipping")
            return
        }
        
        analysisJob = coroutineScope.launch {
            try {
                isAnalyzing = true
                showLoadingState("正在初始化AI模型...")
                
                // 确保AI引擎已初始化
                if (analysisEngine?.isInitialized() != true) {
                    val initialized = analysisEngine?.initialize()
                    if (initialized != true) {
                        showErrorState("AI模型初始化失败")
                        return@launch
                    }
                }
                
                showLoadingState("AI正在分析图片内容...")
                
                // 开始流式分析
                analysisEngine?.analyzeImageStream(bitmap)?.collectLatest { chunk ->
                    coroutineScope.launch(Dispatchers.Main) {
                        appendAnalysisResult(chunk)
                    }
                }
                
                // 分析完成
                coroutineScope.launch(Dispatchers.Main) {
                    showCompletedState()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: AI analysis failed")
                coroutineScope.launch(Dispatchers.Main) {
                    showErrorState("AI分析失败: ${e.message}")
                }
            } finally {
                isAnalyzing = false
            }
        }
    }
    
    /**
     * 显示加载状态
     */
    private fun showLoadingState(message: String) {
        progressBar?.visibility = View.VISIBLE
        tvStatus?.text = message
        tvStatus?.visibility = View.VISIBLE
        tvResult?.visibility = View.GONE
    }
    
    /**
     * 显示错误状态
     */
    private fun showErrorState(message: String) {
        progressBar?.visibility = View.GONE
        tvStatus?.text = "❌ $message"
        tvStatus?.visibility = View.VISIBLE
        tvResult?.visibility = View.GONE
    }
    
    /**
     * 显示完成状态
     */
    private fun showCompletedState() {
        progressBar?.visibility = View.GONE
        tvStatus?.text = "✅ 分析完成"
        btnCopy?.isEnabled = true
    }
    
    /**
     * 追加分析结果
     */
    private fun appendAnalysisResult(chunk: String) {
        if (tvResult?.visibility == View.GONE) {
            tvResult?.visibility = View.VISIBLE
            tvResult?.text = ""
        }
        
        // 追加文本
        val currentText = tvResult?.text.toString()
        tvResult?.text = currentText + chunk
        
        // 自动滚动到底部
        scrollView?.post {
            scrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }
    
    /**
     * 复制分析结果到剪贴板
     */
    private fun copyAnalysisResult() {
        try {
            val analysisText = tvResult?.text.toString()
            if (analysisText.isNotEmpty()) {
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("AI分析结果", analysisText)
                clipboardManager.setPrimaryClip(clipData)
                
                Toast.makeText(context, "分析结果已复制到剪贴板", Toast.LENGTH_SHORT).show()
                Timber.d("$TAG: Analysis result copied to clipboard")
            } else {
                Toast.makeText(context, "没有可复制的内容", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error copying analysis result")
            Toast.makeText(context, "复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 窗口关闭时回调
     */
    override fun onWindowClosed() {
        try {
            // 取消正在进行的分析任务
            analysisJob?.cancel()
            analysisJob = null
            
            // 清理资源
            currentBitmap?.recycle()
            currentBitmap = null
            
            // 重置状态
            isAnalyzing = false
            
            Timber.d("$TAG: Window closed and resources cleaned up")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error during window cleanup")
        }
    }
} 