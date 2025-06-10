package com.shenji.aikeyboard.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.Switch
import android.widget.TextView
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ui.FloatingWindowManager
import com.shenji.aikeyboard.ui.ScreenCapturePermissionManager
import timber.log.Timber

class InputMethodSettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_SCREEN_CAPTURE = 2001
    }

    private lateinit var btnEnableIme: Button
    private lateinit var btnSetDefaultIme: Button
    private lateinit var appIconTop: ImageView
    private lateinit var btnFuzzySettings: ImageButton
    private lateinit var switchFloatingWindow: Switch
    private lateinit var floatingWindowManager: FloatingWindowManager
    
    // 屏幕录制权限相关UI
    private lateinit var tvPermissionStatus: TextView
    private lateinit var ivPermissionIcon: ImageView
    private lateinit var btnGrantPermission: Button
    private lateinit var btnClearPermission: Button
    
    // 权限管理器
    private lateinit var permissionManager: ScreenCapturePermissionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏模式
        setupFullScreenMode()
        
        setContentView(R.layout.activity_input_method_settings)
        
        // 隐藏ActionBar
        supportActionBar?.hide()
        
        // 初始化管理器
        floatingWindowManager = FloatingWindowManager.getInstance(this)
        permissionManager = ScreenCapturePermissionManager.getInstance(this)
        
        // 初始化UI元素
        initUI()
        
        // 检测输入法状态并更新按钮
        updateButtonStates()
        
        // 更新权限状态显示
        updatePermissionStatus()
    }
    
    override fun onResume() {
        super.onResume()
        // 每次回到页面时更新按钮状态
        updateButtonStates()
        // 更新权限状态
        updatePermissionStatus()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                try {
                    // 保存屏幕录制权限
                    permissionManager.savePermission(resultCode, data)
                    updatePermissionStatus()
                    Toast.makeText(this, "屏幕录制权限授权成功！", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Timber.e(e, "保存屏幕录制权限失败")
                    Toast.makeText(this, "权限授权失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "用户拒绝了屏幕录制权限", Toast.LENGTH_SHORT).show()
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
            Timber.w("设置全屏模式失败: ${e.message}")
        }
    }
    
    private fun initUI() {
        // 设置顶部图标
        setupTopIcon()
        
        // 创建按钮
        createButtons()
        
        // 设置返回按钮
        findViewById<Button>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
        
        // 设置模糊音设置按钮
        btnFuzzySettings = findViewById(R.id.btnFuzzySettings)
        btnFuzzySettings.setOnClickListener {
            openFuzzyPinyinSettings()
        }
        
        // 设置悬浮窗开关
        setupFloatingWindowSwitch()
        
        // 设置屏幕录制权限相关UI
        setupScreenCapturePermissionUI()
    }
    
    /**
     * 设置顶部图标
     */
    private fun setupTopIcon() {
        appIconTop = findViewById(R.id.appIconTop)
        
        // 从assets加载卡通设计图片
        try {
            val inputStream = assets.open("images/appicon.png")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val drawable = BitmapDrawable(resources, bitmap)
            appIconTop.setImageDrawable(drawable)
            inputStream.close()
            Timber.d("顶部图标加载成功")
        } catch (e: Exception) {
            Timber.e("加载顶部图标失败: ${e.message}", e)
        }
    }
    
    /**
     * 创建按钮
     */
    private fun createButtons() {
        // 创建第一步按钮：勾选神迹输入法
        btnEnableIme = createStepButton(
            R.id.btnEnableImeContainer,
            "第一步 勾选神迹输入法"
        ) { openInputMethodSettings() }
        
        // 创建第二步按钮：切换到神迹输入法
        btnSetDefaultIme = createStepButton(
            R.id.btnSetDefaultImeContainer,
            "第二步 切换到神迹输入法"
        ) { openInputMethodPicker() }
        
        Timber.d("所有按钮创建完成")
    }
    
    /**
     * 创建步骤按钮
     */
    private fun createStepButton(containerId: Int, text: String, onClick: () -> Unit): Button {
        val container = findViewById<FrameLayout>(containerId)
        val button = Button(this)
        
        // 设置按钮文本和基本样式
        button.text = text
        button.textSize = 16f
        
        // 设置按钮尺寸
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (56 * resources.displayMetrics.density).toInt() // 56dp高度
        )
        button.layoutParams = layoutParams
        
        // 移除Material Design效果
        button.elevation = 0f
        button.stateListAnimator = null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            button.outlineProvider = null
        }
        
        // 设置点击事件
        button.setOnClickListener { onClick() }
        
        // 添加到容器
        container.addView(button)
        
        return button
    }
    
    /**
     * 更新按钮状态
     */
    private fun updateButtonStates() {
        val isImeEnabled = isInputMethodEnabled()
        val isImeDefault = isInputMethodDefault()
        
        Timber.d("输入法状态: 已启用=$isImeEnabled, 是默认=$isImeDefault")
        
        if (!isImeEnabled) {
            // 情况1：输入法未启用 - 第一步白色，第二步浅色
            setButtonStyle(btnEnableIme, true, false)  // 白色背景，深色文字
            setButtonStyle(btnSetDefaultIme, false, true) // 浅色背景，浅色文字
        } else if (!isImeDefault) {
            // 情况2：输入法已启用但非默认 - 第一步浅色，第二步白色
            setButtonStyle(btnEnableIme, false, false) // 浅色背景，深色文字
            setButtonStyle(btnSetDefaultIme, true, false) // 白色背景，深色文字
        } else {
            // 情况3：输入法已启用且为默认 - 两个都是浅色（已完成状态）
            setButtonStyle(btnEnableIme, false, false) // 浅色背景，深色文字
            setButtonStyle(btnSetDefaultIme, false, false) // 浅色背景，深色文字
        }
    }
    
    /**
     * 设置按钮样式
     * @param button 按钮
     * @param isActive 是否为激活状态（白色背景）
     * @param isDisabled 是否为禁用状态（浅色文字）
     */
    private fun setButtonStyle(button: Button, isActive: Boolean, isDisabled: Boolean) {
        val background = android.graphics.drawable.GradientDrawable()
        background.cornerRadius = 12 * resources.displayMetrics.density
        
        if (isActive) {
            // 白色背景，深色文字
            background.setColor(android.graphics.Color.WHITE)
            button.setTextColor(getColor(R.color.splash_background_color))
        } else {
            // 浅色背景
            background.setColor(android.graphics.Color.parseColor("#80FFFFFF")) // 50%透明的白色
            if (isDisabled) {
                // 浅色文字
                button.setTextColor(android.graphics.Color.parseColor("#80FFFFFF"))
            } else {
                // 深色文字
                button.setTextColor(getColor(R.color.splash_background_color))
            }
        }
        
        button.background = background
    }
    
    /**
     * 检查输入法是否已启用
     */
    private fun isInputMethodEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledInputMethods = imm.enabledInputMethodList
        
        return enabledInputMethods.any { 
            it.packageName == packageName 
        }
    }
    
    /**
     * 检查输入法是否为默认输入法
     */
    private fun isInputMethodDefault(): Boolean {
        val currentInputMethod = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        
        return currentInputMethod?.contains(packageName) == true
    }
    
    // 打开输入法设置界面
    private fun openInputMethodSettings() {
        try {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            Toast.makeText(this, "请在列表中启用「神迹输入法」", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Timber.e(e, "打开输入法设置失败")
            Toast.makeText(this, "打开输入法设置失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 打开输入法选择器
    private fun openInputMethodPicker() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
            Toast.makeText(this, "请选择「神迹输入法」", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Timber.e(e, "打开输入法选择器失败")
            Toast.makeText(this, "打开输入法选择器失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 打开模糊音设置页面
    private fun openFuzzyPinyinSettings() {
        try {
            val intent = Intent(this, FuzzyPinyinSettingsActivity::class.java)
            startActivity(intent)
            Timber.d("打开模糊音设置页面")
        } catch (e: Exception) {
            Timber.e(e, "打开模糊音设置页面失败")
            Toast.makeText(this, "打开模糊音设置失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 设置悬浮窗开关
     */
    private fun setupFloatingWindowSwitch() {
        switchFloatingWindow = findViewById(R.id.switch_floating_window)
        
        // 设置初始状态
        switchFloatingWindow.isChecked = floatingWindowManager.isFloatingWindowEnabled()
        
        // 设置开关监听器
        switchFloatingWindow.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 检查权限
                if (floatingWindowManager.checkAndRequestPermission()) {
                    // 有权限，直接启用
                    floatingWindowManager.setFloatingWindowEnabled(true)
                    Toast.makeText(this, "悬浮窗已启用", Toast.LENGTH_SHORT).show()
                } else {
                    // 没有权限，重置开关状态
                    switchFloatingWindow.isChecked = false
                    Toast.makeText(this, "请先授权悬浮窗权限", Toast.LENGTH_LONG).show()
                }
            } else {
                // 禁用悬浮窗
                floatingWindowManager.setFloatingWindowEnabled(false)
                Toast.makeText(this, "悬浮窗已禁用", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 设置屏幕录制权限相关UI
     */
    private fun setupScreenCapturePermissionUI() {
        tvPermissionStatus = findViewById(R.id.tv_permission_status)
        ivPermissionIcon = findViewById(R.id.iv_permission_icon)
        btnGrantPermission = findViewById(R.id.btn_grant_permission)
        btnClearPermission = findViewById(R.id.btn_clear_permission)
        
        // 设置授权按钮点击事件
        btnGrantPermission.setOnClickListener {
            requestScreenCapturePermission()
        }
        
        // 设置清除权限按钮点击事件
        btnClearPermission.setOnClickListener {
            clearScreenCapturePermission()
        }
    }
    
    /**
     * 请求屏幕录制权限
     */
    private fun requestScreenCapturePermission() {
        try {
            val intent = permissionManager.createScreenCaptureIntent()
            startActivityForResult(intent, REQUEST_CODE_SCREEN_CAPTURE)
        } catch (e: Exception) {
            Timber.e(e, "请求屏幕录制权限失败")
            Toast.makeText(this, "请求权限失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 清除屏幕录制权限
     */
    private fun clearScreenCapturePermission() {
        try {
            permissionManager.clearPermission()
            updatePermissionStatus()
            Toast.makeText(this, "已清除屏幕录制权限", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.e(e, "清除屏幕录制权限失败")
            Toast.makeText(this, "清除权限失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 更新权限状态显示
     */
    private fun updatePermissionStatus() {
        val hasPermission = permissionManager.hasStoredPermission()
        val isValid = permissionManager.isPermissionValid()
        
        // 添加调试日志
        Timber.d("更新权限状态：hasPermission=$hasPermission, isValid=$isValid")
        
        if (hasPermission && isValid) {
            // 权限已授权且有效
            tvPermissionStatus.text = "权限状态：已授权"
            ivPermissionIcon.setImageResource(android.R.drawable.ic_dialog_info)
            ivPermissionIcon.setColorFilter(getColor(android.R.color.holo_green_light))
            btnGrantPermission.isEnabled = false
            btnGrantPermission.text = "权限已授权"
            btnClearPermission.isEnabled = true
            Timber.d("权限状态显示为：已授权")
        } else if (hasPermission && !isValid) {
            // 权限已保存但失效
            tvPermissionStatus.text = "权限状态：已失效"
            ivPermissionIcon.setImageResource(android.R.drawable.ic_dialog_alert)
            ivPermissionIcon.setColorFilter(getColor(android.R.color.holo_orange_light))
            btnGrantPermission.isEnabled = true
            btnGrantPermission.text = "重新授权"
            btnClearPermission.isEnabled = true
            Timber.d("权限状态显示为：已失效")
        } else {
            // 未授权
            tvPermissionStatus.text = "权限状态：未授权"
            ivPermissionIcon.setImageResource(android.R.drawable.ic_dialog_alert)
            ivPermissionIcon.setColorFilter(getColor(android.R.color.holo_red_light))
            btnGrantPermission.isEnabled = true
            btnGrantPermission.text = "授权屏幕录制"
            btnClearPermission.isEnabled = false
            Timber.d("权限状态显示为：未授权")
        }
    }
} 