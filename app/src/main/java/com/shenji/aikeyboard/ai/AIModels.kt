package com.shenji.aikeyboard.ai

import android.view.inputmethod.EditorInfo

/**
 * AI引擎信息
 */
data class AIEngineInfo(
    val name: String,           // 引擎名称
    val version: String,        // 版本号
    val modelSize: Long,        // 模型大小(字节)
    val capabilities: Set<AICapability>, // 支持的能力
    val maxContextLength: Int,  // 最大上下文长度
    val averageLatency: Long    // 平均响应时间(ms)
)

/**
 * AI能力枚举
 */
enum class AICapability {
    PINYIN_CORRECTION,    // 拼音纠错
    TEXT_CONTINUATION,    // 文本续写
    SEMANTIC_ANALYSIS,    // 语义分析
    GRAMMAR_CHECK,        // 语法检查
    STYLE_CONVERSION,     // 风格转换
    TRANSLATION,          // 翻译
    MULTIMODAL_ANALYSIS   // 多模态分析（图像+文本）
}

/**
 * 输入上下文
 */
data class InputContext(
    val appPackage: String,           // 当前应用包名
    val inputType: Int,               // 输入类型 (EditorInfo.TYPE_*)
    val previousText: String,         // 前文
    val cursorPosition: Int,          // 光标位置
    val userPreferences: UserPreferences, // 用户偏好
    val timestamp: Long               // 时间戳
)

/**
 * 用户偏好设置
 */
data class UserPreferences(
    val language: String = "zh-CN",   // 语言偏好
    val aiEnabled: Boolean = true,    // AI功能开关
    val correctionLevel: CorrectionLevel = CorrectionLevel.MEDIUM, // 纠错级别
    val privacyMode: Boolean = false  // 隐私模式
)

/**
 * 纠错级别
 */
enum class CorrectionLevel {
    LOW,      // 低敏感度，只纠正明显错误
    MEDIUM,   // 中等敏感度，平衡准确性和干扰
    HIGH      // 高敏感度，积极纠错
}

/**
 * 纠错建议
 */
data class CorrectionSuggestion(
    val originalInput: String,        // 原始输入
    val correctedText: String,        // 纠正后文本
    val correctedPinyin: String,      // 纠正后拼音
    val confidence: Float,            // 置信度 0.0-1.0
    val errorType: ErrorType,         // 错误类型
    val explanation: String?          // 解释说明
)

/**
 * 错误类型
 */
enum class ErrorType {
    CONSONANT_CONFUSION,  // 声母混淆
    VOWEL_ERROR,         // 韵母错误
    TONE_MISSING,        // 音调缺失
    TYPO,               // 打字错误
    DIALECT_INFLUENCE,   // 方言影响
    UNKNOWN             // 未知错误
}

/**
 * 续写建议
 */
data class ContinuationSuggestion(
    val text: String,                 // 续写文本
    val confidence: Float,            // 置信度
    val type: ContinuationType        // 续写类型
)

/**
 * 续写类型
 */
enum class ContinuationType {
    WORD_COMPLETION,      // 词语补全
    SENTENCE_COMPLETION,  // 句子补全
    PARAGRAPH_CONTINUATION // 段落续写
}

/**
 * 语义分析结果
 */
data class SemanticAnalysis(
    val intent: String,               // 用户意图
    val sentiment: Sentiment,         // 情感倾向
    val topics: List<String>,         // 主题标签
    val confidence: Float             // 分析置信度
)

/**
 * 情感倾向
 */
enum class Sentiment {
    POSITIVE,    // 积极
    NEGATIVE,    // 消极
    NEUTRAL      // 中性
} 