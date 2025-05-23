package com.shenji.aikeyboard.utils

import com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter
import com.shenji.aikeyboard.utils.PinyinSegmenterOptimized
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 拼音优化测试套件
 * 用于验证算法优化和架构统一的效果
 */
object PinyinOptimizationTestSuite {
    
    /**
     * 测试用例数据
     */
    private val testCases = mapOf(
        // 基础测试用例
        "基础音节" to mapOf(
            "a" to listOf("a"),
            "wo" to listOf("wo"),
            "ni" to listOf("ni"),
            "ta" to listOf("ta")
        ),
        
        // 双音节测试
        "双音节" to mapOf(
            "nihao" to listOf("ni", "hao"),
            "beijing" to listOf("bei", "jing"),
            "shanghai" to listOf("shang", "hai"),
            "guangzhou" to listOf("guang", "zhou")
        ),
        
        // 多音节测试
        "多音节" to mapOf(
            "zhongguo" to listOf("zhong", "guo"),
            "zhonghua" to listOf("zhong", "hua"),
            "shehuizhuyi" to listOf("she", "hui", "zhu", "yi"),
            "makesizhuyizhexue" to listOf("ma", "ke", "si", "zhu", "yi", "zhe", "xue")
        ),
        
        // 复杂拼音测试
        "复杂拼音" to mapOf(
            "chuangxin" to listOf("chuang", "xin"),
            "xiandaihua" to listOf("xian", "dai", "hua"),
            "xinshidai" to listOf("xin", "shi", "dai"),
            "gaigekafang" to listOf("gai", "ge", "ka", "fang")
        ),
        
        // 边界情况测试
        "边界情况" to mapOf(
            "zhi" to listOf("zhi"),
            "chi" to listOf("chi"),
            "shi" to listOf("shi"),
            "ri" to listOf("ri"),
            "zi" to listOf("zi"),
            "ci" to listOf("ci"),
            "si" to listOf("si")
        ),
        
        // 长拼音测试
        "长拼音" to mapOf(
            "zhonghuarenmingongheguo" to listOf("zhong", "hua", "ren", "min", "gong", "he", "guo"),
            "shehuizhuyihexinjiazhi" to listOf("she", "hui", "zhu", "yi", "he", "xin", "jia", "zhi")
        ),
        
        // 新增：分段匹配测试用例
        "分段匹配" to mapOf(
            "wofaxianshujukuyouwenti" to listOf("wo", "fa", "xian", "shu", "ju", "ku", "you", "wen", "ti"),
            "nihaoshijiehenmeihao" to listOf("ni", "hao", "shi", "jie", "hen", "mei", "hao"),
            "zhongguorenmindaxuexue" to listOf("zhong", "guo", "ren", "min", "da", "xue", "xue")
        )
    )
    
    /**
     * 性能测试用例
     */
    private val performanceTestCases = listOf(
        "nihao", "beijing", "shanghai", "guangzhou", "shenzhen",
        "zhongguo", "zhonghua", "renmin", "gongheguo", "shehuizhuyi",
        "chuangxin", "fazhan", "gaige", "kaifang", "xiandaihua",
        "xinshidai", "xinfazhan", "xinlinian", "xingeju", "xinzhengchang"
    )
    
    /**
     * 运行完整测试套件
     */
    suspend fun runFullTestSuite(): TestSuiteResult = withContext(Dispatchers.IO) {
        val result = TestSuiteResult()
        
        Timber.i("=== 拼音优化测试套件开始 ===")
        
        // 1. 功能正确性测试
        Timber.i("1. 运行功能正确性测试...")
        val functionalResult = runFunctionalTests()
        result.functionalTestResult = functionalResult
        
        // 2. 性能基准测试
        Timber.i("2. 运行性能基准测试...")
        val performanceResult = runPerformanceTests()
        result.performanceTestResult = performanceResult
        
        // 3. 缓存效果测试
        Timber.i("3. 运行缓存效果测试...")
        val cacheResult = runCacheEffectivenessTest()
        result.cacheTestResult = cacheResult
        
        // 4. 架构统一测试
        Timber.i("4. 运行架构统一测试...")
        val architectureResult = runArchitectureUnificationTest()
        result.architectureTestResult = architectureResult
        
        // 5. 生成综合报告
        val report = generateComprehensiveReport(result)
        result.comprehensiveReport = report
        
        Timber.i("=== 拼音优化测试套件完成 ===")
        Timber.i(report)
        
        return@withContext result
    }
    
