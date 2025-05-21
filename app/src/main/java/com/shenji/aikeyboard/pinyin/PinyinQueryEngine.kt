package com.shenji.aikeyboard.pinyin

import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.Entry
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 拼音查询引擎 - 标准化模块的核心类
 * 
 * 负责：
 * 1. 判断输入类型（单字符、拼音音节、拼音分词、首字母缩写等）
 * 2. 分词处理（使用PinyinSplitter）
 * 3. 构建查询条件
 * 4. 执行查询并返回标准化结果
 * 5. 提供详细的解释信息（用于测试工具）
 */
class PinyinQueryEngine {
    
    // 单例模式
    companion object {
        private var instance: PinyinQueryEngine? = null
        
        @JvmStatic
        fun getInstance(): PinyinQueryEngine {
            if (instance == null) {
                instance = PinyinQueryEngine()
            }
            return instance!!
        }
    }
    
    // 拼音分词器
    private val pinyinSplitter = PinyinSplitter.getInstance()
    
    /**
     * 对输入执行拼音查询（异步方法）
     * 
     * @param input 用户输入
     * @param limit 返回候选词数量限制，默认20个
     * @param needExplain 是否需要详细解释（测试工具需要，输入法默认不需要）
     * @return 标准查询结果对象
     */
    suspend fun query(
        input: String, 
        limit: Int = 20,
        needExplain: Boolean = false
    ): PinyinQueryResult {
        try {
            // 1. 如果输入为空，直接返回空结果
            if (input.isEmpty()) {
                return PinyinQueryResult.empty()
            }
            
            // 2. 清理和标准化输入
            val normalizedInput = input.trim().lowercase()
            
            // 3. 判断输入类型/阶段
            val inputType = classifyInput(normalizedInput)
            
            // 4. 根据输入类型执行相应的查询
            return when (inputType) {
                InputType.INITIAL_LETTER -> queryInitialLetter(normalizedInput, limit, needExplain)
                InputType.PINYIN_SYLLABLE -> queryPinyinSyllable(normalizedInput, limit, needExplain)
                InputType.SYLLABLE_SPLIT -> querySyllableSplit(normalizedInput, limit, needExplain)
                InputType.ACRONYM -> queryAcronym(normalizedInput, limit, needExplain)
                InputType.DYNAMIC_SYLLABLE -> queryDynamicSyllable(normalizedInput, limit, needExplain)
                else -> PinyinQueryResult.empty(inputType, "未知类型的输入")
            }
        } catch (e: Exception) {
            Timber.e(e, "拼音查询引擎异常")
            return PinyinQueryResult.error(e.message ?: "未知错误")
        }
    }
    
    /**
     * 判断输入类型
     */
    private fun classifyInput(input: String): InputType {
        if (input.isEmpty()) {
            return InputType.UNKNOWN
        }

        // 提取纯拼音部分
        val pinyinPart = extractPinyinPart(input)
        if (pinyinPart.isEmpty()) {
            return InputType.UNKNOWN
        }

        // 单字符首字母
        if (pinyinPart.length == 1 && pinyinPart.matches(Regex("^[a-z]$"))) {
            return InputType.INITIAL_LETTER
        }

        // 单个完整拼音音节
        if (isValidPinyinSyllable(pinyinPart) && !pinyinPart.contains(" ")) {
            return InputType.PINYIN_SYLLABLE
        }

        // 尝试动态音节识别（优先级高于音节拆分和首字母缩写）
        val dynamicSyllables = pinyinSplitter.splitDynamicInput(pinyinPart)
        if (dynamicSyllables.isNotEmpty() && dynamicSyllables.any { it.length == 1 }) {
            // 如果拆分结果包含单字符，说明有未完成的音节，使用动态音节识别
            return InputType.DYNAMIC_SYLLABLE
        }
        
        // 其他情况，尝试常规音节拆分或作为缩写处理
        val canSplit = canSplitToValidSyllables(pinyinPart)
        
        return when {
            canSplit -> InputType.SYLLABLE_SPLIT
            else -> InputType.ACRONYM
        }
    }
    
    /**
     * 验证是否为有效的拼音音节
     */
    private fun isValidPinyinSyllable(input: String): Boolean {
        return pinyinSplitter.getPinyinSyllables().contains(input)
    }
    
    /**
     * 判断是否可以拆分为有效音节
     */
    private fun canSplitToValidSyllables(input: String): Boolean {
        val result = pinyinSplitter.splitPinyin(input)
        return result.isNotEmpty()
    }
    
