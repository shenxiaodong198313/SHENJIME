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

        // 单字符首字母
        if (input.length == 1 && input.matches(Regex("^[a-z]$"))) {
            return InputType.INITIAL_LETTER
        }

        // 单个完整拼音音节
        if (isValidPinyinSyllable(input) && !input.contains(" ")) {
            return InputType.PINYIN_SYLLABLE
        }

        // 其他情况，尝试音节拆分或作为缩写处理
        val canSplit = canSplitToValidSyllables(input)
        
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
        
        if (needExplain) {
            explanation.append("查询过程:\n")
            explanation.append("1. 使用精确音节匹配规则查询单字词典\n")
            explanation.append("- 查询条件: type='chars' AND pinyin == '$input'\n")
        }
        
        try {
            val realm = ShenjiApplication.realm
            
            // 查询单字词典中精确匹配的单字
            val query = realm.query<Entry>("type == $0 AND pinyin == $1", 
                "chars", input)
            
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
            syllables = listOf(input),
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
        
        // 拆分音节
        val syllables = pinyinSplitter.splitPinyin(input)
        
        if (syllables.isEmpty()) {
            if (needExplain) {
                explanation.append("音节拆分失败，无法获得有效音节\n")
            }
            return@withContext PinyinQueryResult(
                inputType = InputType.SYLLABLE_SPLIT,
                candidates = emptyList(),
                syllables = emptyList(),
                explanation = explanation.toString()
            )
        }
        
        if (needExplain) {
            explanation.append("查询过程:\n")
            explanation.append("1. 音节拆分结果: ${syllables.joinToString("+")}\n")
        }
        
        // 将音节连接为完整的拼音字符串（带空格）
        val fullPinyin = syllables.joinToString(" ")
        
        if (needExplain) {
            explanation.append("2. 构建完整拼音查询: '$fullPinyin'\n")
            explanation.append("- 查询条件: pinyin == '$fullPinyin'\n")
        }
        
        try {
            val realm = ShenjiApplication.realm
            
            // 查询精确匹配的词条
            val query = realm.query<Entry>("pinyin == $0", fullPinyin)
            
            var entries = query.find()
                .sortedByDescending { it.frequency }
            
            if (needExplain) {
                explanation.append("- 精确匹配结果: ${entries.size}个\n")
            }
            
            // 如果精确匹配没有结果，尝试前缀匹配
            if (entries.isEmpty() && syllables.size >= 2) {
                if (needExplain) {
                    explanation.append("3. 精确匹配无结果，尝试前缀匹配\n")
                    explanation.append("- 查询条件: pinyin BEGINSWITH '$fullPinyin'\n")
                }
                
                val prefixQuery = realm.query<Entry>("pinyin BEGINSWITH $0", fullPinyin)
                entries = prefixQuery.find()
                    .sortedByDescending { it.frequency }
                    .take(limit)
                
                if (needExplain) {
                    explanation.append("- 前缀匹配结果: ${entries.size}个\n")
                }
            } else {
                if (needExplain) {
                    explanation.append("3. 使用精确匹配结果\n")
                }
                entries = entries.take(limit)
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
                            matchType = MatchType.SYLLABLE_SPLIT
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "查询音节拆分候选词异常")
            if (needExplain) {
                explanation.append("查询异常: ${e.message}\n")
            }
        }
        
        // 返回结果对象，应用limit限制
        PinyinQueryResult(
            inputType = InputType.SYLLABLE_SPLIT,
            candidates = candidates.take(limit),
            syllables = syllables,
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
        
        if (needExplain) {
            explanation.append("查询过程:\n")
            explanation.append("1. 使用首字母缩写规则查询词典\n")
            explanation.append("- 查询条件: initialLetters == '$input'\n")
        }
        
        try {
            val realm = ShenjiApplication.realm
            
            // 查询匹配首字母缩写的词条
            val query = realm.query<Entry>("initialLetters == $0", input)
            
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
} 