    /**
     * 功能正确性测试
     */
    private fun runFunctionalTests(): FunctionalTestResult {
        val result = FunctionalTestResult()
        var totalTests = 0
        var passedTests = 0
        
        for ((category, cases) in testCases) {
            Timber.d("测试分类: $category")
            
            for ((input, expected) in cases) {
                totalTests++
                val actual = UnifiedPinyinSplitter.split(input)
                
                if (actual == expected) {
                    passedTests++
                    Timber.v("✅ '$input' -> ${actual.joinToString("+")} (正确)")
                } else {
                    Timber.w("❌ '$input' -> 期望: ${expected.joinToString("+")}, 实际: ${actual.joinToString("+")}")
                    result.failedCases.add(FunctionalTestResult.FailedCase(input, expected, actual))
                }
            }
        }
        
        result.totalTests = totalTests
        result.passedTests = passedTests
        result.successRate = if (totalTests > 0) passedTests * 100.0 / totalTests else 0.0
        
        return result
    }
    
    /**
     * 性能基准测试
     */
    private fun runPerformanceTests(): PerformanceTestResult {
        val result = PerformanceTestResult()
        
        // 重置性能统计
        UnifiedPinyinSplitter.resetPerformanceStats()
        
        // 冷启动测试
        val coldStartTime = measureTimeMillis {
            performanceTestCases.forEach { input ->
                UnifiedPinyinSplitter.split(input)
            }
        }
        
        val coldStats = UnifiedPinyinSplitter.getPerformanceStats()
        result.coldStartTime = coldStartTime
        result.coldStartStats = coldStats
        
        // 热启动测试（重复执行）
        val hotStartTime = measureTimeMillis {
            repeat(3) {
                performanceTestCases.forEach { input ->
                    UnifiedPinyinSplitter.split(input)
                }
            }
        }
        
        val hotStats = UnifiedPinyinSplitter.getPerformanceStats()
        result.hotStartTime = hotStartTime
        result.hotStartStats = hotStats
        
        // 计算性能提升
        result.speedupRatio = if (hotStartTime > 0) coldStartTime.toDouble() / hotStartTime.toDouble() else 0.0
        
        return result
    }
    
    /**
     * 缓存效果测试
     */
    private fun runCacheEffectivenessTest(): CacheTestResult {
        val result = CacheTestResult()
        
        // 清空缓存
        UnifiedPinyinSplitter.clearCache()
        UnifiedPinyinSplitter.resetPerformanceStats()
        
        // 第一轮：缓存填充
        performanceTestCases.forEach { input ->
            UnifiedPinyinSplitter.split(input)
        }
        val firstRoundStats = UnifiedPinyinSplitter.getPerformanceStats()
        
        // 第二轮：缓存命中
        performanceTestCases.forEach { input ->
            UnifiedPinyinSplitter.split(input)
        }
        val secondRoundStats = UnifiedPinyinSplitter.getPerformanceStats()
        
        result.firstRoundStats = firstRoundStats
        result.secondRoundStats = secondRoundStats
        result.cacheHitRate = secondRoundStats.cacheHitRate
        result.fastPathRate = secondRoundStats.fastPathRate
        
        return result
    }
    
    /**
     * 架构统一测试
     */
    private fun runArchitectureUnificationTest(): ArchitectureTestResult {
        val result = ArchitectureTestResult()
        
        // 测试统一接口
        val testInput = "nihao"
        
        // 主要接口测试
        val splitResult = UnifiedPinyinSplitter.split(testInput)
        val multipleSplits = UnifiedPinyinSplitter.getMultipleSplits(testInput)
        val dynamicSplit = UnifiedPinyinSplitter.splitDynamic(testInput)
        val smartSplit = UnifiedPinyinSplitter.splitSmart(testInput)
        
        result.mainInterfaceWorking = splitResult.isNotEmpty()
        result.multipleInterfaceWorking = multipleSplits.isNotEmpty()
        result.dynamicInterfaceWorking = dynamicSplit.isNotEmpty()
        result.smartInterfaceWorking = smartSplit.isNotEmpty()
        
        // 辅助功能测试
        val isValid = UnifiedPinyinSplitter.isValidSyllable("ni")
        val syllableCount = UnifiedPinyinSplitter.getValidSyllables().size
        val initials = UnifiedPinyinSplitter.generateInitials(testInput)
        val normalized = UnifiedPinyinSplitter.normalize(testInput)
        
        result.validationWorking = isValid
        result.syllableCountCorrect = syllableCount > 400 // 应该有400+个音节
        result.initialsWorking = initials.isNotEmpty()
        result.normalizationWorking = normalized.contains(" ")
        
        // 性能监控测试
        val stats = UnifiedPinyinSplitter.getPerformanceStats()
        result.performanceMonitoringWorking = stats.totalRequests > 0
        
        // 自测试
        result.selfTestPassed = UnifiedPinyinSplitter.runSelfTest()
        
        // 新增：分段匹配测试
        result.segmentedSplitWorking = runSegmentedSplitTest()
        
        return result
    }
    
