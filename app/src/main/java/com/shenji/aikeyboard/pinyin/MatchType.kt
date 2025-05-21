package com.shenji.aikeyboard.pinyin

/**
 * 匹配类型枚举
 * 
 * 定义查询匹配类型，标识候选词是通过何种方式匹配的
 */
enum class MatchType {
    /**
     * 首字母匹配
     * 例如：'z'匹配'中'
     */
    INITIAL_LETTER,
    
    /**
     * 拼音音节/前缀匹配
     * 例如：'zhong'匹配'中'
     */
    PINYIN_SYLLABLE,
    
    /**
     * 音节拆分匹配
     * 例如：'nihao'拆分为'ni'+'hao'匹配'你好'
     */
    SYLLABLE_SPLIT,
    
    /**
     * 首字母缩写匹配
     * 例如：'bjr'匹配'北京人'
     */
    ACRONYM,
    
    /**
     * 未知匹配类型
     */
    UNKNOWN
} 