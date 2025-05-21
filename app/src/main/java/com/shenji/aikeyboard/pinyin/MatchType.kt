package com.shenji.aikeyboard.pinyin

/**
 * 匹配类型枚举
 * 
 * 定义查询匹配类型，标识候选词是通过何种方式匹配的
 */
enum class MatchType {
    /**
     * 未知类型
     */
    UNKNOWN,
    
    /**
     * 单字符首字母匹配
     * 例如：输入z，匹配"找"（zhao）的首字母z
     */
    INITIAL_LETTER,
    
    /**
     * 拼音音节匹配
     * 例如：输入zao，匹配拼音为"zao"的字词
     */
    PINYIN_SYLLABLE,
    
    /**
     * 拼音音节拆分匹配
     * 例如：输入nihao，拆分为ni+hao并匹配
     */
    SYLLABLE_SPLIT,
    
    /**
     * 首字母缩写匹配
     * 例如：输入bjrm，匹配"北京人民"(beijing renmin)
     */
    ACRONYM,
    
    /**
     * 模糊拼音匹配
     * 例如：输入zan但匹配zang（模糊规则an=ang）
     */
    FUZZY_SYLLABLE
} 