    /**
     * 分段匹配专项测试
     */
    private fun runSegmentedSplitTest(): Boolean {
        val segmentTestCases = mapOf(
            "wofaxianshujukuyouwenti" to 3, // 至少应该分为3个分段
            "nihaoshijiehenmeihao" to 2,    // 至少应该分为2个分段
            "zhongguorenmindaxuexue" to 2   // 至少应该分为2个分段
        )
        
        var allPassed = true
        
        for ((input, expectedMinSegments) in segmentTestCases) {
            try {
                // 测试分段拆分
                val segments = UnifiedPinyinSplitter.splitIntoSegments(input)
                
                if (segments.size < expectedMinSegments) {
                    Timber.e("分段拆分测试失败: '$input' 期望至少 $expectedMinSegments 个分段, 实际 ${segments.size} 个")
                    allPassed = false
                } else {
                    Timber.d("分段拆分测试通过: '$input' -> ${segments.size} 个分段")
                }
                
                // 测试分段选项
                val segmentOptions = UnifiedPinyinSplitter.getSegmentedSplitOptions(input)
                if (segmentOptions.isEmpty()) {
                    Timber.e("分段选项测试失败: '$input' 没有返回任何分段选项")
                    allPassed = false
                } else {
                    Timber.d("分段选项测试通过: '$input' -> ${segmentOptions.size} 种选项")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "分段拆分测试异常: '$input'")
                allPassed = false
            }
        }
        
        return allPassed
    }
    
    /**
     * 生成综合报告
     */
    private fun generateComprehensiveReport(result: TestSuiteResult): String {
        val report = StringBuilder()
        
        report.appendLine("=== 拼音优化测试套件综合报告 ===")
        report.appendLine()
        
        // 功能测试结果
        report.appendLine("📋 功能正确性测试:")
        report.appendLine("  总测试数: ${result.functionalTestResult.totalTests}")
        report.appendLine("  通过数: ${result.functionalTestResult.passedTests}")
        report.appendLine("  成功率: ${String.format("%.1f", result.functionalTestResult.successRate)}%")
        if (result.functionalTestResult.failedCases.isNotEmpty()) {
            report.appendLine("  失败用例:")
            result.functionalTestResult.failedCases.forEach { case ->
                report.appendLine("    '${case.input}' 期望: ${case.expected.joinToString("+")}, 实际: ${case.actual.joinToString("+")}")
            }
        }
        report.appendLine()
        
        // 性能测试结果
        report.appendLine("⚡ 性能基准测试:")
        report.appendLine("  冷启动耗时: ${result.performanceTestResult.coldStartTime}ms")
        report.appendLine("  热启动耗时: ${result.performanceTestResult.hotStartTime}ms")
        report.appendLine("  性能提升: ${String.format("%.2f", result.performanceTestResult.speedupRatio)}倍")
        report.appendLine("  平均拆分耗时: ${String.format("%.2f", result.performanceTestResult.hotStartStats.averageSplitTime)}ms")
        report.appendLine()
        
        // 缓存测试结果
        report.appendLine("🚀 缓存效果测试:")
        report.appendLine("  缓存命中率: ${String.format("%.1f", result.cacheTestResult.cacheHitRate)}%")
        report.appendLine("  快速路径命中率: ${String.format("%.1f", result.cacheTestResult.fastPathRate)}%")
        report.appendLine()
        
        // 架构测试结果
        report.appendLine("🏗️ 架构统一测试:")
        report.appendLine("  主要接口: ${if (result.architectureTestResult.mainInterfaceWorking) "✅" else "❌"}")
        report.appendLine("  多重拆分: ${if (result.architectureTestResult.multipleInterfaceWorking) "✅" else "❌"}")
        report.appendLine("  动态拆分: ${if (result.architectureTestResult.dynamicInterfaceWorking) "✅" else "❌"}")
        report.appendLine("  智能拆分: ${if (result.architectureTestResult.smartInterfaceWorking) "✅" else "❌"}")
        report.appendLine("  音节验证: ${if (result.architectureTestResult.validationWorking) "✅" else "❌"}")
        report.appendLine("  性能监控: ${if (result.architectureTestResult.performanceMonitoringWorking) "✅" else "❌"}")
        report.appendLine("  自测试: ${if (result.architectureTestResult.selfTestPassed) "✅" else "❌"}")
        report.appendLine("  分段拆分: ${if (result.architectureTestResult.segmentedSplitWorking) "✅" else "❌"}")
        report.appendLine()
        
        // 总体评估
        val overallScore = calculateOverallScore(result)
        report.appendLine("🎯 总体评估:")
        report.appendLine("  综合得分: ${String.format("%.1f", overallScore)}/100")
        report.appendLine("  评级: ${getGradeByScore(overallScore)}")
        
        return report.toString()
    }
    
