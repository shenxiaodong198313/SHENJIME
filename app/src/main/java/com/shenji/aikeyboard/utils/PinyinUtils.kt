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
        
        // 简化版只返回输入本身，不做分词
        return trimmed
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
        return "拼音分词功能已移除"
    }

    // 拼音首字母提取
    fun generateInitials(pinyin: String): String {
        if (pinyin.isBlank()) return ""
        
        return pinyin.split(" ")
            .filter { it.isNotBlank() }
            .map { it.first().toString() }
            .joinToString("")
    }
} 