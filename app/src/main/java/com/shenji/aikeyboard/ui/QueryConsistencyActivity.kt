package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.utils.QueryConsistencyChecker
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 查询一致性测试活动
 * 用于验证输入法和测试工具的查询结果是否一致
 */
class QueryConsistencyActivity : AppCompatActivity() {
    
    private lateinit var inputField: EditText
    private lateinit var testButton: Button
    private lateinit var runStandardButton: Button
    private lateinit var resultText: TextView
    
    private val consistencyChecker = QueryConsistencyChecker()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_query_consistency)
        
        // 设置返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "查询一致性测试"
        
        // 初始化视图
        inputField = findViewById(R.id.input_field)
        testButton = findViewById(R.id.test_button)
        runStandardButton = findViewById(R.id.run_standard_button)
        resultText = findViewById(R.id.result_text)
        
        // 设置测试按钮点击事件
        testButton.setOnClickListener {
            val input = inputField.text.toString().trim()
            if (input.isNotEmpty()) {
                testInput(input)
            }
        }
        
        // 设置运行标准测试按钮点击事件
        runStandardButton.setOnClickListener {
            runStandardTests()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    /**
     * 测试单个输入
     */
    private fun testInput(input: String) {
        resultText.text = "正在测试: '$input'..."
        
        lifecycleScope.launch {
            try {
                val result = consistencyChecker.checkConsistency(input)
                
                val sb = StringBuilder()
                sb.appendLine("测试结果: ${if (result.consistent) "一致 ✓" else "不一致 ✗"}")
                sb.appendLine("输入: '$input'")
                sb.appendLine("阶段: ${result.stage}")
                
                if (result.syllables.isNotEmpty()) {
                    sb.appendLine("音节拆分: ${result.syllables.joinToString("+")}")
                }
                
                sb.appendLine("\n输入法结果: (${result.inputMethodResults.size}个)")
                result.inputMethodResults.forEachIndexed { index, word ->
                    sb.appendLine("${index + 1}. $word")
                }
                
                sb.appendLine("\n测试工具结果: (${result.testToolResults.size}个)")
                result.testToolResults.forEachIndexed { index, word ->
                    sb.appendLine("${index + 1}. $word")
                }
                
                if (!result.consistent) {
                    sb.appendLine("\n差异:")
                    if (result.missingInInputMethod.isNotEmpty()) {
                        sb.appendLine("输入法缺少: ${result.missingInInputMethod.joinToString(", ")}")
                    }
                    if (result.missingInTestTool.isNotEmpty()) {
                        sb.appendLine("测试工具缺少: ${result.missingInTestTool.joinToString(", ")}")
                    }
                }
                
                resultText.text = sb.toString()
            } catch (e: Exception) {
                Timber.e(e, "测试异常")
                resultText.text = "测试异常: ${e.message}"
            }
        }
    }
    
    /**
     * 运行标准测试集
     */
    private fun runStandardTests() {
        resultText.text = "正在运行标准测试集..."
        
        lifecycleScope.launch {
            try {
                val testCases = listOf(
                    "w",            // 单字母
                    "wei",          // 单音节
                    "nihao",        // 音节拆分
                    "wx",           // 首字母缩写
                    "weix",         // 首字母缩写
                    "weixin",       // 音节拆分
                    "beijing",      // 音节拆分
                    "zhongwen",     // 音节拆分
                    "zhongguo"      // 音节拆分
                )
                
                val results = consistencyChecker.runTestCases(testCases)
                val report = consistencyChecker.generateReport(results)
                
                resultText.text = report
            } catch (e: Exception) {
                Timber.e(e, "运行标准测试异常")
                resultText.text = "运行标准测试异常: ${e.message}"
            }
        }
    }
} 