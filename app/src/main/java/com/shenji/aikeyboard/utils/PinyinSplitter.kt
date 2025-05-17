package com.shenji.aikeyboard.utils

import timber.log.Timber

/**
 * 拼音分词器
 * 将无空格拼音字符串分割为标准音节序列
 * 采用从右到左的最长匹配算法
 */
object PinyinSplitter {
    
    // 缓存常用拼音分词结果，避免重复计算
    private val splitCache = mutableMapOf<String, List<String>>()
    
    /**
     * 将无空格拼音分割为标准音节列表
     * 采用从右到左的最长匹配算法
     * 
     * @param input 待分割的拼音字符串
     * @return 分割后的音节列表，若无法分割则返回空列表
     */
    fun split(input: String): List<String> {
        // 检查输入是否为空
        if (input.isBlank()) return emptyList()
        
        // 预处理输入
        val normalized = preprocessInput(input) ?: return emptyList()
        
        // 检查缓存
        splitCache[normalized]?.let { return it }
        
        // 从右到左分词
        val result = splitFromRight(normalized)
        
        // 缓存结果（仅当成功分词时）
        if (result.isNotEmpty()) {
            splitCache[normalized] = result
        }
        
        return result
    }
    
    /**
     * 预处理输入拼音
     * 1. 转换为小写
     * 2. 移除非法字符
     * 3. 替换特殊字符（如ü→v）
     * 
     * @return 处理后的字符串，若包含非法字符则返回null
     */
    private fun preprocessInput(input: String): String? {
        // 检查是否只包含合法字符
        if (!input.matches(Regex("[a-zü ]+"))) {
            Timber.d("输入包含非法字符: $input")
            return null
        }
        
        // 转换为小写并规范化特殊字符
        return PinyinSyllableManager.normalizeSpecialChar(input.lowercase().trim())
    }
    
    /**
     * 从右到左进行拼音分词
     * 优先匹配最长音节
     * 
     * @param input 预处理后的输入字符串
     * @return 分词结果，若无法完全分词则返回空列表
     */
    private fun splitFromRight(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        
        val result = mutableListOf<String>()
        var pos = input.length
        
        while (pos > 0) {
            // 计算当前位置可能的最大音节长度
            val maxLen = PinyinSyllableManager.MAX_SYLLABLE_LENGTH.coerceAtMost(pos)
            var found = false
            
            // 从最长音节开始尝试匹配
            for (len in maxLen downTo 1) {
                val substr = input.substring(pos - len, pos)
                
                if (PinyinSyllableManager.isValidSyllable(substr)) {
                    result.add(substr)
                    pos -= len
                    found = true
                    break
                }
            }
            
            // 若无法匹配任何有效音节，则分词失败
            if (!found) {
                Timber.d("无法分词，在位置 $pos 处无法找到有效音节: ${input.substring(0, pos)}")
                return emptyList()
            }
        }
        
        // 由于是从右到左分词，需要反转结果
        return result.reversed()
    }
    
    /**
     * 将拼音音节列表转换为带空格的字符串
     */
    fun joinSyllables(syllables: List<String>): String {
        return syllables.joinToString(" ")
    }
    
    /**
     * 单步操作：将无空格拼音转换为带空格的标准拼音
     * 若无法分词，则返回原输入
     */
    fun normalize(input: String): String {
        val syllables = split(input)
        return if (syllables.isNotEmpty()) {
            joinSyllables(syllables)
        } else {
            // 无法分词时返回原输入
            input
        }
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        splitCache.clear()
    }
} 