package com.shenji.aikeyboard.pinyin

/**
 * 拼音查询结果数据模型
 * 
 * 标准化的查询结果对象，包含查询的所有结果信息
 */
data class PinyinQueryResult(
    // 输入类型
    val inputType: InputType = InputType.UNKNOWN,
    
    // 候选词列表
    val candidates: List<PinyinCandidate> = emptyList(),
    
    // 音节拆分结果（如果有）
    val syllables: List<String> = emptyList(),
    
    // 查询过程的详细解释（仅在测试工具中使用）
    val explanation: String = "",
    
    // 是否发生错误
    val isError: Boolean = false,
    
    // 错误信息（如果有）
    val errorMessage: String = ""
) {
    companion object {
        /**
         * 创建空结果
         */
        fun empty(inputType: InputType = InputType.UNKNOWN, explanation: String = ""): PinyinQueryResult {
            return PinyinQueryResult(
                inputType = inputType,
                candidates = emptyList(),
                explanation = explanation
            )
        }
        
        /**
         * 创建错误结果
         */
        fun error(errorMessage: String): PinyinQueryResult {
            return PinyinQueryResult(
                inputType = InputType.UNKNOWN,
                candidates = emptyList(),
                isError = true,
                errorMessage = errorMessage
            )
        }
    }
    
    /**
     * 获取候选词数量
     */
    val size: Int get() = candidates.size
    
    /**
     * 是否有候选词
     */
    val isEmpty: Boolean get() = candidates.isEmpty()
    
    /**
     * 是否有音节拆分
     */
    val hasSyllables: Boolean get() = syllables.isNotEmpty()
    
    /**
     * 获取拼音字符串（带空格）
     */
    val fullPinyin: String get() = syllables.joinToString(" ")
    
    /**
     * 获取拼音首字母字符串
     */
    val initialLetters: String get() = syllables.joinToString("") { 
        if (it.isNotEmpty()) it.first().toString() else "" 
    }
    
    /**
     * 获取统计信息
     */
    fun getStats(): String {
        val singleCharCount = candidates.count { it.word.length == 1 }
        val phraseCount = candidates.count { it.word.length > 1 }
        
        return "总计${candidates.size}个 (单字${singleCharCount}个, 词组${phraseCount}个)"
    }
} 