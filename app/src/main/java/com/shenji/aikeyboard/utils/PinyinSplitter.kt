package com.shenji.aikeyboard.utils

import timber.log.Timber

/**
 * 拼音分词器
 * 将无空格拼音字符串分割为标准音节序列
 * 支持两种分词算法：从左到右和从右到左，优先使用从左到右
 */
object PinyinSplitter {
    
    // 缓存常用拼音分词结果，避免重复计算
    private val splitCache = mutableMapOf<String, List<String>>()
    
    /**
     * 将无空格拼音分割为标准音节列表
     * 先尝试从左到右的最长匹配算法，若失败则尝试从右到左的最长匹配
     * 
     * @param input 待分割的拼音字符串
     * @return 分割后的音节列表，若无法分割则返回空列表
     */
    fun split(input: String): List<String> {
        // 【PYDEBUG】记录开始拆分拼音
        Timber.d("【PYDEBUG】开始拆分拼音: '$input'")
        
        // 检查输入是否为空
        if (input.isBlank()) {
            Timber.d("【PYDEBUG】输入为空，返回空列表")
            return emptyList()
        }
        
        // 预处理输入
        val normalized = preprocessInput(input)
        if (normalized == null) {
            Timber.d("【PYDEBUG】预处理失败，返回空列表")
            return emptyList()
        }
        Timber.d("【PYDEBUG】预处理后: '$normalized'")
        
        // 检查缓存
        splitCache[normalized]?.let { 
            Timber.d("【PYDEBUG】命中缓存: ${it.joinToString(", ")}")
            return it 
        }
        
        // 首先尝试从左到右分词（更符合汉语拼音习惯和构词规则）
        Timber.d("【PYDEBUG】尝试从左到右分词: '$normalized'")
        val resultFromLeft = splitFromLeft(normalized)
        Timber.d("【PYDEBUG】从左到右分词结果: ${resultFromLeft.joinToString(", ")}")
        
        // 若从左到右分词失败，则尝试从右到左分词
        val result = if (resultFromLeft.isNotEmpty()) {
            Timber.d("【PYDEBUG】使用从左到右分词结果")
            resultFromLeft
        } else {
            Timber.d("【PYDEBUG】尝试从右到左分词: '$normalized'")
            val resultFromRight = splitFromRight(normalized)
            Timber.d("【PYDEBUG】从右到左分词结果: ${resultFromRight.joinToString(", ")}")
            resultFromRight
        }
        
        // 缓存结果（仅当成功分词时）
        if (result.isNotEmpty()) {
            splitCache[normalized] = result
            Timber.d("【PYDEBUG】成功分词并缓存: '$normalized' -> ${result.joinToString(", ")}")
        } else {
            Timber.d("【PYDEBUG】无法完整分词: '$normalized'")
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
            Timber.d("【PYDEBUG】输入包含非法字符: $input")
            return null
        }
        
        // 转换为小写并规范化特殊字符
        val result = PinyinSyllableManager.normalizeSpecialChar(input.lowercase().trim())
        Timber.d("【PYDEBUG】预处理: '$input' -> '$result'")
        return result
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
                Timber.d("【PYDEBUG】从右到左检查: '$substr'")
                
                if (PinyinSyllableManager.isValidSyllable(substr)) {
                    result.add(substr)
                    pos -= len
                    found = true
                    Timber.d("【PYDEBUG】从右到左找到音节: '$substr'")
                    break
                }
            }
            
