package com.shenji.aikeyboard.engine.analyzer

import com.shenji.aikeyboard.utils.PinyinSegmenterOptimized
import timber.log.Timber

/**
 * 智能输入分析器
 * 
 * 负责分析用户输入，识别输入模式和分段结构
 * 支持多种输入模式的精确识别和混合模式处理
 */
class InputAnalyzer {
    
    /**
     * 分析用户输入
     * 
     * @param input 用户输入的拼音字符串
     * @return 输入分析结果
     */
    fun analyze(input: String): InputAnalysis {
        val startTime = System.currentTimeMillis()
        
        try {
            // 1. 基础验证
            if (input.isEmpty()) {
                return InputAnalysis.empty()
            }
            
            // 2. 字符模式分析
            val charPattern = analyzeCharacterPattern(input)
            
            // 3. 音节结构分析
            val syllableStructure = analyzeSyllableStructure(input)
            
            // 4. 输入模式识别
            val inputMode = identifyInputMode(input, charPattern, syllableStructure)
            
            // 5. 分段解析
            val segments = parseSegments(input, inputMode)
            
            // 6. 置信度计算
            val confidence = calculateConfidence(inputMode, segments)
            
            // 7. 备选模式分析
            val alternatives = findAlternativeModes(input, inputMode)
            
            val processingTime = System.currentTimeMillis() - startTime
            
            val result = InputAnalysis(
                input = input,
                mode = inputMode,
                confidence = confidence,
                segments = segments,
                alternatives = alternatives,
                charPattern = charPattern,
                syllableStructure = syllableStructure,
                processingTime = processingTime
            )
            
            Timber.d("输入分析完成: '$input' -> $inputMode (置信度: ${String.format("%.2f", confidence)}, 耗时: ${processingTime}ms)")
            
            return result
            
        } catch (e: Exception) {
            Timber.e(e, "输入分析异常: '$input'")
            return InputAnalysis.error(input, e.message ?: "未知错误")
        }
    }
    
    /**
     * 字符模式分析
     */
    private fun analyzeCharacterPattern(input: String): CharacterPattern {
        val letterCount = input.count { it in 'a'..'z' }
        val digitCount = input.count { it.isDigit() }
        val spaceCount = input.count { it.isWhitespace() }
        val otherCount = input.length - letterCount - digitCount - spaceCount
        
        val isAllLetters = letterCount == input.length
        val hasSpaces = spaceCount > 0
        val hasDigits = digitCount > 0
        val hasOthers = otherCount > 0
        
        return CharacterPattern(
            totalLength = input.length,
            letterCount = letterCount,
            digitCount = digitCount,
            spaceCount = spaceCount,
            otherCount = otherCount,
            isAllLetters = isAllLetters,
            hasSpaces = hasSpaces,
            hasDigits = hasDigits,
            hasOthers = hasOthers
        )
    }
    
    /**
     * 音节结构分析
     */
    private fun analyzeSyllableStructure(input: String): SyllableStructure {
        // 尝试拼音拆分
        val syllables = PinyinSegmenterOptimized.cut(input)
        val canSplit = syllables.isNotEmpty() && syllables.size > 1
        
        // 检查是否为单个有效音节
        val isSingleSyllable = syllables.size == 1 && PinyinSegmenterOptimized.isValidSyllable(input)
        
        // 检查部分音节匹配
        val partialMatches = findPartialSyllableMatches(input)
        
        return SyllableStructure(
            syllables = syllables,
            canSplit = canSplit,
            isSingleSyllable = isSingleSyllable,
            partialMatches = partialMatches,
            totalSyllables = syllables.size
        )
    }
    
    /**
     * 输入模式识别
     */
    private fun identifyInputMode(
        input: String,
        charPattern: CharacterPattern,
        syllableStructure: SyllableStructure
    ): InputMode {
        
        // 1. 单字母输入
        if (input.length == 1 && charPattern.isAllLetters) {
            return InputMode.SINGLE_LETTER
        }
        
        // 2. 纯拼音模式
        if (syllableStructure.canSplit && syllableStructure.syllables.joinToString("") == input) {
            return if (syllableStructure.totalSyllables > 4) {
                InputMode.SENTENCE_INPUT
            } else {
                InputMode.PURE_PINYIN
            }
        }
        
        // 3. 单个完整音节
        if (syllableStructure.isSingleSyllable) {
            return InputMode.PURE_PINYIN
        }
        
        // 4. 部分拼音输入
        if (syllableStructure.partialMatches.isNotEmpty()) {
            return InputMode.PARTIAL_PINYIN
        }
        
        // 5. 纯首字母缩写
        if (charPattern.isAllLetters && input.length <= 6 && !syllableStructure.canSplit) {
            return InputMode.PURE_ACRONYM
        }
        
        // 6. 混合模式检测
        val mixedMode = detectMixedMode(input, syllableStructure)
        if (mixedMode != null) {
            return mixedMode
        }
        
        // 7. 渐进式输入检测
        if (isProgressiveInput(input, syllableStructure)) {
            return InputMode.PROGRESSIVE_INPUT
        }
        
        // 8. 默认为纯首字母缩写
        return InputMode.PURE_ACRONYM
    }
    
