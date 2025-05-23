package com.shenji.aikeyboard.tools

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.utils.PinyinSegmenterOptimized
import timber.log.Timber

/**
 * 拼音拆分测试Activity
 * 用于验证拼音拆分算法的修复效果
 */
class PinyinTestActivity : AppCompatActivity() {
    
    private lateinit var inputEditText: EditText
    private lateinit var resultTextView: TextView
    private lateinit var testButton: Button
    private lateinit var batchTestButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pinyin_test)
        
        initViews()
        setupListeners()
        
        // 自动运行批量测试
        runBatchTest()
    }
    
    private fun initViews() {
        inputEditText = findViewById(R.id.input_edit_text)
        resultTextView = findViewById(R.id.result_text_view)
        testButton = findViewById(R.id.test_button)
        batchTestButton = findViewById(R.id.batch_test_button)
        
        // 设置默认测试输入
        inputEditText.setText("woshibeijingren")
    }
    
    private fun setupListeners() {
        testButton.setOnClickListener {
            val input = inputEditText.text.toString().trim()
            if (input.isNotEmpty()) {
                testSingleInput(input)
            }
        }
        
        batchTestButton.setOnClickListener {
            runBatchTest()
        }
    }
    
    private fun testSingleInput(input: String) {
        try {
            val result = PinyinSegmenterOptimized.testSplit(input)
            resultTextView.text = result
            Timber.d("单个测试结果: $result")
        } catch (e: Exception) {
            val errorMsg = "测试失败: ${e.message}"
            resultTextView.text = errorMsg
            Timber.e(e, errorMsg)
        }
    }
    
    private fun runBatchTest() {
        try {
            val result = PinyinSegmenterOptimized.runBatchTest()
            resultTextView.text = result
            Timber.d("批量测试完成")
        } catch (e: Exception) {
            val errorMsg = "批量测试失败: ${e.message}"
            resultTextView.text = errorMsg
            Timber.e(e, errorMsg)
        }
    }
} 