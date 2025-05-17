package com.shenji.aikeyboard.utils

import timber.log.Timber

/**
 * 拼音测试助手类
 * 提供拼音相关功能的测试方法
 */
object PinyinTestHelper {
    
    /**
     * 测试拼音分词功能
     * @return 测试结果信息
     */
    fun testPinyinSplitter(): String {
        val testResults = StringBuilder()
        testResults.append("==== 拼音分词测试 ====\n")
        
        // 测试用例
        val testCases = listOf(
            "zhangsan" to "zhang san",         // 正常分割
            "xianggang" to "xiang gang",       // 从右到左匹配避免失败
            "beijingshi" to "bei jing shi",    // 多音节分割
            "xian" to "xian",                  // 优先匹配长音节
            "laohu" to "lao hu",               // 简单双音节
            "nihao" to "ni hao",               // 常用问候语
            "zhongguoren" to "zhong guo ren",  // 三字词组
            "shanghai" to "shang hai",         // 地名
            "laoshihaomafan" to "lao shi hao ma fan", // 长句子
            "jintian" to "jin tian",           // 今天
            "jianyue" to "jian yue",           // 简约
            "zhang1" to null,                  // 包含非法字符
            "zhx" to null                      // 无法分割为合法音节
        )
        
        // 运行测试
        testCases.forEach { (input, expected) ->
            val syllables = PinyinSplitter.split(input)
            val result = if (syllables.isNotEmpty()) {
                PinyinSplitter.joinSyllables(syllables)
            } else {
                null
            }
            
            val isSuccess = result == expected
            val status = if (isSuccess) "✓" else "✗"
            
            testResults.append("$status $input -> $result")
            if (!isSuccess) {
                testResults.append(" (期望: $expected)")
            }
            testResults.append("\n")
            
            // 记录日志
            if (!isSuccess) {
                Timber.d("分词测试失败: 输入=$input, 结果=$result, 期望=$expected")
            }
        }
        
        testResults.append("\n==== 特殊情况测试 ====\n")
        
        // 特殊情况测试
        val spaceInput = "ni hao ma"
        val spaceResult = PinyinUtils.normalize(spaceInput)
        testResults.append("带空格输入: $spaceInput -> $spaceResult\n")
        
        val emptyInput = ""
        val emptyResult = PinyinUtils.normalize(emptyInput)
        testResults.append("空输入: '$emptyInput' -> '$emptyResult'\n")
        
        return testResults.toString()
    }
    
    /**
     * 测试首字母生成功能
     * @return 测试结果信息
     */
    fun testInitialsGeneration(): String {
        val testResults = StringBuilder()
        testResults.append("==== 首字母生成测试 ====\n")
        
        // 测试用例
        val testCases = listOf(
            "zhang san" to "zs", 
            "bei jing" to "bj",
            "zhong guo ren" to "zgr",
            "ni hao ma" to "nhm",
            "wo ai zhong guo" to "wazg"
        )
        
        // 运行测试
        testCases.forEach { (input, expected) ->
            val result = PinyinUtils.generateInitials(input)
            val isSuccess = result == expected
            val status = if (isSuccess) "✓" else "✗"
            
            testResults.append("$status $input -> $result")
            if (!isSuccess) {
                testResults.append(" (期望: $expected)")
            }
            testResults.append("\n")
        }
        
        // 测试无空格输入的首字母生成
        testResults.append("\n==== 无空格输入首字母测试 ====\n")
        
        val noSpaceTestCases = listOf(
            "zhangsan" to "zs", 
            "beijing" to "bj",
            "zhongguoren" to "zgr"
        )
        
        noSpaceTestCases.forEach { (input, expected) ->
            val result = PinyinUtils.generateInitials(input)
            val isSuccess = result == expected
            val status = if (isSuccess) "✓" else "✗"
            
            testResults.append("$status $input -> $result")
            if (!isSuccess) {
                testResults.append(" (期望: $expected)")
            }
            testResults.append("\n")
        }
        
        return testResults.toString()
    }
    
    /**
     * 运行所有测试
     * @return 完整测试结果信息
     */
    fun runAllTests(): String {
        return testPinyinSplitter() + "\n" + testInitialsGeneration()
    }
} 