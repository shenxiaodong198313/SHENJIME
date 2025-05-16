package com.shenji.aikeyboard.utils

import com.shenji.aikeyboard.data.PinyinSplitter
import timber.log.Timber

/**
 * 拼音工具类
 * 提供拼音处理相关的工具方法
 */
object PinyinUtils {

    /**
     * 规范化拼音，确保拼音音节之间有空格分隔
     * @param input 原始拼音输入
     * @return 规范化后的拼音
     */
    fun normalize(input: String): String {
        if (input.isBlank()) return input
        
        val trimmed = input.trim().lowercase()
        
        // 如果已经包含空格，检查分词是否正确
        if (trimmed.contains(" ")) {
            val syllables = trimmed.split(" ")
            val validatedSyllables = mutableListOf<String>()
            
            for (syllable in syllables) {
                if (PinyinSplitter.isValidSyllable(syllable) || syllable.length == 1) {
                    // 有效音节，直接添加
                    validatedSyllables.add(syllable)
                } else {
                    // 无效音节，重新分词
                    Timber.w("发现无效拼音音节: '$syllable'，进行重新分词")
                    val splitted = PinyinSplitter.split(syllable)
                    validatedSyllables.addAll(splitted.split(" "))
                }
            }
            
            return validatedSyllables.joinToString(" ")
        }
        
        // 无空格输入，直接使用分词器
        return PinyinSplitter.split(trimmed)
    }
    
    /**
     * 计算拼音的音节数量
     * @param pinyin 规范化后的拼音（带空格）
     * @return 音节数量
     */
    fun countSyllables(pinyin: String): Int {
        if (pinyin.isBlank()) return 0
        
        val normalized = normalize(pinyin)
        return normalized.split(" ").size
    }
    
    /**
     * 获取第n个拼音音节
     * @param pinyin 规范化后的拼音（带空格）
     * @param index 音节索引（从0开始）
     * @return 指定位置的音节，如果索引越界则返回null
     */
    fun getSyllable(pinyin: String, index: Int): String? {
        if (pinyin.isBlank()) return null
        
        val normalized = normalize(pinyin)
        val syllables = normalized.split(" ")
        
        return if (index >= 0 && index < syllables.size) {
            syllables[index]
        } else {
            null
        }
    }
    
    /**
     * 测试拼音分词效果
     * 用于验证拼音分词器的正确性
     * @return 测试结果信息
     */
    fun testPinyinSplitter(): String {
        val testCases = mapOf(
            "jiating" to "jia ting",
            "beijingdaxue" to "bei jing da xue",
            "woainizhonghua" to "wo ai ni zhong hua",
            "zhijianguan" to "zhi jian guan",
            "shuangshuying" to "shuang shu ying",
            "xiangyun" to "xiang yun",
            "zhuangyuanjin" to "zhuang yuan jin",
            "jiatinghuanjingzenmeyang" to "jia ting huan jing zen me yang"
        )
        
        val results = mutableListOf<String>()
        var passCount = 0
        
        results.add("音节库大小: ${PinyinSplitter.getSyllableCount()}")
        results.add("========= 拼音分词测试 =========")
        
        testCases.forEach { (input, expected) ->
            val actual = normalize(input)
            val isCorrect = actual == expected
            
            if (isCorrect) passCount++
            
            results.add("输入: '$input'")
            results.add("期望: '$expected'")
            results.add("实际: '$actual'")
            results.add("结果: ${if (isCorrect) "✓" else "✗"}")
            results.add("-----------------------------")
        }
        
        results.add("测试通过率: $passCount/${testCases.size}")
        
        return results.joinToString("\n")
    }

    // 拼音首字母提取
    fun generateInitials(pinyin: String): String {
        if (pinyin.isBlank()) return ""
        
        return pinyin.split(" ")
            .filter { it.isNotBlank() }
            .map { it.first().toString() }
            .joinToString("")
    }

    /**
     * 分割连续的拼音字符串为音节 (带空格) - 已弃用
     * @param pinyin 要分割的拼音字符串, 如"nihao"
     * @return 分割后的拼音, 如"ni hao"
     */
    @Deprecated("使用PinyinSplitter.split()替代")
    fun splitPinyinIntoSyllables(pinyin: String): String {
        return PinyinSplitter.split(pinyin)
    }
} 