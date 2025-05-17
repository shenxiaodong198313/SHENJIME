package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.utils.SyllableTestTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyllableTestActivity : AppCompatActivity() {
    
    private lateinit var etPinyinInput: TextInputEditText
    private lateinit var btnTest: Button
    private lateinit var tvTestResult: TextView
    private lateinit var btnRunTestSuite: Button
    private lateinit var tvTestSuiteResult: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnClearLog: Button
    private lateinit var btnCopyLog: Button
    
    private lateinit var syllableTestTool: SyllableTestTool
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_syllable_test)
        
        // 设置返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 初始化视图
        initViews()
        
        // 初始化测试工具
        syllableTestTool = SyllableTestTool(this)
        
        // 设置点击事件
        setupClickListeners()
        
        // 初始化日志显示
        updateLogDisplay()
    }
    
    private fun initViews() {
        etPinyinInput = findViewById(R.id.etPinyinInput)
        btnTest = findViewById(R.id.btnTest)
        tvTestResult = findViewById(R.id.tvTestResult)
        btnRunTestSuite = findViewById(R.id.btnRunTestSuite)
        tvTestSuiteResult = findViewById(R.id.tvTestSuiteResult)
        tvLog = findViewById(R.id.tvLog)
        btnClearLog = findViewById(R.id.btnClearLog)
        btnCopyLog = findViewById(R.id.btnCopyLog)
        
        // 设置回车键触发测试
        etPinyinInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                testPinyin()
                true
            } else {
                false
            }
        }
    }
    
    private fun setupClickListeners() {
        // 单个拼音测试
        btnTest.setOnClickListener {
            testPinyin()
        }
        
        // 运行测试套件
        btnRunTestSuite.setOnClickListener {
            runTestSuite()
        }
        
        // 清除日志
        btnClearLog.setOnClickListener {
            syllableTestTool.clearLogs()
            updateLogDisplay()
        }
        
        // 复制日志
        btnCopyLog.setOnClickListener {
            copyLogToClipboard()
        }
    }
    
    /**
     * 复制日志到剪贴板
     */
    private fun copyLogToClipboard() {
        val logContent = tvLog.text.toString()
        if (logContent.isNotEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("音节测试日志", logContent)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "日志为空，无法复制", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun testPinyin() {
        val inputPinyin = etPinyinInput.text.toString().trim()
        
        if (inputPinyin.isEmpty()) {
            tvTestResult.text = "请输入拼音"
            tvTestResult.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            return
        }
        
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                syllableTestTool.testPinyin(inputPinyin)
            }
            
            when (result.status) {
                SyllableTestTool.TestStatus.SUCCESS -> {
                    val splitResult = result.splits.joinToString(" ")
                    val consistencyStatus = if (result.consistent) "一致" else "不一致"
                    tvTestResult.text = "分词成功: $splitResult\n与系统分词结果$consistencyStatus"
                    tvTestResult.setTextColor(ContextCompat.getColor(this@SyllableTestActivity, 
                        if (result.consistent) android.R.color.holo_green_dark 
                        else android.R.color.holo_orange_dark))
                }
                SyllableTestTool.TestStatus.FAILURE -> {
                    tvTestResult.text = "分词失败: ${result.error ?: "未知错误"}"
                    tvTestResult.setTextColor(ContextCompat.getColor(this@SyllableTestActivity, 
                        android.R.color.holo_red_dark))
                }
                SyllableTestTool.TestStatus.ERROR -> {
                    tvTestResult.text = "测试错误: ${result.error ?: "未知错误"}"
                    tvTestResult.setTextColor(ContextCompat.getColor(this@SyllableTestActivity, 
                        android.R.color.holo_red_dark))
                }
            }
            
            // 更新日志显示
            updateLogDisplay()
        }
    }
    
    private fun runTestSuite() {
        btnRunTestSuite.isEnabled = false
        btnRunTestSuite.text = "测试中..."
        
        lifecycleScope.launch {
            val summary = withContext(Dispatchers.Default) {
                syllableTestTool.runTestSuite()
            }
            
            // 显示测试结果
            val passRate = summary.getPassRate()
            tvTestSuiteResult.text = "通过: ${summary.passed}/${summary.getTotalTests()} (${passRate}%)"
            tvTestSuiteResult.setTextColor(
                ContextCompat.getColor(
                    this@SyllableTestActivity,
                    if (summary.failed == 0) android.R.color.holo_green_dark 
                    else android.R.color.holo_red_dark
                )
            )
            
            // 恢复按钮状态
            btnRunTestSuite.isEnabled = true
            btnRunTestSuite.text = "运行测试套件"
            
            // 更新日志显示
            updateLogDisplay()
        }
    }
    
    private fun updateLogDisplay() {
        tvLog.text = syllableTestTool.getLogContent()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
} 