            // 若无法匹配任何有效音节，则分词失败
            if (!found) {
                Timber.d("【PYDEBUG】从右到左分词失败，在位置 $pos 处无法找到有效音节: ${input.substring(0, pos)}")
                return emptyList()
            }
        }
        
        // 由于是从右到左分词，需要反转结果
        val reversed = result.reversed()
        Timber.d("【PYDEBUG】从右到左最终结果: ${reversed.joinToString(", ")}")
        return reversed
    }
    
    /**
     * 从左到右进行拼音分词
     * 优先匹配最长音节
     * 
     * @param input 预处理后的输入字符串
     * @return 分词结果，若无法完全分词则返回空列表
     */
    private fun splitFromLeft(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        
        val result = mutableListOf<String>()
        var pos = 0
        
        while (pos < input.length) {
            // 计算当前位置可能的最大音节长度
            val maxLen = minOf(PinyinSyllableManager.MAX_SYLLABLE_LENGTH, input.length - pos)
            var found = false
            
            // 从最长音节开始尝试匹配
            for (len in maxLen downTo 1) {
                if (pos + len <= input.length) {
                    val substr = input.substring(pos, pos + len)
                    Timber.d("【PYDEBUG】从左到右检查: '$substr'")
                    
                    val isValid = PinyinSyllableManager.isValidSyllable(substr)
                    Timber.d("【PYDEBUG】'$substr' 是否有效音节: $isValid")
                    
                    if (isValid) {
                        result.add(substr)
                        pos += len
                        found = true
                        Timber.d("【PYDEBUG】从左到右找到音节: '$substr'")
                        break
                    }
                }
            }
            
            // 若无法匹配任何有效音节，则分词失败
            if (!found) {
                Timber.d("【PYDEBUG】从左到右分词失败，在位置 $pos 处无法找到有效音节: ${input.substring(pos)}")
                return emptyList()
            }
        }
        
        Timber.d("【PYDEBUG】从左到右最终结果: ${result.joinToString(", ")}")
        return result
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
    
    /**
     * 智能分词
     * 尝试将输入尽可能地分解为标准音节和单字母部分
     * 即使无法完整分词也会返回部分结果
     * 
     * @param input 待分词的拼音字符串
     * @return 分解后的部分列表（可能包含非标准音节）
     */
    fun smartSplit(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        
        // 预处理输入
        val normalized = preprocessInput(input) ?: return emptyList()
        
        // 先尝试完整分词
        val fullSplit = split(normalized)
        if (fullSplit.isNotEmpty()) {
            Timber.d("完整分词成功: '$normalized' -> ${fullSplit.joinToString(", ")}")
            return fullSplit
        }
        
        // 如果完整分词失败，尝试部分分词 - 先尝试从左到右分词
        val partialSplit = partialSplit(normalized)
        Timber.d("部分分词结果: '$normalized' -> ${partialSplit.joinToString(", ")}")
        
        // 特殊处理：检查是否可以识别出有效音节+单字母组合（如"weix"）
        val syllablePlusSingle = checkSyllablePlusSingleLetter(normalized)
        if (syllablePlusSingle.isNotEmpty()) {
            Timber.d("识别音节+单字母组合: '$normalized' -> ${syllablePlusSingle.joinToString(", ")}")
            return syllablePlusSingle
        }
        
        return partialSplit
    }
    
    /**
     * 检查是否是有效音节+单字母的组合
     * 例如"weix" -> ["wei", "x"]
     */
    private fun checkSyllablePlusSingleLetter(input: String): List<String> {
        if (input.length <= 2) return emptyList()
        
        // 从最长的有效音节开始尝试
        for (i in minOf(input.length - 1, PinyinSyllableManager.MAX_SYLLABLE_LENGTH) downTo 1) {
            val prefix = input.substring(0, i)
            
            if (PinyinSyllableManager.isValidSyllable(prefix)) {
                // 如果前缀是有效音节，剩余部分按字母拆分
                val result = mutableListOf<String>()
                result.add(prefix)
                
                var pos = i
                while (pos < input.length) {
                    // 检查后续部分是否可以形成有效音节
                    var foundNextSyllable = false
                    for (j in minOf(input.length - pos, PinyinSyllableManager.MAX_SYLLABLE_LENGTH) downTo 1) {
                        if (pos + j <= input.length) {
                            val nextPart = input.substring(pos, pos + j)
                            if (PinyinSyllableManager.isValidSyllable(nextPart)) {
                                result.add(nextPart)
                                pos += j
                                foundNextSyllable = true
                                break
                            }
                        }
                    }
                    
                    // 如果没找到有效音节，添加单字母
                    if (!foundNextSyllable) {
                        result.add(input.substring(pos, pos + 1))
                        pos += 1
                    }
                }
                
                // 记录日志
                Timber.d("音节+字母分割结果: '$input' -> ${result.joinToString(", ")}")
                return result
            }
        }
        
        return emptyList()
    }
    
    /**
     * 部分分词
     * 从左到右，尽可能匹配最长的有效音节，无法匹配时添加单字母
     */
    private fun partialSplit(input: String): List<String> {
        val parts = mutableListOf<String>()
        var pos = 0
        
        while (pos < input.length) {
            // 尝试从当前位置匹配最长的有效音节
            var found = false
            
            // 从最长可能的音节长度开始尝试
            val maxLen = minOf(PinyinSyllableManager.MAX_SYLLABLE_LENGTH, input.length - pos)
            
            for (len in maxLen downTo 1) {
                if (pos + len <= input.length) {
                    val part = input.substring(pos, pos + len)
                    
                    if (PinyinSyllableManager.isValidSyllable(part)) {
                        Timber.d("找到有效音节: '$part'")
                        parts.add(part)
                        pos += len
                        found = true
                        break
                    }
                }
            }
            
            // 如果无法匹配有效音节，添加单字母
            if (!found) {
                val letter = input.substring(pos, pos + 1)
                Timber.d("添加单字母: '$letter'")
                parts.add(letter)
                pos += 1
            }
        }
        
        return parts
    }
} 