package com.shenji.aikeyboard.engine.analyzer

/**
 * 输入模式枚举
 * 
 * 定义了所有支持的输入模式类型
 */
enum class InputMode {
    // 基础模式
    SINGLE_LETTER,           // b
    PURE_ACRONYM,           // bj, bjr
    PURE_PINYIN,            // beijing, beijingren
    PARTIAL_PINYIN,         // beij, beijingr
    
    // 混合模式
    ACRONYM_PINYIN_MIX,     // bjing, bjeijing
    PINYIN_ACRONYM_MIX,     // beijingr, beijingre
    
    // 高级模式
    SENTENCE_INPUT,         // woshibeijingren
    PROGRESSIVE_INPUT,      // 渐进式输入过程
    CONTEXT_AWARE,          // 基于上下文的输入
    
    // 特殊模式
    CORRECTION_MODE,        // 纠错模式
    PREDICTION_MODE         // 预测模式
}

/**
 * 分段类型枚举
 */
enum class SegmentType {
    SINGLE_LETTER,      // 单字母
    ACRONYM,           // 缩写
    COMPLETE_SYLLABLE, // 完整音节
    PARTIAL_SYLLABLE,  // 部分音节
    UNKNOWN            // 未知
}

/**
 * 输入分析结果
 * 
 * 包含完整的输入分析信息
 */
data class InputAnalysis(
    val input: String,                          // 原始输入
    val mode: InputMode,                        // 识别的输入模式
    val confidence: Float,                      // 置信度 (0.0-1.0)
    val segments: List<InputSegment>,           // 分段结果
    val alternatives: List<InputMode>,          // 备选模式
    val charPattern: CharacterPattern,          // 字符模式
    val syllableStructure: SyllableStructure,   // 音节结构
    val processingTime: Long,                   // 处理耗时(ms)
    val errorMessage: String? = null            // 错误信息
) {
    /**
     * 是否为高置信度结果
     */
    val isHighConfidence: Boolean get() = confidence >= 0.8f
    
    /**
     * 是否为混合模式
     */
    val isMixedMode: Boolean get() = mode in listOf(
        InputMode.ACRONYM_PINYIN_MIX,
        InputMode.PINYIN_ACRONYM_MIX
    )
    
    /**
     * 获取主要分段（最长的分段）
     */
    val primarySegment: InputSegment? get() = segments.maxByOrNull { it.length }
    
    /**
     * 获取所有音节分段
     */
    val syllableSegments: List<InputSegment> get() = segments.filter { 
        it.type == SegmentType.COMPLETE_SYLLABLE 
    }
    
    /**
     * 获取所有缩写分段
     */
    val acronymSegments: List<InputSegment> get() = segments.filter { 
        it.type == SegmentType.ACRONYM 
    }
    
    companion object {
        /**
         * 创建空的分析结果
         */
        fun empty() = InputAnalysis(
            input = "",
            mode = InputMode.SINGLE_LETTER,
            confidence = 0.0f,
            segments = emptyList(),
            alternatives = emptyList(),
            charPattern = CharacterPattern.empty(),
            syllableStructure = SyllableStructure.empty(),
            processingTime = 0L
        )
        
        /**
         * 创建错误的分析结果
         */
        fun error(input: String, message: String) = InputAnalysis(
            input = input,
            mode = InputMode.SINGLE_LETTER,
            confidence = 0.0f,
            segments = emptyList(),
            alternatives = emptyList(),
            charPattern = CharacterPattern.empty(),
            syllableStructure = SyllableStructure.empty(),
            processingTime = 0L,
            errorMessage = message
        )
    }
}

/**
 * 输入分段信息
 * 
 * 表示输入中的一个分段
 */
data class InputSegment(
    val text: String,           // 分段文本
    val type: SegmentType,      // 分段类型
    val startPos: Int,          // 起始位置
    val endPos: Int             // 结束位置
) {
    /**
     * 分段长度
     */
    val length: Int get() = endPos - startPos
    
    /**
     * 是否为音节分段
     */
    val isSyllable: Boolean get() = type == SegmentType.COMPLETE_SYLLABLE
    
    /**
     * 是否为缩写分段
     */
    val isAcronym: Boolean get() = type == SegmentType.ACRONYM
    
    /**
     * 是否为部分音节
     */
    val isPartialSyllable: Boolean get() = type == SegmentType.PARTIAL_SYLLABLE
}

/**
 * 字符模式信息
 * 
 * 描述输入的字符组成特征
 */
data class CharacterPattern(
    val totalLength: Int,       // 总长度
    val letterCount: Int,       // 字母数量
    val digitCount: Int,        // 数字数量
    val spaceCount: Int,        // 空格数量
    val otherCount: Int,        // 其他字符数量
    val isAllLetters: Boolean,  // 是否全为字母
    val hasSpaces: Boolean,     // 是否包含空格
    val hasDigits: Boolean,     // 是否包含数字
    val hasOthers: Boolean      // 是否包含其他字符
) {
    /**
     * 字母占比
     */
    val letterRatio: Float get() = if (totalLength > 0) letterCount.toFloat() / totalLength else 0f
    
    /**
     * 是否为纯字母输入
     */
    val isPureLetters: Boolean get() = isAllLetters && totalLength > 0
    
    /**
     * 是否为简单输入（只包含字母和空格）
     */
    val isSimpleInput: Boolean get() = !hasDigits && !hasOthers
    
    companion object {
        fun empty() = CharacterPattern(
            totalLength = 0,
            letterCount = 0,
            digitCount = 0,
            spaceCount = 0,
            otherCount = 0,
            isAllLetters = false,
            hasSpaces = false,
            hasDigits = false,
            hasOthers = false
        )
    }
}

/**
 * 音节结构信息
 * 
 * 描述输入的音节组成特征
 */
data class SyllableStructure(
    val syllables: List<String>,        // 拆分的音节列表
    val canSplit: Boolean,              // 是否可以拆分
    val isSingleSyllable: Boolean,      // 是否为单个音节
    val partialMatches: List<String>,   // 部分匹配的音节
    val totalSyllables: Int             // 音节总数
) {
    /**
     * 是否为多音节
     */
    val isMultiSyllable: Boolean get() = totalSyllables > 1
    
    /**
     * 是否有部分匹配
     */
    val hasPartialMatches: Boolean get() = partialMatches.isNotEmpty()
    
    /**
     * 平均音节长度
     */
    val averageSyllableLength: Float get() = if (totalSyllables > 0) {
        syllables.sumOf { it.length }.toFloat() / totalSyllables
    } else 0f
    
    /**
     * 最长音节
     */
    val longestSyllable: String? get() = syllables.maxByOrNull { it.length }
    
    /**
     * 最短音节
     */
    val shortestSyllable: String? get() = syllables.minByOrNull { it.length }
    
    companion object {
        fun empty() = SyllableStructure(
            syllables = emptyList(),
            canSplit = false,
            isSingleSyllable = false,
            partialMatches = emptyList(),
            totalSyllables = 0
        )
    }
} 