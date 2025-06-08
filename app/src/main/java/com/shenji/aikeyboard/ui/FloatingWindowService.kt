package com.shenji.aikeyboard.ui

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.shenji.aikeyboard.R
import timber.log.Timber

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
    private var settingsButton: LinearLayout? = null
    
    // 触摸相关变量
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
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
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingView()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("$TAG: FloatingWindowService onStartCommand")
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("$TAG: FloatingWindowService onDestroy")
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
            
            // 设置初始位置（屏幕右侧中间）
            params.gravity = Gravity.TOP or Gravity.END
            params.x = 0
            params.y = getScreenHeight() / 2
            
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
        // 悬浮按钮点击事件
        floatingButton?.setOnClickListener {
            toggleMenu()
        }
        
        // AI分析按钮点击事件
        aiAnalysisButton?.setOnClickListener {
            Timber.d("$TAG: AI Analysis clicked")
            handleAIAnalysisClick()
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
     * 设置触摸事件监听器（用于拖拽）
     */
    private fun setupTouchListener() {
        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = (view.layoutParams as WindowManager.LayoutParams).x
                    initialY = (view.layoutParams as WindowManager.LayoutParams).y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x = initialX + (initialTouchX - event.rawX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    
                    // 限制在屏幕范围内
                    params.x = params.x.coerceIn(0, getScreenWidth() - view.width)
                    params.y = params.y.coerceIn(0, getScreenHeight() - view.height)
                    
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
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
        Timber.d("$TAG: Menu collapsed")
    }
    
    /**
     * 处理AI分析按钮点击
     */
    private fun handleAIAnalysisClick() {
        try {
            // 启动AI分析功能
            val intent = Intent(this, AITestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(this, "启动AI分析", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error launching AI analysis")
            Toast.makeText(this, "启动AI分析失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 处理设置按钮点击
     */
    private fun handleSettingsClick() {
        try {
            // 启动设置页面
            val intent = Intent(this, com.shenji.aikeyboard.settings.InputMethodSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
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
} 