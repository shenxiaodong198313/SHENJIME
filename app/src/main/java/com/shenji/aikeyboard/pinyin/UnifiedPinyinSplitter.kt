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
 * 5. v/ü转换：支持汉语拼音v代替ü的输入规则
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
        // 预处理：处理v到ü的转换
        val processedInput = preprocessVToU(input)
        return PinyinSegmenterOptimized.cut(processedInput)
    }
    
    /**
     * 预处理v到ü的转换
     * 根据汉语拼音规则，处理v代替ü的情况
     * 
     * 规则：
     * 1. lv -> lü (绿)
     * 2. nv -> nü (女) 
     * 3. jv -> ju (居) - j后面的v转为u
     * 4. qv -> qu (去) - q后面的v转为u
     * 5. xv -> xu (虚) - x后面的v转为u
     * 6. yv -> yu (鱼) - y后面的v转为u
     * 
     * @param input 原始拼音输入
     * @return 处理后的拼音字符串
     */
    fun preprocessVToU(input: String): String {
        if (input.isEmpty() || !input.contains('v')) {
            return input
        }
        
        var result = input.lowercase()
        
        // 🔧 修复：更精确的v转换规则
        // 1. lv -> lü (绿色)
        result = result.replace(Regex("\\blv\\b"), "lü")
        result = result.replace(Regex("\\blv([aeiou])"), "lü$1") 
        result = result.replace(Regex("\\blv([ng])"), "lü$1")
        result = result.replace(Regex("lv([bcdfghjklmnpqrstvwxyz])"), "lü$1") // 连续拼音
        
        // 2. nv -> nü (女性)
        result = result.replace(Regex("\\bnv\\b"), "nü")
        result = result.replace(Regex("\\bnv([aeiou])"), "nü$1")
        result = result.replace(Regex("\\bnv([ng])"), "nü$1") 
        result = result.replace(Regex("nv([bcdfghjklmnpqrstvwxyz])"), "nü$1") // 连续拼音
        
        // 3. j/q/x/y + v -> j/q/x/y + u (居、去、虚、鱼)
        result = result.replace(Regex("\\b([jqxy])v\\b"), "$1u")
        result = result.replace(Regex("\\b([jqxy])v([aeiou])"), "$1u$2")
        result = result.replace(Regex("\\b([jqxy])v([ng])"), "$1u$2")
        result = result.replace(Regex("([jqxy])v([bcdfghjklmnpqrstvwxyz])"), "$1u$2") // 连续拼音
        
        if (result != input) {
            Timber.d("🔄 拼音v转换: '$input' -> '$result'")
        }
        return result
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
    
    /**
     * 分段拆分 - 新增功能
     * 将长拼音字符串拆分为多个独立的词组片段
     * 适用于句子级别的拼音输入
     * 
     * @param input 输入的拼音字符串
     * @return 分段拆分结果，每个元素是一个词组的音节列表
     */
    fun splitIntoSegments(input: String): List<List<String>> {
        val cleanInput = input.trim().lowercase().replace(" ", "")
        if (cleanInput.isEmpty()) return emptyList()
        
        // 如果输入较短（≤6字符），直接使用普通拆分
        if (cleanInput.length <= 6) {
            val result = split(cleanInput)
            return if (result.isNotEmpty()) listOf(result) else emptyList()
        }
        
        // 长输入使用分段策略
        return performSegmentedSplit(cleanInput)
    }
    
    /**
     * 获取分段拆分的候选方案
     * 返回多种可能的分段方式
     * 
     * @param input 输入的拼音字符串
     * @return 多种分段方案，每个方案包含多个词组
     */
    fun getSegmentedSplitOptions(input: String): List<List<List<String>>> {
        val cleanInput = input.trim().lowercase().replace(" ", "")
        if (cleanInput.isEmpty()) return emptyList()
        
        val options = mutableListOf<List<List<String>>>()
        
        // 主要分段方案
        val primarySegments = splitIntoSegments(cleanInput)
        if (primarySegments.isNotEmpty()) {
            options.add(primarySegments)
        }
        
        // 备选分段方案：更细粒度的分割
        val fineGrainedSegments = performFineGrainedSplit(cleanInput)
        if (fineGrainedSegments.isNotEmpty() && fineGrainedSegments != primarySegments) {
            options.add(fineGrainedSegments)
        }
        
        // 备选方案：按固定长度分段
        val fixedLengthSegments = performFixedLengthSplit(cleanInput)
        if (fixedLengthSegments.isNotEmpty() && !options.contains(fixedLengthSegments)) {
            options.add(fixedLengthSegments)
        }
        
        return options
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
    
    /**
     * 执行分段拆分的核心逻辑
     */
    private fun performSegmentedSplit(input: String): List<List<String>> {
        val segments = mutableListOf<List<String>>()
        var pos = 0
        
        while (pos < input.length) {
            val segment = findNextSegment(input, pos)
            if (!segment.isEmpty()) {
                segments.add(segment.syllables)
                pos = segment.endPos
            } else {
                // 无法找到有效分段，尝试单字符处理
                val remaining = input.substring(pos)
                val fallbackSplit = partialSplit(remaining)
                if (fallbackSplit.isNotEmpty()) {
                    segments.add(fallbackSplit)
                }
                break
            }
        }
        
        return segments
    }
    
    /**
     * 查找下一个有效的词组分段
     */
    private fun findNextSegment(input: String, startPos: Int): SegmentResult {
        if (startPos >= input.length) return SegmentResult.empty()
        
        // 尝试不同长度的分段，优先较长的分段
        for (segmentLength in minOf(input.length - startPos, 12) downTo 2) {
            val candidate = input.substring(startPos, startPos + segmentLength)
            val syllables = split(candidate)
            
            // 检查是否是有效的词组分段
            if (syllables.isNotEmpty() && isValidSegment(syllables)) {
                return SegmentResult(syllables, startPos + segmentLength)
            }
        }
        
        // 如果没找到合适的分段，尝试单个音节
        for (syllableLength in minOf(input.length - startPos, 6) downTo 1) {
            val candidate = input.substring(startPos, startPos + syllableLength)
            if (isValidSyllable(candidate)) {
                return SegmentResult(listOf(candidate), startPos + syllableLength)
            }
        }
        
        return SegmentResult.empty()
    }
    
    /**
     * 检查是否是有效的词组分段
     */
    private fun isValidSegment(syllables: List<String>): Boolean {
        // 基本检查：所有音节都有效
        if (syllables.any { !isValidSyllable(it) }) {
            return false
        }
        
        // 长度检查：合理的词组长度（1-4个音节）
        if (syllables.size > 4) {
            return false
        }
        
        // 可以添加更多的词组有效性检查
        // 例如：检查是否是常见的词组模式
        
        return true
    }
    
    /**
     * 执行细粒度分段拆分
     */
    private fun performFineGrainedSplit(input: String): List<List<String>> {
        val segments = mutableListOf<List<String>>()
        var pos = 0
        
        while (pos < input.length) {
            // 优先寻找较短的分段（1-2个音节）
            val segment = findShortSegment(input, pos)
            if (!segment.isEmpty()) {
                segments.add(segment.syllables)
                pos = segment.endPos
            } else {
                // 处理剩余字符
                val remaining = input.substring(pos, minOf(pos + 3, input.length))
                val syllables = partialSplit(remaining)
                if (syllables.isNotEmpty()) {
                    segments.add(syllables)
                    pos += remaining.length
                } else {
                    pos++
                }
            }
        }
        
        return segments
    }
    
    /**
     * 查找短分段（1-2个音节）
     */
    private fun findShortSegment(input: String, startPos: Int): SegmentResult {
        if (startPos >= input.length) return SegmentResult.empty()
        
        // 优先尝试2个音节的组合
        for (segmentLength in minOf(input.length - startPos, 6) downTo 2) {
            val candidate = input.substring(startPos, startPos + segmentLength)
            val syllables = split(candidate)
            
            if (syllables.isNotEmpty() && syllables.size <= 2) {
                return SegmentResult(syllables, startPos + segmentLength)
            }
        }
        
        // 尝试单个音节
        for (syllableLength in minOf(input.length - startPos, 4) downTo 1) {
            val candidate = input.substring(startPos, startPos + syllableLength)
            if (isValidSyllable(candidate)) {
                return SegmentResult(listOf(candidate), startPos + syllableLength)
            }
        }
        
        return SegmentResult.empty()
    }
    
    /**
     * 按固定长度执行分段拆分
     */
    private fun performFixedLengthSplit(input: String): List<List<String>> {
        val segments = mutableListOf<List<String>>()
        var pos = 0
        val segmentSize = 6 // 每段大约6个字符
        
        while (pos < input.length) {
            val endPos = minOf(pos + segmentSize, input.length)
            val segment = input.substring(pos, endPos)
            val syllables = split(segment)
            
            if (syllables.isNotEmpty()) {
                segments.add(syllables)
                pos = endPos
            } else {
                // 如果无法拆分，尝试更短的片段
                val shorterSegment = input.substring(pos, minOf(pos + 3, input.length))
                val shorterSyllables = partialSplit(shorterSegment)
                if (shorterSyllables.isNotEmpty()) {
                    segments.add(shorterSyllables)
                    pos += shorterSegment.length
                } else {
                    pos++
                }
            }
        }
        
        return segments
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
        
        // 测试分段拆分功能
        val segmentTestCases = mapOf(
            "wofaxianwenti" to 2, // 应该分为2-4个分段
            "nihaoshijie" to 2
        )
        
        for ((input, expectedMinSegments) in segmentTestCases) {
            val segments = splitIntoSegments(input)
            if (segments.size < expectedMinSegments) {
                Timber.e("分段拆分测试失败: '$input' 期望至少 $expectedMinSegments 个分段, 实际 ${segments.size} 个")
                allPassed = false
            }
        }
        
        Timber.i("统一拼音拆分器自测${if (allPassed) "通过" else "失败"}")
        return allPassed
    }
    
    // ==================== 数据类定义 ====================
    
    /**
     * 分段结果数据类
     */
    private data class SegmentResult(
        val syllables: List<String>,
        val endPos: Int
    ) {
        fun isEmpty(): Boolean = syllables.isEmpty()
        
        companion object {
            fun empty(): SegmentResult = SegmentResult(emptyList(), 0)
        }
    }
} 
 