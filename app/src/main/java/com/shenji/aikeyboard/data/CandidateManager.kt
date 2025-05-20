package com.shenji.aikeyboard.data

import android.util.LruCache
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.model.Candidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import io.realm.kotlin.ext.query

// 调试信息类型别名，方便外部引用
typealias DebugInfo = StagedDictionaryRepository.DebugInfo

/**
 * 候选词管理器
 * 负责协调候选词生成和查询
 */
class CandidateManager(private val repository: DictionaryRepository) {
    
    // 分阶段查询仓库 - 保留但不再使用
    private val stagedRepository = StagedDictionaryRepository()
    
    // 调试信息
    val debugInfo: DebugInfo get() = stagedRepository.debugInfo
    
    // 回退缓存实现，存储最近的输入和候选词结果的映射
    private val candidateCache = object {
        // 缓存大小控制，最多保存20个最近查询
        private val cache = LruCache<String, List<WordFrequency>>(20)
        
        // 存入缓存
        fun put(input: String, candidates: List<WordFrequency>) {
            cache.put(input, candidates)
        }
        
        // 获取缓存，不存在返回null
        fun get(input: String): List<WordFrequency>? = cache.get(input)
        
        // 清空缓存
        fun clear() = cache.evictAll()
    }
    
    // 保存上一次输入内容，用于判断是否为回退操作
    private var previousInput: String = ""
    
    // 优化状态信息
    data class OptimizationStatus(
        val cacheUsed: Boolean = false,         // 是否使用了缓存
        val earlyTerminated: Boolean = false,   // 是否提前终止
        val queryTime: Long = 0,                // 查询耗时(ms)
        val backwardFilterUsed: Boolean = false,// 是否使用了回退过滤
        val stagesExecuted: Int = 0             // 执行的阶段数
    )
    
    // 当前查询优化状态
    private var currentOptimizationStatus = OptimizationStatus()
    
    // 获取当前优化状态
    val optimizationStatus: OptimizationStatus get() = currentOptimizationStatus
    
    // 拼音分词器
    private val pinyinSplitter = PinyinSplitterOptimized()
    
    /**
     * 根据输入生成候选词
     * @param input 用户输入
     * @param limit 候选词数量限制
     * @return 候选词列表
     */
    suspend fun generateCandidates(input: String, limit: Int): List<WordFrequency> = withContext(Dispatchers.IO) {
        // 规范化输入
        val normalizedInput = input.trim().lowercase()
        
        if (normalizedInput.isEmpty()) {
            Timber.d("输入为空，返回空候选词列表")
            currentOptimizationStatus = OptimizationStatus() // 重置优化状态
            return@withContext emptyList()
        }
        
        Timber.d("生成'$normalizedInput'的候选词")
        
        // 重置优化状态
        currentOptimizationStatus = OptimizationStatus()
        
        // 记录查询开始时间
        val startTime = System.currentTimeMillis()
        
        // 检查是否从缓存中获取结果
        val cachedResults = candidateCache.get(normalizedInput)
        if (cachedResults != null) {
            Timber.d("从缓存获取'$normalizedInput'的候选词")
            previousInput = normalizedInput
            
            // 更新优化状态
            currentOptimizationStatus = OptimizationStatus(
                cacheUsed = true,
                queryTime = System.currentTimeMillis() - startTime,
                stagesExecuted = 0 // 从缓存获取不执行查询阶段
            )
            
            return@withContext cachedResults
        }
        
        // 使用测试工具的查询逻辑
        val results = queryUsingTestToolLogic(normalizedInput, limit)
        
        // 更新缓存
        candidateCache.put(normalizedInput, results)
        previousInput = normalizedInput
        
        // 更新优化状态
        currentOptimizationStatus = OptimizationStatus(
            queryTime = System.currentTimeMillis() - startTime,
            stagesExecuted = 1 // 只执行一个统一的查询阶段
        )
        
        return@withContext results
    }
    
