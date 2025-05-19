package com.shenji.aikeyboard.utils

import android.util.Log

/**
 * 拼音分词器测试类
 * 用于比较原始分词器和优化版分词器的分词效果差异，并与标准答案进行比对
 */
object PinyinSegmenterTest {
    private const val TAG = "PinyinSegmenterTest"
    
    // 测试结果对象
    data class TestResult(
        val input: String,
        val standardResult: List<String>,
        val optimizedResult: List<String>,
        val isMatch: Boolean
    )
    
    /**
     * 测试并比较两个分词器的结果
     * @param input 输入的拼音字符串
     * @param expectedResult 期望的拆分结果（标准答案）
     * @return 测试结果描述
     */
    fun compareSegmenters(input: String, expectedResult: List<String>? = null): String {
        val originalResult = PinyinSegmenter.cut(input)
        val optimizedResult = PinyinSegmenterOptimized.cut(input)
        
        val resultBuilder = StringBuilder()
        resultBuilder.append("输入: $input\n")
        resultBuilder.append("原始分词: ${originalResult.joinToString(" + ")}\n")
        resultBuilder.append("优化分词: ${optimizedResult.joinToString(" + ")}\n")
        
        // 如果提供了期望结果，进行比对
        if (expectedResult != null) {
            val isMatch = optimizedResult == expectedResult
            resultBuilder.append("标准答案: ${expectedResult.joinToString(" + ")}\n")
            resultBuilder.append("结果匹配: ${if (isMatch) "✓" else "✗"}\n")
            
            if (!isMatch) {
                resultBuilder.append("差异说明: 期望 ${expectedResult.joinToString(" + ")} 但得到 ${optimizedResult.joinToString(" + ")}\n")
            }
        }
        
        // 记录到日志
        Log.d(TAG, resultBuilder.toString())
        
        return resultBuilder.toString()
    }
    
    /**
     * 批量测试基础测试用例
     * @return 测试结果描述
     */
    fun runBasicTests(): String {
        val resultBuilder = StringBuilder()
        resultBuilder.append("==== 基础测试用例 ====\n\n")
        
        // 单音节词
        val singleSyllableTests = listOf(
            "ni" to listOf("ni"),
            "wo" to listOf("wo"),
            "ta" to listOf("ta")
        )
        
        resultBuilder.append("---- 单音节词 ----\n")
        for ((input, expected) in singleSyllableTests) {
            resultBuilder.append(compareSegmenters(input, expected))
            resultBuilder.append("\n")
        }
        
        // 双音节词
        val doubleSyllableTests = listOf(
            "hao" to listOf("hao"),
            "zai" to listOf("zai"),
            "shang" to listOf("shang")
        )
        
        resultBuilder.append("---- 双音节词 ----\n")
        for ((input, expected) in doubleSyllableTests) {
            resultBuilder.append(compareSegmenters(input, expected))
            resultBuilder.append("\n")
        }
        
        // 三音节词
        val tripleSyllableTests = listOf(
            "zhongwen" to listOf("zhong", "wen"),
            "xuesheng" to listOf("xue", "sheng"),
            "tianqi" to listOf("tian", "qi")
        )
        
        resultBuilder.append("---- 三音节词 ----\n")
        for ((input, expected) in tripleSyllableTests) {
            resultBuilder.append(compareSegmenters(input, expected))
            resultBuilder.append("\n")
        }
        
        // 四音节词
        val fourSyllableTests = listOf(
            "beijingdaxue" to listOf("bei", "jing", "da", "xue"),
            "nanjingdaxue" to listOf("nan", "jing", "da", "xue"),
            "shanghaishiyi" to listOf("shang", "hai", "shi", "yi")
        )
        
        resultBuilder.append("---- 四音节词 ----\n")
        for ((input, expected) in fourSyllableTests) {
            resultBuilder.append(compareSegmenters(input, expected))
            resultBuilder.append("\n")
        }
        
        return resultBuilder.toString()
    }
    