    /**
     * 查询首字母候选词
     */
    private suspend fun queryInitialLetter(
        input: String, 
        limit: Int,
        needExplain: Boolean
    ): PinyinQueryResult = withContext(Dispatchers.IO) {
        val explanation = StringBuilder()
        val candidates = mutableListOf<PinyinCandidate>()
        
        if (needExplain) {
            explanation.append("查询过程:\n")
            explanation.append("1. 使用首字母匹配规则查询单字词典\n")
            explanation.append("- 查询条件: type='chars' AND initialLetters BEGINSWITH '$input'\n")
        }
        
        try {
            val realm = ShenjiApplication.realm
            
            // 查询单字词典中匹配首字母的单字
            val query = realm.query<Entry>("type == $0 AND initialLetters BEGINSWITH $1", 
                "chars", input)
            
            val entries = query.find()
                .sortedByDescending { it.frequency }
            
            if (needExplain) {
                explanation.append("- 单字匹配结果: ${entries.size}个\n")
            }
            
            // 转换为候选词，添加去重逻辑
            val seenWords = mutableSetOf<String>()
            entries.forEach { entry ->
                // 只添加未见过的词
                if (seenWords.add(entry.word)) {
                    candidates.add(
                        PinyinCandidate(
                            word = entry.word,
                            pinyin = entry.pinyin,
                            frequency = entry.frequency,
                            type = entry.type,
                            matchType = MatchType.INITIAL_LETTER
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "查询首字母候选词异常")
            if (needExplain) {
                explanation.append("查询异常: ${e.message}\n")
            }
        }
        
        // 返回结果对象，应用limit限制
        PinyinQueryResult(
            inputType = InputType.INITIAL_LETTER,
            candidates = candidates.take(limit),
            syllables = listOf(),
            explanation = explanation.toString()
        )
    }
    
    /**
     * 查询单音节候选词
     */
    private suspend fun queryPinyinSyllable(
        input: String, 
        limit: Int,
        needExplain: Boolean
    ): PinyinQueryResult = withContext(Dispatchers.IO) {
        val explanation = StringBuilder()
        val candidates = mutableListOf<PinyinCandidate>()
        
        // 从输入中提取纯英文拼音部分
        val pinyinPart = extractPinyinPart(input)
        
        if (needExplain) {
            explanation.append("查询过程:\n")
            explanation.append("1. 使用精确音节匹配规则查询单字词典\n")
            explanation.append("- 原始输入: '$input'\n")
            explanation.append("- 提取拼音部分: '$pinyinPart'\n")
            explanation.append("- 查询条件: type='chars' AND pinyin == '$pinyinPart'\n")
        }
        
        try {
            val realm = ShenjiApplication.realm
            
            // 查询单字词典中精确匹配的单字
            val query = realm.query<Entry>("type == $0 AND pinyin == $1", 
                "chars", pinyinPart)
            
            val entries = query.find()
                .sortedByDescending { it.frequency }
            
            if (needExplain) {
                explanation.append("- 单字精确匹配结果: ${entries.size}个\n")
            }
            
            // 转换为候选词，添加去重逻辑
            val seenWords = mutableSetOf<String>()
            entries.forEach { entry ->
                // 只添加未见过的词
                if (seenWords.add(entry.word)) {
                    candidates.add(
                        PinyinCandidate(
                            word = entry.word,
                            pinyin = entry.pinyin,
                            frequency = entry.frequency,
                            type = entry.type,
                            matchType = MatchType.PINYIN_SYLLABLE
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "查询单音节候选词异常")
            if (needExplain) {
                explanation.append("查询异常: ${e.message}\n")
            }
        }
        
        // 返回结果对象，应用limit限制
        PinyinQueryResult(
            inputType = InputType.PINYIN_SYLLABLE,
            candidates = candidates.take(limit),
            syllables = listOf(pinyinPart),
            explanation = explanation.toString()
        )
    }
    
    /**
     * 查询音节拆分候选词
     */
    private suspend fun querySyllableSplit(
        input: String, 
        limit: Int,
        needExplain: Boolean
    ): PinyinQueryResult = withContext(Dispatchers.IO) {
        val explanation = StringBuilder()
        val candidates = mutableListOf<PinyinCandidate>()
        
        // 从输入中提取纯英文拼音部分
        val pinyinPart = extractPinyinPart(input)
        
        // 获取所有可能的拆分结果
        val allSplitResults = pinyinSplitter.getMultipleSplitResults(pinyinPart)
        
        if (allSplitResults.isEmpty()) {
            if (needExplain) {
                explanation.append("音节拆分失败，无法获得有效音节\n")
                explanation.append("- 原始输入: '$input'\n")
                explanation.append("- 提取拼音部分: '$pinyinPart'\n")
            }
            return@withContext PinyinQueryResult(
                inputType = InputType.SYLLABLE_SPLIT,
                candidates = emptyList(),
                syllables = emptyList(),
                allSyllableSplits = emptyList(),
                explanation = explanation.toString()
            )
        }
        
        if (needExplain) {
            explanation.append("查询过程:\n")
            explanation.append("1. 获取到${allSplitResults.size}种可能的音节拆分结果:\n")
            allSplitResults.forEachIndexed { index, syllables ->
                explanation.append("   - 拆分方案${index + 1}: ${syllables.joinToString("+")}\n")
            }
            explanation.append("- 原始输入: '$input'\n")
            explanation.append("- 提取拼音部分: '$pinyinPart'\n")
        }
        
        // 尝试每一种拆分方案
        var successIndex = -1
        var currentCandidates = mutableListOf<PinyinCandidate>()
        
        for ((index, syllables) in allSplitResults.withIndex()) {
            // 将音节连接为完整的拼音字符串（带空格）
            val fullPinyin = syllables.joinToString(" ")
            
            if (needExplain) {
                explanation.append("2. 尝试拆分方案${index + 1}: ${syllables.joinToString("+")}\n")
                explanation.append("   - 构建完整拼音查询: '$fullPinyin'\n")
                explanation.append("   - 查询条件: pinyin == '$fullPinyin'\n")
            }
            
            try {
                val realm = ShenjiApplication.realm
                
                // 查询精确匹配的词条
                val query = realm.query<Entry>("pinyin == $0", fullPinyin)
                
                var entries = query.find()
                    .sortedByDescending { it.frequency }
                
                if (needExplain) {
                    explanation.append("   - 精确匹配结果: ${entries.size}个\n")
                }
                
                // 如果精确匹配没有结果，尝试前缀匹配
                if (entries.isEmpty() && syllables.size >= 2) {
                    if (needExplain) {
                        explanation.append("   - 精确匹配无结果，尝试前缀匹配\n")
                        explanation.append("   - 查询条件: pinyin BEGINSWITH '$fullPinyin'\n")
                    }
                    
                    val prefixQuery = realm.query<Entry>("pinyin BEGINSWITH $0", fullPinyin)
                    entries = prefixQuery.find()
                        .sortedByDescending { it.frequency }
                        .take(limit)
                    
                    if (needExplain) {
                        explanation.append("   - 前缀匹配结果: ${entries.size}个\n")
                    }
                } else {
                    entries = entries.take(limit)
                }
                
                // 如果找到了候选词，则使用这个拆分方案
                if (entries.isNotEmpty()) {
                    successIndex = index
                    currentCandidates.clear()
                    
                    // 转换为候选词，添加去重逻辑
                    val seenWords = mutableSetOf<String>()
                    entries.forEach { entry ->
                        // 只添加未见过的词
                        if (seenWords.add(entry.word)) {
                            currentCandidates.add(
                                PinyinCandidate(
                                    word = entry.word,
                                    pinyin = entry.pinyin,
                                    frequency = entry.frequency,
                                    type = entry.type,
                                    matchType = MatchType.SYLLABLE_SPLIT
                                )
                            )
                        }
                    }
                    
                    if (needExplain) {
                        explanation.append("3. 成功找到候选词，使用拆分方案${index + 1}\n")
                    }
                    
                    // 找到候选词后，不再尝试其他拆分方案
                    break
                } else if (needExplain) {
                    explanation.append("   - 未找到候选词，尝试下一个拆分方案\n")
                }
            } catch (e: Exception) {
                Timber.e(e, "查询音节拆分候选词异常")
                if (needExplain) {
                    explanation.append("   - 查询异常: ${e.message}\n")
                }
            }
        }
        
        // 如果所有拆分方案都没有找到候选词，使用第一个拆分方案作为结果
        val usedIndex = if (successIndex >= 0) successIndex else 0
        val usedSyllables = if (allSplitResults.isNotEmpty()) allSplitResults[usedIndex] else emptyList()
        
        if (successIndex < 0 && needExplain) {
            explanation.append("3. 所有拆分方案都未找到候选词，使用默认拆分方案\n")
        }
        
        // 返回结果对象
        PinyinQueryResult(
            inputType = InputType.SYLLABLE_SPLIT,
            candidates = currentCandidates,
            syllables = usedSyllables,
            allSyllableSplits = allSplitResults,
            usedSplitIndex = usedIndex,
            explanation = explanation.toString()
        )
    }
    
    /**
     * 查询首字母缩写候选词
     */
    private suspend fun queryAcronym(
        input: String, 
        limit: Int,
        needExplain: Boolean
    ): PinyinQueryResult = withContext(Dispatchers.IO) {
        val explanation = StringBuilder()
        val candidates = mutableListOf<PinyinCandidate>()
        
        // 从输入中提取纯英文拼音部分
        val pinyinPart = extractPinyinPart(input)
        
        if (needExplain) {
            explanation.append("查询过程:\n")
            explanation.append("1. 使用首字母缩写规则查询词典\n")
            explanation.append("- 原始输入: '$input'\n")
            explanation.append("- 提取拼音部分: '$pinyinPart'\n")
            explanation.append("- 查询条件: initialLetters == '$pinyinPart'\n")
        }
        
        try {
            val realm = ShenjiApplication.realm
            
            // 查询匹配首字母缩写的词条
            val query = realm.query<Entry>("initialLetters == $0", pinyinPart)
            
            val entries = query.find()
                .sortedByDescending { it.frequency }
            
            if (needExplain) {
                explanation.append("- 匹配结果: ${entries.size}个\n")
            }
            
            // 转换为候选词，添加去重逻辑
            val seenWords = mutableSetOf<String>()
            entries.forEach { entry ->
                // 只添加未见过的词
                if (seenWords.add(entry.word)) {
                    candidates.add(
                        PinyinCandidate(
                            word = entry.word,
                            pinyin = entry.pinyin,
                            frequency = entry.frequency,
                            type = entry.type,
                            matchType = MatchType.ACRONYM
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "查询首字母缩写候选词异常")
            if (needExplain) {
                explanation.append("查询异常: ${e.message}\n")
            }
        }
        
        // 返回结果对象，应用limit限制
        PinyinQueryResult(
            inputType = InputType.ACRONYM,
            candidates = candidates.take(limit),
            syllables = listOf(),
            explanation = explanation.toString()
        )
    }
    
    /**
     * 查询动态音节候选词
     * 处理未完成的拼音输入，如"shenjingb"，将其拆分为完整音节+剩余字母
     */
    private suspend fun queryDynamicSyllable(
        input: String, 
        limit: Int,
        needExplain: Boolean
    ): PinyinQueryResult = withContext(Dispatchers.IO) {
        val explanation = StringBuilder()
        val candidates = mutableListOf<PinyinCandidate>()
        
        // 从输入中提取纯英文拼音部分
        val pinyinPart = extractPinyinPart(input)
        
        // 使用动态音节拆分
        val syllables = pinyinSplitter.splitDynamicInput(pinyinPart)
        
        if (syllables.isEmpty()) {
            if (needExplain) {
                explanation.append("动态音节拆分失败，无法获得有效拆分\n")
                explanation.append("- 原始输入: '$input'\n")
                explanation.append("- 提取拼音部分: '$pinyinPart'\n")
            }
            return@withContext PinyinQueryResult(
                inputType = InputType.DYNAMIC_SYLLABLE,
                candidates = emptyList(),
                syllables = emptyList(),
                explanation = explanation.toString()
            )
        }
        
        if (needExplain) {
            explanation.append("查询过程:\n")
            explanation.append("1. 使用首字母缩写规则查询词典\n")
            explanation.append("- 原始输入: '$input'\n")
            explanation.append("- 提取拼音部分: '$pinyinPart'\n")
            explanation.append("- 音节拆分: ${syllables.joinToString("+")}\n")
        }
        
        try {
            val realm = ShenjiApplication.realm
            
            // 查询策略1：如果最后一个元素是单字符，作为词首字母缩写来查询
            if (syllables.last().length == 1) {
                // 前面部分拼成拼音字符串（带空格）
                val completeSyllables = syllables.dropLast(1)
                val lastChar = syllables.last()
                
                if (needExplain) {
                    explanation.append("2. 完整音节: ${completeSyllables.joinToString("+")}\n")
                    explanation.append("3. 剩余首字母: $lastChar\n")
                }
                
                // 如果有完整音节部分
                if (completeSyllables.isNotEmpty()) {
                    val fullPinyin = completeSyllables.joinToString(" ")
                    
                    if (needExplain) {
                        explanation.append("4. 查询条件: pinyin BEGINSWITH '$fullPinyin' AND initialLetters LIKE '*$lastChar*'\n")
                    }
                    
                    // 查询拼音以完整音节开头，且初始字母包含剩余字符的词条
                    val query = realm.query<Entry>("pinyin BEGINSWITH $0 AND initialLetters CONTAINS $1", 
                        fullPinyin, lastChar)
                    
                    val entries = query.find()
                        .sortedByDescending { it.frequency }
                        .take(limit)
                    
                    if (needExplain) {
                        explanation.append("- 匹配结果: ${entries.size}个\n")
                    }
                    
                    // 转换为候选词
                    val seenWords = mutableSetOf<String>()
                    entries.forEach { entry ->
                        if (seenWords.add(entry.word)) {
                            candidates.add(
                                PinyinCandidate(
                                    word = entry.word,
                                    pinyin = entry.pinyin,
                                    frequency = entry.frequency,
                                    type = entry.type,
                                    matchType = MatchType.SYLLABLE_SPLIT
                                )
                            )
                        }
                    }
                } 
                // 如果只有单个字母
                else {
                    if (needExplain) {
                        explanation.append("4. 只有单个字母，使用首字母查询\n")
                    }
                    
                    // 只有一个字母的情况，按首字母查询
                    val initialQuery = realm.query<Entry>("initialLetters BEGINSWITH $0", lastChar)
                    
                    val entries = initialQuery.find()
                        .sortedByDescending { it.frequency }
                        .take(limit)
                    
                    if (needExplain) {
                        explanation.append("- 匹配结果: ${entries.size}个\n")
                    }
                    
                    // 转换为候选词
                    val seenWords = mutableSetOf<String>()
                    entries.forEach { entry ->
                        if (seenWords.add(entry.word)) {
                            candidates.add(
                                PinyinCandidate(
                                    word = entry.word,
                                    pinyin = entry.pinyin,
                                    frequency = entry.frequency,
                                    type = entry.type,
                                    matchType = MatchType.INITIAL_LETTER
                                )
                            )
                        }
                    }
                }
            } 
            // 所有音节都是完整的
            else {
                if (needExplain) {
                    explanation.append("2. 全部为完整音节，使用标准拼音查询\n")
                }
                
                // 使用标准音节查询方式
                val fullPinyin = syllables.joinToString(" ")
                
                if (needExplain) {
                    explanation.append("- 查询条件: pinyin == '$fullPinyin'\n")
                }
                
                val query = realm.query<Entry>("pinyin == $0", fullPinyin)
                
                val entries = query.find()
                    .sortedByDescending { it.frequency }
                    .take(limit)
                
                if (needExplain) {
                    explanation.append("- 匹配结果: ${entries.size}个\n")
                }
                
                // 转换为候选词
                val seenWords = mutableSetOf<String>()
                entries.forEach { entry ->
                    if (seenWords.add(entry.word)) {
                        candidates.add(
                            PinyinCandidate(
                                word = entry.word,
                                pinyin = entry.pinyin,
                                frequency = entry.frequency,
                                type = entry.type,
                                matchType = MatchType.SYLLABLE_SPLIT
                            )
                        )
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "查询动态音节候选词异常")
            if (needExplain) {
                explanation.append("查询异常: ${e.message}\n")
            }
        }
        
        // 返回结果对象，应用limit限制
        PinyinQueryResult(
            inputType = InputType.DYNAMIC_SYLLABLE,
            candidates = candidates.take(limit),
            syllables = syllables,
            explanation = explanation.toString()
        )
    }
    
    /**
     * 从混合输入中提取纯拼音部分
     * 例如从"你好吗我很好微信gongzhonghao"中提取"gongzhonghao"
     */
    private fun extractPinyinPart(input: String): String {
        // 正则表达式匹配连续的英文字母和数字
        val regex = Regex("[a-zA-Z0-9]+")
        val matches = regex.findAll(input)
        
        // 返回最后一个匹配项（作为最新的拼音输入）
        return matches.lastOrNull()?.value ?: input
    }
} 