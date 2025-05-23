package com.shenji.aikeyboard.pinyin

import com.shenji.aikeyboard.utils.PinyinSegmenterOptimized
import timber.log.Timber

/**
 * 统一拼音拆分器 - 整合所有拆分功能
 * 
 * 设计目标：
 * 1. 统一接口：提供一致的拆分API
 * 2. 性能优化：集成最优算法和缓存策略
 * 3. 功能完整：支持多种拆分模式和场景
 * 4. 易于维护：单一职责，清晰架构
 * 
 * 替代原有的多个拆分器：
 * - PinyinSplitter (pinyin包)
 * - PinyinSplitter (data包) 
 * - PinyinSplitterOptimized
 * - PinyinSegmenterOptimized (作为核心引擎)
 */
object UnifiedPinyinSplitter {
    
    // ==================== 核心接口 ====================
    
    /**
     * 主要拆分接口
     * 将连续拼音字符串拆分为音节列表
     * 
     * @param input 输入的拼音字符串
     * @return 拆分后的音节列表
     */
    fun split(input: String): List<String> {
        return PinyinSegmenterOptimized.cut(input)
    }
    
    /**
     * 多种拆分方案
     * 返回多种可能的拆分结果，按优先级排序
     * 
     * @param input 输入的拼音字符串
     * @return 多种拆分方案的列表
     */
    fun getMultipleSplits(input: String): List<List<String>> {
        val results = mutableListOf<List<String>>()
        
        // 主要拆分方案
        val primaryResult = split(input)
        if (primaryResult.isNotEmpty()) {
            results.add(primaryResult)
        }
        
        // 如果输入本身是有效音节
        if (isValidSyllable(input)) {
            val singleResult = listOf(input)
            if (!results.contains(singleResult)) {
                results.add(singleResult)
            }
        }
        
        // 首字母+音节混合模式
        val mixedResult = checkMixedInitialAndSyllable(input)
        if (mixedResult.isNotEmpty() && !results.contains(mixedResult)) {
            results.add(mixedResult)
        }
        
        return results
    }
    
    /**
     * 动态拆分（适用于输入过程中的实时拆分）
     * 即使无法完全拆分也会返回部分结果
     * 
     * @param input 输入的拼音字符串
     * @return 拆分结果，包含完整音节和剩余字符
     */
    fun splitDynamic(input: String): List<String> {
        val cleanInput = input.trim().lowercase().replace(" ", "")
        if (cleanInput.isEmpty()) return emptyList()
        
        // 先尝试完整拆分
        val fullSplit = split(cleanInput)
        if (fullSplit.isNotEmpty()) {
            return fullSplit
        }
        
        // 部分拆分：尽可能多地识别音节
        return partialSplit(cleanInput)
    }
    
    /**
     * 智能拆分
     * 结合多种策略，提供最佳拆分结果
     * 
     * @param input 输入的拼音字符串
     * @return 智能拆分结果
     */
    fun splitSmart(input: String): List<String> {
        val cleanInput = input.trim().lowercase().replace(" ", "")
        if (cleanInput.isEmpty()) return emptyList()
        
        // 快速路径：单音节或已知音节
        if (isValidSyllable(cleanInput)) {
            return listOf(cleanInput)
        }
        
        // 主要拆分算法
        val primaryResult = split(cleanInput)
        if (primaryResult.isNotEmpty()) {
            return primaryResult
        }
        
        // 备选方案：部分拆分
        return partialSplit(cleanInput)
    }
    
    // ==================== 辅助功能 ====================
    
    /**
     * 检查是否为有效音节
     */
    fun isValidSyllable(syllable: String): Boolean {
        return PinyinSegmenterOptimized.isValidSyllable(syllable)
    }
    
    /**
     * 获取所有有效音节
     */
    fun getValidSyllables(): Set<String> {
        return PinyinSegmenterOptimized.getValidSyllables()
    }
    
    /**
     * 生成拼音首字母缩写
     */
    fun generateInitials(pinyin: String): String {
        if (pinyin.isEmpty()) return ""
        
        // 如果拼音包含空格，按空格分割
        if (pinyin.contains(" ")) {
            return pinyin.split(" ")
                .filter { it.isNotEmpty() }
                .joinToString("") { it.first().toString() }
        }
        
        // 尝试拆分后生成首字母
        val syllables = split(pinyin)
        return if (syllables.isNotEmpty()) {
            syllables.joinToString("") { it.first().toString() }
        } else {
            // 无法拆分时，取第一个字符
            pinyin.first().toString()
        }
    }
    
