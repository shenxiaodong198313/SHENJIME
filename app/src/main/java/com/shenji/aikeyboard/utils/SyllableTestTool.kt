package com.shenji.aikeyboard.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.shenji.aikeyboard.utils.PinyinSegmenter
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 音节测试工具类 - 用于测试拼音音节分割功能
 */
class SyllableTestTool(private val context: Context) {
    
    private val TAG = "SyllableTestTool"
    private val syllablesByLength: Map<Int, Set<String>>
    private val dateFormat = SimpleDateFormat("[yyyy-MM-dd HH:mm:ss.SSS]", Locale.getDefault())
    private var logBuffer = StringBuilder()
    
    init {
        // 根据音节长度初始化音节集合
        val allSyllables = getAllSyllables()
        syllablesByLength = allSyllables.groupBy { it.length }.mapValues { entry -> entry.value.toSet() }
        setupLogger()
    }
    
    /**
     * 初始化日志系统
     */
    private fun setupLogger() {
        // 确保日志目录存在
        val logDir = File(context.getExternalFilesDir(null), "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        
        // 创建日志文件
        val logFile = File(logDir, "syllable_test.log")
        if (!logFile.exists()) {
            logFile.createNewFile()
        }
        
        log(LogLevel.INFO, "音节测试工具初始化完成，共载入${getAllSyllables().size}个音节")
    }
    
    /**
     * 记录日志
     */
    private fun log(level: LogLevel, message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "$timestamp[${level.name}] $message"
        
        // 添加到内存缓冲区
        logBuffer.append(logMessage).append("\n")
        
        // 根据日志级别使用不同的Timber方法
        when (level) {
            LogLevel.DEBUG -> Timber.d(message)
            LogLevel.INFO -> Timber.i(message)
            LogLevel.WARNING -> Timber.w(message)
            LogLevel.ERROR -> Timber.e(message)
        }
        
        // 写入文件
        try {
            val logFile = File(context.getExternalFilesDir(null), "logs/syllable_test.log")
            FileWriter(logFile, true).use { it.write("$logMessage\n") }
        } catch (e: Exception) {
            Timber.e(e, "无法写入日志文件")
        }
    }
    
    /**
     * 获取所有日志内容
     */
    fun getLogContent(): String {
        return logBuffer.toString()
    }
    
    /**
     * 清除日志缓冲区
     */
    fun clearLogs() {
        logBuffer.clear()
        log(LogLevel.INFO, "日志已清除")
    }
    
    /**
     * 测试单个拼音输入
     */
    fun testPinyin(inputPinyin: String): TestResult {
        log(LogLevel.INFO, "开始测试输入: $inputPinyin")
        
        try {
            // 预处理输入(转小写，过滤非法字符)
            val cleaned = preprocess(inputPinyin)
            if (cleaned == null) {
                log(LogLevel.ERROR, "输入包含非法字符，预处理失败")
                return TestResult.failure("Invalid characters")
            }
            
            log(LogLevel.DEBUG, "预处理后输入: $cleaned")
            
            // 使用两种方法测试分词
            val customResult = splitPinyin(cleaned)
            // 使用PinyinSegmenter分词
            val splitterResult = PinyinSegmenter.cut(cleaned)
            
            // 记录结果
            if (customResult == null) {
                log(LogLevel.WARNING, "自定义分词失败: $cleaned")
            } else {
                log(LogLevel.DEBUG, "自定义分词结果: ${customResult.joinToString(" ")}")
            }
            
            if (splitterResult.isEmpty()) {
                log(LogLevel.WARNING, "系统分词失败: $cleaned")
                return TestResult.failure("No valid syllables found")
            }
            
            log(LogLevel.INFO, "系统分词成功: ${splitterResult.joinToString(" ")}")
            
            // 比较两种方法的结果是否一致
            val isConsistent = customResult != null && 
                    customResult.size == splitterResult.size && 
                    customResult.zip(splitterResult).all { (a, b) -> a == b }
            
            if (!isConsistent) {
                log(LogLevel.WARNING, "分词结果不一致: 自定义=${customResult?.joinToString(" ")}, 系统=${splitterResult.joinToString(" ")}")
            } else if (customResult != null) {
                log(LogLevel.INFO, "分词结果一致")
            }
            
            return TestResult.success(splitterResult, isConsistent)
            
        } catch (e: Exception) {
            log(LogLevel.ERROR, "测试过程中发生错误: ${e.message}")
            Timber.e(e)
            return TestResult.error(e.toString())
        }
    }
    
    /**
     * 预处理输入(转小写，过滤非法字符)
     */
    private fun preprocess(input: String): String? {
        val lowercased = input.lowercase()
        
        // 允许拼音字母和特殊字符ü
        return if (lowercased.all { it in 'a'..'z' || it == 'ü' }) {
            lowercased
        } else {
            null
        }
    }
    
    /**
     * 自定义拼音分词算法(与系统分词对比)
     */
    private fun splitPinyin(input: String): List<String>? {
        val result = mutableListOf<String>()
        var pos = input.length
        
        while (pos > 0) {
            var matched = false
            // 从6个字符开始尝试匹配(最长拼音音节可能6个字符，如"zhuang")
            for (length in 6 downTo 1) {
                if (pos - length < 0) continue
                
                val substring = input.substring(pos - length, pos)
                val syllables = syllablesByLength[length] ?: emptySet()
                
                if (substring in syllables) {
                    result.add(substring)
                    pos -= length
                    matched = true
                    log(LogLevel.DEBUG, "匹配音节: '$substring' → 成功")
                    break
                }
            }
            
            if (!matched) {
                log(LogLevel.DEBUG, "无法匹配位置 $pos")
                return null
            }
        }
        
        return result.reversed()
    }
    
    /**
     * 运行预定义测试用例集
     */
    fun runTestSuite(): TestSummary {
        // 测试数据集合，按类别分组
        val singleSyllableTests = mapOf(
            "a" to listOf("a"),
            "o" to listOf("o"),
            "e" to listOf("e"),
            "zhi" to listOf("zhi"),
            "chi" to listOf("chi"),
            "shi" to listOf("shi"),
            "ri" to listOf("ri"),
            "zi" to listOf("zi"),
            "ci" to listOf("ci"),
            "si" to listOf("si"),
            // 增加特殊音节测试
            "nü" to listOf("nv"),
            "lüe" to listOf("lve"),
            "qiong" to listOf("qiong"),
            "juan" to listOf("juan")
        )
        
        val doubleSyllableTests = mapOf(
            "xian" to listOf("xian"),
            "xue" to listOf("xue"),
            "yun" to listOf("yun"),
            "wai" to listOf("wai"),
            "jia" to listOf("jia")
        )
        
        val multiSyllableTests = mapOf(
            "zhangsan" to listOf("zhang", "san"),
            "xianggang" to listOf("xiang", "gang"),
            "beijing" to listOf("bei", "jing"),
            "qingming" to listOf("qing", "ming"),
            "haier" to listOf("hai", "er"),
            "hongkong" to listOf("hong", "kong"),
            "changsha" to listOf("chang", "sha"),
            "shanghai" to listOf("shang", "hai"),
            "daxue" to listOf("da", "xue"),
            "renmin" to listOf("ren", "min"),
            "zhongguo" to listOf("zhong", "guo")
        )
        
        // 多音字测试
        val homophoneTests = mapOf(
            "xing" to listOf("xing"),
            "hang" to listOf("hang"),
            "chang" to listOf("chang"),
            "zhong" to listOf("zhong")
        )
        
        val boundaryTests = mapOf(
            "xianzai" to listOf("xian", "zai"),
            "guangming" to listOf("guang", "ming"),
            "bj" to listOf("bi", "ji"),
            "sh" to listOf("shi"),
            // 增加复杂最长匹配测试
            "xiangong" to listOf("xiang", "gong"),
            "zhuang" to listOf("zhuang"),
            "guangming" to listOf("guang", "ming")
        )
        
        // 首字母缩写测试
        val abbreviationTests = mapOf(
            "sh" to listOf("shi"),
            "gz" to listOf("gu", "zhou")
        )
        
        val invalidInputTests = mapOf(
            "zhang1" to null,
            "ni@you" to null,
            "abc123" to null,
            "zhx" to null,
            "erhu" to null,
            "wxyz" to null,
            "hao123" to null,
            "erhua1" to null,
            "z1h" to null,
            // 增加特殊字符与编码测试
            "zhang san" to null,
            "Ｚｈａｎｇ" to null,
            "zhōng" to null
        )
        
        // 极端无效组合
        val extremeInputTests = mapOf(
            "xyz" to null,
            "aaa" to listOf("a", "a", "a")
        )
        
        val specialTests = mapOf(
            "aaaaa" to listOf("a", "a", "a", "a", "a"),
            "zhangzhangzhang" to listOf("zhang", "zhang", "zhang")
        )
        
        // 针对修复的专项测试
        val fixedCasesTests = mapOf(
            "xiangong" to listOf("xiang", "gong"),
            "nü" to listOf("nv"),
            "lüe" to listOf("lve"),
            "wai" to listOf("wai"),
            "xianzai" to listOf("xian", "zai")
        )
        
        // 合并所有测试用例
        val allTestCases = singleSyllableTests + doubleSyllableTests + multiSyllableTests + 
                           homophoneTests + boundaryTests + abbreviationTests + 
                           invalidInputTests + extremeInputTests + specialTests + 
                           fixedCasesTests
        
        val summary = TestSummary()
        log(LogLevel.INFO, "开始运行测试套件，共${allTestCases.size}个测试用例")
        
        // 按类别执行测试并统计结果
        executeTestsByCategory("单音节测试", singleSyllableTests, summary)
        executeTestsByCategory("双音节测试", doubleSyllableTests, summary)
        executeTestsByCategory("多音节测试", multiSyllableTests, summary)
        executeTestsByCategory("多音字测试", homophoneTests, summary)
        executeTestsByCategory("边界场景测试", boundaryTests, summary)
        executeTestsByCategory("首字母缩写测试", abbreviationTests, summary)
        executeTestsByCategory("非法输入测试", invalidInputTests, summary)
        executeTestsByCategory("极端无效组合测试", extremeInputTests, summary)
        executeTestsByCategory("特殊场景测试", specialTests, summary)
        executeTestsByCategory("修复案例测试", fixedCasesTests, summary)
        
        // 生成并记录详细总结报告
        val detailedReport = summary.generateDetailedReport()
        log(LogLevel.INFO, detailedReport)
        
        return summary
    }
    
    /**
     * 按类别执行测试
     */
    private fun executeTestsByCategory(categoryName: String, testCases: Map<String, List<String>?>, summary: TestSummary) {
        log(LogLevel.INFO, "======= $categoryName (${testCases.size}个测试) =======")
        
        // 确保类别结果对象存在
        val categoryResult = summary.categoryResults.getOrPut(categoryName) { 
            TestSummary.CategoryResult() 
        }
        
        for ((pinyin, expected) in testCases) {
            val result = testPinyin(pinyin)
            
            if (result.status == TestStatus.SUCCESS) {
                val actual = result.splits
                if (expected != null && actual == expected) {
                    summary.passed++
                    categoryResult.passed++
                    log(LogLevel.INFO, "测试通过: $pinyin → ${actual.joinToString(" ")}")
                } else if (expected == null) {
                    summary.failed++
                    categoryResult.failed++
                    log(LogLevel.ERROR, "测试失败: $pinyin，期望失败但得到 ${actual.joinToString(" ")}")
                } else {
                    summary.failed++
                    categoryResult.failed++
                    log(LogLevel.ERROR, "测试失败: $pinyin，期望 ${expected.joinToString(" ")}，实际 ${actual.joinToString(" ")}")
                }
            } else {
                if (expected == null) {
                    summary.passed++
                    categoryResult.passed++
                    log(LogLevel.INFO, "非法输入测试通过: $pinyin 正确拒绝")
                } else {
                    summary.failed++
                    categoryResult.failed++
                    log(LogLevel.ERROR, "测试失败: $pinyin，错误类型: ${result.error}")
                }
            }
        }
        
        // 添加类别测试小结
        val categoryPassRate = if (testCases.size > 0) 
            (categoryResult.passed * 100.0 / testCases.size).toInt() else 0
        log(LogLevel.INFO, "------- $categoryName 测试完成: 通过 ${categoryResult.passed}/${testCases.size} (${categoryPassRate}%) -------")
    }
    
    /**
     * 日志级别枚举
     */
    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }
    
