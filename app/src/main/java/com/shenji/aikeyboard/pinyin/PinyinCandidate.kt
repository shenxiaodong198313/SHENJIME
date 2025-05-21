package com.shenji.aikeyboard.pinyin

import com.shenji.aikeyboard.data.Entry

/**
 * 拼音候选词数据模型
 * 
 * 标准化的候选词数据模型，包含候选词的所有相关信息
 */
data class PinyinCandidate(
    // 候选词文本
    val word: String,
    
    // 拼音
    val pinyin: String = "",
    
    // 首字母缩写
    val initialLetters: String = "",
    
    // 词频
    val frequency: Int = 0,
    
    // 词典类型
    val type: String = "",
    
    // 匹配类型
    val matchType: MatchType = MatchType.UNKNOWN
) {
    companion object {
        /**
         * 从Entry对象创建PinyinCandidate对象
         * 
         * @param entry 词条对象
         * @param matchType 匹配类型
         * @return 候选词对象
         */
        fun fromEntry(entry: Entry, matchType: MatchType): PinyinCandidate {
            return PinyinCandidate(
                word = entry.word,
                pinyin = entry.pinyin,
                initialLetters = entry.initialLetters,
                frequency = entry.frequency,
                type = entry.type,
                matchType = matchType
            )
        }
    }
    
    /**
     * 转换为展示文本
     * 用于测试工具或调试目的
     */
    fun toDisplayText(): String {
        return "$word (拼音: $pinyin, 词频: $frequency, 词典: $type)"
    }
} 