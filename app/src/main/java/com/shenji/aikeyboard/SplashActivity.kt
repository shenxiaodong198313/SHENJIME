package com.shenji.aikeyboard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageView
import android.widget.Button
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.trie.TrieType
import com.shenji.aikeyboard.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * 重构的启动页 - 分阶段内存优化启动流程
 * 
 * 启动阶段：
 * 1. 数据库初始化阶段 (0-40%)
 * 2. 内存清理阶段 (40-50%) 
 * 3. 核心词典加载阶段 (50-90%)
 * 4. 启动完成阶段 (90-100%)
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var splashIcon: ImageView
    private lateinit var buildDictButton: Button
    private val handler = Handler(Looper.getMainLooper())
    
    // 启动阶段枚举
    private enum class StartupPhase {
        DATABASE_INIT,      // 数据库初始化
        MEMORY_CLEANUP,     // 内存清理
        CORE_DICT_LOADING,  // 核心词典加载
        STARTUP_COMPLETE    // 启动完成
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏模式，使用更安全的方法
        try {
            // 设置状态栏和导航栏颜色与背景一致
            window.statusBarColor = getColor(R.color.splash_background_color)
            window.navigationBarColor = getColor(R.color.splash_background_color)
            
            // 使用传统的全屏方法，更兼容
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        } catch (e: Exception) {
            // 如果设置失败，使用最基本的全屏模式
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
        }
        
        setContentView(R.layout.activity_splash)
        
        // 隐藏ActionBar
        supportActionBar?.hide()
        
        initViews()
        
        // 开始启动主题动画
        startSplashThemeAnimation()
    }
    
    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        detailText = findViewById(R.id.detailText)
        splashIcon = findViewById(R.id.splashIcon)
        buildDictButton = findViewById(R.id.buildDictButton)
        
        // 从assets加载卡通设计图片
        try {
            val inputStream = assets.open("images/appicon.png")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val drawable = BitmapDrawable(resources, bitmap)
            splashIcon.setImageDrawable(drawable)
            inputStream.close()
        } catch (e: Exception) {
            Timber.e(e, "加载卡通设计图片失败")
        }
        
        // 调整图标位置以匹配启动主题
        adjustIconPosition()
        
        // 设置按钮点击事件（暂时不需要）
        buildDictButton.setOnClickListener {
            // 暂时不做任何操作，用于观察
        }
        
        progressBar.max = 100
        progressBar.progress = 0
        statusText.text = "正在启动神迹输入法..."
        detailText.text = "正在准备数据库和词典..."
    }
    
    /**
     * 调整图标位置以匹配启动主题
     */
    private fun adjustIconPosition() {
        splashIcon.post {
            try {
                // 获取各种屏幕尺寸
                val displayMetrics = resources.displayMetrics
                val currentScreenHeight = displayMetrics.heightPixels  // 当前可用屏幕高度
                val realScreenHeight = getRealScreenHeight()           // 真实物理屏幕高度
                val statusBarHeight = getStatusBarHeight()             // 状态栏高度
                val navigationBarHeight = getNavigationBarHeight()     // 导航栏高度
                
                // 计算可用屏幕高度（排除状态栏和导航栏）
                val availableHeight = realScreenHeight - statusBarHeight - navigationBarHeight
                
                // 计算图标应该在的位置，模拟启动主题的center + top="-100dp"效果
                // 启动主题是基于真实屏幕高度居中，然后向上偏移100dp
                val iconHeightPx = 280 * displayMetrics.density
                val offsetPx = 100 * displayMetrics.density
                
                // 使用真实屏幕高度计算位置：真实屏幕高度的中心 - 图标高度的一半 - 100dp偏移
                // 智能适配：根据状态栏高度动态调整，确保在不同设备上都一致
                val dynamicAdjustment = statusBarHeight * 0.5f // 状态栏高度的50%作为调整量
                val targetY = (realScreenHeight / 2) - (iconHeightPx / 2) - offsetPx + dynamicAdjustment
                
                // 设置图标的绝对位置
                val layoutParams = splashIcon.layoutParams as android.widget.RelativeLayout.LayoutParams
                layoutParams.topMargin = targetY.toInt()
                layoutParams.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL)
                layoutParams.removeRule(android.widget.RelativeLayout.CENTER_IN_PARENT)
                splashIcon.layoutParams = layoutParams
                
                // 显示详细的屏幕信息
                val screenInfo = """
                    当前屏幕高度: ${currentScreenHeight}px (${(currentScreenHeight / displayMetrics.density).toInt()}dp)
                    真实屏幕高度: ${realScreenHeight}px (${(realScreenHeight / displayMetrics.density).toInt()}dp)
                    状态栏高度: ${statusBarHeight}px (${(statusBarHeight / displayMetrics.density).toInt()}dp)
                    导航栏高度: ${navigationBarHeight}px (${(navigationBarHeight / displayMetrics.density).toInt()}dp)
                    可用高度: ${availableHeight}px (${(availableHeight / displayMetrics.density).toInt()}dp)
                    图标Y位置: ${targetY.toInt()}px (${(targetY / displayMetrics.density).toInt()}dp)
                    屏幕密度: ${displayMetrics.density}
                """.trimIndent()
                
                detailText.text = screenInfo
                
                Timber.d("屏幕信息详情: $screenInfo")
            } catch (e: Exception) {
                Timber.e(e, "调整图标位置失败")
                detailText.text = "获取屏幕信息失败: ${e.message}"
            }
        }
    }
    
    /**
     * 获取状态栏高度
     */
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
    
    /**
     * 获取手机真实屏幕高度（物理屏幕，不包括系统栏、导航栏等）
     */
    private fun getRealScreenHeight(): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ 使用新API
            val windowMetrics = windowManager.currentWindowMetrics
            windowMetrics.bounds.height()
        } else {
            // Android 10及以下使用传统方法
            val displayMetrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            displayMetrics.heightPixels
        }
    }
    
    /**
     * 获取导航栏高度
     */
    private fun getNavigationBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
    
    /**
     * 计算启动主题中图标的理论位置
     * 模拟 android:gravity="center" android:top="-100dp" 的效果
     */
    private fun calculateSplashThemeIconPosition(screenHeight: Int, displayMetrics: android.util.DisplayMetrics): Float {
        val iconHeightPx = 280 * displayMetrics.density
        val offsetPx = 100 * displayMetrics.density
        
        // 启动主题逻辑：屏幕中心 - 图标高度的一半 - 100dp偏移
        return (screenHeight / 2) - (iconHeightPx / 2) - offsetPx
    }
    
    /**
     * 启动主题动画 - 控制何时进入加载页面
     */
    private fun startSplashThemeAnimation() {
        // 初始时隐藏所有加载页面内容，只显示背景和居中图标
        progressBar.alpha = 0f
        statusText.alpha = 0f
        findViewById<TextView>(R.id.appTitle).alpha = 0f
        findViewById<TextView>(R.id.appSubtitle).visibility = android.view.View.GONE
        buildDictButton.alpha = 0f
        
        // 延迟1.5秒后进入加载页面
        handler.postDelayed({
            showLoadingPage()
        }, 1500)
    }
    
    /**
     * 显示加载页面 - 图标保持居中不变，只显示底部文字
     */
    private fun showLoadingPage() {
        // 图标保持原样，不做任何改变
        // 计算并显示图标距离屏幕的信息
        calculateAndShowIconDistance()
        
        // 隐藏其他所有元素
        progressBar.alpha = 0f
        statusText.alpha = 0f
        findViewById<TextView>(R.id.appTitle).alpha = 0f
        buildDictButton.alpha = 0f
    }
    
    /**
     * 计算并显示图标距离屏幕的信息
     */
    private fun calculateAndShowIconDistance() {
        splashIcon.post {
            try {
                val displayMetrics = resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels
                val statusBarHeight = getStatusBarHeight()
                
                // 获取图标的实际位置
                val iconTop = splashIcon.top
                val iconBottom = splashIcon.bottom
                val iconHeight = iconBottom - iconTop
                
                // 计算距离
                val distanceFromTop = iconTop
                val distanceFromBottom = screenHeight - iconBottom
                val distanceFromScreenCenter = Math.abs((screenHeight / 2) - (iconTop + iconHeight / 2))
                
                // 计算启动主题中图标的理论位置
                val splashThemeIconTop = calculateSplashThemeIconPosition(screenHeight, displayMetrics)
                val splashThemeIconBottom = splashThemeIconTop + (280 * displayMetrics.density)
                val splashThemeDistanceFromTop = splashThemeIconTop
                val splashThemeDistanceFromBottom = screenHeight - splashThemeIconBottom
                
                // 显示距离信息对比
                val difference = (distanceFromTop - splashThemeDistanceFromTop).toInt()
                val distanceInfo = """
                    加载页面: 距顶部 ${distanceFromTop}px (${(distanceFromTop / displayMetrics.density).toInt()}dp)
                    启动主题: 距顶部 ${splashThemeDistanceFromTop.toInt()}px (${(splashThemeDistanceFromTop / displayMetrics.density).toInt()}dp)
                    差异: ${difference}px (${(difference / displayMetrics.density).toInt()}dp)
                    ${if (difference > 0) "加载页面图标偏下" else if (difference < 0) "加载页面图标偏上" else "位置一致"}
                """.trimIndent()
                
                detailText.text = distanceInfo
                detailText.alpha = 1f
                detailText.gravity = android.view.Gravity.CENTER
                
                Timber.d("图标位置信息: $distanceInfo")
            } catch (e: Exception) {
                Timber.e(e, "计算图标距离失败")
                detailText.text = "加载页面"
                detailText.alpha = 1f
                detailText.gravity = android.view.Gravity.CENTER
            }
        }
    }
    

    
    /**
     * 显示加载页面内容
     */
    private fun showLoadingPageContent() {
        // 淡入显示进度相关元素（标题和副标题已经显示了）
        progressBar.animate().alpha(1f).setDuration(400).start()
        statusText.animate().alpha(1f).setDuration(400).setStartDelay(100).start()
        detailText.animate().alpha(1f).setDuration(400).setStartDelay(200).start()
    }

    /**
     * 优化的分阶段初始化流程
     */
    private fun startOptimizedInitialization() {
        lifecycleScope.launch {
            try {
                // 阶段1: 数据库初始化 (0-40%)
                executePhase(StartupPhase.DATABASE_INIT, 0, 40) {
                    initializeDatabase()
                }
                
                // 阶段2: 内存清理 (40-50%)
                executePhase(StartupPhase.MEMORY_CLEANUP, 40, 50) {
                    performMemoryCleanup()
                }
                
                // 阶段3: 核心词典加载 (50-90%)
                executePhase(StartupPhase.CORE_DICT_LOADING, 50, 90) {
                    loadCoreCharsDictionary()
                }
                
                // 阶段4: 启动完成 (90-100%)
                executePhase(StartupPhase.STARTUP_COMPLETE, 90, 100) {
                    finalizeStartup()
                }
                
                // 跳转到主界面
                navigateToMainActivity()
                
            } catch (e: Exception) {
                Timber.e(e, "启动初始化失败")
                handleStartupError(e)
            }
        }
    }
    
    /**
     * 执行启动阶段
     */
    private suspend fun executePhase(
        phase: StartupPhase,
        startProgress: Int,
        endProgress: Int,
        action: suspend () -> Unit
    ) {
        updateProgress(startProgress, getPhaseTitle(phase), getPhaseDetail(phase))
        
        val startTime = System.currentTimeMillis()
        action()
        val duration = System.currentTimeMillis() - startTime
        
        updateProgress(endProgress, getPhaseTitle(phase), "完成 (耗时${duration}ms)")
        Timber.i("${getPhaseTitle(phase)}完成，耗时: ${duration}ms")
        
        // 短暂延迟，让用户看到进度
        delay(200)
    }
    
    /**
     * 阶段1: 数据库初始化
     */
    private suspend fun initializeDatabase() = withContext(Dispatchers.IO) {
        updateDetail("检查Realm数据库状态...")
        
        try {
            // 检查Realm实例是否可用
            val realm = ShenjiApplication.realm
            val entryCount = try {
                realm.query(Entry::class).count().find()
            } catch (e: Exception) {
                Timber.w(e, "Realm数据库查询失败")
                0
            }
            
            updateDetail("数据库状态: ${entryCount}个词条")
            Timber.i("数据库验证完成 - 词条数: $entryCount")
            
            if (entryCount > 0) {
                updateDetail("数据库连接正常")
            } else {
                updateDetail("数据库为空，将使用降级模式")
                Timber.w("数据库为空，可能需要重新初始化")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "数据库状态检查失败")
            updateDetail("数据库检查异常: ${e.message}")
            // 不抛出异常，让应用继续启动
        }
    }
    
    /**
     * 阶段2: 内存清理
     */
    private suspend fun performMemoryCleanup() = withContext(Dispatchers.IO) {
        updateDetail("正在清理内存...")
        
        val runtime = Runtime.getRuntime()
        val beforeCleanup = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        
        // 强制垃圾回收
        updateDetail("执行垃圾回收...")
        System.gc()
        delay(500) // 等待GC完成
        
        val afterCleanup = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val freed = beforeCleanup - afterCleanup
        
        updateDetail("内存清理完成，释放了${freed}MB")
        Timber.i("内存清理: 清理前${beforeCleanup}MB -> 清理后${afterCleanup}MB，释放${freed}MB")
        
        // 记录当前内存状态
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val usedMemory = afterCleanup
        val freeMemory = maxMemory - usedMemory
        
        Timber.i("内存状态: 最大${maxMemory}MB，已用${usedMemory}MB，可用${freeMemory}MB")
        updateDetail("可用内存: ${freeMemory}MB")
    }
    
    /**
     * 阶段3: 加载核心chars和base词典
     */
    private suspend fun loadCoreCharsDictionary() = withContext(Dispatchers.IO) {
        updateDetail("正在加载核心词典...")
        
        try {
            val trieManager = TrieManager.instance
            trieManager.init()
            
            var loadedCount = 0
            var totalTime = 0L
            
            // 加载chars词典
            if (trieManager.isTrieFileExists(TrieType.CHARS)) {
                updateDetail("正在加载单字词典...")
                
                val startTime = System.currentTimeMillis()
                val success = trieManager.loadTrieToMemory(TrieType.CHARS)
                val loadTime = System.currentTimeMillis() - startTime
                totalTime += loadTime
                
                if (success) {
                    loadedCount++
                    updateDetail("单字词典加载成功 (${loadTime}ms)")
                    Timber.i("chars词典加载成功，耗时: ${loadTime}ms")
                } else {
                    updateDetail("单字词典加载失败")
                    Timber.w("chars词典加载失败")
                }
            } else {
                updateDetail("未找到单字词典文件")
                Timber.w("chars词典文件不存在")
            }
            
            // 加载base词典
            if (trieManager.isTrieFileExists(TrieType.BASE)) {
                updateDetail("正在加载基础词典...")
                
                val startTime = System.currentTimeMillis()
                val success = trieManager.loadTrieToMemory(TrieType.BASE)
                val loadTime = System.currentTimeMillis() - startTime
                totalTime += loadTime
                
                if (success) {
                    loadedCount++
                    updateDetail("基础词典加载成功 (${loadTime}ms)")
                    Timber.i("base词典加载成功，耗时: ${loadTime}ms")
                } else {
                    updateDetail("基础词典加载失败")
                    Timber.w("base词典加载失败")
                }
            } else {
                updateDetail("未找到基础词典文件")
                Timber.w("base词典文件不存在")
            }
            
            // 总结加载结果
            updateDetail("词典加载完成: ${loadedCount}/2 (总耗时${totalTime}ms)")
            Timber.i("核心词典加载完成: ${loadedCount}/2个成功，总耗时: ${totalTime}ms")
            
            // 检查内存使用
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            updateDetail("当前内存使用: ${usedMemory}MB")
            
        } catch (e: Exception) {
            Timber.e(e, "加载核心词典时出错")
            updateDetail("词典加载异常: ${e.message}")
            // 不抛出异常，允许应用继续启动
        }
    }
    
    /**
     * 阶段4: 启动完成
     */
    private suspend fun finalizeStartup() = withContext(Dispatchers.IO) {
        updateDetail("正在完成启动准备...")
        
        // 最终内存状态检查
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val freeMemory = maxMemory - usedMemory
        
        updateDetail("启动完成 - 内存: ${usedMemory}/${maxMemory}MB")
        Timber.i("启动完成 - 内存使用: ${usedMemory}MB/${maxMemory}MB，剩余: ${freeMemory}MB")
        
        // 记录启动成功
        Timber.i("神迹输入法启动成功，准备进入主界面")
    }
    
    /**
     * 跳转到主Activity
     */
    private fun navigateToMainActivity() {
        handler.post {
            updateProgress(100, "启动完成", "正在进入主界面...")
            
            // 执行图标缩放动画
            startIconScaleAnimation {
                // 动画完成后跳转
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
                
                // 添加淡入淡出动画
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
    }
    
    /**
     * 启动图标缩放动画
     */
    private fun startIconScaleAnimation(onComplete: () -> Unit) {
        // 创建缩放动画：从280dp缩小到120dp (模拟切换到加载页的效果)
        val scaleXAnimator = ObjectAnimator.ofFloat(splashIcon, "scaleX", 1.0f, 0.43f)
        val scaleYAnimator = ObjectAnimator.ofFloat(splashIcon, "scaleY", 1.0f, 0.43f)
        
        // 创建动画集合
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleXAnimator, scaleYAnimator)
        animatorSet.duration = 300 // 300ms动画时长
        
        // 动画完成后执行回调
        animatorSet.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete()
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
        
        animatorSet.start()
    }
    
    /**
     * 处理启动错误
     */
    private fun handleStartupError(error: Exception) {
        handler.post {
            progressBar.progress = 100
            statusText.text = "启动遇到问题"
            detailText.text = "错误: ${error.message ?: "未知错误"}"
            
            Timber.e(error, "启动失败")
        }
        
        // 延迟后仍然跳转，避免卡在启动页
        handler.postDelayed({
            navigateToMainActivity()
        }, 3000)
    }
    
    /**
     * 更新进度
     */
    private fun updateProgress(progress: Int, status: String, detail: String) {
        handler.post {
            progressBar.progress = progress
            statusText.text = status
            detailText.text = detail
        }
    }
    
    /**
     * 更新详细信息
     */
    private suspend fun updateDetail(detail: String) {
        withContext(Dispatchers.Main) {
            detailText.text = detail
        }
    }
    
    /**
     * 获取阶段标题
     */
    private fun getPhaseTitle(phase: StartupPhase): String {
        return when (phase) {
            StartupPhase.DATABASE_INIT -> "初始化数据库"
            StartupPhase.MEMORY_CLEANUP -> "优化内存"
            StartupPhase.CORE_DICT_LOADING -> "加载核心词典"
            StartupPhase.STARTUP_COMPLETE -> "启动完成"
        }
    }
    
    /**
     * 获取阶段详细描述
     */
    private fun getPhaseDetail(phase: StartupPhase): String {
        return when (phase) {
            StartupPhase.DATABASE_INIT -> "正在初始化Realm数据库..."
            StartupPhase.MEMORY_CLEANUP -> "正在清理内存，优化性能..."
            StartupPhase.CORE_DICT_LOADING -> "正在加载核心词典..."
            StartupPhase.STARTUP_COMPLETE -> "正在完成启动准备..."
        }
    }
    
    override fun onBackPressed() {
        // 在启动页禁用返回键，防止用户意外退出
        // super.onBackPressed()
    }
} 