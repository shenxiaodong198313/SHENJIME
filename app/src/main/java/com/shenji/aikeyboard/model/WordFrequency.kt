package com.shenji.aikeyboard.model

/**
 * 词频数据类
 * 用于ShenjiInputMethodService中的候选词
 */
data class WordFrequency(
    val word: String,
    val frequency: Int = 0
) 