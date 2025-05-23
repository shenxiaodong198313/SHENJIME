package com.shenji.aikeyboard.model

/**
 * 候选词数据模型
 * 
 * 包含候选词的完整信息，支持智能排序和用户学习
 */
data class Candidate(
    val word: String,                    // 候选词文本
    val pinyin: String,                  // 完整拼音
    val initialLetters: String,          // 首字母缩写
    val frequency: Int,                  // 基础词频
    val type: String,                    // 词典类型
    val weight: CandidateWeight,         // 综合权重
    val source: CandidateSource,         // 候选词来源
    val metadata: CandidateMetadata = CandidateMetadata()  // 元数据
) {
    /**
     * 最终排序权重
     */
    val finalWeight: Float get() = weight.totalWeight
    
    /**
     * 是否为用户偏好词
     */
    val isUserPreferred: Boolean get() = weight.userPreference > 0.7f
    
    /**
     * 候选词显示文本（可能包含高亮等格式）
     */
    fun getDisplayText(highlight: Boolean = false): String {
        return if (highlight && source.matchType != MatchType.EXACT) {
            // 可以在这里添加高亮逻辑
            word
        } else {
            word
        }
    }
}

/**
 * 候选词权重信息
 */
data class CandidateWeight(
    val baseFrequency: Float,        // 基础词频权重 (0.0-1.0)
    val matchAccuracy: Float,        // 匹配精度权重 (0.0-1.0)
    val userPreference: Float,       // 用户偏好权重 (0.0-1.0)
    val contextRelevance: Float,     // 上下文相关性 (0.0-1.0)
    val inputEfficiency: Float,      // 输入效率权重 (0.0-1.0)
    val temporalFactor: Float,       // 时间因子权重 (0.0-1.0)
    val customBonus: Float = 0.0f    // 自定义加成 (0.0-1.0)
) {
    /**
     * 计算总权重
     * 各权重的重要性配比可以根据实际使用情况调整
     */
    val totalWeight: Float get() = 
        baseFrequency * 0.25f +
        matchAccuracy * 0.25f +
        userPreference * 0.20f +
        contextRelevance * 0.15f +
        inputEfficiency * 0.10f +
        temporalFactor * 0.05f +
        customBonus
    
    companion object {
        /**
         * 创建默认权重
         */
        fun default(baseFreq: Int) = CandidateWeight(
            baseFrequency = normalizeFrequency(baseFreq),
            matchAccuracy = 0.5f,
            userPreference = 0.0f,
            contextRelevance = 0.0f,
            inputEfficiency = 0.5f,
            temporalFactor = 0.5f
        )
        
        /**
         * 标准化词频到0-1范围
         */
        fun normalizeFrequency(freq: Int): Float {
            // 假设最大词频为100000
            return (freq.toFloat() / 100000f).coerceIn(0.0f, 1.0f)
        }
    }
}

/**
 * 候选词来源信息
 */
data class CandidateSource(
    val generator: GeneratorType,        // 生成器类型
    val matchType: MatchType,           // 匹配类型
    val layer: Int,                     // 生成层级 (1-5)
    val confidence: Float,              // 置信度 (0.0-1.0)
    val processingTime: Long = 0L       // 处理耗时(ms)
)

/**
 * 生成器类型
 */
enum class GeneratorType {
    EXACT_MATCH,        // 精确匹配生成器
    PREFIX_MATCH,       // 前缀匹配生成器
    FUZZY_MATCH,        // 模糊匹配生成器
    SMART_SUGGESTION,   // 智能联想生成器
    CONTEXT_PREDICTION, // 上下文预测生成器
    USER_LEARNING,      // 用户学习生成器
    COMBINATION,        // 组合生成器
    PROGRESSIVE,        // 渐进生成器
    PARTIAL             // 部分匹配生成器
}

/**
 * 匹配类型
 */
enum class MatchType {
    EXACT,              // 精确匹配
    PREFIX,             // 前缀匹配
    FUZZY,              // 模糊匹配
    ACRONYM,            // 首字母匹配
    MIXED,              // 混合匹配
    PREDICTION,         // 预测匹配
    PARTIAL,            // 部分匹配
    COMBINATION         // 组合匹配
}

/**
 * 候选词元数据
 */
data class CandidateMetadata(
    val length: Int = 0,                // 词长度
    val syllableCount: Int = 0,         // 音节数
    val isCommonWord: Boolean = false,  // 是否为常用词
    val isUserDefined: Boolean = false, // 是否为用户自定义词
    val lastUsedTime: Long = 0L,        // 最后使用时间
    val usageCount: Int = 0,            // 使用次数
    val tags: Set<String> = emptySet()  // 标签集合
)

/**
 * 候选词构建器
 */
class CandidateBuilder {
    private var word: String = ""
    private var pinyin: String = ""
    private var initialLetters: String = ""
    private var frequency: Int = 0
    private var type: String = ""
    private var weight: CandidateWeight = CandidateWeight.default(0)
    private var source: CandidateSource = CandidateSource(
        GeneratorType.EXACT_MATCH, 
        MatchType.EXACT, 
        1, 
        1.0f
    )
    private var metadata: CandidateMetadata = CandidateMetadata()
    
    fun word(word: String) = apply { this.word = word }
    fun pinyin(pinyin: String) = apply { this.pinyin = pinyin }
    fun initialLetters(letters: String) = apply { this.initialLetters = letters }
    fun frequency(freq: Int) = apply { this.frequency = freq }
    fun type(type: String) = apply { this.type = type }
    fun weight(weight: CandidateWeight) = apply { this.weight = weight }
    fun source(source: CandidateSource) = apply { this.source = source }
    fun metadata(metadata: CandidateMetadata) = apply { this.metadata = metadata }
    
    fun build(): Candidate {
        require(word.isNotEmpty()) { "候选词文本不能为空" }
        
        return Candidate(
            word = word,
            pinyin = pinyin,
            initialLetters = initialLetters,
            frequency = frequency,
            type = type,
            weight = weight,
            source = source,
            metadata = metadata.copy(
                length = word.length,
                syllableCount = if (pinyin.isNotEmpty()) pinyin.split(" ").size else 0
            )
        )
    }
} 