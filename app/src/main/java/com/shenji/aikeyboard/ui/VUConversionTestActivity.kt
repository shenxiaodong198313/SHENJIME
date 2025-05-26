package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.test.VUConversionTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * v/ü转换功能测试Activity
 * 
 * 提供可视化界面来测试和验证神迹输入法的v代替ü功能：
 * 1. 运行完整的v/ü转换测试套件
 * 2. 显示详细的测试结果
 * 3. 提供实时测试功能
 * 4. 生成测试报告
 */
class VUConversionTestActivity : AppCompatActivity() {
    
    private lateinit var btnRunTest: Button
    private lateinit var btnClearResults: Button
    private lateinit var btnQuickTest: Button
    private lateinit var tvResults: TextView
    private lateinit var scrollView: ScrollView
    
    private lateinit var vuTest: VUConversionTest
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vu_conversion_test)
        
        initViews()
        initTest()
        setupClickListeners()
        
        Timber.d("v/ü转换测试Activity已启动")
    }
    
    private fun initViews() {
        btnRunTest = findViewById(R.id.btn_run_test)
        btnClearResults = findViewById(R.id.btn_clear_results)
        btnQuickTest = findViewById(R.id.btn_quick_test)
        tvResults = findViewById(R.id.tv_results)
        scrollView = findViewById(R.id.scroll_view)
        
        // 设置初始状态
        tvResults.text = "点击'运行完整测试'开始v/ü转换功能测试\n\n" +
                "测试内容包括：\n" +
                "• 基础v转换规则（lv→绿，nv→女等）\n" +
                "• 拼音分割中的v处理\n" +
                "• 候选词查询中的v支持\n" +
                "• 模糊匹配中的v/ü转换\n" +
                "• 边界情况处理"
    }
    
    private fun initTest() {
        vuTest = VUConversionTest(this)
    }
    
    private fun setupClickListeners() {
        btnRunTest.setOnClickListener {
            runCompleteTest()
        }
        
        btnClearResults.setOnClickListener {
            clearResults()
        }
        
        btnQuickTest.setOnClickListener {
            runQuickTest()
        }
    }
    
    /**
     * 运行完整的v/ü转换测试
     */
    private fun runCompleteTest() {
        btnRunTest.isEnabled = false
        btnRunTest.text = "测试中..."
        
        lifecycleScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    vuTest.runCompleteTest()
                }
                
                val reportText = report.generateReport()
                
                withContext(Dispatchers.Main) {
                    tvResults.text = reportText
                    scrollToTop()
                    Toast.makeText(this@VUConversionTestActivity, "测试完成", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "运行测试失败")
                withContext(Dispatchers.Main) {
                    tvResults.text = "测试失败：${e.message}\n\n${e.stackTraceToString()}"
                    Toast.makeText(this@VUConversionTestActivity, "测试失败", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    btnRunTest.isEnabled = true
                    btnRunTest.text = "运行完整测试"
                }
            }
        }
    }
    
    /**
     * 运行快速测试
     */
    private fun runQuickTest() {
        btnQuickTest.isEnabled = false
        btnQuickTest.text = "快速测试中..."
        
        lifecycleScope.launch {
            try {
                val quickResults = withContext(Dispatchers.IO) {
                    runQuickVUTest()
                }
                
                withContext(Dispatchers.Main) {
                    tvResults.text = quickResults
                    scrollToTop()
                    Toast.makeText(this@VUConversionTestActivity, "快速测试完成", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "快速测试失败")
                withContext(Dispatchers.Main) {
                    tvResults.text = "快速测试失败：${e.message}"
                    Toast.makeText(this@VUConversionTestActivity, "快速测试失败", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    btnQuickTest.isEnabled = true
                    btnQuickTest.text = "快速测试"
                }
            }
        }
    }
    
    /**
     * 运行快速v/ü测试
     */
    private suspend fun runQuickVUTest(): String {
        val report = StringBuilder()
        report.appendLine("=== 快速v/ü转换测试 ===")
        report.appendLine("测试时间: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}")
        report.appendLine()
        
        // 测试基础转换
        val basicTests = mapOf(
            "lv" to "lü",
            "nv" to "nü", 
            "jv" to "ju",
            "qv" to "qu",
            "xv" to "xu",
            "yv" to "yu"
        )
        
        report.appendLine("【基础转换测试】")
        var passed = 0
        var total = 0
        
        for ((input, expected) in basicTests) {
            total++
            val actual = com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter.preprocessVToU(input)
            val success = actual == expected
            if (success) passed++
            
            val status = if (success) "✅" else "❌"
            report.appendLine("  $status $input → $actual (期望: $expected)")
        }
        
        report.appendLine()
        report.appendLine("【测试结果】")
        report.appendLine("通过: $passed/$total")
        report.appendLine("成功率: ${if (total > 0) String.format("%.1f%%", passed * 100.0 / total) else "0%"}")
        
        if (passed == total) {
            report.appendLine()
            report.appendLine("🎉 所有基础转换测试通过！")
            report.appendLine("神迹输入法v/ü转换功能正常工作。")
        } else {
            report.appendLine()
            report.appendLine("⚠️ 部分测试失败，请检查v/ü转换逻辑。")
        }
        
        return report.toString()
    }
    
    /**
     * 清空结果
     */
    private fun clearResults() {
        tvResults.text = "结果已清空\n\n点击测试按钮开始新的测试..."
        scrollToTop()
    }
    
    /**
     * 滚动到顶部
     */
    private fun scrollToTop() {
        scrollView.post {
            scrollView.scrollTo(0, 0)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("v/ü转换测试Activity已销毁")
    }
} 