package com.shenji.aikeyboard.test

import android.content.Context
import com.shenji.aikeyboard.keyboard.SmartPinyinEngine
import com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter
import com.shenji.aikeyboard.settings.FuzzyPinyinManager
import com.shenji.aikeyboard.utils.PinyinSegmenterOptimized
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * v/ü转换功能测试类
 * 
 * 测试神迹输入法对汉语拼音v代替ü规则的支持：
 * 1. 基础v转换规则（lv→绿，nv→女等）
 * 2. 拼音分割中的v处理
 * 3. 候选词查询中的v支持
 * 4. 模糊匹配中的v/ü转换
 */
class VUConversionTest(private val context: Context) {
    
    private val smartEngine = SmartPinyinEngine.getInstance()
    private val fuzzyPinyinManager = FuzzyPinyinManager.getInstance()
    
    /**
     * 运行完整的v/ü转换测试
     */
    fun runCompleteTest(): TestReport {
        val report = TestReport()
        
        Timber.i("🧪 开始v/ü转换功能测试")
        
        // 1. 基础v转换测试
        report.addSection("基础v转换", testBasicVConversion())
        
        // 2. 拼音分割测试
        report.addSection("拼音分割", testPinyinSplitting())
        
        // 3. 候选词查询测试
        report.addSection("候选词查询", testCandidateQuery())
        
        // 4. 模糊匹配测试
        report.addSection("模糊匹配", testFuzzyMatching())
        
        // 5. 边界情况测试
        report.addSection("边界情况", testBoundaryConditions())
        
        Timber.i("🧪 v/ü转换功能测试完成")
        return report
    }
    
    /**
     * 测试基础v转换规则
     */
    private fun testBasicVConversion(): List<TestCase> {
        val testCases = mutableListOf<TestCase>()
        
        // 测试lv -> lü转换
        testCases.add(TestCase(
            name = "lv转换",
            input = "lv",
            expected = "lü",
            actual = UnifiedPinyinSplitter.preprocessVToU("lv"),
            description = "lv应该转换为lü（绿色的拼音）"
        ))
        
        // 测试nv -> nü转换
        testCases.add(TestCase(
            name = "nv转换", 
            input = "nv",
            expected = "nü",
            actual = UnifiedPinyinSplitter.preprocessVToU("nv"),
            description = "nv应该转换为nü（女性的拼音）"
        ))
        
        // 测试jv -> ju转换
        testCases.add(TestCase(
            name = "jv转换",
            input = "jv",
            expected = "ju", 
            actual = UnifiedPinyinSplitter.preprocessVToU("jv"),
            description = "jv应该转换为ju（居住的拼音）"
        ))
        
        // 测试qv -> qu转换
        testCases.add(TestCase(
            name = "qv转换",
            input = "qv",
            expected = "qu",
            actual = UnifiedPinyinSplitter.preprocessVToU("qv"),
            description = "qv应该转换为qu（去年的拼音）"
        ))
        
        // 测试xv -> xu转换
        testCases.add(TestCase(
            name = "xv转换",
            input = "xv",
            expected = "xu",
            actual = UnifiedPinyinSplitter.preprocessVToU("xv"),
            description = "xv应该转换为xu（虚心的拼音）"
        ))
        
        // 测试yv -> yu转换
        testCases.add(TestCase(
            name = "yv转换",
            input = "yv",
            expected = "yu",
            actual = UnifiedPinyinSplitter.preprocessVToU("yv"),
            description = "yv应该转换为yu（鱼类的拼音）"
        ))
        
        return testCases
    }
    
    /**
     * 测试拼音分割功能
     */
    private fun testPinyinSplitting(): List<TestCase> {
        val testCases = mutableListOf<TestCase>()
        
        val splittingTests = mapOf(
            "lvse" to listOf("lü", "se"),
            "nvhai" to listOf("nü", "hai"),
            "jvzhu" to listOf("ju", "zhu"),
            "qvnian" to listOf("qu", "nian"),
            "xvxin" to listOf("xu", "xin"),
            "yvlei" to listOf("yu", "lei"),
            "lvxing" to listOf("lü", "xing"),
            "nvren" to listOf("nü", "ren")
        )
        
        for ((input, expected) in splittingTests) {
            val actual = UnifiedPinyinSplitter.split(input)
            testCases.add(TestCase(
                name = "分割$input",
                input = input,
                expected = expected.joinToString(" + "),
                actual = actual.joinToString(" + "),
                description = "连续拼音$input 应该正确分割并处理v转换"
            ))
        }
        
        return testCases
    }
    
