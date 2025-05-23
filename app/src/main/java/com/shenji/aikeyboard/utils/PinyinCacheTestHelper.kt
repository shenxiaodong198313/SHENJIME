package com.shenji.aikeyboard.utils

import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 拼音缓存测试助手
 * 用于验证拼音拆分缓存优化的效果
 */
object PinyinCacheTestHelper {
    
    /**
     * 测试用例数据
     */
    private val testCases = listOf(
        // 常见拼音组合
        "nihao", "beijing", "shanghai", "guangzhou", "shenzhen",
        "zhongguo", "zhonghua", "renmin", "gongheguo", "shehuizhuyi",
        
        // 长拼音测试
        "zhonghuarenmingongheguo", "shehuizhuyihexinjiazhi", "makesizhuyizhexue",
        
        // 短拼音测试
        "wo", "ni", "ta", "de", "le", "ma", "ba", "ge", "yi", "er",
        
        // 复杂拼音测试
        "chuangxin", "fazhan", "gaige", "kaifang", "xiandaihua",
        "xinshidai", "xinfazhan", "xinlinian", "xingeju", "xinzhengchang",
        
        // 重复测试（验证缓存效果）
        "nihao", "beijing", "shanghai", "nihao", "beijing", "shanghai"
    )
    
    /**
     * 执行缓存性能测试
     * @return 测试结果报告
     */
    suspend fun runCachePerformanceTest(): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()
        report.appendLine("=== 拼音拆分缓存性能测试 ===")
        report.appendLine()
        
        // 重置统计数据
        PinyinSegmenterOptimized.resetPerformanceStats()
        report.appendLine("1. 重置性能统计")
        
        // 第一轮测试：冷启动（无缓存）
        report.appendLine("\n2. 第一轮测试（冷启动）:")
        val firstRoundStartTime = System.currentTimeMillis()
        
        testCases.forEach { input ->
            val result = PinyinSegmenterOptimized.cut(input)
            report.appendLine("   '$input' -> ${result.joinToString("+")}")
        }
        
        val firstRoundEndTime = System.currentTimeMillis()
        val firstRoundTime = firstRoundEndTime - firstRoundStartTime
        
        val firstRoundStats = PinyinSegmenterOptimized.getPerformanceStats()
        report.appendLine("\n第一轮统计:")
        report.appendLine(firstRoundStats.toString())
        report.appendLine("第一轮总耗时: ${firstRoundTime}ms")
        
        // 第二轮测试：热启动（有缓存）
        report.appendLine("\n3. 第二轮测试（热启动）:")
        val secondRoundStartTime = System.currentTimeMillis()
        
        testCases.forEach { input ->
            val result = PinyinSegmenterOptimized.cut(input)
            report.appendLine("   '$input' -> ${result.joinToString("+")}")
        }
        
        val secondRoundEndTime = System.currentTimeMillis()
        val secondRoundTime = secondRoundEndTime - secondRoundStartTime
        
        val secondRoundStats = PinyinSegmenterOptimized.getPerformanceStats()
        report.appendLine("\n第二轮统计:")
        report.appendLine(secondRoundStats.toString())
        report.appendLine("第二轮总耗时: ${secondRoundTime}ms")
        
        // 性能对比分析
        report.appendLine("\n4. 性能对比分析:")
        val speedupRatio = if (secondRoundTime > 0) {
            firstRoundTime.toDouble() / secondRoundTime.toDouble()
        } else {
            Double.POSITIVE_INFINITY
        }
        
        report.appendLine("   第一轮耗时: ${firstRoundTime}ms")
        report.appendLine("   第二轮耗时: ${secondRoundTime}ms")
        report.appendLine("   性能提升: ${String.format("%.2f", speedupRatio)}倍")
        report.appendLine("   缓存命中率: ${String.format("%.1f", secondRoundStats.cacheHitRate)}%")
        
        // 缓存效果评估
        report.appendLine("\n5. 缓存效果评估:")
        when {
            secondRoundStats.cacheHitRate >= 80.0 -> {
                report.appendLine("   ✅ 缓存效果优秀 (命中率 >= 80%)")
            }
            secondRoundStats.cacheHitRate >= 60.0 -> {
                report.appendLine("   ⚠️ 缓存效果良好 (命中率 >= 60%)")
            }
            secondRoundStats.cacheHitRate >= 40.0 -> {
                report.appendLine("   ⚠️ 缓存效果一般 (命中率 >= 40%)")
            }
            else -> {
                report.appendLine("   ❌ 缓存效果较差 (命中率 < 40%)")
            }
        }
        
        when {
            speedupRatio >= 3.0 -> {
                report.appendLine("   ✅ 性能提升显著 (>= 3倍)")
            }
            speedupRatio >= 2.0 -> {
                report.appendLine("   ✅ 性能提升明显 (>= 2倍)")
            }
            speedupRatio >= 1.5 -> {
                report.appendLine("   ⚠️ 性能有所提升 (>= 1.5倍)")
            }
            else -> {
                report.appendLine("   ❌ 性能提升不明显 (< 1.5倍)")
            }
        }
        
        report.appendLine("\n=== 测试完成 ===")
        
        val finalReport = report.toString()
        Timber.d("缓存性能测试完成:\n$finalReport")
        
        return@withContext finalReport
    }
    
    /**
     * 执行压力测试
     * @param iterations 迭代次数
     * @return 压力测试结果
     */
    suspend fun runStressTest(iterations: Int = 1000): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()
        report.appendLine("=== 拼音拆分压力测试 ===")
        report.appendLine("迭代次数: $iterations")
        report.appendLine()
        
        // 重置统计
        PinyinSegmenterOptimized.resetPerformanceStats()
        
        val startTime = System.currentTimeMillis()
        
        // 执行压力测试
        repeat(iterations) { i ->
            val testInput = testCases[i % testCases.size]
            PinyinSegmenterOptimized.cut(testInput)
            
            // 每100次输出进度
            if ((i + 1) % 100 == 0) {
                Timber.v("压力测试进度: ${i + 1}/$iterations")
            }
        }
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        
        val finalStats = PinyinSegmenterOptimized.getPerformanceStats()
        
        report.appendLine("压力测试结果:")
        report.appendLine(finalStats.toString())
        report.appendLine("总耗时: ${totalTime}ms")
        report.appendLine("平均每次耗时: ${String.format("%.3f", totalTime.toDouble() / iterations)}ms")
        report.appendLine("QPS: ${String.format("%.1f", iterations * 1000.0 / totalTime)}")
        
        val finalReport = report.toString()
        Timber.d("压力测试完成:\n$finalReport")
        
        return@withContext finalReport
    }
    
    /**
     * 获取当前缓存状态报告
     */
    fun getCurrentCacheStatus(): String {
        val stats = PinyinSegmenterOptimized.getPerformanceStats()
        return """
            当前缓存状态:
            ${stats.toString()}
        """.trimIndent()
    }
} 