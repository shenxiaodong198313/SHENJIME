package com.shenji.aikeyboard.utils

import android.content.Context
import com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter
import com.shenji.aikeyboard.settings.FuzzyPinyinManager
import timber.log.Timber

/**
 * v/ü转换测试工具
 * 专门用于测试和验证v代替ü的拼音转换功能
 */
class VUConversionTestTool(private val context: Context) {
    
    private val fuzzyPinyinManager = FuzzyPinyinManager.getInstance()
    
    /**
     * 运行完整的v/ü转换测试套件
     */
    fun runFullTestSuite(): TestReport {
        val report = TestReport()
        
        // 基础转换测试
        report.addCategory("基础v/ü转换", runBasicConversionTests())
        
        // 连续拼音转换测试
        report.addCategory("连续拼音转换", runContinuousPinyinTests())
        
        // 模糊匹配测试
        report.addCategory("模糊匹配功能", runFuzzyMatchingTests())
        
        // 边界情况测试
        report.addCategory("边界情况处理", runBoundaryTests())
        
        // 性能测试
        report.addCategory("性能测试", runPerformanceTests())
        
        return report
    }
    
    /**
     * 运行基础v/ü转换测试
     */
    private fun runBasicConversionTests(): List<TestResult> {
        val results = mutableListOf<TestResult>()
        
        val basicTests = mapOf(
            "lv" to "lü",
            "lvse" to "lüse", 
            "lve" to "lüe",
            "nv" to "nü",
            "nvhai" to "nühai",
            "nver" to "nüer",
            "jv" to "ju",
            "qv" to "qu", 
            "xv" to "xu",
            "yv" to "yu"
        )
        
        for ((input, expected) in basicTests) {
            val converted = convertVToU(input)
            val passed = converted == expected
            results.add(TestResult(
                testName = "基础转换: $input -> $expected",
                input = input,
                expected = expected,
                actual = converted,
                passed = passed
            ))
            
            if (!passed) {
                Timber.w("基础转换测试失败: '$input' 期望 '$expected', 实际 '$converted'")
            }
        }
        
        return results
    }
    
    /**
     * 运行连续拼音转换测试
     */
    private fun runContinuousPinyinTests(): List<TestResult> {
        val results = mutableListOf<TestResult>()
        
        val continuousTests = mapOf(
            "lvxing" to "lüxing",
            "nvxing" to "nüxing", 
            "lvyou" to "lüyou",
            "nvren" to "nüren",
            "jvzi" to "juzi",
            "qvdao" to "qudao",
            "xvyao" to "xuyao",
            "yvle" to "yule"
        )
        
        for ((input, expected) in continuousTests) {
            val converted = convertVToU(input)
            val passed = converted == expected
            results.add(TestResult(
                testName = "连续拼音: $input -> $expected",
                input = input,
                expected = expected,
                actual = converted,
                passed = passed
            ))
        }
        
        return results
    }
    
    /**
     * 运行模糊匹配功能测试
     */
    private fun runFuzzyMatchingTests(): List<TestResult> {
        val results = mutableListOf<TestResult>()
        
        val fuzzyTests = listOf(
            "lv", "nv", "jv", "qv", "xv", "yv",
            "lvse", "nvhai", "jvzi", "qvdao"
        )
        
        for (input in fuzzyTests) {
            val variants = fuzzyPinyinManager.applyFuzzyRules(input)
            val hasVUVariants = variants.any { it != input && (it.contains('ü') || it.contains('v')) }
            
            results.add(TestResult(
                testName = "模糊匹配: $input",
                input = input,
                expected = "包含v/ü变体",
                actual = "变体数: ${variants.size}, 包含v/ü: $hasVUVariants",
                passed = hasVUVariants
            ))
        }
        
        return results
    }
    
    /**
     * 运行边界情况测试
     */
    private fun runBoundaryTests(): List<TestResult> {
        val results = mutableListOf<TestResult>()
        
        val boundaryTests = mapOf(
            "" to "",           // 空字符串
            "v" to "ü",         // 单个v
            "ü" to "ü",         // 单个ü
            "abc" to "abc",     // 无v的字符串
            "lvlv" to "lülü",   // 重复模式
            "nvnv" to "nünü",   // 重复模式
            "vlv" to "vlü",     // 混合情况
            "lvnv" to "lünü"    // 多种转换
        )
        
        for ((input, expected) in boundaryTests) {
            val converted = convertVToU(input)
            val passed = converted == expected
            results.add(TestResult(
                testName = "边界情况: $input -> $expected",
                input = input,
                expected = expected,
                actual = converted,
                passed = passed
            ))
        }
        
        return results
    }
    
