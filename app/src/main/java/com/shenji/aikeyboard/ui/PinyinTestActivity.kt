package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.utils.PinyinSegmenterTest

/**
 * 拼音测试界面
 * 用于测试和演示优化后的拼音分词功能
 */
class PinyinTestActivity : AppCompatActivity() {
    
    private lateinit var inputEditText: EditText
    private lateinit var testButton: Button
    private lateinit var testAllButton: Button
    private lateinit var resultTextView: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pinyin_test)
        
        initViews()
        setupListeners()
    }
    
    private fun initViews() {
        inputEditText = findViewById(R.id.inputEditText)
        testButton = findViewById(R.id.testButton)
        testAllButton = findViewById(R.id.testAllButton)
        resultTextView = findViewById(R.id.resultTextView)
        
        // 设置结果文本视图可滚动
        resultTextView.movementMethod = ScrollingMovementMethod()
    }
    
    private fun setupListeners() {
        // 测试单个拼音输入
        testButton.setOnClickListener {
            val input = inputEditText.text.toString().trim()
            if (input.isNotEmpty()) {
                val result = PinyinSegmenterTest.compareSegmenters(input)
                resultTextView.text = result
            } else {
                resultTextView.text = "请输入拼音字符串"
            }
        }
        
        // 测试所有预定义拼音案例
        testAllButton.setOnClickListener {
            val results = PinyinSegmenterTest.runAllTests()
            resultTextView.text = results
        }
    }
} 