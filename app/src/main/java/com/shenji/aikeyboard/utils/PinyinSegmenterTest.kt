package com.shenji.aikeyboard.utils

import android.util.Log

/**
 * 拼音分词器测试类
 * 用于比较原始分词器和优化版分词器的分词效果差异
 */
object PinyinSegmenterTest {
    private const val TAG = "PinyinSegmenterTest"
    
    /**
     * 测试并比较两个分词器的结果
     * @param input 输入的拼音字符串
     * @return 测试结果描述
     */
    fun compareSegmenters(input: String): String {
        val originalResult = PinyinSegmenter.cut(input)
        val optimizedResult = PinyinSegmenterOptimized.cut(input)
        
        val resultBuilder = StringBuilder()
        resultBuilder.append("输入: $input\n")
        resultBuilder.append("原始分词: ${originalResult.joinToString(" + ")}\n")
        resultBuilder.append("优化分词: ${optimizedResult.joinToString(" + ")}\n")
        
        // 记录到日志
        Log.d(TAG, resultBuilder.toString())
        
        return resultBuilder.toString()
    }
    
    /**
     * 批量测试多个拼音案例
     * @return 测试结果描述
     */
    fun runAllTests(): String {
        val testCases = listOf(
            "nihao",     // 预期: ni + hao, 原始可能: n + i + hao
            "xihuan",    // 预期: xi + huan, 原始可能分析错误
            "beijing",   // 预期: bei + jing
            "zhongwen",  // 预期: zhong + wen 
            "xuesheng",  // 预期: xue + sheng
            "yingyong",  // 预期: ying + yong
            "dianshi",   // 预期: dian + shi
            "pingguo"    // 预期: ping + guo
        )
        
        val resultBuilder = StringBuilder()
        resultBuilder.append("==== 拼音分词对比测试 ====\n\n")
        
        for (testCase in testCases) {
            resultBuilder.append(compareSegmenters(testCase))
            resultBuilder.append("\n")
        }
        
        return resultBuilder.toString()
    }
} 