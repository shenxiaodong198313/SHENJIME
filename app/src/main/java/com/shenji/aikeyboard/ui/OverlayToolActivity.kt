package com.shenji.aikeyboard.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.R
import timber.log.Timber

/**
 * 输入法工具栏页面Activity
 * 特点：
 * 1. 半透明背景
 * 2. 顶部距离屏幕有间距
 * 3. 键盘自动收起
 * 4. 支持返回操作
 */
class OverlayToolActivity : AppCompatActivity() {
    
    private lateinit var btnBack: ImageButton
    private lateinit var contentContainer: LinearLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置窗口特性
        setupWindowFlags()
        
        setContentView(R.layout.activity_overlay_tool)
        
        // 隐藏ActionBar
        supportActionBar?.hide()
        
        // 初始化UI
        initViews()
        
        // 设置点击事件
        setupClickListeners()
        
        Timber.d("OverlayToolActivity 已启动")
    }
    
    /**
     * 设置窗口标志
     */
    private fun setupWindowFlags() {
        try {
            // 设置窗口为全屏，但保持状态栏可见
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
            Timber.e(e, "设置窗口标志失败")
        }
    }
    
    /**
     * 初始化视图
     */
    private fun initViews() {
        try {
            btnBack = findViewById(R.id.btnBack)
            contentContainer = findViewById(R.id.contentContainer)
            
            Timber.d("视图初始化完成")
        } catch (e: Exception) {
            Timber.e(e, "初始化视图失败")
        }
    }
    
    /**
     * 设置点击事件
     */
    private fun setupClickListeners() {
        // 返回按钮点击事件
        btnBack.setOnClickListener {
            onBackPressed()
        }
        
        // 点击背景区域关闭页面
        findViewById<View>(R.id.backgroundOverlay).setOnClickListener {
            onBackPressed()
        }
        
        // 设置功能按钮点击事件
        setupFunctionButtons()
    }
    
    /**
     * 设置功能按钮点击事件
     */
    private fun setupFunctionButtons() {
        // 工具栏页面暂时没有功能按钮
        Timber.d("工具栏页面功能按钮设置完成")
    }
    
    /**
     * 启动MNN主界面
     */
    private fun startMnnMainActivity() {
        try {
            val intent = android.content.Intent(this, com.shenji.aikeyboard.mnn.main.MainActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Timber.e(e, "启动MNN主界面失败")
            android.widget.Toast.makeText(this, "启动AI助手失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 启动设置界面
     */
    private fun startSettingsActivity() {
        try {
            val intent = android.content.Intent(this, com.shenji.aikeyboard.settings.InputMethodSettingsActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Timber.e(e, "启动设置界面失败")
            android.widget.Toast.makeText(this, "启动设置失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        // 添加退出动画
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_down)
    }
    
    override fun finish() {
        super.finish()
        // 添加退出动画
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_down)
    }
} 