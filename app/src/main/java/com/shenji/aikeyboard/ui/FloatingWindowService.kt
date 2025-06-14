package com.shenji.aikeyboard.ui

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.shenji.aikeyboard.R
import timber.log.Timber
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import com.shenji.aikeyboard.assists.service.EnhancedAssistsService

/**
 * 悬浮窗服务
 * 提供悬浮按钮和展开菜单功能
 */
class FloatingWindowService : Service() {
    
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var menuView: View? = null
    private var isMenuExpanded = false
    
    // 悬浮按钮相关视图
    private var floatingButton: ImageView? = null
    private var menuLayout: LinearLayout? = null
    private var aiAnalysisButton: LinearLayout? = null
    private var aiWeChatAnalysisButton: LinearLayout? = null
    private var weChatAutoChatButton: LinearLayout? = null
    private var accessibilityAnalysisButton: LinearLayout? = null
    private var settingsButton: LinearLayout? = null
    
    // 触摸相关变量
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    // 屏幕截图管理器
    private lateinit var screenCaptureManager: ScreenCaptureManager
    
    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 广播接收器
    private val floatingWindowReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.shenji.aikeyboard.HIDE_FLOATING_WINDOW" -> {
                    floatingView?.visibility = View.INVISIBLE
                    Timber.d("$TAG: Floating window hidden via broadcast")
                }
                "com.shenji.aikeyboard.RESTORE_FLOATING_WINDOW" -> {
                    floatingView?.visibility = View.VISIBLE
                    Timber.d("$TAG: Floating window restored via broadcast")
                }
            }
        }
    }
    
    companion object {
        private const val TAG = "FloatingWindowService"
        
        /**
         * 启动悬浮窗服务
         */
        fun startService(context: Context) {
            if (canDrawOverlays(context)) {
                val intent = Intent(context, FloatingWindowService::class.java)
                context.startService(intent)
            } else {
                Toast.makeText(context, "请先授权悬浮窗权限", Toast.LENGTH_SHORT).show()
            }
        }
        
        /**
         * 停止悬浮窗服务
         */
        fun stopService(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.stopService(intent)
        }
        
        /**
         * 检查是否有悬浮窗权限
         */
        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
        
        /**
         * 请求悬浮窗权限
         */
        fun requestOverlayPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("$TAG: FloatingWindowService onCreate")
        
        if (!canDrawOverlays(this)) {
            Timber.w("$TAG: No overlay permission, stopping service")
            stopSelf()
            return
        }
        
        // 启动前台服务
        startForegroundService()
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingView()
        
        // 注册广播接收器（兼容Android 14+）
        val filter = IntentFilter().apply {
            addAction("com.shenji.aikeyboard.HIDE_FLOATING_WINDOW")
            addAction("com.shenji.aikeyboard.RESTORE_FLOATING_WINDOW")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(floatingWindowReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(floatingWindowReceiver, filter)
        }
        
        // 初始化屏幕截图管理器
        screenCaptureManager = ScreenCaptureManager.getInstance(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("$TAG: FloatingWindowService onStartCommand")
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("$TAG: FloatingWindowService onDestroy")
        
        // 关闭悬浮分析窗口
        floatingAIAnalysisWindow?.close()
        floatingWeChatAnalysisWindow?.close()
        floatingWeChatAutoChatWindow?.close()
        floatingAssistsAnalysisWindow?.close()
        floatingAIAnalysisWindow = null
        floatingWeChatAnalysisWindow = null
        floatingWeChatAutoChatWindow = null
        floatingAssistsAnalysisWindow = null
        
        // 取消注册广播接收器
        try {
            unregisterReceiver(floatingWindowReceiver)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error unregistering broadcast receiver")
        }
        
        removeFloatingView()
    }
    
    /**
     * 创建悬浮视图
     */
    private fun createFloatingView() {
        try {
            // 创建悬浮按钮视图
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button_layout, null)
            
            // 获取视图组件
            floatingButton = floatingView?.findViewById(R.id.floating_button)
            menuLayout = floatingView?.findViewById(R.id.menu_layout)
            aiAnalysisButton = floatingView?.findViewById(R.id.ai_analysis_button)
            aiWeChatAnalysisButton = floatingView?.findViewById(R.id.ai_wechat_analysis_button)
            weChatAutoChatButton = floatingView?.findViewById(R.id.wechat_auto_chat_button)
            accessibilityAnalysisButton = floatingView?.findViewById(R.id.accessibility_analysis_button)
            settingsButton = floatingView?.findViewById(R.id.settings_button)
            
            // 设置点击事件
            setupClickListeners()
            
            // 设置触摸事件
            setupTouchListener()
            
            // 配置窗口参数
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            
            // 设置初始位置（屏幕右侧中间，确保菜单展开时有足够空间）
            params.gravity = Gravity.TOP or Gravity.START
            // 给菜单留出足够的空间（菜单宽度约150dp + 安全边距20dp）
            params.x = getScreenWidth() - dpToPx(56 + 20)  // 悬浮按钮宽度56dp + 右边距20dp
            params.y = getScreenHeight() / 2 - dpToPx(35)  // 减去按钮高度的一半来居中
            
            // 添加到窗口管理器
            windowManager?.addView(floatingView, params)
            
            Timber.d("$TAG: Floating view created and added")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error creating floating view")
        }
    }
    
    /**
     * 设置点击事件监听器
     */
    private fun setupClickListeners() {
        // AI界面分析按钮点击事件
        aiAnalysisButton?.setOnClickListener {
            Timber.d("$TAG: AI Interface Analysis clicked")
            handleAIAnalysisClick()
            collapseMenu()
        }
        
        // AI微信对话分析按钮点击事件
        aiWeChatAnalysisButton?.setOnClickListener {
            Timber.d("$TAG: AI WeChat Analysis clicked")
            handleWeChatAnalysisClick()
            collapseMenu()
        }
        
        // 微信AI自动聊天按钮点击事件
        weChatAutoChatButton?.setOnClickListener {
            Timber.d("$TAG: WeChat Auto Chat clicked")
            handleWeChatAutoChatClick()
            collapseMenu()
        }
        
        // 无障碍分析界面按钮点击事件
        accessibilityAnalysisButton?.setOnClickListener {
            Timber.d("$TAG: Accessibility Analysis clicked")
            handleAccessibilityAnalysisClick()
            collapseMenu()
        }
        
        // 设置按钮点击事件
        settingsButton?.setOnClickListener {
            Timber.d("$TAG: Settings clicked")
            handleSettingsClick()
            collapseMenu()
        }
    }
    
    /**
     * 设置触摸事件监听器（用于垂直拖拽，固定在右侧）
     */
    private fun setupTouchListener() {
        // 为悬浮按钮设置触摸监听器
        floatingButton?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = (floatingView!!.layoutParams as WindowManager.LayoutParams).y
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = floatingView!!.layoutParams as WindowManager.LayoutParams
                    // 保持当前X坐标位置（不在拖拽时改变）
                    // 只允许Y坐标变化（垂直拖拽）
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    
                    // 限制Y在屏幕范围内
                    params.y = params.y.coerceIn(0, getScreenHeight() - floatingView!!.height)
                    
                    windowManager?.updateViewLayout(floatingView!!, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 如果移动距离很小，则认为是点击事件
                    val moveDistance = kotlin.math.abs(event.rawY - initialTouchY)
                    if (moveDistance < dpToPx(10)) {
                        toggleMenu()
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * dp转px
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    /**
     * 切换菜单显示状态
     */
    private fun toggleMenu() {
        if (isMenuExpanded) {
            collapseMenu()
        } else {
            expandMenu()
        }
    }
    
    /**
     * 展开菜单
     */
    private fun expandMenu() {
        // 展开菜单前，确保悬浮窗位置能够完全显示菜单
        adjustFloatingWindowPosition(true)
        
        menuLayout?.visibility = View.VISIBLE
        isMenuExpanded = true
        Timber.d("$TAG: Menu expanded")
    }
    
    /**
     * 收起菜单
     */
    private fun collapseMenu() {
        menuLayout?.visibility = View.GONE
        isMenuExpanded = false
        
        // 收起菜单后，恢复到紧贴右侧的位置
        adjustFloatingWindowPosition(false)
        
        Timber.d("$TAG: Menu collapsed")
    }
    
    /**
     * 调整悬浮窗位置以适应菜单展开/收起状态
     * @param isExpanded 是否为展开状态
     */
    private fun adjustFloatingWindowPosition(isExpanded: Boolean) {
        try {
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            if (isExpanded) {
                // 展开时：确保整个菜单都能显示，向左移动给菜单留出空间
                // 菜单最大宽度约150dp，加上安全边距20dp
                val menuMaxWidth = dpToPx(150 + 20)
                val buttonWidth = dpToPx(56)
                
                // 计算新的X坐标，确保菜单右边缘不超出屏幕
                val newX = getScreenWidth() - menuMaxWidth
                params.x = kotlin.math.max(0, newX) // 确保不会移出屏幕左边缘
                
            } else {
                // 收起时：恢复到右侧位置
                params.x = getScreenWidth() - dpToPx(56 + 20) // 按钮宽度 + 右边距
            }
            
            // 应用新的位置
            windowManager?.updateViewLayout(floatingView!!, params)
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error adjusting floating window position")
        }
    }
    
    // 悬浮窗口实例
    private var floatingAIAnalysisWindow: FloatingAIAnalysisWindow? = null
    private var floatingWeChatAnalysisWindow: FloatingWeChatAnalysisWindow? = null
    private var floatingWeChatAutoChatWindow: FloatingWeChatAutoChatWindow? = null
    private var floatingAssistsAnalysisWindow: FloatingAssistsAnalysisWindow? = null

    /**
     * 处理AI界面分析按钮点击
     */
    private fun handleAIAnalysisClick() {
        try {
            Timber.d("$TAG: AI Interface Analysis button clicked")
            
            // 检查无障碍服务是否开启
            if (!EnhancedAssistsService.isServiceEnabled()) {
                Toast.makeText(this, "请先开启无障碍服务以使用AI分析功能", Toast.LENGTH_LONG).show()
                // 打开设置页面引导用户开启服务
                val intent = Intent(this, com.shenji.aikeyboard.settings.InputMethodSettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return
            }
            
            // 创建并显示悬浮AI分析窗口
            if (floatingAIAnalysisWindow == null) {
                floatingAIAnalysisWindow = FloatingAIAnalysisWindow(this, serviceScope)
                floatingAIAnalysisWindow?.initializeWindow()
            }
            
            Toast.makeText(this, "正在启动AI界面分析...", Toast.LENGTH_SHORT).show()
            floatingAIAnalysisWindow?.startAnalysis()
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error in handleAIAnalysisClick")
            Toast.makeText(this, "启动AI界面分析失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 处理AI微信对话分析按钮点击
     */
    private fun handleWeChatAnalysisClick() {
        try {
            Timber.d("$TAG: AI WeChat Analysis button clicked")
            
            // 检查无障碍服务是否开启
            if (!EnhancedAssistsService.isServiceEnabled()) {
                Toast.makeText(this, "请先开启无障碍服务以使用微信对话分析功能", Toast.LENGTH_LONG).show()
                // 打开设置页面引导用户开启服务
                val intent = Intent(this, com.shenji.aikeyboard.settings.InputMethodSettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return
            }
            
            // 创建并显示悬浮微信分析窗口
            if (floatingWeChatAnalysisWindow == null) {
                floatingWeChatAnalysisWindow = FloatingWeChatAnalysisWindow(this, serviceScope)
                floatingWeChatAnalysisWindow?.initializeWindow()
            }
            
            Toast.makeText(this, "正在启动AI微信对话分析...", Toast.LENGTH_SHORT).show()
            floatingWeChatAnalysisWindow?.startAnalysis()
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error in handleWeChatAnalysisClick")
            Toast.makeText(this, "启动AI微信对话分析失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 处理微信AI自动聊天按钮点击
     */
    private fun handleWeChatAutoChatClick() {
        try {
            Timber.d("$TAG: WeChat Auto Chat button clicked")
            
            // 检查Assists无障碍服务是否可用
            if (!com.shenji.aikeyboard.assists.AssistsManager.isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "请先开启Assists无障碍服务权限", Toast.LENGTH_LONG).show()
                // 引导用户去开启Assists无障碍服务
                com.shenji.aikeyboard.assists.AssistsManager.openAccessibilitySettings()
                return
            }
            
            // 创建并显示微信AI自动聊天窗口
            if (floatingWeChatAutoChatWindow == null) {
                floatingWeChatAutoChatWindow = FloatingWeChatAutoChatWindow(this, serviceScope)
                floatingWeChatAutoChatWindow?.initializeWindow()
            }
            
            Toast.makeText(this, "正在启动微信AI自动聊天...", Toast.LENGTH_SHORT).show()
            floatingWeChatAutoChatWindow?.showAndAnalyze()
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error in handleWeChatAutoChatClick")
            Toast.makeText(this, "启动微信AI自动聊天失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 处理无障碍分析界面按钮点击 - 升级为Assists框架入口
     */
    private fun handleAccessibilityAnalysisClick() {
        try {
            Timber.d("$TAG: Assists Analysis button clicked")
            
            // 检查Assists无障碍服务是否可用
            if (!com.shenji.aikeyboard.assists.AssistsManager.isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "请先开启Assists无障碍服务权限", Toast.LENGTH_LONG).show()
                // 引导用户去开启Assists无障碍服务
                com.shenji.aikeyboard.assists.AssistsManager.openAccessibilitySettings()
                return
            }
            
            // 创建并显示悬浮Assists分析窗口
            if (floatingAssistsAnalysisWindow == null) {
                floatingAssistsAnalysisWindow = FloatingAssistsAnalysisWindow(this, serviceScope)
                floatingAssistsAnalysisWindow?.initializeWindow()
            }
            
            Toast.makeText(this, "正在启动Assists框架分析...", Toast.LENGTH_SHORT).show()
            floatingAssistsAnalysisWindow?.startAnalysis()
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error in handleAccessibilityAnalysisClick")
            Toast.makeText(this, "启动Assists分析失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示传统无障碍分析弹窗（降级方案）
     */
    private fun showLegacyAccessibilityAnalysisDialog() {
        try {
            // 创建弹窗视图
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_accessibility_analysis, null)
            
            // 配置弹窗窗口参数
            val dialogParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            
            dialogParams.gravity = Gravity.CENTER
            
            // 添加弹窗到窗口管理器
            windowManager?.addView(dialogView, dialogParams)
            
            // 设置弹窗UI
            setupLegacyAccessibilityAnalysisDialog(dialogView)
            
            Timber.d("$TAG: Accessibility analysis dialog shown")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error showing accessibility analysis dialog")
            Toast.makeText(this, "显示无障碍分析弹窗失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置传统无障碍分析弹窗UI
     */
    private fun setupLegacyAccessibilityAnalysisDialog(dialogView: View) {
        try {
            val btnClose = dialogView.findViewById<Button>(R.id.btn_close_dialog)
            val btnRefresh = dialogView.findViewById<Button>(R.id.btn_refresh)
            val btnCopyText = dialogView.findViewById<Button>(R.id.btn_copy_text)
            val tvStatusHint = dialogView.findViewById<TextView>(R.id.tv_status_hint)
            val tvTextNodes = dialogView.findViewById<TextView>(R.id.tv_text_nodes)
            
            // 关闭按钮点击事件
            btnClose.setOnClickListener {
                windowManager?.removeView(dialogView)
            }
            
            // 点击弹窗背景区域关闭
            dialogView.setOnClickListener {
                windowManager?.removeView(dialogView)
            }
            
            // 防止点击内容区域关闭弹窗
            dialogView.findViewById<LinearLayout>(R.id.dialog_content)?.setOnClickListener { 
                // 阻止事件冒泡
            }
            
            // 刷新读取按钮
            btnRefresh.setOnClickListener {
                readAccessibilityFullAnalysis(tvStatusHint, tvTextNodes)
            }
            
            // 复制文本按钮
            btnCopyText.setOnClickListener {
                copyTextToClipboard(tvTextNodes.text.toString())
            }
            
            // 立即开始读取文本节点
            readAccessibilityFullAnalysis(tvStatusHint, tvTextNodes)
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error setting up accessibility analysis dialog")
        }
    }

    /**
     * 读取无障碍分析内容（6大功能分组，彩色分区显示）
     */
    private fun readAccessibilityFullAnalysis(tvStatusHint: TextView, tvTextNodes: TextView) {
        tvStatusHint.text = "正在使用Assists框架分析界面..."
        tvTextNodes.text = "初始化Assists分析引擎..."
        
        serviceScope.launch {
            try {
                val sb = StringBuilder()
                
                // 1. 应用信息
                sb.append("<font color='#4CAF50'><b>=== 应用信息 ===</b></font><br>")
                try {
                    val pkg = com.shenji.aikeyboard.assists.AssistsManager.getCurrentPackageName()
                    sb.append("包名: $pkg<br>")
                } catch (e: Exception) {
                    sb.append("获取应用信息失败: ${e.message}<br>")
                }
                sb.append("<br>")
                
                // 2. 所有节点
                sb.append("<font color='#2196F3'><b>=== 所有节点 ===</b></font><br>")
                try {
                    val allNodes = com.shenji.aikeyboard.assists.AssistsManager.getAllNodes()
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
                
                // 3. 文本节点
                sb.append("<font color='#9C27B0'><b>=== 文本节点 ===</b></font><br>")
                try {
                    // 使用AssistsManager获取文本节点，避免直接调用EnhancedAssistsService
                    val textNodesList = mutableListOf<String>()
                    com.shenji.aikeyboard.assists.AssistsManager.getAllTextNodes { nodes ->
                        textNodesList.addAll(nodes)
                    }
                    // 等待一下让回调执行
                    kotlinx.coroutines.delay(100)
                    
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
                
                // 4. 查找测试
                sb.append("<font color='#FF9800'><b>=== 查找测试 ===</b></font><br>")
                try {
                    val found = com.shenji.aikeyboard.assists.AssistsManager.findNodesById("android:id/content")
                    sb.append("通过id查找(android:id/content): ${found.size} 个<br>")
                } catch (e: Exception) {
                    sb.append("查找测试失败: ${e.message}<br>")
                }
                sb.append("<br>")
                
                // 5. Core测试
                sb.append("<font color='#F44336'><b>=== Core测试 ===</b></font><br>")
                try {
                    val allNodes = com.ven.assists.AssistsCore.getAllNodes()
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
                    val statusInfo = com.shenji.aikeyboard.assists.AssistsManager.isAccessibilityServiceEnabled()
                    sb.append("Assists无障碍服务: ${if (statusInfo) "已启用" else "未启用"}<br>")
                } catch (e: Exception) {
                    sb.append("服务详情获取失败: ${e.message}<br>")
                }
                
                // 更新UI
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    tvStatusHint.text = "Assists分析完成，已分组展示"
                    tvTextNodes.text = android.text.Html.fromHtml(sb.toString())
                }
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 分析过程出错")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    tvStatusHint.text = "Assists分析失败"
                    tvTextNodes.text = "分析过程出错：${e.message}"
                }
            }
        }
    }

    /**
     * 复制文本到剪贴板
     */
    private fun copyTextToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("无障碍分析结果", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error copying text to clipboard")
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 处理设置按钮点击
     */
    private fun handleSettingsClick() {
        try {
            // 启动设置页面
            val intent = Intent(this, com.shenji.aikeyboard.settings.InputMethodSettingsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            Toast.makeText(this, "打开设置", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error launching settings")
            Toast.makeText(this, "打开设置失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 移除悬浮视图
     */
    private fun removeFloatingView() {
        try {
            floatingView?.let { view ->
                windowManager?.removeView(view)
                floatingView = null
            }
            Timber.d("$TAG: Floating view removed")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error removing floating view")
        }
    }
    
    /**
     * 获取屏幕宽度
     */
    private fun getScreenWidth(): Int {
        return resources.displayMetrics.widthPixels
    }
    
    /**
     * 获取屏幕高度
     */
    private fun getScreenHeight(): Int {
        return resources.displayMetrics.heightPixels
    }
    
    /**
     * 启动前台服务
     */
    private fun startForegroundService() {
        val channelId = "floating_window_channel"
        val channelName = "悬浮窗服务"
        
        // 创建通知渠道（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "神迹AI键盘悬浮窗服务"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // 创建点击通知的Intent
        val notificationIntent = Intent(this, com.shenji.aikeyboard.ui.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 构建通知
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("神迹AI键盘")
            .setContentText("悬浮窗和AI分析功能已启用")
            .setSmallIcon(R.drawable.ic_ai_analysis)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        // 启动前台服务
        startForeground(1, notification)
        Timber.d("$TAG: Foreground service started")
    }
}

/**
 * 临时保存Bitmap的单例类
 */
object TempBitmapHolder {
    var bitmap: Bitmap? = null
} 