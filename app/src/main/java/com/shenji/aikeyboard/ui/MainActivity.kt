package com.shenji.aikeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.Button
import android.widget.ImageView
import android.widget.FrameLayout
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.settings.InputMethodSettingsActivity
import com.shenji.aikeyboard.mnn.main.MainActivity as MnnMainActivity
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 主界面Activity
 * 神迹AI键盘的主入口界面
 */
class MainActivity : AppCompatActivity() {

    private lateinit var btnImeSettings: Button
    private lateinit var btnLogs: Button
    private lateinit var btnOptimizedTest: Button
    private lateinit var btnLlmInference: Button
    private lateinit var btnAiTest: Button
    private lateinit var appIconTop: ImageView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            
            // 设置全屏模式
            setupFullScreenMode()
            
            // 记录到系统日志
            Log.i("MainActivity", "开始创建主界面")
            
            setContentView(R.layout.activity_main)
            
            // 隐藏ActionBar
            supportActionBar?.hide()
            
            // 设置UI组件
            setupUI()
            
            // 启动后台词典加载
            startBackgroundDictionaryLoading()
            
            Log.i("MainActivity", "主界面创建完成")
        } catch (e: Exception) {
            // 记录错误
            Log.e("MainActivity", "创建主界面时发生错误: ${e.message}", e)
            
            // 尝试加载基础布局并显示错误
            try {
                setContentView(R.layout.activity_main)
                Toast.makeText(this, "界面初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Log.e("MainActivity", "无法加载备用布局: ${e2.message}", e2)
                finish() // 无法恢复，关闭活动
            }
        }
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
            Log.w("MainActivity", "设置全屏模式失败: ${e.message}")
        }
    }
    
    private fun setupUI() {
        try {
            // 设置顶部图标
            setupTopIcon()
            
            // 创建按钮
            createButtons()
            
            Log.d("MainActivity", "所有UI元素设置完成")
        } catch (e: Exception) {
            Log.e("MainActivity", "设置UI元素失败: ${e.message}", e)
            Toast.makeText(this, "界面初始化异常，部分功能可能不可用", Toast.LENGTH_LONG).show()      
        }
    }
    
    /**
     * 设置顶部图标
     */
    private fun setupTopIcon() {
        try {
            val appIconTop = findViewById<ImageView>(R.id.appIconTop)
            appIconTop?.setImageResource(R.mipmap.ic_launcher)
            Log.d("MainActivity", "顶部图标设置完成")
        } catch (e: Exception) {
            Log.e("MainActivity", "设置图标失败: ${e.message}", e)
        }
    }
    
    /**
     * 创建所有按钮
     */
    private fun createButtons() {
        try {
            // 输入法设置按钮
            createWhiteButton(
                R.id.inputMethodSettingsButtonContainer,
                "输入法设置"
            ) { openInputMethodSettings() }
            
            // 日志详情按钮
            createWhiteButton(
                R.id.logDetailButtonContainer,
                "日志详情"
            ) { openLogDetail() }
            
            // 候选词引擎测试按钮
            createWhiteButton(
                R.id.optimizedCandidateTestButtonContainer,
                "候选词引擎测试"
            ) { openOptimizedCandidateTest() }
            
            // AI功能测试按钮
            createWhiteButton(
                R.id.aiTestButtonContainer,
                "AI功能测试"
            ) { openAiTest() }
            
            // MNN推理按钮 - 新增
            createWhiteButton(
                R.id.mnnInferenceButtonContainer,
                "MNN AI助手"
            ) { openMnnInference() }
            

            
            Log.d("MainActivity", "所有按钮创建完成")
        } catch (e: Exception) {
            Log.e("MainActivity", "按钮创建失败: ${e.message}", e)
        }
    }
    
    /**
     * 创建白色背景按钮
     */
    private fun createWhiteButton(containerId: Int, text: String, onClick: () -> Unit): Button {
        val container = findViewById<FrameLayout>(containerId)
        val button = Button(this)
        
        // 设置按钮文本和样式
        button.text = text
        button.textSize = 16f
        button.setTextColor(getColor(R.color.splash_background_color))
        
        // 创建纯白色背景
        val whiteBackground = android.graphics.drawable.GradientDrawable()
        whiteBackground.setColor(android.graphics.Color.WHITE)
        whiteBackground.cornerRadius = 12 * resources.displayMetrics.density
        
        // 应用背景和样式
        button.background = whiteBackground
        button.elevation = 0f
        button.stateListAnimator = null
        
        // 移除Material Design效果
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            button.outlineProvider = null
        }
        
        // 设置按钮尺寸
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (56 * resources.displayMetrics.density).toInt() // 56dp高度
        )
        button.layoutParams = layoutParams
        
        // 设置点击事件
        button.setOnClickListener { onClick() }
        
        // 添加到容器
        container.addView(button)
        
        return button
    }
    
    /**
     * 创建紫色背景按钮
     */
    private fun createPurpleButton(containerId: Int, text: String, onClick: () -> Unit): Button {
        val container = findViewById<FrameLayout>(containerId)
        val button = Button(this)
        
        // 设置按钮文本和样式
        button.text = text
        button.textSize = 16f
        button.setTextColor(android.graphics.Color.WHITE)
        
        // 创建紫色背景
        val purpleBackground = android.graphics.drawable.GradientDrawable()
        purpleBackground.setColor(android.graphics.Color.parseColor("#9C27B0")) // Material Purple
        purpleBackground.cornerRadius = 12 * resources.displayMetrics.density
        
        // 应用背景和样式
        button.background = purpleBackground
        button.elevation = 0f
        button.stateListAnimator = null
        
        // 移除Material Design效果
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            button.outlineProvider = null
        }
        
        // 设置按钮尺寸
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (56 * resources.displayMetrics.density).toInt() // 56dp高度
        )
        button.layoutParams = layoutParams
        
        // 设置点击事件
        button.setOnClickListener { onClick() }
        
        // 添加到容器
        container.addView(button)
        
        return button
    }
    
    /**
     * 打开输入法设置
     */
    private fun openInputMethodSettings() {
        try {
            Timber.d("打开输入法设置")
            val intent = Intent(this, InputMethodSettingsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "打开输入法设置失败: ${e.message}", e)
            Toast.makeText(this, "无法打开输入法设置: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 打开日志详情
     */
    private fun openLogDetail() {
        try {
            Log.d("MainActivity", "开始打开日志详情")
            Timber.d("打开日志详情")
            val intent = Intent(this, LogDetailActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "打开日志详情失败: ${e.message}", e)
            Toast.makeText(this, "无法打开日志详情: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 打开候选词引擎测试
     */
    private fun openOptimizedCandidateTest() {
        try {
            Timber.d("打开候选词引擎测试")
            val intent = Intent(this, SmartPinyinMvpTestActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "打开候选词引擎测试失败: ${e.message}", e)
            Toast.makeText(this, "无法打开候选词引擎测试: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 打开MNN推理 - 跳转到MNN专用界面
     */
    private fun openMnnInference() {
        try {
            Timber.d("打开MNN AI助手")
            val intent = Intent(this, MnnMainActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "打开MNN AI助手失败: ${e.message}", e)
            Toast.makeText(this, "无法打开MNN AI助手: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 打开AI功能测试
     */
    private fun openAiTest() {
        try {
            Timber.d("打开AI功能测试")
            val intent = Intent(this, AITestActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "打开AI功能测试失败: ${e.message}", e)
            Toast.makeText(this, "无法打开AI功能测试: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    

    
    /**
     * 启动后台词典加载
     */
    private fun startBackgroundDictionaryLoading() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val trieManager = TrieManager.instance
                
                Timber.i("主界面启动 - 开始优化的词典加载策略")
                Timber.i("内存优化策略：启动时已加载chars，现在异步加载base，其他词典需手动加载")
                
                val runtime = Runtime.getRuntime()
                val maxMemory = runtime.maxMemory() / 1024 / 1024
                val usedMemoryBefore = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                
                Timber.i("异步加载前内存状态: 已用${usedMemoryBefore}MB / 最大${maxMemory}MB")
                
                // 等待一段时间，确保主界面完全加载
                delay(2000)
                
                Timber.i("简化的词典加载策略：只加载核心词典")
                
            } catch (e: Exception) {
                Timber.e(e, "异步词典加载过程异常")
            }
        }
    }
} 