    /**
     * 检测混合模式
     */
    private fun detectMixedMode(input: String, syllableStructure: SyllableStructure): InputMode? {
        // 检测缩写+拼音混合 (如: bjing)
        if (detectAcronymPinyinMix(input)) {
            return InputMode.ACRONYM_PINYIN_MIX
        }
        
        // 检测拼音+缩写混合 (如: beijingr)
        if (detectPinyinAcronymMix(input)) {
            return InputMode.PINYIN_ACRONYM_MIX
        }
        
        return null
    }
    
    /**
     * 检测缩写+拼音混合模式
     */
    private fun detectAcronymPinyinMix(input: String): Boolean {
        // 尝试从不同位置分割，检查前部分是否为缩写，后部分是否为拼音
        for (i in 1 until input.length) {
            val prefix = input.substring(0, i)
            val suffix = input.substring(i)
            
            // 前部分应该是短的字母组合（缩写）
            if (prefix.length <= 3 && prefix.all { it in 'a'..'z' }) {
                // 后部分应该能拆分为拼音
                val suffixSyllables = PinyinSegmenterOptimized.cut(suffix)
                if (suffixSyllables.isNotEmpty() && suffixSyllables.joinToString("") == suffix) {
                    return true
                }
            }
        }
        return false
    }
    
    /**
     * 检测拼音+缩写混合模式
     */
    private fun detectPinyinAcronymMix(input: String): Boolean {
        // 尝试从不同位置分割，检查前部分是否为拼音，后部分是否为缩写
        for (i in 2 until input.length) {
            val prefix = input.substring(0, i)
            val suffix = input.substring(i)
            
            // 前部分应该能拆分为拼音
            val prefixSyllables = PinyinSegmenterOptimized.cut(prefix)
            if (prefixSyllables.isNotEmpty() && prefixSyllables.joinToString("") == prefix) {
                // 后部分应该是短的字母组合（缩写）
                if (suffix.length <= 3 && suffix.all { it in 'a'..'z' }) {
                    return true
                }
            }
        }
        return false
    }
    
    /**
     * 检测渐进式输入
     */
    private fun isProgressiveInput(input: String, syllableStructure: SyllableStructure): Boolean {
        // 如果输入看起来像是一个音节的部分输入
        if (input.length >= 2 && input.length <= 5) {
            // 检查是否有音节以此开头
            val validSyllables = PinyinSegmenterOptimized.getValidSyllables()
            for (syllable in validSyllables) {
                if (syllable.startsWith(input) && syllable != input) {
                    return true
                }
            }
        }
        return false
    }
    
    /**
     * 查找部分音节匹配
     */
    private fun findPartialSyllableMatches(input: String): List<String> {
        val matches = mutableListOf<String>()
        val validSyllables = PinyinSegmenterOptimized.getValidSyllables()
        
        // 查找以输入开头的音节
        for (syllable in validSyllables) {
            if (syllable.startsWith(input) && syllable != input) {
                matches.add(syllable)
            }
        }
        
        return matches.take(10) // 限制数量
    }
    
    /**
     * 分段解析
     */
    private fun parseSegments(input: String, mode: InputMode): List<InputSegment> {
        return when (mode) {
            InputMode.SINGLE_LETTER -> listOf(
                InputSegment(input, SegmentType.SINGLE_LETTER, 0, input.length)
            )
            
            InputMode.PURE_PINYIN, InputMode.SENTENCE_INPUT -> {
                val syllables = PinyinSegmenterOptimized.cut(input)
                var pos = 0
                syllables.map { syllable ->
                    val segment = InputSegment(syllable, SegmentType.COMPLETE_SYLLABLE, pos, pos + syllable.length)
                    pos += syllable.length
                    segment
                }
            }
            
            InputMode.PURE_ACRONYM -> {
                input.mapIndexed { index, char ->
                    InputSegment(char.toString(), SegmentType.ACRONYM, index, index + 1)
                }
            }
            
            InputMode.PARTIAL_PINYIN -> listOf(
                InputSegment(input, SegmentType.PARTIAL_SYLLABLE, 0, input.length)
            )
            
            InputMode.ACRONYM_PINYIN_MIX -> parseAcronymPinyinMix(input)
            InputMode.PINYIN_ACRONYM_MIX -> parsePinyinAcronymMix(input)
            
            else -> listOf(
                InputSegment(input, SegmentType.UNKNOWN, 0, input.length)
            )
        }
    }
    
