package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import android.widget.Button
import android.widget.TextView
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.logger.LogManager
import timber.log.Timber

class LogDetailActivity : AppCompatActivity() {

    private lateinit var btnCopyLog: Button
    private lateinit var tvLogContent: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏模式
        setupFullScreenMode()
        
        setContentView(R.layout.activity_log_detail)
        
        // 隐藏ActionBar
        supportActionBar?.hide()
        
        setupUI()
        loadLogContent()
    }
    
    /**
     * 设置全屏模式
     */
    private fun setupFullScreenMode() {
        try {
            // 设置状态栏和导航栏颜色与背景一致
            window.statusBarColor = getColor(R.color.splash_background_color)
            window.navigationBarColor = getColor(R.color.splash_background_color)
            
            // 使用传统的全屏方法，更兼容
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        } catch (e: Exception) {
            Timber.w("设置全屏模式失败: ${e.message}")
        }
    }
    
    private fun setupUI() {
        // 获取日志内容TextView
        tvLogContent = findViewById(R.id.tvLogContent)
        
        // 创建复制日志按钮
        createCopyButton()
        
        // 设置返回按钮
        findViewById<Button>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
    }
    
    /**
     * 创建复制日志按钮
     */
    private fun createCopyButton() {
        val container = findViewById<FrameLayout>(R.id.btnCopyLogContainer)
        btnCopyLog = Button(this)
        
        // 设置按钮文本和样式
        btnCopyLog.text = getString(R.string.copy_log)
        btnCopyLog.textSize = 16f
        btnCopyLog.setTextColor(getColor(R.color.splash_background_color))
        
        // 创建纯白色背景
        val whiteBackground = android.graphics.drawable.GradientDrawable()
        whiteBackground.setColor(android.graphics.Color.WHITE)
        whiteBackground.cornerRadius = 12 * resources.displayMetrics.density
        
        // 应用背景和样式
        btnCopyLog.background = whiteBackground
        btnCopyLog.elevation = 0f
        btnCopyLog.stateListAnimator = null
        
        // 移除Material Design效果
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            btnCopyLog.outlineProvider = null
        }
        
        // 设置按钮尺寸
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (56 * resources.displayMetrics.density).toInt() // 56dp高度
        )
        btnCopyLog.layoutParams = layoutParams
        
        // 设置点击事件
        btnCopyLog.setOnClickListener {
            copyLogToClipboard()
        }
        
        // 添加到容器
        container.addView(btnCopyLog)
        
        Timber.d("复制日志按钮创建完成")
    }
    
    /**
     * 加载日志内容
     */
    private fun loadLogContent() {
        Timber.d("加载日志内容")
        val logContent = LogManager.readCrashLog()
        
        if (logContent != null && logContent.isNotEmpty()) {
            tvLogContent.text = logContent
        } else {
            tvLogContent.text = getString(R.string.no_logs)
        }
    }
    
    /**
     * 复制日志内容到剪贴板
     */
    private fun copyLogToClipboard() {
        val logContent = tvLogContent.text.toString()
        
        if (logContent.isNotEmpty() && logContent != getString(R.string.no_logs)) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("神迹输入法日志", logContent)
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(this, getString(R.string.log_copied), Toast.LENGTH_SHORT).show()
            Timber.d("日志内容已复制到剪贴板")
        } else {
            Toast.makeText(this, "没有可复制的日志内容", Toast.LENGTH_SHORT).show()
        }
    }
} 