    /**
     * 计算总体得分
     */
    private fun calculateOverallScore(result: TestSuiteResult): Double {
        var score = 0.0
        
        // 功能正确性 (40分)
        score += result.functionalTestResult.successRate * 0.4
        
        // 性能提升 (25分)
        val performanceScore = minOf(result.performanceTestResult.speedupRatio * 10, 25.0)
        score += performanceScore
        
        // 缓存效果 (20分)
        val cacheScore = result.cacheTestResult.cacheHitRate * 0.2
        score += cacheScore
        
        // 架构统一 (15分)
        val architectureScore = if (result.architectureTestResult.allTestsPassed()) 15.0 else 0.0
        score += architectureScore
        
        return score
    }
    
    /**
     * 根据得分获取评级
     */
    private fun getGradeByScore(score: Double): String {
        return when {
            score >= 90 -> "优秀 (A+)"
            score >= 80 -> "良好 (A)"
            score >= 70 -> "中等 (B)"
            score >= 60 -> "及格 (C)"
            else -> "需改进 (D)"
        }
    }
    
    /**
     * 测量执行时间
     */
    private inline fun measureTimeMillis(block: () -> Unit): Long {
        val start = System.currentTimeMillis()
        block()
        return System.currentTimeMillis() - start
    }
    
    // ==================== 数据类定义 ====================
    
    data class TestSuiteResult(
        var functionalTestResult: FunctionalTestResult = FunctionalTestResult(),
        var performanceTestResult: PerformanceTestResult = PerformanceTestResult(),
        var cacheTestResult: CacheTestResult = CacheTestResult(),
        var architectureTestResult: ArchitectureTestResult = ArchitectureTestResult(),
        var comprehensiveReport: String = ""
    )
    
    data class FunctionalTestResult(
        var totalTests: Int = 0,
        var passedTests: Int = 0,
        var successRate: Double = 0.0,
        val failedCases: MutableList<FailedCase> = mutableListOf()
    ) {
        data class FailedCase(
            val input: String,
            val expected: List<String>,
            val actual: List<String>
        )
    }
    
    data class PerformanceTestResult(
        var coldStartTime: Long = 0,
        var hotStartTime: Long = 0,
        var speedupRatio: Double = 0.0,
        var coldStartStats: PinyinSegmenterOptimized.PerformanceStats = PinyinSegmenterOptimized.getPerformanceStats(),
        var hotStartStats: PinyinSegmenterOptimized.PerformanceStats = PinyinSegmenterOptimized.getPerformanceStats()
    )
    
    data class CacheTestResult(
        var firstRoundStats: PinyinSegmenterOptimized.PerformanceStats = PinyinSegmenterOptimized.getPerformanceStats(),
        var secondRoundStats: PinyinSegmenterOptimized.PerformanceStats = PinyinSegmenterOptimized.getPerformanceStats(),
        var cacheHitRate: Double = 0.0,
        var fastPathRate: Double = 0.0
    )
    
    data class ArchitectureTestResult(
        var mainInterfaceWorking: Boolean = false,
        var multipleInterfaceWorking: Boolean = false,
        var dynamicInterfaceWorking: Boolean = false,
        var smartInterfaceWorking: Boolean = false,
        var validationWorking: Boolean = false,
        var syllableCountCorrect: Boolean = false,
        var initialsWorking: Boolean = false,
        var normalizationWorking: Boolean = false,
        var performanceMonitoringWorking: Boolean = false,
        var selfTestPassed: Boolean = false,
        var segmentedSplitWorking: Boolean = false
    ) {
        fun allTestsPassed(): Boolean {
            return mainInterfaceWorking && multipleInterfaceWorking && 
                   dynamicInterfaceWorking && smartInterfaceWorking &&
                   validationWorking && syllableCountCorrect &&
                   initialsWorking && normalizationWorking &&
                   performanceMonitoringWorking && selfTestPassed &&
                   segmentedSplitWorking
        }
    }
} 