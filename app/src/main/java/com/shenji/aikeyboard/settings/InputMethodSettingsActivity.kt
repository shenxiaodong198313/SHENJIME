package com.shenji.aikeyboard.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.R
import timber.log.Timber

class InputMethodSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_method_settings)
        
        // 设置标题
        title = getString(R.string.ime_settings)
        
        // 初始化UI元素
        initUI()
    }
    
    private fun initUI() {
        // 启用输入法按钮
        findViewById<Button>(R.id.btn_enable_ime)?.setOnClickListener {
            openInputMethodSettings()
        }
        
        // 设为默认输入法按钮
        findViewById<Button>(R.id.btn_set_default_ime)?.setOnClickListener {
            openInputMethodPicker()
        }
        
        // 模糊拼音设置按钮
        findViewById<Button>(R.id.btn_fuzzy_pinyin)?.setOnClickListener {
            openFuzzyPinyinSettings()
        }
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
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            Toast.makeText(this, "请选择「神迹输入法」作为默认输入法", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // 尝试使用备用方法
            try {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            } catch (e2: Exception) {
                Timber.e(e2, "打开输入法选择器失败")
                Toast.makeText(this, "打开输入法选择器失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 打开模糊拼音设置
    private fun openFuzzyPinyinSettings() {
        try {
            val intent = Intent(this, FuzzyPinyinSettingsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "打开模糊拼音设置失败: ${e.message}")
            Toast.makeText(this, "打开模糊拼音设置失败", Toast.LENGTH_SHORT).show()
        }
    }
} 