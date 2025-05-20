package com.shenji.aikeyboard.utils

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
        
        // 如果已经包含空格，假设已经是规范化的拼音
        if (trimmed.contains(" ")) {
            return trimmed
        }
        
        // 使用优化版拼音分词器进行分词
        val syllables = PinyinSegmenterOptimized.cut(trimmed)
        return syllables.joinToString(" ")
    }
    
    /**
     * 计算拼音的音节数量
     * @param pinyin 规范化后的拼音（带空格）
     * @return 音节数量
     */
    fun countSyllables(pinyin: String): Int {
        if (pinyin.isBlank()) return 0
        
        return pinyin.split(" ").size
    }
    
    /**
     * 获取第n个拼音音节
     * @param pinyin 规范化后的拼音（带空格）
     * @param index 音节索引（从0开始）
     * @return 指定位置的音节，如果索引越界则返回null
     */
    fun getSyllable(pinyin: String, index: Int): String? {
        if (pinyin.isBlank()) return null
        
        val syllables = pinyin.split(" ")
        
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
        val testCases = listOf(
            "zhangsan",     // 正常分割
            "xianggang",    // 匹配测试
            "xian",         // 优先匹配长音节
            "nihao",        // 测试n+i+hao问题
            "weixn",        // 测试weix+n问题
            "beijing",      // 正常分割
            "zhongwen",     // 正常分割
            "xuesheng"      // 正常分割
        )
        
        val results = testCases.map { input ->
            val syllables = PinyinSegmenterOptimized.cut(input)
            val result = syllables.joinToString(" + ")
            "$input -> $result"
        }
        
        return results.joinToString("\n")
    }

    // 拼音首字母提取
    fun generateInitials(pinyin: String): String {
        if (pinyin.isBlank()) return ""
        
        // 如果拼音包含空格，按空格分割
        if (pinyin.contains(" ")) {
            return pinyin.split(" ")
                .filter { it.isNotEmpty() }
                .joinToString("") { if (it.isNotEmpty()) it.first().toString() else "" }
        } 
        // 如果拼音不包含空格，尝试使用拼音分词器拆分
        else {
            // 使用优化版拼音分词器进行分词
            val syllables = PinyinSegmenterOptimized.cut(pinyin)
            if (syllables.isNotEmpty()) {
                return syllables.joinToString("") { 
                    if (it.isNotEmpty()) it.first().toString() else "" 
                }
            }
            
            // 如果无法拆分或发生异常，将整个拼音的首字母作为initialLetters
            return if (pinyin.isNotEmpty()) pinyin.first().toString() else ""
        }
    }
} 