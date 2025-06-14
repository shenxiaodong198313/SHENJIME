package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Html
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.assists.AssistsManager
import com.ven.assists.AssistsCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Assists框架分析悬浮窗口
 * 基于DraggableFloatingWindow实现可拖拽的Assists框架分析功能
 */
class FloatingAssistsAnalysisWindow(
    context: Context,
    private val coroutineScope: CoroutineScope
) : DraggableFloatingWindow(context) {
    
    companion object {
        private const val TAG = "FloatingAssistsAnalysisWindow"
    }
    
    // UI组件
    private var btnCopy: Button? = null
    private var progressBar: ProgressBar? = null
    private var tvStatusHint: TextView? = null
    private var tvTextNodes: TextView? = null
    private var scrollView: ScrollView? = null
    
    // 当前状态
    private var isAnalyzing = false
    private var analysisJob: Job? = null
    
    /**
     * 初始化窗口
     */
    fun initializeWindow() {
        initialize()
        setupContentView()
        setupUI()
    }
    
    /**
     * 开始Assists框架分析
     */
    fun startAnalysis() {
        show()
        startAssistsAnalysis()
    }
    
    /**
     * 设置内容视图
     */
    private fun setupContentView() {
        setContentView(R.layout.content_assists_analysis)
    }
    
    /**
     * 设置UI
     */
    private fun setupUI() {
        val contentView = getContentView() ?: return
        
        try {
            btnCopy = contentView.findViewById(R.id.btn_copy)
            progressBar = contentView.findViewById(R.id.progress_bar)
            tvStatusHint = contentView.findViewById(R.id.tv_status_hint)
            tvTextNodes = contentView.findViewById(R.id.tv_text_nodes)
            scrollView = contentView.findViewById(R.id.scroll_view)
            
            // 设置复制按钮
            btnCopy?.setOnClickListener {
                copyAnalysisResult()
            }
            
            // 初始状态
            showLoadingState("准备开始Assists框架分析...")
            
            Timber.d("$TAG: UI setup completed")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to setup UI")
        }
    }
    
    /**
     * 开始Assists框架分析
     */
    private fun startAssistsAnalysis() {
        if (isAnalyzing) {
            Timber.w("$TAG: Already analyzing, skipping")
            return
        }
        
        analysisJob = coroutineScope.launch {
            try {
                isAnalyzing = true
                showLoadingState("正在分析Assists框架...")
                
                val sb = StringBuilder()
                
                // 1. 基础信息
                sb.append("<font color='#4CAF50'><b>=== 基础信息 ===</b></font><br>")
                try {
                    val serviceEnabled = AssistsManager.isAccessibilityServiceEnabled()
                    sb.append("无障碍服务状态: ${if (serviceEnabled) "已启用" else "未启用"}<br>")
                    
                    val rootNode = com.ven.assists.service.AssistsService.instance?.rootInActiveWindow
                    sb.append("根节点: ${if (rootNode != null) "已获取" else "未获取"}<br>")
                    
                    val packageName = AssistsCore.getPackageName()
                    sb.append("当前包名: $packageName<br>")
                } catch (e: Exception) {
                    sb.append("基础信息获取失败: ${e.message}<br>")
                }
                sb.append("<br>")
                
                // 更新UI显示进度
                withContext(Dispatchers.Main) {
                    tvStatusHint?.text = "正在分析节点信息..."
                    tvTextNodes?.text = Html.fromHtml(sb.toString())
                }
                
                // 2. 所有节点
                sb.append("<font color='#2196F3'><b>=== 所有节点 ===</b></font><br>")
                try {
                    val allNodes = AssistsManager.getAllNodes()
                    sb.append("节点总数: ${allNodes.size}<br>")
                    val limitedNodes = allNodes.take(10)
                    limitedNodes.forEachIndexed { i, node ->
                        sb.append("${i+1}. 类型: ${node.className}  文本: ${node.text}  id: ${node.viewIdResourceName}<br>")
                    }
                    if (allNodes.size > 10) sb.append("...<br>")
                } catch (e: Exception) {
                    sb.append("获取所有节点失败: ${e.message}<br>")
                }
                sb.append("<br>")
                
                // 更新UI显示进度
                withContext(Dispatchers.Main) {
                    tvStatusHint?.text = "正在分析文本节点..."
                    tvTextNodes?.text = Html.fromHtml(sb.toString())
                }
                
                // 3. 文本节点
                sb.append("<font color='#9C27B0'><b>=== 文本节点 ===</b></font><br>")
                try {
                    val textNodesList = mutableListOf<String>()
                    AssistsManager.getAllTextNodes { nodes ->
                        textNodesList.addAll(nodes)
                    }
                    // 等待一下让回调执行
                    delay(100)
                    
                    if (textNodesList.isNotEmpty()) {
                        sb.append("文本节点数: ${textNodesList.size}<br>")
                        val limitedTextNodes = textNodesList.take(20)
                        limitedTextNodes.forEachIndexed { i, text ->
                            sb.append("${i+1}. $text<br>")
                        }
                        if (textNodesList.size > 20) sb.append("...<br>")
                    } else {
                        sb.append("未发现文本节点<br>")
                    }
                } catch (e: Exception) {
                    sb.append("文本节点采集失败: ${e.message}<br>")
                }
                sb.append("<br>")
                
                // 更新UI显示进度
                withContext(Dispatchers.Main) {
                    tvStatusHint?.text = "正在进行查找测试..."
                    tvTextNodes?.text = Html.fromHtml(sb.toString())
                }
                
                // 4. 查找测试
                sb.append("<font color='#FF9800'><b>=== 查找测试 ===</b></font><br>")
                try {
                    val found = AssistsManager.findNodesById("android:id/content")
                    sb.append("通过id查找(android:id/content): ${found.size} 个<br>")
                } catch (e: Exception) {
                    sb.append("查找测试失败: ${e.message}<br>")
                }
                sb.append("<br>")
                
                // 5. Core测试
                sb.append("<font color='#F44336'><b>=== Core测试 ===</b></font><br>")
                try {
                    val allNodes = AssistsCore.getAllNodes()
                    val clickable = allNodes.count { it.isClickable }
                    val editable = allNodes.count { it.isEditable }
                    val scrollable = allNodes.count { it.isScrollable }
                    sb.append("可点击: $clickable  可编辑: $editable  可滚动: $scrollable<br>")
                } catch (e: Exception) {
                    sb.append("Core测试失败: ${e.message}<br>")
                }
                sb.append("<br>")
                
                // 6. 服务详情
                sb.append("<font color='#607D8B'><b>=== 服务详情 ===</b></font><br>")
                try {
                    val statusInfo = AssistsManager.isAccessibilityServiceEnabled()
                    sb.append("Assists无障碍服务: ${if (statusInfo) "已启用" else "未启用"}<br>")
                } catch (e: Exception) {
                    sb.append("服务详情获取失败: ${e.message}<br>")
                }
                
                // 更新UI显示最终结果
                withContext(Dispatchers.Main) {
                    showCompletedState()
                    tvTextNodes?.text = Html.fromHtml(sb.toString())
                    
                    // 自动滚动到顶部
                    scrollView?.post {
                        scrollView?.scrollTo(0, 0)
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Assists analysis failed")
                withContext(Dispatchers.Main) {
                    showErrorState("Assists分析失败: ${e.message}")
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
        tvStatusHint?.text = message
        tvStatusHint?.visibility = View.VISIBLE
    }
    
    /**
     * 显示错误状态
     */
    private fun showErrorState(message: String) {
        progressBar?.visibility = View.GONE
        tvStatusHint?.text = "❌ $message"
        tvStatusHint?.visibility = View.VISIBLE
    }
    
    /**
     * 显示完成状态
     */
    private fun showCompletedState() {
        progressBar?.visibility = View.GONE
        tvStatusHint?.text = "✅ Assists分析完成，已分组展示"
        btnCopy?.isEnabled = true
    }
    
    /**
     * 复制分析结果到剪贴板
     */
    private fun copyAnalysisResult() {
        try {
            val analysisText = tvTextNodes?.text.toString()
            if (analysisText.isNotEmpty()) {
                // 移除HTML标签
                val plainText = Html.fromHtml(analysisText).toString()
                
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("Assists分析结果", plainText)
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
            
            // 重置状态
            isAnalyzing = false
            
            Timber.d("$TAG: Window closed and resources cleaned up")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error during window cleanup")
        }
    }
} 