package com.shenji.aikeyboard.model

/**
 * 候选词数据模型
 * 用于测试工具中展示候选词信息
 */
data class Candidate(
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
    /**
     * 匹配类型
     */
    enum class MatchType {
        // 首字母匹配
        INITIAL_LETTER,
        
        // 拼音前缀匹配
        PINYIN_PREFIX,
        
        // 拼音拆分匹配
        SYLLABLE_SPLIT,
        
        // 首字母缩写匹配
        ACRONYM,
        
        // 未知匹配类型
        UNKNOWN
    }
    
    companion object {
        /**
         * 从Entry对象转换为Candidate对象
         */
        fun fromEntry(entry: com.shenji.aikeyboard.data.Entry, matchType: MatchType): Candidate {
            return Candidate(
                word = entry.word,
                pinyin = entry.pinyin,
                initialLetters = entry.initialLetters,
                frequency = entry.frequency,
                type = entry.type,
                matchType = matchType
            )
        }
    }
} 