    /**
     * 批量测试复杂测试用例
     * @return 测试结果描述
     */
    fun runComplexTests(): String {
        val resultBuilder = StringBuilder()
        resultBuilder.append("==== 复杂测试用例 ====\n\n")
        
        // 包含声母和韵母组合的词
        val smYmTests = listOf(
            "zhongguo" to listOf("zhong", "guo"),
            "jiaoyu" to listOf("jiao", "yu"),
            "kexue" to listOf("ke", "xue")
        )
        
        resultBuilder.append("---- 包含声母和韵母组合的词 ----\n")
        for ((input, expected) in smYmTests) {
            resultBuilder.append(compareSegmenters(input, expected))
            resultBuilder.append("\n")
        }
        
        // 包含特殊韵母的词
        val specialYmTests = listOf(
            "yuan" to listOf("yuan"),
            "yun" to listOf("yun"),
            "yue" to listOf("yue")
        )
        
        resultBuilder.append("---- 包含特殊韵母的词 ----\n")
        for ((input, expected) in specialYmTests) {
            resultBuilder.append(compareSegmenters(input, expected))
            resultBuilder.append("\n")
        }
        
        // 包含轻声的词
        val qingShengTests = listOf(
            "men" to listOf("men"),
            "de" to listOf("de"),
            "le" to listOf("le")
        )
        
        resultBuilder.append("---- 包含轻声的词 ----\n")
        for ((input, expected) in qingShengTests) {
            resultBuilder.append(compareSegmenters(input, expected))
            resultBuilder.append("\n")
        }
        
        // 包含多音字的词
        val duoYinTests = listOf(
            "changan" to listOf("chang", "an"),
            "hangzhou" to listOf("hang", "zhou"),
            "chengdu" to listOf("cheng", "du")
        )
        
        resultBuilder.append("---- 包含多音字的词 ----\n")
        for ((input, expected) in duoYinTests) {
            resultBuilder.append(compareSegmenters(input, expected))
            resultBuilder.append("\n")
        }
        
        // 包含连续相同声母或韵母的词
        val repeatTests = listOf(
            "lala" to listOf("la", "la"),
            "mama" to listOf("ma", "ma"),
            "baba" to listOf("ba", "ba")
        )
        
        resultBuilder.append("---- 包含连续相同声母或韵母的词 ----\n")
        for ((input, expected) in repeatTests) {
            resultBuilder.append(compareSegmenters(input, expected))
            resultBuilder.append("\n")
        }
        
        return resultBuilder.toString()
    }
    
    /**
     * 批量测试特殊情况测试用例
     * @return 测试结果描述
     */
    fun runSpecialTests(): String {
        val resultBuilder = StringBuilder()
        resultBuilder.append("==== 特殊情况测试用例 ====\n\n")
        
        // One音节式组合的词
        val oneSyllableTests = listOf(
            "qiong" to listOf("qiong"),
            "xiong" to listOf("xiong"),
            "chong" to listOf("chong")
        )
        
        resultBuilder.append("---- 包含不常见组合的词 ----\n")
        for ((input, expected) in oneSyllableTests) {
            resultBuilder.append(compareSegmenters(input, expected))
            resultBuilder.append("\n")
        }
        
        // 复合韵母的词
        val compoundTests = listOf(
            "jiao" to listOf("jiao"),
            "xiao" to listOf("xiao"),
            "chao" to listOf("chao")
        )
        
        resultBuilder.append("---- 包含复合韵母的词 ----\n")
        for ((input, expected) in compoundTests) {
            resultBuilder.append(compareSegmenters(input, expected))
            resultBuilder.append("\n")
        }
        
        // 包含零声母的词
        val zeroSmTests = listOf(
            "an" to listOf("an"),
            "en" to listOf("en"),
            "in" to listOf("in")
        )
        
        resultBuilder.append("---- 包含零声母的词 ----\n")
        for ((input, expected) in zeroSmTests) {
            resultBuilder.append(compareSegmenters(input, expected))
            resultBuilder.append("\n")
        }
        
        return resultBuilder.toString()
    }
    
    /**
     * 批量测试组合测试用例
     * @return 测试结果描述
     */
    fun runCombinedTests(): String {
        val resultBuilder = StringBuilder()
        resultBuilder.append("==== 组合测试用例 ====\n\n")
        
        // 包含多个词的句子
        val sentenceTests = listOf(
            "nihaobeijing" to listOf("ni", "hao", "bei", "jing"),
            "woaizhongguo" to listOf("wo", "ai", "zhong", "guo"),
            "taqunanjing" to listOf("ta", "qu", "nan", "jing")
        )
        
        resultBuilder.append("---- 包含多个词的句子 ----\n")
        for ((input, expected) in sentenceTests) {
            resultBuilder.append(compareSegmenters(input, expected))
            resultBuilder.append("\n")
        }
        
        return resultBuilder.toString()
    }
    
