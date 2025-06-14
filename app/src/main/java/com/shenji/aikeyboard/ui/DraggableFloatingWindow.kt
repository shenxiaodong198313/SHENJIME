package com.shenji.aikeyboard.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import com.shenji.aikeyboard.R
import timber.log.Timber

/**
 * 可拖拽悬浮窗口基类
 * 提供统一的拖拽移动、调整大小、关闭等功能
 */
@SuppressLint("ClickableViewAccessibility")
abstract class DraggableFloatingWindow(
    protected val context: Context,
    private val onClose: (() -> Unit)? = null
) {
    
    companion object {
        private const val TAG = "DraggableFloatingWindow"
        
        // 默认窗口大小（参考设计图）
        private const val DEFAULT_WIDTH_DP = 350
        private const val DEFAULT_HEIGHT_DP = 500
        
        // 最小窗口大小
        private const val MIN_WIDTH_DP = 280
        private const val MIN_HEIGHT_DP = 400
    }
    
    // 窗口管理器和布局参数
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    
    // 根视图和内容容器
    private var rootView: View? = null
    private var contentContainer: FrameLayout? = null
    
    // 控制元素
    private var moveHandle: ImageView? = null
    private var resizeHandle: ImageView? = null
    private var closeButton: ImageView? = null
    
    // 触摸事件相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialWidth = 0
    private var initialHeight = 0
    
    // 窗口状态
    private var isShowing = false
    
    /**
     * 初始化悬浮窗口
     */
    fun initialize() {
        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            createRootView()
            setupLayoutParams()
            setupTouchListeners()
            Timber.d("$TAG: Floating window initialized")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize floating window")
        }
    }
    
    /**
     * 显示悬浮窗口
     */
    fun show() {
        try {
            if (!isShowing && rootView != null && layoutParams != null) {
                windowManager?.addView(rootView, layoutParams)
                isShowing = true
                onWindowShown()
                Timber.d("$TAG: Floating window shown")
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to show floating window")
        }
    }
    
    /**
     * 隐藏悬浮窗口
     */
    fun hide() {
        try {
            if (isShowing && rootView != null) {
                windowManager?.removeView(rootView)
                isShowing = false
                onWindowHidden()
                Timber.d("$TAG: Floating window hidden")
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to hide floating window")
        }
    }
    
    /**
     * 关闭悬浮窗口
     */
    fun close() {
        try {
            hide()
            onWindowClosed()
            onClose?.invoke()
            Timber.d("$TAG: Floating window closed")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to close floating window")
        }
    }
    
    /**
     * 设置内容视图
     */
    fun setContentView(view: View) {
        contentContainer?.removeAllViews()
        contentContainer?.addView(view)
    }
    
    /**
     * 设置内容视图（通过布局ID）
     */
    fun setContentView(layoutId: Int) {
        val view = LayoutInflater.from(context).inflate(layoutId, contentContainer, false)
        setContentView(view)
    }
    
    /**
     * 获取内容视图
     */
    fun getContentView(): View? {
        return if (contentContainer?.childCount ?: 0 > 0) {
            contentContainer?.getChildAt(0)
        } else null
    }
    
    /**
     * 创建根视图
     */
    private fun createRootView() {
        rootView = LayoutInflater.from(context).inflate(R.layout.draggable_floating_window, null)
        
        // 获取控制元素
        moveHandle = rootView?.findViewById(R.id.iv_move_handle)
        resizeHandle = rootView?.findViewById(R.id.iv_resize_handle)
        closeButton = rootView?.findViewById(R.id.iv_close_button)
        contentContainer = rootView?.findViewById(R.id.fl_content_container)
        
        // 设置关闭按钮点击事件
        closeButton?.setOnClickListener {
            close()
        }
    }
    
    /**
     * 设置布局参数
     */
    private fun setupLayoutParams() {
        layoutParams = WindowManager.LayoutParams().apply {
            // 窗口类型
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            // 窗口标志
            flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            
            // 窗口格式
            format = PixelFormat.TRANSLUCENT
            
            // 默认大小
            width = dpToPx(DEFAULT_WIDTH_DP)
            height = dpToPx(DEFAULT_HEIGHT_DP)
            
            // 默认位置（居中）
            gravity = Gravity.TOP or Gravity.START
            x = (getScreenWidth() - width) / 2
            y = (getScreenHeight() - height) / 2
        }
    }
    
    /**
     * 设置触摸事件监听器
     */
    private fun setupTouchListeners() {
        // 移动手柄触摸事件
        moveHandle?.setOnTouchListener { _, event ->
            handleMoveTouch(event)
        }
        
        // 调整大小手柄触摸事件
        resizeHandle?.setOnTouchListener { _, event ->
            handleResizeTouch(event)
        }
    }
    
    /**
     * 处理移动触摸事件
     */
    private fun handleMoveTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams?.x ?: 0
                initialY = layoutParams?.y ?: 0
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                val deltaX = (event.rawX - initialTouchX).toInt()
                val deltaY = (event.rawY - initialTouchY).toInt()
                
                layoutParams?.let { params ->
                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    
                    // 限制窗口不超出屏幕边界
                    params.x = params.x.coerceIn(0, getScreenWidth() - params.width)
                    params.y = params.y.coerceIn(0, getScreenHeight() - params.height)
                    
                    windowManager?.updateViewLayout(rootView, params)
                }
                return true
            }
        }
        return false
    }
    
    /**
     * 处理调整大小触摸事件
     */
    private fun handleResizeTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialWidth = layoutParams?.width ?: 0
                initialHeight = layoutParams?.height ?: 0
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                val deltaX = (event.rawX - initialTouchX).toInt()
                val deltaY = (event.rawY - initialTouchY).toInt()
                
                layoutParams?.let { params ->
                    // 调整宽度和高度
                    val newWidth = (initialWidth + deltaX).coerceAtLeast(dpToPx(MIN_WIDTH_DP))
                    val newHeight = (initialHeight + deltaY).coerceAtLeast(dpToPx(MIN_HEIGHT_DP))
                    
                    // 限制不超出屏幕
                    params.width = newWidth.coerceAtMost(getScreenWidth() - params.x)
                    params.height = newHeight.coerceAtMost(getScreenHeight() - params.y)
                    
                    windowManager?.updateViewLayout(rootView, params)
                }
                return true
            }
        }
        return false
    }
    
    /**
     * dp转px
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    /**
     * 获取屏幕宽度
     */
    private fun getScreenWidth(): Int {
        return context.resources.displayMetrics.widthPixels
    }
    
    /**
     * 获取屏幕高度
     */
    private fun getScreenHeight(): Int {
        return context.resources.displayMetrics.heightPixels
    }
    
    // 抽象方法，子类需要实现
    
    /**
     * 窗口显示时回调
     */
    protected open fun onWindowShown() {}
    
    /**
     * 窗口隐藏时回调
     */
    protected open fun onWindowHidden() {}
    
    /**
     * 窗口关闭时回调（用于终止后台进程等清理工作）
     */
    protected abstract fun onWindowClosed()
} 