    /**
     * 使用测试工具的查询逻辑生成候选词
     * 确保与测试工具完全一致的查询流程
     */
    private suspend fun queryUsingTestToolLogic(input: String, limit: Int): List<WordFrequency> {
        val realm = ShenjiApplication.realm
        
        // 判断输入类型
        val inputStage = classifyInputStage(input)
        Timber.d("输入'$input'被分类为: $inputStage")
        
        return when (inputStage) {
            InputStage.INITIAL_LETTER -> {
                // 查询单字词典中匹配首字母的
                val singleChars = realm.query<Entry>("type == $0 AND initialLetters BEGINSWITH $1", 
                    "chars", input).find()
                
                // 直接返回单字结果，不再查询其他表
                singleChars
                    .sortedByDescending { it.frequency }
                    .take(limit)
                    .map { WordFrequency(it.word, it.frequency) }
            }
            
            InputStage.ACRONYM -> {
                // 单字母组合查询，根据字母数量匹配相应词长的候选词
                if (input.length > 1 && input.all { it in 'a'..'z' }) {
                    Timber.d("单字母组合查询: '$input', 字母数: ${input.length}")
                    
                    // 合并结果，确保不重复
                    val result = mutableListOf<WordFrequency>()
                    val seenWords = mutableSetOf<String>()
                    
                    // 1. 首先添加精确匹配的首字母结果（完全匹配首字母缩写）
                    val exactMatches = realm.query<Entry>(
                        "initialLetters == $0", input
                    ).find().sortedByDescending { it.frequency }
                    
                    exactMatches.forEach { entry ->
                        if (seenWords.add(entry.word)) {
                            // 提高精确匹配的权重，使它们排在前面
                            result.add(WordFrequency(entry.word, entry.frequency * 2))
                        }
                    }
                    
                    // 2. 添加以输入为前缀的首字母缩写匹配（如wx可匹配wxyz）
                    val prefixMatches = realm.query<Entry>(
                        "initialLetters BEGINSWITH $0", input
                    ).find().sortedByDescending { it.frequency }
                    
                    prefixMatches.forEach { entry ->
                        if (seenWords.add(entry.word)) {
                            result.add(WordFrequency(entry.word, entry.frequency))
                        }
                    }
                    
                    // 3. 如果结果不足，添加长度匹配的条目（词长与首字母缩写长度匹配）
                    if (result.size < limit) {
                        // 根据字母数量选择对应的词典
                        val dictTypes = when(input.length) {
                            2, 3 -> listOf("base", "place", "people", "chars")
                            4, 5 -> listOf("correlation", "base", "place", "people")
                            else -> listOf("associational", "correlation", "base")
                        }
                        
                        // 查询每个词典
                        dictTypes.forEach { dictType ->
                            if (result.size < limit) {
                                val lengthMatches = realm.query<Entry>(
                                    "type == $0 AND length(word) == $1", 
                                    dictType, input.length
                                ).find().sortedByDescending { it.frequency }
                                
                                lengthMatches.forEach { entry ->
                                    if (seenWords.add(entry.word) && result.size < limit) {
                                        result.add(WordFrequency(entry.word, entry.frequency / 2)) // 降低优先级
                                    }
                                }
                            }
                        }
                    }
                    
                    // 按词频排序并取前limit个结果
                    result.sortedByDescending { it.frequency }.take(limit)
                } else {
                    // 常规缩写查询
                    val entries = realm.query<Entry>("initialLetters == $0", input).find()
                    
                    entries
                        .sortedByDescending { it.frequency }
                        .take(limit)
                        .map { WordFrequency(it.word, it.frequency) }
                }
            }
            
            InputStage.PINYIN_COMPLETION -> {
                // 检查是否是单个有效音节
                val isSingleSyllable = isValidPinyin(input) && !input.contains(" ")
                
                // 如果是单个有效音节，只查询单字词典
                if (isSingleSyllable) {
                    val singleChars = realm.query<Entry>("type == $0 AND pinyin == $1",
                        "chars", input).find()
                        .sortedByDescending { it.frequency }
                        .take(limit)
                        .map { WordFrequency(it.word, it.frequency) }
                    
                    singleChars
                } else {
                    // 先查询单字词典
                    val singleChars = realm.query<Entry>("type == $0 AND pinyin BEGINSWITH $1",
                        "chars", input).find()
                        .sortedByDescending { it.frequency }
                        .take(limit / 2)
                        .map { WordFrequency(it.word, it.frequency) }
                    
                    // 然后查询其他词典
                    val otherWords = realm.query<Entry>("type != $0 AND pinyin BEGINSWITH $1",
                        "chars", input).find()
                        .sortedByDescending { it.frequency }
                        .take(limit / 2)
                        .map { WordFrequency(it.word, it.frequency) }
                    
                    // 合并结果
                    (singleChars + otherWords)
                        .sortedByDescending { it.frequency }
                        .take(limit)
                }
            }
            
            InputStage.SYLLABLE_SPLIT -> {
                // 拆分音节
                val syllables = pinyinSplitter.splitPinyin(input)
                
                if (syllables.isEmpty()) {
                    return@queryUsingTestToolLogic emptyList()
                }
                
                // 将音节连接为完整的拼音字符串（带空格）
                val fullPinyin = syllables.joinToString(" ")
                
                // 直接查询完整拼音匹配的词条
                val entries = realm.query<Entry>("pinyin == $0", fullPinyin).find()
                    .sortedByDescending { it.frequency }
                
                // 如果精确匹配没有结果，尝试前缀匹配
                if (entries.isEmpty() && syllables.size >= 2) {
                    // 查询以这些音节开头的词条
                    val prefixMatches = realm.query<Entry>("pinyin BEGINSWITH $0", fullPinyin).find()
                        .sortedByDescending { it.frequency }
                        .take(limit)
                    
                    prefixMatches.map { WordFrequency(it.word, it.frequency) }
                } else {
                    entries.take(limit).map { WordFrequency(it.word, it.frequency) }
                }
            }
            
            else -> emptyList()
        }
    }
    