    /**
     * 测试结果状态枚举
     */
    enum class TestStatus {
        SUCCESS, FAILURE, ERROR
    }
    
    /**
     * 测试结果类
     */
    data class TestResult(
        val status: TestStatus,
        val splits: List<String> = emptyList(),
        val error: String? = null,
        val consistent: Boolean = true
    ) {
        companion object {
            fun success(splits: List<String>, consistent: Boolean = true): TestResult {
                return TestResult(TestStatus.SUCCESS, splits, consistent = consistent)
            }
            
            fun failure(error: String): TestResult {
                return TestResult(TestStatus.FAILURE, error = error)
            }
            
            fun error(error: String): TestResult {
                return TestResult(TestStatus.ERROR, error = error)
            }
        }
    }
    
    /**
     * 测试套件结果摘要
     */
    data class TestSummary(
        var passed: Int = 0,
        var failed: Int = 0,
        // 按分类统计的结果
        val categoryResults: MutableMap<String, CategoryResult> = mutableMapOf()
    ) {
        /**
         * 获取总测试数
         */
        fun getTotalTests(): Int = passed + failed
        
        /**
         * 获取通过率
         */
        fun getPassRate(): Int {
            val total = getTotalTests()
            return if (total > 0) (passed * 100.0 / total).toInt() else 0
        }
        
        /**
         * 生成详细的总结报告
         */
        fun generateDetailedReport(): String {
            val sb = StringBuilder()
            sb.appendLine("==== 音节分词测试总结报告 ====")
            sb.appendLine("总测试数: ${getTotalTests()}")
            sb.appendLine("通过: $passed")
            sb.appendLine("失败: $failed")
            sb.appendLine("通过率: ${getPassRate()}%")
            sb.appendLine()
            
            if (categoryResults.isNotEmpty()) {
                sb.appendLine("==== 分类测试结果 ====")
                categoryResults.forEach { (category, result) ->
                    val categoryPassRate = if (result.total > 0) 
                        (result.passed * 100.0 / result.total).toInt() else 0
                    sb.appendLine("$category: 通过 ${result.passed}/${result.total} (${categoryPassRate}%)")
                }
            }
            
            return sb.toString()
        }
        
        /**
         * 分类测试结果
         */
        data class CategoryResult(
            var passed: Int = 0,
            var failed: Int = 0
        ) {
            val total: Int get() = passed + failed
        }
    }
    
    private fun getAllSyllables(): Set<String> {
        // 组合声母+韵母和独立韵母
        val set = mutableSetOf<String>()
        val smb = PinyinSegmenter.javaClass.getDeclaredField("smb").apply { isAccessible = true }.get(PinyinSegmenter) as Array<String>
        val ymbmax = PinyinSegmenter.javaClass.getDeclaredField("ymbmax").apply { isAccessible = true }.get(PinyinSegmenter) as Array<String>
        val ymbmin = PinyinSegmenter.javaClass.getDeclaredField("ymbmin").apply { isAccessible = true }.get(PinyinSegmenter) as Array<String>
        for (sm in smb) {
            for (ym in ymbmax) {
                set.add(sm + ym)
            }
        }
        for (ym in ymbmin) {
            set.add(ym)
        }
        for (ym in ymbmax) {
            set.add(ym)
        }
        return set
    }
} 