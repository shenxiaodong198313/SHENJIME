package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.assists.AssistsManager
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
 * 使用assists框架显示过滤后的微信对话内容
 */
class FloatingWeChatAutoChatWindow(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    
    companion object {
        private const val TAG = "FloatingWeChatAutoChatWindow"
    }
    
    // UI组件
    private var contentView: View? = null
    private var tvAnalysisResult: TextView? = null
    private var scrollView: ScrollView? = null
    private var btnRefresh: Button? = null
    private var btnCopy: Button? = null
    private var btnClose: Button? = null
    
    // Assists窗口包装器
    private var windowWrapper: AssistsWindowWrapper? = null
    
    // 当前状态
    private var analysisJob: Job? = null
    private var currentAnalysisText = ""
    
    /**
     * 初始化窗口
     */
    fun initializeWindow() {
        try {
            createContentView()
            setupAssistsWindow()
            setupUI()
            Timber.d("$TAG: Window initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize window")
        }
    }
    
    /**
     * 显示窗口并开始分析
     */
    fun showAndAnalyze() {
        try {
            windowWrapper?.let { wrapper ->
                if (!AssistsWindowManager.contains(wrapper)) {
                    AssistsWindowManager.add(wrapper)
                }
                startWeChatConversationAnalysis()
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to show window")
        }
    }
    
    /**
     * 关闭窗口
     */
    fun close() {
        try {
            analysisJob?.cancel()
            windowWrapper?.let { wrapper ->
                AssistsWindowManager.removeView(wrapper.getView())
            }
            windowWrapper = null
            Timber.d("$TAG: Window closed")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error closing window")
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
                    width = (context.resources.displayMetrics.widthPixels * 0.9).toInt()
                    height = (context.resources.displayMetrics.heightPixels * 0.8).toInt()
                }
            ).apply {
                showOption = true
                showBackground = true
                initialCenter = true
            }
        }
    }
    
    /**
     * 设置UI组件
     */
    private fun setupUI() {
        contentView?.let { view ->
            try {
                tvAnalysisResult = view.findViewById(R.id.tv_analysis_result)
                scrollView = view.findViewById(R.id.scroll_view)
                btnRefresh = view.findViewById(R.id.btn_refresh)
                btnCopy = view.findViewById(R.id.btn_copy)
                btnClose = view.findViewById(R.id.btn_close)
                
                // 设置按钮点击事件
                btnRefresh?.setOnClickListener {
                    refreshAnalysis()
                }
                
                btnCopy?.setOnClickListener {
                    copyAnalysisResult()
                }
                
                btnClose?.setOnClickListener {
                    close()
                }
                
                // 初始显示
                showLoadingState()
                
                Timber.d("$TAG: UI setup completed")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to setup UI")
            }
        }
    }
    
    /**
     * 开始微信对话分析
     */
    private fun startWeChatConversationAnalysis() {
        if (analysisJob?.isActive == true) {
            Timber.w("$TAG: Analysis already in progress")
            return
        }
        
        analysisJob = coroutineScope.launch {
            try {
                showLoadingState()
                
                // 检查assists服务状态
                if (!AssistsManager.isAccessibilityServiceEnabled()) {
                    showErrorState("Assists无障碍服务未启用\n请在设置中开启无障碍服务")
                    return@launch
                }
                
                // 在IO线程中执行数据过滤
                val filteredData = withContext(Dispatchers.IO) {
                    WeChatConversationFilter.filterWeChatConversation()
                }
                
                // 格式化显示文本
                val displayText = WeChatConversationFilter.formatConversationForDisplay(filteredData)
                
                // 在主线程中更新UI
                withContext(Dispatchers.Main) {
                    showAnalysisResult(displayText)
                }
                
                Timber.d("$TAG: WeChat conversation analysis completed")
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: WeChat conversation analysis failed")
                withContext(Dispatchers.Main) {
                    showErrorState("分析微信对话时出错: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 刷新分析
     */
    private fun refreshAnalysis() {
        Timber.d("$TAG: Refreshing analysis")
        startWeChatConversationAnalysis()
    }
    
    /**
     * 显示加载状态
     */
    private fun showLoadingState() {
        currentAnalysisText = "正在分析微信对话内容...\n\n请确保：\n1. 已开启Assists无障碍服务\n2. 当前在微信聊天界面\n3. 聊天界面有对话内容"
        tvAnalysisResult?.text = currentAnalysisText
        btnCopy?.isEnabled = false
    }
    
    /**
     * 显示错误状态
     */
    private fun showErrorState(message: String) {
        currentAnalysisText = "❌ $message"
        tvAnalysisResult?.text = currentAnalysisText
        btnCopy?.isEnabled = false
    }
    
    /**
     * 显示分析结果
     */
    private fun showAnalysisResult(result: String) {
        currentAnalysisText = result
        tvAnalysisResult?.text = result
        btnCopy?.isEnabled = true
        
        // 自动滚动到顶部
        scrollView?.post {
            scrollView?.scrollTo(0, 0)
        }
    }
    
    /**
     * 复制分析结果
     */
    private fun copyAnalysisResult() {
        try {
            if (currentAnalysisText.isNotEmpty()) {
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("微信对话分析结果", currentAnalysisText)
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
} 