package com.shenji.aikeyboard.tools

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.CandidateManager
import com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * 组合候选词测试Activity
 * 用于验证组合候选词生成功能
 */
class CombinationTestActivity : AppCompatActivity() {
    
    private lateinit var inputEditText: EditText
    private lateinit var resultTextView: TextView
    private lateinit var testButton: Button
    private lateinit var batchTestButton: Button
    
    private val candidateManager = CandidateManager()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_combination_test)
        
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
        inputEditText.setText("wobushibeijingren")
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
        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                
                // 1. 拼音拆分测试
                val syllables = UnifiedPinyinSplitter.split(input)
                
                // 2. 候选词生成测试
                val candidates = candidateManager.generateCandidates(input, 10)
                
                val endTime = System.currentTimeMillis()
                
                // 3. 生成测试报告
                val report = generateTestReport(input, syllables, candidates, endTime - startTime)
                
                resultTextView.text = report
                Timber.d("单个测试完成: $input")
                
            } catch (e: Exception) {
                val errorMsg = "测试失败: ${e.message}"
                resultTextView.text = errorMsg
                Timber.e(e, errorMsg)
            }
        }
    }
    
    private fun runBatchTest() {
        lifecycleScope.launch {
            try {
                val testCases = listOf(
                    "wobushibeijingren",
                    "woshibeijingren", 
                    "woainizhongguo",
                    "jintiantianqihenhao",
                    "mingtianyaokaoshi",
                    "xiexienidebangzhu"
                )
                
                val results = mutableListOf<String>()
                results.add("=== 组合候选词批量测试 ===")
                results.add("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                results.add("")
                
                for (testCase in testCases) {
                    try {
                        val startTime = System.currentTimeMillis()
                        
                        // 拼音拆分
                        val syllables = UnifiedPinyinSplitter.split(testCase)
                        
                        // 候选词生成
                        val candidates = candidateManager.generateCandidates(testCase, 5)
                        
                        val endTime = System.currentTimeMillis()
                        
                        // 添加测试结果
                        results.add("输入: '$testCase'")
                        results.add("拆分: ${syllables.joinToString(" + ")}")
                        results.add("候选词数量: ${candidates.size}")
                        if (candidates.isNotEmpty()) {
                            results.add("候选词:")
                            candidates.forEachIndexed { index, candidate ->
                                results.add("  ${index + 1}. ${candidate.word} (${candidate.type})")
                            }
                        } else {
                            results.add("❌ 未找到候选词")
                        }
                        results.add("耗时: ${endTime - startTime}ms")
                        results.add("")
                        
                    } catch (e: Exception) {
                        results.add("输入: '$testCase' - 测试失败: ${e.message}")
                        results.add("")
                        Timber.e(e, "测试失败: $testCase")
                    }
                }
                
                results.add("=== 测试完成 ===")
                
                resultTextView.text = results.joinToString("\n")
                Timber.d("批量测试完成")
                
            } catch (e: Exception) {
                val errorMsg = "批量测试失败: ${e.message}"
                resultTextView.text = errorMsg
                Timber.e(e, errorMsg)
            }
        }
    }
    
    private fun generateTestReport(
        input: String,
        syllables: List<String>,
        candidates: List<com.shenji.aikeyboard.model.Candidate>,
        duration: Long
    ): String {
        val report = StringBuilder()
        
        report.appendLine("=== 组合候选词测试报告 ===")
        report.appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        report.appendLine("输入: '$input'")
        report.appendLine("输入长度: ${input.length}")
        report.appendLine()
        
        report.appendLine("=== 拼音拆分测试 ===")
        if (syllables.isNotEmpty()) {
            report.appendLine("拆分结果: ${syllables.joinToString(" + ")}")
            report.appendLine("拆分音节数: ${syllables.size}")
            report.appendLine("✓ 拆分成功")
        } else {
            report.appendLine("❌ 拆分失败")
        }
        report.appendLine()
        
        report.appendLine("=== 候选词生成测试 ===")
        report.appendLine("生成耗时: ${duration}ms")
        report.appendLine("候选词数量: ${candidates.size}")
        
        if (candidates.isNotEmpty()) {
            report.appendLine("✓ 找到候选词")
            report.appendLine("候选词列表:")
            candidates.forEachIndexed { index, candidate ->
                report.appendLine("  ${index + 1}. ${candidate.word}")
                report.appendLine("     类型: ${candidate.type}")
                report.appendLine("     拼音: ${candidate.pinyin}")
                report.appendLine("     权重: ${"%.3f".format(candidate.finalWeight)}")
                report.appendLine("     生成器: ${candidate.source.generator}")
            }
        } else {
            report.appendLine("❌ 未找到候选词")
            report.appendLine("可能原因:")
            report.appendLine("  1. 拼音拆分错误")
            report.appendLine("  2. 数据库中无对应词条")
            report.appendLine("  3. 组合生成逻辑有问题")
        }
        report.appendLine()
        
        report.appendLine("=== 修复建议 ===")
        if (candidates.isEmpty()) {
            report.appendLine("🔧 建议检查:")
            report.appendLine("1. 验证拼音拆分是否正确")
            report.appendLine("2. 检查数据库中是否存在相关词条")
            report.appendLine("3. 验证组合候选词生成逻辑")
        } else {
            report.appendLine("✅ 测试通过，组合候选词生成正常")
        }
        
        report.appendLine()
        report.appendLine("=== 测试报告结束 ===")
        
        return report.toString()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        candidateManager.cleanup()
    }
} 