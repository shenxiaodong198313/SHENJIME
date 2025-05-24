package com.shenji.aikeyboard.data.trie

/**
 * Trie树类型枚举
 * 简化版本，不依赖复杂的TrieBuilder
 */
enum class TrieType {
    CHARS,          // 单字词典
    BASE,           // 基础词典
    CORRELATION,    // 关联词典
    ASSOCIATIONAL, // 联想词典
    PLACE,          // 地名词典
    PEOPLE,         // 人名词典
    POETRY,         // 诗词词典
    CORRECTIONS,    // 纠错词典
    COMPATIBLE      // 兼容词典
} 