package com.shenji.aikeyboard.pinyin

/**
 * 输入类型枚举
 * 
 * 定义输入可能的各种类型，用于确定如何处理用户输入
 */
enum class InputType {
    /**
     * 单个字母作为首字母处理
     * 例如：'z'可匹配所有z开头的字：'中'、'张'等
     */
    INITIAL_LETTER,
    
    /**
     * 单个完整拼音音节
     * 例如：'zhong'作为一个完整音节处理
     */
    PINYIN_SYLLABLE,
    
    /**
     * 连续拼音需要拆分为多个音节
     * 例如：'nihao'拆分为'ni'+'hao'
     */
    SYLLABLE_SPLIT,
    
    /**
     * 首字母缩写模式
     * 例如：'bjr'匹配'北京人'等词
     */
    ACRONYM,
    
    /**
     * 未知或无法识别的输入类型
     */
    UNKNOWN,
    
    /**
     * 动态音节识别
     * 处理未完成的拼音输入，将输入拆分为完整音节+剩余字母
     * 例如：shenjingb -> 神经病
     */
    DYNAMIC_SYLLABLE,
    
    /**
     * 中文输入
     */
    CHINESE,
    
    /**
     * 错误类型
     */
    ERROR
} 