    /**
     * 判断输入阶段
     */
    private fun classifyInputStage(input: String): InputStage {
        if (input.isEmpty()) {
            return InputStage.UNKNOWN
        }

        if (input.length == 1 && input.matches(Regex("^[a-z]$"))) {
            return InputStage.INITIAL_LETTER // 首字母阶段
        }
        
        // 优先检查是否是单字母组合（如"wx", "nh"等），每个字符都是单个字母
        if (input.all { it in 'a'..'z' } && input.length > 1) {
            // 对于短字母组合(2-3个字母)，更倾向于判断为首字母缩写
            if (input.length <= 3) {
                Timber.d("短字母组合识别为首字母缩写: '$input'")
                return InputStage.ACRONYM
            }
            
            // 检查每个字符是否是可能的首字母（非有效拼音音节）
            val allSingleLetters = input.all { 
                val singleChar = it.toString()
                !isValidPinyin(singleChar) // 不是有效拼音音节
            }
            
            // 如果全部是可能的首字母，则认为是单字母组合
            if (allSingleLetters) {
                Timber.d("识别为单字母组合: '$input'")
                return InputStage.ACRONYM // 首字母缩写阶段
            }

            // 检查字符组合是否可能是首字母缩写，即使不是所有字符都是单字母
            // 如果无法作为拼音拆分，则认为是首字母缩写
            if (!canSplitToValidSyllables(input)) {
                Timber.d("无法拆分为拼音，识别为首字母缩写: '$input'")
                return InputStage.ACRONYM
            }
        }

        // 单个完整拼音音节，直接归类为拼音补全阶段
        if (isValidPinyin(input) && !input.contains(" ")) {
            return InputStage.PINYIN_COMPLETION // 拼音补全阶段
        }

        // 其他情况，尝试音节拆分或作为缩写处理
        val canSplit = canSplitToValidSyllables(input)
        
        // 输出调试信息
        if (!canSplit) {
            Timber.d("无法进行音节拆分，作为首字母缩写处理: '$input'")
        }
        
        return when {
            canSplit -> InputStage.SYLLABLE_SPLIT // 音节拆分阶段
            else -> InputStage.ACRONYM // 无法拆分则作为首字母缩写阶段
        }
    }
    
    /**
     * 验证是否为有效的拼音音节
     */
    private fun isValidPinyin(input: String): Boolean {
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
     * 输入阶段枚举
     */
    enum class InputStage {
        INITIAL_LETTER,      // 首字母阶段
        PINYIN_COMPLETION,   // 拼音补全阶段
        SYLLABLE_SPLIT,      // 音节拆分阶段
        ACRONYM,             // 首字母缩写阶段
        UNKNOWN              // 未知阶段
    }
    
    /**
     * 根据回退后的输入过滤候选词
     * 保留拼音或首字母仍然匹配的候选词
     */
    private fun filterCandidatesForBackward(
        candidates: List<WordFrequency>, 
        currentInput: String
    ): List<WordFrequency> {
        // 获取拼音分词器实例
        val pinyinSplitter = PinyinSplitter.getInstance()
        
        return candidates.filter { candidate ->
            // 获取标准化的拼音（只取前缀部分）
            val normalizedQuery = currentInput.trim().lowercase()
            
            // 查询数据库检查当前词是否仍然匹配
            val entries = repository.queryByWord(candidate.word)
            if (entries.isNotEmpty()) {
                val entry = entries.first()
                
                // 检查拼音是否匹配（前缀匹配）
                val pinyin = entry.pinyin.replace(" ", "")
                if (pinyin.startsWith(normalizedQuery)) {
                    return@filter true
                }
                
                // 检查首字母是否匹配
                if (entry.initialLetters.startsWith(normalizedQuery)) {
                    return@filter true
                }
            }
            
            // 默认不保留
            false
        }
    }
} 