    /**
     * 运行所有测试用例并统计匹配情况
     * @return 测试结果描述和统计信息
     */
    fun runAllTests(): String {
        val testResults = mutableListOf<TestResult>()
        
        // 添加所有测试用例及其期望结果
        val allTests = listOf(
            // 单音节词
            "ni" to listOf("ni"),
            "wo" to listOf("wo"),
            "ta" to listOf("ta"),
            // 双音节词
            "hao" to listOf("hao"),
            "zai" to listOf("zai"),
            "shang" to listOf("shang"),
            // 三音节词
            "zhongwen" to listOf("zhong", "wen"),
            "xuesheng" to listOf("xue", "sheng"),
            "tianqi" to listOf("tian", "qi"),
            // 四音节词
            "beijingdaxue" to listOf("bei", "jing", "da", "xue"),
            "nanjingdaxue" to listOf("nan", "jing", "da", "xue"),
            "shanghaishiyi" to listOf("shang", "hai", "shi", "yi"),
            // 包含声母和韵母组合的词
            "zhongguo" to listOf("zhong", "guo"),
            "jiaoyu" to listOf("jiao", "yu"),
            "kexue" to listOf("ke", "xue"),
            // 包含特殊韵母的词
            "yuan" to listOf("yuan"),
            "yun" to listOf("yun"),
            "yue" to listOf("yue"),
            // 包含轻声的词
            "men" to listOf("men"),
            "de" to listOf("de"),
            "le" to listOf("le"),
            // 包含多音字的词
            "changan" to listOf("chang", "an"),
            "hangzhou" to listOf("hang", "zhou"),
            "chengdu" to listOf("cheng", "du"),
            // 包含连续相同声母或韵母的词
            "lala" to listOf("la", "la"),
            "mama" to listOf("ma", "ma"),
            "baba" to listOf("ba", "ba"),
            // 包含不常见组合的词
            "qiong" to listOf("qiong"),
            "xiong" to listOf("xiong"),
            "chong" to listOf("chong"),
            // 包含复合韵母的词
            "jiao" to listOf("jiao"),
            "xiao" to listOf("xiao"),
            "chao" to listOf("chao"),
            // 包含零声母的词
            "an" to listOf("an"),
            "en" to listOf("en"),
            "in" to listOf("in"),
            // 包含多个词的句子
            "nihaobeijing" to listOf("ni", "hao", "bei", "jing"),
            "woaizhongguo" to listOf("wo", "ai", "zhong", "guo"),
            "taqunanjing" to listOf("ta", "qu", "nan", "jing")
        )
        
        // 执行测试并收集结果
        for ((input, expected) in allTests) {
            val optimizedResult = PinyinSegmenterOptimized.cut(input)
            val isMatch = optimizedResult == expected
            testResults.add(TestResult(input, expected, optimizedResult, isMatch))
        }
        
        // 统计测试结果
        val totalTests = testResults.size
        val passedTests = testResults.count { it.isMatch }
        val failedTests = testResults.filter { !it.isMatch }
        
        // 构建测试报告
        val resultBuilder = StringBuilder()
        resultBuilder.append("==== 拼音分词测试报告 ====\n\n")
        resultBuilder.append("测试统计: 共 $totalTests 个测试用例, 通过 $passedTests 个, 失败 ${totalTests - passedTests} 个\n")
        resultBuilder.append("通过率: ${(passedTests * 100.0 / totalTests).toInt()}%\n\n")
        
        // 如果有失败的测试，列出详情
        if (failedTests.isNotEmpty()) {
            resultBuilder.append("==== 失败测试详情 ====\n\n")
            failedTests.forEach { result ->
                resultBuilder.append("输入: ${result.input}\n")
                resultBuilder.append("期望: ${result.standardResult.joinToString(" + ")}\n")
                resultBuilder.append("实际: ${result.optimizedResult.joinToString(" + ")}\n")
                resultBuilder.append("\n")
            }
        }
        
        // 添加详细测试结果
        resultBuilder.append(runBasicTests())
        resultBuilder.append(runComplexTests())
        resultBuilder.append(runSpecialTests())
        resultBuilder.append(runCombinedTests())
        
        return resultBuilder.toString()
    }
} 