package com.shenji.aikeyboard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.databinding.ActivityMainBinding
import timber.log.Timber
import com.shenji.aikeyboard.ui.DevToolsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupUI()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }
    
    override fun onResume() {
        super.onResume()
        // 更新输入法状态
        updateInputMethodStatus()
    }
    
    private fun setupUI() {
        // 设置按钮点击事件
        binding.btnSettings.setOnClickListener {
            openInputMethodSettings()
        }
        
        binding.btnLogs.setOnClickListener {
            openLogDetail()
        }
        
        binding.btnDictManager.setOnClickListener {
            openDictManager()
        }
        
        binding.btnInputTest.setOnClickListener {
            openInputTest()
        }
        
        // 添加开发工具入口
        binding.btnDevTools.setOnClickListener {
            openDevTools()
        }
    }
    
    /**
     * 更新输入法状态
     */
    private fun updateInputMethodStatus() {
        val isEnabled = isInputMethodEnabled()
        val isSelected = isInputMethodSelected()
        
        binding.tvStatus.text = when {
            isSelected -> getString(R.string.ime_status).replace("未启用", "已启用并设为默认")
            isEnabled -> getString(R.string.ime_status).replace("未启用", "已启用但非默认")
            else -> getString(R.string.ime_status)
        }
    }
    
    /**
     * 检查输入法是否已启用
     */
    private fun isInputMethodEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val imeId = "com.shenji.aikeyboard/.ime.ShenjiInputMethodService"
        
        for (info in imm.enabledInputMethodList) {
            if (info.id == imeId) {
                return true
            }
        }
        return false
    }
    
    /**
     * 检查输入法是否被选为默认
     */
    private fun isInputMethodSelected(): Boolean {
        val currentImeId = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val imeId = "com.shenji.aikeyboard/.ime.ShenjiInputMethodService"
        return currentImeId == imeId
    }
    
    /**
     * 打开输入法设置
     */
    private fun openInputMethodSettings() {
        try {
            Timber.d("打开输入法设置")
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: Exception) {
            Timber.e(e, "打开输入法设置失败")
        }
    }
    
    /**
     * 打开日志详情
     */
    private fun openLogDetail() {
        Timber.d("打开日志详情")
        val intent = Intent(this, LogDetailActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * 打开词典管理界面
     */
    private fun openDictManager() {
        Timber.d("打开词典管理")
        val intent = Intent(this, DictionaryManagerActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * 打开输入测试界面
     */
    private fun openInputTest() {
        Timber.d("打开输入测试")
        val intent = Intent(this, InputTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * 打开开发工具界面
     */
    private fun openDevTools() {
        Timber.d("打开开发工具")
        val intent = Intent(this, DevToolsActivity::class.java)
        startActivity(intent)
    }
} 