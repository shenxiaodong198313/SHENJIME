package com.shenji.aikeyboard.model

/**
 * 词频数据类
 * 用于ShenjiInputMethodService中的候选词
 */
data class WordFrequency(
    val word: String,
    val frequency: Int = 0
) : Comparable<WordFrequency> {
    
    override fun compareTo(other: WordFrequency): Int {
        // 按频率降序排列
        return other.frequency - frequency
    }
    
    override fun toString(): String {
        return "$word(频率:$frequency)"
    }
} 