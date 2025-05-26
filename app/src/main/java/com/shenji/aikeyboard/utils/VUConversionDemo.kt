package com.shenji.aikeyboard.utils

import android.content.Context
import com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter
import com.shenji.aikeyboard.settings.FuzzyPinyinManager
import timber.log.Timber

/**
 * v/ü转换功能演示程序
 * 
 * 展示神迹输入法v/ü转换功能的完整实现：
 * 1. 基础v/ü转换规则
 * 2. 拼音分割中的v/ü处理
 * 3. 模糊匹配中的v/ü支持
 * 4. 实际输入场景演示
 */
class VUConversionDemo(private val context: Context) {
    
    private val fuzzyPinyinManager = FuzzyPinyinManager.getInstance()
    
    /**
     * 运行完整的v/ü转换演示
     */
    fun runFullDemo(): String {
        val report = StringBuilder()
        report.appendLine("=== 神迹输入法 v/ü转换功能演示 ===")
        report.appendLine("演示时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
        report.appendLine()
        
        // 1. 基础转换规则演示
        report.appendLine("【1. 基础v/ü转换规则】")
        report.append(demonstrateBasicConversion())
        report.appendLine()
        
        // 2. 拼音分割演示
        report.appendLine("【2. 拼音分割中的v/ü处理】")
        report.append(demonstratePinyinSplitting())
        report.appendLine()
        
        // 3. 模糊匹配演示
        report.appendLine("【3. 模糊匹配中的v/ü支持】")
        report.append(demonstrateFuzzyMatching())
        report.appendLine()
        
        // 4. 实际输入场景演示
        report.appendLine("【4. 实际输入场景演示】")
        report.append(demonstrateRealInputScenarios())
        report.appendLine()
        
        // 5. 性能测试
        report.appendLine("【5. 性能测试】")
        report.append(demonstratePerformance())
        
        return report.toString()
    }
    
    /**
     * 演示基础v/ü转换规则
     */
    private fun demonstrateBasicConversion(): String {
        val report = StringBuilder()
        
        val conversionRules = listOf(
            "汉语拼音v代替ü的规则：",
            "1. lv → lü (绿色的绿)",
            "2. nv → nü (女人的女)", 
            "3. jv → ju (居住的居)",
            "4. qv → qu (去年的去)",
            "5. xv → xu (虚心的虚)",
            "6. yv → yu (鱼类的鱼)",
            ""
        )
        
        conversionRules.forEach { report.appendLine(it) }
        
        val testCases = mapOf(
            "lv" to "绿",
            "lvse" to "绿色",
            "nv" to "女",
            "nvhai" to "女孩",
            "jv" to "居",
            "jvzhu" to "居住",
            "qv" to "去",
            "qvnian" to "去年",
            "xv" to "虚",
            "xvxin" to "虚心",
            "yv" to "鱼",
            "yvlei" to "鱼类"
        )
        
        report.appendLine("转换示例：")
        for ((input, meaning) in testCases) {
            val syllables = UnifiedPinyinSplitter.split(input)
            val converted = syllables.joinToString(" ")
            report.appendLine("  输入: $input → 拆分: $converted → 含义: $meaning")
        }
        
        return report.toString()
    }
    
    /**
     * 演示拼音分割中的v/ü处理
     */
    private fun demonstratePinyinSplitting(): String {
        val report = StringBuilder()
        
        report.appendLine("拼音分割器会自动处理v/ü转换：")
        
        val splittingTests = listOf(
            "lvse" to "绿色",
            "nvhai" to "女孩", 
            "jvzhu" to "居住",
            "qvnian" to "去年",
            "xvxin" to "虚心",
            "yvlei" to "鱼类",
            "lvxingnvren" to "绿星女人",
            "jvzhuqvnian" to "居住去年"
        )
        
        for ((input, meaning) in splittingTests) {
            val syllables = UnifiedPinyinSplitter.split(input)
            val syllableCount = syllables.size
            report.appendLine("  '$input' → ${syllables.joinToString(" + ")} (${syllableCount}个音节) → $meaning")
        }
        
        return report.toString()
    }
    
    /**
     * 演示模糊匹配中的v/ü支持
     */
    private fun demonstrateFuzzyMatching(): String {
        val report = StringBuilder()
        
        // 确保v/ü模糊匹配已启用
        val originalSetting = fuzzyPinyinManager.isVEqualsU()
        fuzzyPinyinManager.setVEqualsU(true)
        
        try {
            report.appendLine("模糊匹配支持v/ü双向转换：")
            
            val fuzzyTests = listOf(
                "lv", "lü", "nv", "nü", "jv", "ju", "qv", "qu", "xv", "xu", "yv", "yu"
            )
            
            for (input in fuzzyTests) {
                val variants = fuzzyPinyinManager.applyFuzzyRules(input)
                if (variants.size > 1) {
                    report.appendLine("  '$input' → 变体: ${variants.joinToString(", ")}")
                }
            }
            
        } finally {
            // 恢复原始设置
            fuzzyPinyinManager.setVEqualsU(originalSetting)
        }
        
        return report.toString()
    }
    
    /**
     * 演示实际输入场景
     */
    private fun demonstrateRealInputScenarios(): String {
        val report = StringBuilder()
        
        report.appendLine("实际输入场景演示：")
        
        val realScenarios = listOf(
            InputScenario("lv", "用户想输入'绿'", listOf("绿", "率", "律")),
            InputScenario("lvse", "用户想输入'绿色'", listOf("绿色")),
            InputScenario("nv", "用户想输入'女'", listOf("女", "怒")),
            InputScenario("nvhai", "用户想输入'女孩'", listOf("女孩")),
            InputScenario("jv", "用户想输入'居'", listOf("居", "局", "举")),
            InputScenario("qv", "用户想输入'去'", listOf("去", "取", "趣")),
            InputScenario("xv", "用户想输入'虚'", listOf("虚", "须", "需")),
            InputScenario("yv", "用户想输入'鱼'", listOf("鱼", "于", "余"))
        )
        
        for (scenario in realScenarios) {
            report.appendLine("  场景: ${scenario.description}")
            report.appendLine("    输入: '${scenario.input}'")
            
            // 拼音分割
            val syllables = UnifiedPinyinSplitter.split(scenario.input)
            report.appendLine("    拆分: ${syllables.joinToString(" + ")}")
            
            // 模糊匹配变体
            val variants = fuzzyPinyinManager.applyFuzzyRules(scenario.input)
            if (variants.size > 1) {
                report.appendLine("    变体: ${variants.joinToString(", ")}")
            }
            
            // 期望候选词
            report.appendLine("    候选词: ${scenario.expectedCandidates.joinToString(", ")}")
            report.appendLine()
        }
        
        return report.toString()
    }
    
    /**
     * 演示性能测试
     */
    private fun demonstratePerformance(): String {
        val report = StringBuilder()
        
        report.appendLine("性能测试结果：")
        
        val testInputs = listOf(
            "lv", "nv", "jv", "qv", "xv", "yv",
            "lvse", "nvhai", "jvzhu", "qvnian", "xvxin", "yvlei",
            "lvxingnvrenqvdaoxuyao"
        )
        
        val iterations = 1000
        
        // 测试拼音分割性能
        val splitStartTime = System.currentTimeMillis()
        repeat(iterations) {
            for (input in testInputs) {
                UnifiedPinyinSplitter.split(input)
            }
        }
        val splitEndTime = System.currentTimeMillis()
        val splitTotalTime = splitEndTime - splitStartTime
        val splitAvgTime = splitTotalTime.toDouble() / (iterations * testInputs.size)
        
        // 测试模糊匹配性能
        val fuzzyStartTime = System.currentTimeMillis()
        repeat(iterations) {
            for (input in testInputs) {
                fuzzyPinyinManager.applyFuzzyRules(input)
            }
        }
        val fuzzyEndTime = System.currentTimeMillis()
        val fuzzyTotalTime = fuzzyEndTime - fuzzyStartTime
        val fuzzyAvgTime = fuzzyTotalTime.toDouble() / (iterations * testInputs.size)
        
        report.appendLine("  拼音分割性能:")
        report.appendLine("    总测试次数: ${iterations * testInputs.size}")
        report.appendLine("    总耗时: ${splitTotalTime}ms")
        report.appendLine("    平均耗时: ${String.format("%.3f", splitAvgTime)}ms")
        report.appendLine()
        
        report.appendLine("  模糊匹配性能:")
        report.appendLine("    总测试次数: ${iterations * testInputs.size}")
        report.appendLine("    总耗时: ${fuzzyTotalTime}ms")
        report.appendLine("    平均耗时: ${String.format("%.3f", fuzzyAvgTime)}ms")
        report.appendLine()
        
        // 获取拼音分割器的性能统计
        val stats = UnifiedPinyinSplitter.getPerformanceStats()
        report.appendLine("  拼音分割器统计:")
        report.appendLine("    总请求数: ${stats.totalRequests}")
        report.appendLine("    缓存命中率: ${String.format("%.1f", stats.cacheHitRate)}%")
        report.appendLine("    快速路径命中率: ${String.format("%.1f", stats.fastPathRate)}%")
        
        return report.toString()
    }
    
    /**
     * 运行快速演示（用于调试）
     */
    fun runQuickDemo(): String {
        val report = StringBuilder()
        report.appendLine("=== v/ü转换快速演示 ===")
        
        val quickTests = listOf("lv", "nv", "jv", "lvse", "nvhai")
        
        for (input in quickTests) {
            val syllables = UnifiedPinyinSplitter.split(input)
            val variants = fuzzyPinyinManager.applyFuzzyRules(input)
            
            report.appendLine("输入: '$input'")
            report.appendLine("  拆分: ${syllables.joinToString(" + ")}")
            report.appendLine("  变体: ${variants.joinToString(", ")}")
            report.appendLine()
        }
        
        return report.toString()
    }
    
    /**
     * 输入场景数据类
     */
    private data class InputScenario(
        val input: String,
        val description: String,
        val expectedCandidates: List<String>
    )
} 