    /**
     * 测试候选词查询功能
     */
    private fun testCandidateQuery(): List<TestCase> {
        val testCases = mutableListOf<TestCase>()
        
        val queryTests = listOf(
            "lv" to "绿",
            "nv" to "女", 
            "jv" to "居",
            "qv" to "去",
            "xv" to "虚",
            "yv" to "鱼"
        )
        
        for ((input, expectedWord) in queryTests) {
            runBlocking {
                try {
                    val candidates = smartEngine.getCandidates(input, 10)
                    val hasExpectedWord = candidates.any { it.word.contains(expectedWord) }
                    
                    testCases.add(TestCase(
                        name = "查询$input",
                        input = input,
                        expected = "包含'$expectedWord'",
                        actual = "候选词数: ${candidates.size}, 包含'$expectedWord': $hasExpectedWord",
                        description = "输入$input 应该能找到包含'$expectedWord'的候选词"
                    ))
                } catch (e: Exception) {
                    testCases.add(TestCase(
                        name = "查询$input",
                        input = input,
                        expected = "正常查询",
                        actual = "异常: ${e.message}",
                        description = "查询过程中发生异常"
                    ))
                }
            }
        }
        
        return testCases
    }
    
    /**
     * 测试模糊匹配功能
     */
    private fun testFuzzyMatching(): List<TestCase> {
        val testCases = mutableListOf<TestCase>()
        
        // 确保v/ü模糊匹配已启用
        val originalSetting = fuzzyPinyinManager.isVEqualsU()
        fuzzyPinyinManager.setVEqualsU(true)
        
        try {
            val fuzzyTests = listOf("lv", "nv", "jv", "qv", "xv", "yv")
            
            for (input in fuzzyTests) {
                val variants = fuzzyPinyinManager.applyFuzzyRules(input)
                val hasVariants = variants.size > 1
                
                testCases.add(TestCase(
                    name = "模糊匹配$input",
                    input = input,
                    expected = "生成变体",
                    actual = "变体: ${variants.joinToString(", ")}",
                    description = "模糊匹配应该为$input 生成v/ü变体"
                ))
            }
        } finally {
            // 恢复原始设置
            fuzzyPinyinManager.setVEqualsU(originalSetting)
        }
        
        return testCases
    }
    
    /**
     * 测试边界情况
     */
    private fun testBoundaryConditions(): List<TestCase> {
        val testCases = mutableListOf<TestCase>()
        
        val boundaryTests = mapOf(
            "" to "",
            "v" to "ü",
            "vv" to "üü",
            "abc" to "abc",
            "lvlv" to "lülü",
            "nvnv" to "nünü",
            "jvqvxvyv" to "juquxuyu"
        )
        
        for ((input, expected) in boundaryTests) {
            val actual = UnifiedPinyinSplitter.preprocessVToU(input)
            testCases.add(TestCase(
                name = "边界测试'$input'",
                input = input,
                expected = expected,
                actual = actual,
                description = "边界情况处理"
            ))
        }
        
        return testCases
    }
    
    /**
     * 测试用例数据类
     */
    data class TestCase(
        val name: String,
        val input: String,
        val expected: String,
        val actual: String,
        val description: String
    ) {
        val passed: Boolean get() = expected == actual
    }
    
    /**
     * 测试报告数据类
     */
    data class TestReport(
        private val sections: MutableMap<String, List<TestCase>> = mutableMapOf()
    ) {
        fun addSection(sectionName: String, testCases: List<TestCase>) {
            sections[sectionName] = testCases
        }
        
        fun generateReport(): String {
            val report = StringBuilder()
            report.appendLine("=== 神迹输入法 v/ü转换功能测试报告 ===")
            report.appendLine("测试时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
            report.appendLine()
            
            var totalTests = 0
            var passedTests = 0
            
            for ((sectionName, testCases) in sections) {
                report.appendLine("【$sectionName】")
                
                for (testCase in testCases) {
                    totalTests++
                    if (testCase.passed) passedTests++
                    
                    val status = if (testCase.passed) "✅ 通过" else "❌ 失败"
                    report.appendLine("  $status ${testCase.name}")
                    report.appendLine("    输入: ${testCase.input}")
                    report.appendLine("    期望: ${testCase.expected}")
                    report.appendLine("    实际: ${testCase.actual}")
                    if (!testCase.passed) {
                        report.appendLine("    说明: ${testCase.description}")
                    }
                    report.appendLine()
                }
            }
            
            report.appendLine("=== 测试总结 ===")
            report.appendLine("总测试数: $totalTests")
            report.appendLine("通过数: $passedTests")
            report.appendLine("失败数: ${totalTests - passedTests}")
            report.appendLine("通过率: ${if (totalTests > 0) String.format("%.1f%%", passedTests * 100.0 / totalTests) else "0%"}")
            
            return report.toString()
        }
    }
} 