    /**
     * 运行性能测试
     */
    private fun runPerformanceTests(): List<TestResult> {
        val results = mutableListOf<TestResult>()
        
        val testInputs = listOf(
            "lv", "nv", "jv", "qv", "xv", "yv",
            "lvse", "nvhai", "jvzi", "qvdao",
            "lvxingnvrenqvdaoxuyao"
        )
        
        val iterations = 1000
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            for (input in testInputs) {
                convertVToU(input)
                fuzzyPinyinManager.applyFuzzyRules(input)
            }
        }
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        val avgTime = totalTime.toDouble() / (iterations * testInputs.size)
        
        val passed = avgTime < 1.0 // 平均每次转换应该小于1ms
        
        results.add(TestResult(
            testName = "性能测试",
            input = "${iterations * testInputs.size} 次转换",
            expected = "< 1ms 平均时间",
            actual = "${String.format("%.3f", avgTime)}ms 平均时间",
            passed = passed
        ))
        
        return results
    }
    
    /**
     * v到ü的转换方法（用于测试）
     */
    private fun convertVToU(input: String): String {
        if (input.isEmpty() || !input.contains('v')) {
            return input
        }
        
        var result = input.lowercase()
        
        // 处理 lv -> lü 的情况
        result = result.replace(Regex("\\blv\\b"), "lü")
        result = result.replace(Regex("\\blv([aeiou])"), "lü$1")
        result = result.replace(Regex("\\blv([ng])"), "lü$1")
        
        // 处理 nv -> nü 的情况  
        result = result.replace(Regex("\\bnv\\b"), "nü")
        result = result.replace(Regex("\\bnv([aeiou])"), "nü$1")
        result = result.replace(Regex("\\bnv([ng])"), "nü$1")
        
        // 处理 j/q/x/y + v -> j/q/x/y + u 的情况
        result = result.replace(Regex("\\b([jqxy])v\\b"), "$1u")
        result = result.replace(Regex("\\b([jqxy])v([aeiou])"), "$1u$2")
        result = result.replace(Regex("\\b([jqxy])v([ng])"), "$1u$2")
        
        // 处理连续拼音中的v转换
        result = result.replace(Regex("lv([^aeiouüng])"), "lü$1")
        result = result.replace(Regex("nv([^aeiouüng])"), "nü$1")
        result = result.replace(Regex("([jqxy])v([^aeiouüng])"), "$1u$2")
        
        // 处理单独的v
        if (result == "v") {
            result = "ü"
        }
        
        return result
    }
    
    /**
     * 测试结果数据类
     */
    data class TestResult(
        val testName: String,
        val input: String,
        val expected: String,
        val actual: String,
        val passed: Boolean
    )
    
    /**
     * 测试报告数据类
     */
    data class TestReport(
        private val categories: MutableMap<String, List<TestResult>> = mutableMapOf()
    ) {
        fun addCategory(name: String, results: List<TestResult>) {
            categories[name] = results
        }
        
        fun generateReport(): String {
            val report = StringBuilder()
            report.appendLine("=== v/ü转换功能测试报告 ===")
            report.appendLine("测试时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
            report.appendLine()
            
            var totalTests = 0
            var passedTests = 0
            
            for ((categoryName, results) in categories) {
                val categoryPassed = results.count { it.passed }
                val categoryTotal = results.size
                
                report.appendLine("【$categoryName】")
                report.appendLine("通过: $categoryPassed/$categoryTotal")
                
                // 显示失败的测试
                val failedTests = results.filter { !it.passed }
                if (failedTests.isNotEmpty()) {
                    report.appendLine("失败的测试:")
                    for (failed in failedTests) {
                        report.appendLine("  - ${failed.testName}")
                        report.appendLine("    输入: '${failed.input}'")
                        report.appendLine("    期望: '${failed.expected}'")
                        report.appendLine("    实际: '${failed.actual}'")
                    }
                }
                report.appendLine()
                
                totalTests += categoryTotal
                passedTests += categoryPassed
            }
            
            report.appendLine("=== 总结 ===")
            report.appendLine("总测试数: $totalTests")
            report.appendLine("通过数: $passedTests")
            report.appendLine("失败数: ${totalTests - passedTests}")
            report.appendLine("通过率: ${String.format("%.1f", passedTests * 100.0 / totalTests)}%")
            
            val overallResult = if (passedTests == totalTests) "✅ 全部通过" else "❌ 存在失败"
            report.appendLine("测试结果: $overallResult")
            
            return report.toString()
        }
        
        fun isAllPassed(): Boolean {
            return categories.values.flatten().all { it.passed }
        }
        
        fun getTotalTests(): Int {
            return categories.values.flatten().size
        }
        
        fun getPassedTests(): Int {
            return categories.values.flatten().count { it.passed }
        }
    }
} 