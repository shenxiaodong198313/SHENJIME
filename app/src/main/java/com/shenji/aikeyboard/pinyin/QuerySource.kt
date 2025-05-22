package com.shenji.aikeyboard.pinyin

/**
 * 查询来源枚举
 * 标识候选词的来源
 */
enum class QuerySource {
    REALM_DATABASE, // 从Realm数据库查询
    TRIE_INDEX      // 从Trie树索引查询
} 