    /**
     * 解析缩写+拼音混合
     */
    private fun parseAcronymPinyinMix(input: String): List<InputSegment> {
        val segments = mutableListOf<InputSegment>()
        
        for (i in 1 until input.length) {
            val prefix = input.substring(0, i)
            val suffix = input.substring(i)
            
            if (prefix.length <= 3) {
                val suffixSyllables = PinyinSegmenterOptimized.cut(suffix)
                if (suffixSyllables.isNotEmpty() && suffixSyllables.joinToString("") == suffix) {
                    // 添加缩写部分
                    prefix.forEachIndexed { index, char ->
                        segments.add(InputSegment(char.toString(), SegmentType.ACRONYM, index, index + 1))
                    }
                    
                    // 添加拼音部分
                    var pos = i
                    suffixSyllables.forEach { syllable ->
                        segments.add(InputSegment(syllable, SegmentType.COMPLETE_SYLLABLE, pos, pos + syllable.length))
                        pos += syllable.length
                    }
                    break
                }
            }
        }
        
        return segments.ifEmpty {
            listOf(InputSegment(input, SegmentType.UNKNOWN, 0, input.length))
        }
    }
    
    /**
     * 解析拼音+缩写混合
     */
    private fun parsePinyinAcronymMix(input: String): List<InputSegment> {
        val segments = mutableListOf<InputSegment>()
        
        for (i in 2 until input.length) {
            val prefix = input.substring(0, i)
            val suffix = input.substring(i)
            
            val prefixSyllables = PinyinSegmenterOptimized.cut(prefix)
            if (prefixSyllables.isNotEmpty() && prefixSyllables.joinToString("") == prefix && suffix.length <= 3) {
                // 添加拼音部分
                var pos = 0
                prefixSyllables.forEach { syllable ->
                    segments.add(InputSegment(syllable, SegmentType.COMPLETE_SYLLABLE, pos, pos + syllable.length))
                    pos += syllable.length
                }
                
                // 添加缩写部分
                suffix.forEachIndexed { index, char ->
                    segments.add(InputSegment(char.toString(), SegmentType.ACRONYM, i + index, i + index + 1))
                }
                break
            }
        }
        
        return segments.ifEmpty {
            listOf(InputSegment(input, SegmentType.UNKNOWN, 0, input.length))
        }
    }
    
    /**
     * 计算置信度
     */
    private fun calculateConfidence(mode: InputMode, segments: List<InputSegment>): Float {
        return when (mode) {
            InputMode.SINGLE_LETTER -> 1.0f
            InputMode.PURE_PINYIN -> if (segments.all { it.type == SegmentType.COMPLETE_SYLLABLE }) 0.95f else 0.7f
            InputMode.PURE_ACRONYM -> 0.8f
            InputMode.PARTIAL_PINYIN -> 0.6f
            InputMode.SENTENCE_INPUT -> 0.9f
            InputMode.ACRONYM_PINYIN_MIX, InputMode.PINYIN_ACRONYM_MIX -> 0.75f
            InputMode.PROGRESSIVE_INPUT -> 0.5f
            else -> 0.3f
        }
    }
    
    /**
     * 查找备选模式
     */
    private fun findAlternativeModes(input: String, primaryMode: InputMode): List<InputMode> {
        val alternatives = mutableListOf<InputMode>()
        
        // 根据主要模式添加可能的备选模式
        when (primaryMode) {
            InputMode.PURE_ACRONYM -> {
                if (input.length <= 6) {
                    alternatives.add(InputMode.PARTIAL_PINYIN)
                }
            }
            InputMode.PARTIAL_PINYIN -> {
                alternatives.add(InputMode.PURE_ACRONYM)
                alternatives.add(InputMode.PROGRESSIVE_INPUT)
            }
            InputMode.PROGRESSIVE_INPUT -> {
                alternatives.add(InputMode.PARTIAL_PINYIN)
                alternatives.add(InputMode.PURE_ACRONYM)
            }
            else -> {
                // 其他模式暂不添加备选
            }
        }
        
        return alternatives
    }
} 