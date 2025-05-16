package com.shenji.aikeyboard.utils

import timber.log.Timber

/**
 * 拼音首字母处理工具类
 */
object PinyinInitialUtils {
    
    /**
     * 从拼音生成首字母缩写
     * 例如: "wei xin" -> "wx"
     */
    fun generateInitials(pinyin: String): String {
        if (pinyin.isBlank()) return ""
        
        return try {
            pinyin.split(" ")
                .filter { it.isNotEmpty() }
                .joinToString("") { if (it.isNotEmpty()) it.first().toString() else "" }
        } catch (e: Exception) {
            Timber.e(e, "首字母生成失败: $pinyin")
            ""
        }
    }
    
    /**
     * 判断输入是否为可能的拼音首字母缩写
     * (纯小写字母，无空格)
     */
    fun isPossibleInitials(input: String): Boolean {
        return input.matches(Regex("[a-z]+")) && !input.contains(" ")
    }
} 