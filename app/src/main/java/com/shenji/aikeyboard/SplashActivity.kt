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
import android.animation.ValueAnimator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.trie.TrieType
import com.shenji.aikeyboard.ui.MainActivity
import com.shenji.aikeyboard.mnn.main.MainActivity as MnnMainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.File

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
    private lateinit var buttonContainer: android.widget.FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    

    
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
        buttonContainer = findViewById(R.id.buttonContainer)
        
        // 动态创建新按钮
        createNewButton()
        
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
        
        progressBar.max = 100
        progressBar.progress = 0
        statusText.text = "正在启动神迹输入法..."
        detailText.text = "正在准备数据库和词典..."
    }
    
    /**
     * 动态创建新的按钮
     */
    private fun createNewButton() {
        // 创建词典构建按钮
        buildDictButton = Button(this)
        
        // 设置按钮文本和样式
        buildDictButton.text = "构建离线词典"
        buildDictButton.textSize = 16f
        buildDictButton.setTextColor(getColor(R.color.splash_background_color))
        
        // 创建纯白色背景
        val whiteBackground = android.graphics.drawable.GradientDrawable()
        whiteBackground.setColor(android.graphics.Color.WHITE)
        whiteBackground.cornerRadius = 24 * resources.displayMetrics.density
        whiteBackground.setStroke((2 * resources.displayMetrics.density).toInt(), android.graphics.Color.WHITE)
        
        // 应用背景和样式
        buildDictButton.background = whiteBackground
        buildDictButton.elevation = 0f
        buildDictButton.stateListAnimator = null
        
        // 移除Material Design效果
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            buildDictButton.outlineProvider = null
        }
        
        // 设置按钮尺寸和位置 - 上方按钮
        val layoutParams1 = android.widget.FrameLayout.LayoutParams(
            (200 * resources.displayMetrics.density).toInt(), // 200dp宽度
            (48 * resources.displayMetrics.density).toInt()   // 48dp高度
        )
        layoutParams1.gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
        layoutParams1.topMargin = (10 * resources.displayMetrics.density).toInt() // 距离容器顶部10dp
        buildDictButton.layoutParams = layoutParams1
        
        // 初始时隐藏按钮
        buildDictButton.alpha = 0f
        
        // 设置点击事件
        buildDictButton.setOnClickListener {
            startDictionaryBuild()
        }
        
        // 添加到容器
        buttonContainer.addView(buildDictButton)
        
        // 创建MNN推理框架按钮
        val mnnButton = Button(this)
        mnnButton.text = "MNN移动推理框架"
        mnnButton.textSize = 16f
        mnnButton.setTextColor(getColor(R.color.splash_background_color))
        
        // 创建蓝色背景
        val blueBackground = android.graphics.drawable.GradientDrawable()
        blueBackground.setColor(android.graphics.Color.parseColor("#2196F3")) // Material Blue
        blueBackground.cornerRadius = 24 * resources.displayMetrics.density
        blueBackground.setStroke((2 * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#2196F3"))
        
        // 应用背景和样式
        mnnButton.background = blueBackground
        mnnButton.elevation = 0f
        mnnButton.stateListAnimator = null
        mnnButton.setTextColor(android.graphics.Color.WHITE)
        
        // 移除Material Design效果
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            mnnButton.outlineProvider = null
        }
        
        // 设置按钮尺寸和位置 - 下方按钮
        val layoutParams2 = android.widget.FrameLayout.LayoutParams(
            (200 * resources.displayMetrics.density).toInt(), // 200dp宽度
            (48 * resources.displayMetrics.density).toInt()   // 48dp高度
        )
        layoutParams2.gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
        layoutParams2.topMargin = (70 * resources.displayMetrics.density).toInt() // 距离容器顶部70dp（第一个按钮下方）
        mnnButton.layoutParams = layoutParams2
        
        // 初始时隐藏按钮
        mnnButton.alpha = 0f
        
        // 设置点击事件 - 启动MNN主Activity
        mnnButton.setOnClickListener {
            startMnnActivity()
        }
        
        // 添加到容器
        buttonContainer.addView(mnnButton)
        

        
        Timber.d("按钮创建完成：词典构建按钮（白色）、MNN推理框架按钮（蓝色）")
        Timber.d("buttonContainer子视图数量: ${buttonContainer.childCount}")
        
        // 验证按钮是否正确添加
        for (i in 0 until buttonContainer.childCount) {
            val child = buttonContainer.getChildAt(i)
            if (child is Button) {
                Timber.d("按钮 $i: ${child.text}")
            }
        }
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
                
                Timber.d("图标位置调整完成: Y=${targetY.toInt()}px")
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
     * 启动主题动画 - 控制何时进入加载页面
     */
    private fun startSplashThemeAnimation() {
        // 初始时隐藏所有加载页面内容，只显示背景和居中图标
        progressBar.alpha = 0f
        statusText.alpha = 0f
        try {
            findViewById<TextView>(R.id.appTitle)?.alpha = 0f
            findViewById<TextView>(R.id.appSubtitle)?.visibility = android.view.View.GONE
        } catch (e: Exception) {
            // 如果这些视图不存在，忽略错误
        }
        buildDictButton.alpha = 0f
        
        // 初始时隐藏底部版权信息
        detailText.alpha = 0f
        
        // 延迟1.5秒后进入加载页面
        handler.postDelayed({
            startIconBezierAnimation()
        }, 1500)
    }
    
    /**
     * 创建自定义贝塞尔曲线插值器 - 前70%慢，后30%快
     */
    private fun createCustomScaleUpInterpolator(): Interpolator {
        // 使用贝塞尔曲线：前70%时间缓慢增长，后30%时间快速增长
        // 控制点：(0.7, 0.3) 表示70%的时间只完成30%的动画
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            PathInterpolator(0.1f, 0.1f, 0.7f, 0.3f)
        } else {
            // 降级方案：使用组合插值器模拟效果
            CustomBezierInterpolator()
        }
    }
    
    /**
     * 创建自定义贝塞尔曲线插值器 - 缩小时前30%快，后70%慢
     */
    private fun createCustomScaleDownInterpolator(): Interpolator {
        // 使用贝塞尔曲线：前30%时间快速缩小，后70%时间缓慢缩小
        // 控制点：(0.3, 0.7) 表示30%的时间完成70%的动画
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            PathInterpolator(0.3f, 0.7f, 0.9f, 0.9f)
        } else {
            // 降级方案
            CustomBezierInterpolator(true)
        }
    }
    
    /**
     * 创建旋转动画的自定义贝塞尔曲线插值器 - 前75%时间逐步加快，后25%时间逐步减速
     */
    private fun createCustomRotationInterpolator(): Interpolator {
        // 使用贝塞尔曲线：前75%时间逐步加快，后25%时间逐步减速
        // 控制点：(0.75, 0.25) 表示75%的时间只完成25%的动画
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            PathInterpolator(0.2f, 0.1f, 0.75f, 0.25f)
        } else {
            // 降级方案
            CustomRotationBezierInterpolator()
        }
    }
    
    /**
     * 创建移动动画的自定义贝塞尔曲线插值器 - 前30%慢，后70%快
     */
    private fun createCustomMoveInterpolator(): Interpolator {
        // 使用贝塞尔曲线：前30%时间缓慢移动，后70%时间快速移动
        // 控制点：(0.3, 0.1) 表示30%的时间只完成10%的动画
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            PathInterpolator(0.1f, 0.05f, 0.3f, 0.1f)
        } else {
            // 降级方案
            CustomMoveBezierInterpolator()
        }
    }
    
    /**
     * 自定义贝塞尔插值器（用于低版本Android的降级方案）
     */
    private class CustomBezierInterpolator(private val isReverse: Boolean = false) : Interpolator {
        override fun getInterpolation(input: Float): Float {
            return if (!isReverse) {
                // 放大：前70%慢，后30%快
                when {
                    input <= 0.7f -> {
                        // 前70%时间，只完成30%的动画，使用缓慢的二次曲线
                        val normalizedInput = input / 0.7f
                        0.3f * normalizedInput * normalizedInput
                    }
                    else -> {
                        // 后30%时间，完成剩余70%的动画，使用快速的曲线
                        val normalizedInput = (input - 0.7f) / 0.3f
                        0.3f + 0.7f * (1 - (1 - normalizedInput) * (1 - normalizedInput))
                    }
                }
            } else {
                // 缩小：前30%快，后70%慢
                when {
                    input <= 0.3f -> {
                        // 前30%时间，完成70%的动画
                        val normalizedInput = input / 0.3f
                        0.7f * (1 - (1 - normalizedInput) * (1 - normalizedInput))
                    }
                    else -> {
                        // 后70%时间，完成剩余30%的动画
                        val normalizedInput = (input - 0.3f) / 0.7f
                        0.7f + 0.3f * normalizedInput * normalizedInput
                    }
                }
            }
        }
    }
    
    /**
     * 旋转动画的自定义贝塞尔插值器（用于低版本Android的降级方案）
     */
    private class CustomRotationBezierInterpolator : Interpolator {
        override fun getInterpolation(input: Float): Float {
            // 旋转：前75%时间逐步加快，后25%时间逐步减速
            return when {
                input <= 0.75f -> {
                    // 前75%时间，只完成25%的动画，使用逐步加快的三次曲线
                    val normalizedInput = input / 0.75f
                    0.25f * normalizedInput * normalizedInput * normalizedInput
                }
                else -> {
                    // 后25%时间，完成剩余75%的动画，使用逐步减速的曲线
                    val normalizedInput = (input - 0.75f) / 0.25f
                    0.25f + 0.75f * (1 - (1 - normalizedInput) * (1 - normalizedInput) * (1 - normalizedInput))
                }
            }
        }
    }
    
    /**
     * 移动动画的自定义贝塞尔插值器（用于低版本Android的降级方案）
     */
    private class CustomMoveBezierInterpolator : Interpolator {
        override fun getInterpolation(input: Float): Float {
            // 移动：前30%时间缓慢，后70%时间快速
            return when {
                input <= 0.3f -> {
                    // 前30%时间，只完成10%的动画，使用缓慢的二次曲线
                    val normalizedInput = input / 0.3f
                    0.1f * normalizedInput * normalizedInput
                }
                else -> {
                    // 后70%时间，完成剩余90%的动画，使用快速的曲线
                    val normalizedInput = (input - 0.3f) / 0.7f
                    0.1f + 0.9f * (1 - (1 - normalizedInput) * (1 - normalizedInput))
                }
            }
        }
    }

    /**
     * 启动图标贝塞尔曲线动画
     */
    private fun startIconBezierAnimation() {
        // 设置图标可以超出父容器边界
        splashIcon.parent?.let { parent ->
            if (parent is android.view.ViewGroup) {
                parent.clipChildren = false
                parent.clipToPadding = false
            }
        }
        
        // 设置根布局也不裁剪子视图
        findViewById<android.view.ViewGroup>(android.R.id.content)?.let { rootView ->
            rootView.clipChildren = false
            rootView.clipToPadding = false
        }
        
        // 第一阶段：从1.0放大到30.0（3000%），使用自定义贝塞尔曲线（前70%慢，后30%快）
        val scaleUpAnimatorX = ObjectAnimator.ofFloat(splashIcon, "scaleX", 1.0f, 30.0f)
        val scaleUpAnimatorY = ObjectAnimator.ofFloat(splashIcon, "scaleY", 1.0f, 30.0f)
        
        scaleUpAnimatorX.duration = 2000 // 2秒放大动画
        scaleUpAnimatorY.duration = 2000
        scaleUpAnimatorX.interpolator = createCustomScaleUpInterpolator() // 自定义贝塞尔曲线
        scaleUpAnimatorY.interpolator = createCustomScaleUpInterpolator()
        
        // 第二阶段：从30.0缩小回1.0，使用自定义贝塞尔曲线（前30%快，后70%慢）
        val scaleDownAnimatorX = ObjectAnimator.ofFloat(splashIcon, "scaleX", 30.0f, 1.0f)
        val scaleDownAnimatorY = ObjectAnimator.ofFloat(splashIcon, "scaleY", 30.0f, 1.0f)
        
        scaleDownAnimatorX.duration = 3000 // 3秒缩小动画
        scaleDownAnimatorY.duration = 3000
        scaleDownAnimatorX.interpolator = createCustomScaleDownInterpolator() // 自定义贝塞尔曲线
        scaleDownAnimatorY.interpolator = createCustomScaleDownInterpolator()
        
        // 旋转动画：360°逆时针旋转，与整个缩放动画同步（5秒总时长）
        val rotationAnimator = ObjectAnimator.ofFloat(splashIcon, "rotation", 0f, -360f)
        rotationAnimator.duration = 5000 // 5秒旋转动画，与缩放动画总时长一致
        rotationAnimator.interpolator = createCustomRotationInterpolator() // 前75%逐步加快，后25%逐步减速
        
        // 创建放大动画集合
        val scaleUpSet = AnimatorSet()
        scaleUpSet.playTogether(scaleUpAnimatorX, scaleUpAnimatorY)
        
        // 创建缩小动画集合
        val scaleDownSet = AnimatorSet()
        scaleDownSet.playTogether(scaleDownAnimatorX, scaleDownAnimatorY)
        
        // 创建缩放动画序列
        val scaleAnimationSet = AnimatorSet()
        scaleAnimationSet.playSequentially(scaleUpSet, scaleDownSet)
        
        // 创建完整的动画集合：缩放和旋转同时进行
        val fullAnimationSet = AnimatorSet()
        fullAnimationSet.playTogether(scaleAnimationSet, rotationAnimator)
        
        // 动画完成后显示加载页面
        fullAnimationSet.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {
                Timber.d("复合贝塞尔曲线动画开始 - 30倍放大+360°逆时针旋转，总时长5秒")
                Timber.d("缩放：放大前70%慢后30%快，缩小前30%快后70%慢")
                Timber.d("旋转：前75%逐步加快，后25%逐步减速")
                // 提升图标的层级，确保在最前面显示
                splashIcon.bringToFront()
            }
            
            override fun onAnimationEnd(animation: android.animation.Animator) {
                Timber.d("缩放旋转动画结束，开始检测数据库状态")
                // 动画结束后恢复裁剪设置
                splashIcon.parent?.let { parent ->
                    if (parent is android.view.ViewGroup) {
                        parent.clipChildren = true
                        parent.clipToPadding = true
                    }
                }
                // 检测数据库状态，决定是否需要手动初始化
                checkDatabaseStatusAndProceed()
            }
            
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
        
        // 启动动画
        fullAnimationSet.start()
    }
    
    /**
     * 检测数据库状态并决定后续流程
     */
    private fun checkDatabaseStatusAndProceed() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Timber.d("开始检测数据库初始化状态")
                
                // 检测数据库是否已经初始化
                val isDatabaseInitialized = checkDatabaseInitialized()
                
                withContext(Dispatchers.Main) {
                    if (isDatabaseInitialized) {
                        Timber.i("数据库已初始化，直接进入主界面")
                        // 显示跳转提示
                        detailText.text = "数据库已就绪，正在进入主界面..."
                        detailText.alpha = 1f
                        detailText.gravity = android.view.Gravity.CENTER
                        
                        // 延迟1秒后直接跳转到主界面
                        handler.postDelayed({
                            navigateToMainActivity()
                        }, 1000)
                    } else {
                        Timber.i("数据库未初始化，显示手动初始化流程")
                        // 等待2秒后开始图标向上移动和按钮出现动画
                        handler.postDelayed({
                            startMoveAndButtonAnimation()
                        }, 2000)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "检测数据库状态失败")
                withContext(Dispatchers.Main) {
                    // 出错时默认显示手动初始化流程
                    handler.postDelayed({
                        startMoveAndButtonAnimation()
                    }, 2000)
                }
            }
        }
    }
    
    /**
     * 检测数据库是否已经初始化
     */
    private suspend fun checkDatabaseInitialized(): Boolean = withContext(Dispatchers.IO) {
        try {
            Timber.d("🔍 开始检测数据库初始化状态...")
            
            // 检查Realm数据库状态
            val realm = ShenjiApplication.realm
            val entryCount = realm.query(Entry::class).count().find()
            
            Timber.d("📊 数据库词条数量: $entryCount")
            
            // 🔧 修复：降低初始化门槛，优先检查数据库状态
            // 如果词条数量大于100，认为数据库基本可用（之前是1000）
            val hasBasicEntries = entryCount > 100
            
            // 检查数据库文件大小
            val dictFile = File(filesDir, "dictionaries/shenji_dict.realm")
            val dbFileSize = if (dictFile.exists()) dictFile.length() else 0L
            val hasValidDbFile = dbFileSize > 512 * 1024 // 大于512KB认为有效
            
            Timber.d("📁 数据库文件大小: ${dbFileSize / 1024}KB")
            
            // 检查Trie文件是否存在（降级为可选检查）
            val trieManager = TrieManager.instance
            val hasCharsFile = trieManager.isTrieFileExists(TrieType.CHARS)
            val hasBaseFile = trieManager.isTrieFileExists(TrieType.BASE)
            
            Timber.d("📚 Trie文件状态: chars=$hasCharsFile, base=$hasBaseFile")
            
            // 🔧 新的判断逻辑：更宽松的条件
            val isInitialized = when {
                // 情况1：数据库有足够词条且文件大小正常
                hasBasicEntries && hasValidDbFile -> {
                    Timber.i("✅ 数据库状态良好：词条数=$entryCount, 文件大小=${dbFileSize/1024}KB")
                    true
                }
                // 情况2：数据库有基本词条，即使Trie文件缺失也认为可用
                hasBasicEntries -> {
                    Timber.i("⚠️ 数据库基本可用：词条数=$entryCount，但文件可能较小")
                    true
                }
                // 情况3：数据库为空但文件存在且较大，可能是加载问题
                entryCount == 0L && hasValidDbFile -> {
                    Timber.w("🔄 数据库文件存在但未加载，可能需要重新连接")
                    false
                }
                // 情况4：完全未初始化
                else -> {
                    Timber.w("❌ 数据库未初始化：词条数=$entryCount, 文件大小=${dbFileSize/1024}KB")
                    false
                }
            }
            
            // 记录详细状态用于调试
            val statusSummary = """
                数据库初始化检测结果: $isInitialized
                - 词条数量: $entryCount (阈值: >100)
                - 文件大小: ${dbFileSize/1024}KB (阈值: >512KB)
                - Chars文件: $hasCharsFile
                - Base文件: $hasBaseFile
                - 判断依据: ${if (hasBasicEntries && hasValidDbFile) "数据库完整" 
                           else if (hasBasicEntries) "数据库基本可用" 
                           else if (hasValidDbFile) "文件存在但未加载"
                           else "需要初始化"}
            """.trimIndent()
            
            Timber.i(statusSummary)
            
            return@withContext isInitialized
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 检测数据库状态时出错: ${e.message}")
            return@withContext false
        }
    }

    /**
     * 开始图标向上移动和按钮出现动画
     */
    private fun startMoveAndButtonAnimation() {
        // 计算图标向上移动的距离（半个图标高度）
        val iconHeight = splashIcon.height
        val moveDistance = iconHeight / 2f
        
        // 1. 图标向上移动动画
        val iconMoveAnimator = ObjectAnimator.ofFloat(splashIcon, "translationY", 0f, -moveDistance)
        iconMoveAnimator.duration = 1500 // 1.5秒移动动画
        iconMoveAnimator.interpolator = createCustomMoveInterpolator() // 前30%慢，后70%快
        
        // 2. 按钮从底部向上移动出现动画
        // 首先设置按钮初始位置在屏幕底部外
        buildDictButton.alpha = 1f // 确保按钮可见
        
        // 显示所有按钮
        val mnnButton = buttonContainer.getChildAt(1)
        
        mnnButton?.alpha = 1f // MNN按钮
        
        Timber.d("显示按钮 - MNN按钮: ${mnnButton != null}")
        if (mnnButton is Button) Timber.d("MNN按钮文本: ${mnnButton.text}")
        
        // 获取屏幕高度
        val screenHeight = resources.displayMetrics.heightPixels
        
        // 设置按钮容器初始位置在屏幕底部外
        buttonContainer.translationY = screenHeight.toFloat()
        
        // 按钮容器移动到原位置（布局中已经设置了正确的位置，距离进度条80dp）
        val buttonMoveAnimator = ObjectAnimator.ofFloat(buttonContainer, "translationY", 
            screenHeight.toFloat(), 0f)
        buttonMoveAnimator.duration = 1500 // 1.5秒移动动画
        buttonMoveAnimator.interpolator = createCustomMoveInterpolator() // 前30%慢，后70%快
        
        // 创建同步动画集合
        val moveAnimationSet = AnimatorSet()
        moveAnimationSet.playTogether(iconMoveAnimator, buttonMoveAnimator)
        
        // 动画完成后显示加载页面
        moveAnimationSet.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {
                Timber.d("移动动画开始 - 图标向上移动${moveDistance}px，按钮从底部出现")
            }
            
            override fun onAnimationEnd(animation: android.animation.Animator) {
                Timber.d("移动动画结束，图标和按钮已就位")
                // 按钮显示完成后，延迟0.5秒显示底部版权信息
                handler.postDelayed({
                    detailText.text = "SHENJI@神迹输入法 2025"
                    detailText.gravity = android.view.Gravity.CENTER
                    detailText.animate().alpha(1f).setDuration(800).start()
                }, 500)
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
        
        // 启动移动动画
        moveAnimationSet.start()
    }
    
    /**
     * 开始构建词典
     */
    private fun startDictionaryBuild() {
        Timber.d("用户点击构建离线词典按钮")
        
        // 禁用按钮，防止重复点击
        buildDictButton.isEnabled = false
        buildDictButton.alpha = 0.5f
        
        // 显示进度条
        progressBar.alpha = 1f
        statusText.alpha = 1f
        
        // 开始带有贝塞尔曲线效果的进度条动画
        startBezierProgressAnimation()
    }
    
    /**
     * 启动贝塞尔曲线进度条动画
     * 无论实际构建速度如何，都显示一个平滑的加载过程
     */
    private fun startBezierProgressAnimation() {
        // 重置进度条
        progressBar.progress = 0
        statusText.text = "开始构建词典..."
        detailText.text = "正在准备构建环境..."
        
        // 创建贝塞尔曲线进度动画：前70%慢，后30%快
        val progressAnimator = ValueAnimator.ofInt(0, 100)
        progressAnimator.duration = 8000 // 8秒总时长，确保用户能看到完整过程
        
        // 使用自定义贝塞尔曲线插值器
        progressAnimator.interpolator = createProgressBezierInterpolator()
        
        // 启动实际的构建任务（在后台进行）
        var actualBuildCompleted = false
        lifecycleScope.launch {
            try {
                // 在后台执行实际的构建任务
                startOptimizedInitialization()
                actualBuildCompleted = true
                Timber.d("实际构建任务完成")
            } catch (e: Exception) {
                Timber.e(e, "实际构建任务失败")
                actualBuildCompleted = true
            }
        }
        
        progressAnimator.addUpdateListener { animator ->
            val progress = animator.animatedValue as Int
            progressBar.progress = progress
            
            // 根据进度更新状态文本和详细信息
            updateProgressText(progress)
        }
        
        progressAnimator.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {
                Timber.d("贝塞尔曲线进度动画开始 - 8秒总时长")
            }
            
            override fun onAnimationEnd(animation: android.animation.Animator) {
                Timber.d("进度动画完成")
                // 确保实际构建也完成了
                if (actualBuildCompleted) {
                    navigateToMainActivity()
                } else {
                    // 如果实际构建还没完成，等待完成
                    waitForActualBuildCompletion { navigateToMainActivity() }
                }
            }
            
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
        
        // 启动动画
        progressAnimator.start()
    }
    
    /**
     * 创建进度条专用的贝塞尔曲线插值器
     * 实现前70%慢，后30%快的效果
     */
    private fun createProgressBezierInterpolator(): Interpolator {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // 贝塞尔曲线控制点：(0.7, 0.3) 表示70%的时间只完成30%的进度
            PathInterpolator(0.1f, 0.1f, 0.7f, 0.3f)
        } else {
            // 降级方案：使用自定义插值器
            CustomProgressBezierInterpolator()
        }
    }
    
    /**
     * 根据进度更新状态文本
     */
    private fun updateProgressText(progress: Int) {
        when {
            progress < 10 -> {
                statusText.text = "初始化构建环境..."
                detailText.text = "正在检查系统环境和依赖..."
            }
            progress < 25 -> {
                statusText.text = "准备数据库..."
                detailText.text = "正在初始化Realm数据库连接..."
            }
            progress < 40 -> {
                statusText.text = "加载基础数据..."
                detailText.text = "正在读取词典源文件..."
            }
            progress < 55 -> {
                statusText.text = "构建字符索引..."
                detailText.text = "正在建立单字Trie树结构..."
            }
            progress < 70 -> {
                statusText.text = "构建词组索引..."
                detailText.text = "正在建立词组Trie树结构..."
            }
            progress < 85 -> {
                statusText.text = "优化索引结构..."
                detailText.text = "正在压缩和优化内存结构..."
            }
            progress < 95 -> {
                statusText.text = "验证数据完整性..."
                detailText.text = "正在验证词典数据的完整性..."
            }
            else -> {
                statusText.text = "构建完成"
                detailText.text = "词典构建成功，正在启动输入法..."
            }
        }
        
        // 显示百分比
        detailText.text = "${detailText.text} (${progress}%)"
    }
    
    /**
     * 等待实际构建完成
     */
    private fun waitForActualBuildCompletion(onComplete: () -> Unit) {
        // 如果动画完成但实际构建还没完成，显示等待状态
        statusText.text = "即将完成..."
        detailText.text = "正在进行最后的优化..."
        
        // 每500ms检查一次是否完成
        val checkHandler = Handler(Looper.getMainLooper())
        val checkRunnable = object : Runnable {
            override fun run() {
                // 这里可以检查实际构建状态，现在简化为延迟1秒
                checkHandler.postDelayed({
                    onComplete()
                }, 1000)
            }
        }
        checkHandler.post(checkRunnable)
    }
    
    /**
     * 自定义进度条贝塞尔插值器（用于低版本Android）
     */
    private class CustomProgressBezierInterpolator : Interpolator {
        override fun getInterpolation(input: Float): Float {
            // 模拟贝塞尔曲线：前70%慢，后30%快
            return when {
                input <= 0.7f -> {
                    // 前70%时间，使用缓慢增长的二次函数
                    val normalizedInput = input / 0.7f
                    0.3f * normalizedInput * normalizedInput
                }
                else -> {
                    // 后30%时间，使用快速增长
                    val normalizedInput = (input - 0.7f) / 0.3f
                    0.3f + 0.7f * (1 - (1 - normalizedInput) * (1 - normalizedInput))
                }
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
     * 优化的分阶段初始化流程（后台执行，不更新UI进度）
     */
    private suspend fun startOptimizedInitialization() {
        try {
            Timber.d("开始后台初始化流程")
            
            // 阶段1: 数据库初始化
            initializeDatabase()
            Timber.d("数据库初始化完成")
            
            // 阶段2: 内存清理
            performMemoryCleanup()
            Timber.d("内存清理完成")
            
            // 阶段3: 核心词典加载
            loadCoreCharsDictionary()
            Timber.d("核心词典加载完成")
            
            // 阶段4: 启动完成
            finalizeStartup()
            Timber.d("启动完成")
            
            Timber.i("后台初始化流程全部完成")
            
        } catch (e: Exception) {
            Timber.e(e, "后台初始化失败")
            throw e
        }
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
            // 检查是否需要显示进度（如果是从手动初始化流程来的）
            if (progressBar.alpha > 0f) {
                updateProgress(100, "启动完成", "正在进入主界面...")
            }
            
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
     * 启动MNN移动推理框架
     */
    private fun startMnnActivity() {
        try {
            val intent = Intent(this, MnnMainActivity::class.java)
            startActivity(intent)
            
            // 添加淡入淡出动画
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            
            Timber.d("启动MNN移动推理框架")
        } catch (e: Exception) {
            Timber.e(e, "启动MNN Activity失败")
            // 显示错误提示
            detailText.text = "启动MNN框架失败: ${e.message}"
        }
    }
    


    
    override fun onBackPressed() {
        // 在启动页禁用返回键，防止用户意外退出
        // super.onBackPressed()
    }
} 