    /**
     * 计算音节数量
     */
    fun countSyllables(pinyin: String): Int {
        return split(pinyin).size
    }
    
    /**
     * 标准化拼音（添加空格分隔）
     */
    fun normalize(input: String): String {
        val syllables = split(input)
        return if (syllables.isNotEmpty()) {
            syllables.joinToString(" ")
        } else {
            input
        }
    }
    
    // ==================== 性能和缓存管理 ====================
    
    /**
     * 获取性能统计
     */
    fun getPerformanceStats(): PinyinSegmenterOptimized.PerformanceStats {
        return PinyinSegmenterOptimized.getPerformanceStats()
    }
    
    /**
     * 重置性能统计
     */
    fun resetPerformanceStats() {
        PinyinSegmenterOptimized.resetPerformanceStats()
    }
    
    /**
     * 清空缓存
     */
    fun clearCache() {
        PinyinSegmenterOptimized.clearCache()
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 检查首字母+音节混合模式
     * 例如：sji -> s + ji
     */
    private fun checkMixedInitialAndSyllable(input: String): List<String> {
        if (input.length < 2) return emptyList()
        
        val initial = input.substring(0, 1)
        if (!initial.matches(Regex("[a-z]"))) return emptyList()
        
        val remaining = input.substring(1)
        
        // 检查剩余部分是否是有效音节
        if (isValidSyllable(remaining)) {
            return listOf(initial, remaining)
        }
        
        // 尝试拆分剩余部分
        val remainingSyllables = split(remaining)
        if (remainingSyllables.isNotEmpty()) {
            return listOf(initial) + remainingSyllables
        }
        
        return emptyList()
    }
    
    /**
     * 部分拆分：尽可能多地识别音节
     */
    private fun partialSplit(input: String): List<String> {
        val result = mutableListOf<String>()
        var pos = 0
        
        while (pos < input.length) {
            var found = false
            
            // 从最长可能的音节开始尝试
            for (len in minOf(input.length - pos, 6) downTo 1) {
                val candidate = input.substring(pos, pos + len)
                if (isValidSyllable(candidate)) {
                    result.add(candidate)
                    pos += len
                    found = true
                    break
                }
            }
            
            // 如果没找到有效音节，添加单个字符
            if (!found) {
                result.add(input.substring(pos, pos + 1))
                pos += 1
            }
        }
        
        return result
    }
    
    // ==================== 兼容性接口 ====================
    
    /**
     * 兼容旧接口：splitPinyin
     */
    @Deprecated("使用 split() 替代", ReplaceWith("split(input)"))
    fun splitPinyin(input: String): List<String> {
        return split(input)
    }
    
    /**
     * 兼容旧接口：cut
     */
    @Deprecated("使用 split() 替代", ReplaceWith("split(input)"))
    fun cut(input: String): List<String> {
        return split(input)
    }
    
    /**
     * 兼容旧接口：trySplitPinyin
     */
    @Deprecated("使用 splitSmart() 替代", ReplaceWith("splitSmart(input)"))
    fun trySplitPinyin(input: String): List<String> {
        return splitSmart(input)
    }
    
    // ==================== 调试和测试支持 ====================
    
    /**
     * 调试信息
     */
    fun getDebugInfo(): String {
        val stats = getPerformanceStats()
        return """
            |统一拼音拆分器调试信息:
            |  核心引擎: PinyinSegmenterOptimized V2.0
            |  支持音节数: ${getValidSyllables().size}
            |  ${stats.toString()}
        """.trimMargin()
    }
    
    /**
     * 测试拆分器功能
     */
    fun runSelfTest(): Boolean {
        val testCases = mapOf(
            "nihao" to listOf("ni", "hao"),
            "beijing" to listOf("bei", "jing"),
            "zhongguo" to listOf("zhong", "guo"),
            "a" to listOf("a"),
            "wo" to listOf("wo")
        )
        
        var allPassed = true
        for ((input, expected) in testCases) {
            val result = split(input)
            if (result != expected) {
                Timber.e("拆分测试失败: '$input' 期望 $expected, 实际 $result")
                allPassed = false
            }
        }
        
        Timber.i("统一拼音拆分器自测${if (allPassed) "通过" else "失败"}")
        return allPassed
    }
} 
 