package com.shenji.aikeyboard.data

import com.shenji.aikeyboard.ShenjiApplication
import timber.log.Timber
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 分阶段查询词典仓库
 * 实现多阶段、多词典的查询逻辑
 */
class StagedDictionaryRepository {
    private val realm get() = ShenjiApplication.realm
    private val repository = DictionaryRepository()
    
    // 调试信息收集
    data class DebugInfo(
        val input: String,
        val stages: Map<Int, List<String>>,
        val duplicates: List<Pair<String, String>>,
        val weights: Map<String, CandidateWeight>
    )
    
    private var _debugInfo = DebugInfo("", emptyMap(), emptyList(), emptyMap())
    val debugInfo get() = _debugInfo
    
    /**
     * 阶段化查询候选词
     * 根据输入类型和长度，分阶段查询不同的词典
     */
    suspend fun queryCandidates(input: String, limit: Int = 20): List<WordFrequency> = coroutineScope {
        val rawInput = input.trim().lowercase()
        
        // 检查是否有特殊前缀
        val normalizedInput = if (rawInput.startsWith("abbr:")) {
            // 移除前缀，强制使用首字母缩写策略
            rawInput.substring(5)
        } else {
            rawInput
        }
        
        if (normalizedInput.isEmpty()) return@coroutineScope emptyList()
        
        Timber.d("开始分阶段查询: '$normalizedInput'")
        
        // 收集调试信息
        val stageResults = mutableMapOf<Int, List<String>>()
        val duplicateDetector = DuplicateDetector()
        val allCandidates = mutableListOf<CandidateEntry>()
        
        try {
            // 检查是否是强制首字母缩写模式
            val isForceAbbreviation = rawInput.startsWith("abbr:")
            
            // 根据输入类型选择查询策略
            when {
                // 1. 单字母输入策略
                isSingleChar(normalizedInput) && !isForceAbbreviation -> {
                    Timber.d("检测到单字母输入: '$normalizedInput'")
                    val results = querySingleChar(normalizedInput, limit)
                    allCandidates.addAll(results)
                    stageResults[1] = results.map { it.word }
                }
                
                // 2. 首字母缩写输入策略 (或强制使用)
                isInitialAbbreviation(normalizedInput) || isForceAbbreviation -> {
                    Timber.d("检测到首字母缩写输入: '$normalizedInput'" + 
                           (if (isForceAbbreviation) " (强制模式)" else ""))
                    
                    // 按照新的分阶段查询策略处理
                    
                    // 阶段1: 首字母匹配 (chars + base)
                    // 单字母拆分查询，优先级1-2词典
                    val stage1Results = if (normalizedInput.length <= 3) {
                        // 对于短输入，优先查询完整首字母匹配
                        queryByInitials(normalizedInput, 1, listOf("chars", "base"), limit / 2)
                    } else {
                        // 对于长输入，拆分为单字母分别查询
                        val singleLetterResults = mutableListOf<CandidateEntry>()
                        normalizedInput.forEach { letter ->
                            val results = querySingleChar(letter.toString(), limit / normalizedInput.length)
                            singleLetterResults.addAll(results)
                        }
                        singleLetterResults
                    }
                    allCandidates.addAll(stage1Results)
                    stageResults[1] = stage1Results.map { it.word }
                    
                    // 阶段2: 关联扩展匹配 (correlation + associational)
                    // 相邻双字母组合查询，优先级3-4词典
                    val stage2Results = if (normalizedInput.length >= 2) {
                        val twoLetterResults = mutableListOf<CandidateEntry>()
                        for (i in 0 until normalizedInput.length - 1) {
                            val twoLetters = normalizedInput.substring(i, i + 2)
                            val results = queryByInitials(
                                twoLetters, 
                                2, 
                                listOf("correlation", "associational"), 
                                limit / (normalizedInput.length - 1)
                            )
                            twoLetterResults.addAll(results)
                        }
                        twoLetterResults
                    } else {
                        emptyList()
                    }
                    allCandidates.addAll(stage2Results)
                    stageResults[2] = stage2Results.map { it.word }
                    
                    // 阶段3: 专业词典补充 (place + people + poetry)
                    // 特定领域词典扩展查询
                    val stage3Results = if (normalizedInput.length >= 3) {
                        val specialDomainResults = queryByInitials(
                            normalizedInput.takeLast(minOf(3, normalizedInput.length)), 
                            3, 
                            listOf("place", "people", "poetry"), 
                            limit / 4
                        ).filter { it.word.length > 2 } // 只保留有意义的词组
                        
                        specialDomainResults
                    } else {
                        emptyList()
                    }
                    allCandidates.addAll(stage3Results)
                    stageResults[3] = stage3Results.map { it.word }
                    
                    // 阶段4: 兜底模糊匹配 (corrections + compatible)
                    // 当前三个阶段都没有足够结果时尝试纠错匹配
                    if (allCandidates.size < 5 && normalizedInput.length >= 2) {
                        val stage4Results = queryByInitials(
                            normalizedInput, 
                            4, 
                            listOf("corrections", "compatible"), 
                            limit / 4
                        )
                        allCandidates.addAll(stage4Results)
                        stageResults[4] = stage4Results.map { it.word }
                    }
                }
                
                // 3. 完整拼音输入策略
                else -> {
                    Timber.d("检测到拼音输入: '$normalizedInput'")
                    // 检测输入长度
                    val syllables = repository.splitPinyin(normalizedInput)
                    val stageCount = if (syllables.size <= 3) 4 else 3 // 音节<=3个时查询所有4个阶段
                    
                    // 阶段1: chars + base
                    val stage1Results = queryByPinyin(normalizedInput, 1, listOf("chars", "base"), limit / 2)
                    allCandidates.addAll(stage1Results)
                    stageResults[1] = stage1Results.map { it.word }
                    
                    // 不足阶段1不足10个结果时继续查询
                    if (stage1Results.size < 10 && stageCount >= 2) {
                        // 阶段2: correlation + associational
                        val stage2Results = queryByPinyin(normalizedInput, 2, 
                                                          listOf("correlation", "associational"), limit / 4)
                        allCandidates.addAll(stage2Results)
                        stageResults[2] = stage2Results.map { it.word }
                        
                        // 不足5个结果时继续查询
                        if (allCandidates.size < 5 && stageCount >= 3) {
                            // 阶段3: 专业词典
                            val stage3Results = queryByPinyin(normalizedInput, 3, 
                                                             listOf("place", "people", "poetry"), limit / 4)
                            allCandidates.addAll(stage3Results)
                            stageResults[3] = stage3Results.map { it.word }
                            
                            // 仍不足5个结果时进行纠错查询
                            if (allCandidates.size < 5 && stageCount >= 4) {
                                // 阶段4: 纠错查询
                                val stage4Results = queryByPinyin(normalizedInput, 4, 
                                                                 listOf("corrections", "compatible"), limit / 4)
                                allCandidates.addAll(stage4Results)
                                stageResults[4] = stage4Results.map { it.word }
                            }
                        }
                    }
                }
            }
            
            // 使用去重检测器处理所有候选词
            val uniqueCandidates = allCandidates.toList()
            allCandidates.clear()
            
            for (candidate in uniqueCandidates) {
                if (duplicateDetector.process(candidate)) {
                    allCandidates.add(candidate)
                }
            }
            
            // 按多维度排序
            val sortedResults = allCandidates.sortedWith { a, b -> 
                CandidateComparator.compare(a, b)
            }
            
            // 收集冲突记录
            val duplicates = duplicateDetector.getDuplicates()
            
            // 更新调试信息
            _debugInfo = DebugInfo(
                input = normalizedInput,
                stages = stageResults,
                duplicates = duplicates,
                weights = sortedResults.take(10).associate { 
                    it.word to CandidateWeight(it.stage, it.frequency, it.matchType, it.lengthBonus) 
                }
            )
            
            // 返回最终结果
            Timber.d("分阶段查询完成，总计${sortedResults.size}个候选词")
            return@coroutineScope sortedResults.take(limit).map { it.toWordFrequency() }
            
        } catch (e: Exception) {
            Timber.e(e, "分阶段查询出错: ${e.message}")
            return@coroutineScope emptyList()
        }
    }
    
    /**
     * 单字母查询策略
     */
    private suspend fun querySingleChar(input: String, limit: Int): List<CandidateEntry> = coroutineScope {
        val results = mutableListOf<CandidateEntry>()
        
        try {
            // 仅查询chars词典，返回所有initialLetters=input的单字
            val entries = repository.searchDirectlyByInitial(
                initial = input,
                limit = limit,
                onlyChars = true
            )
            
            // 转换为CandidateEntry，设置为阶段1，精确匹配
            results.addAll(entries.map { WordFrequency(it.word, it.frequency) }
                .map { 
                    CandidateEntry(
                        word = it.word,
                        pinyin = "", // 这里不需要拼音
                        frequency = it.frequency,
                        type = "chars",
                        stage = 1,
                        matchType = 0, // 精确匹配
                        lengthBonus = 0 // 单字无长度奖励
                    )
                })
            
        } catch (e: Exception) {
            Timber.e(e, "单字母查询出错: ${e.message}")
        }
        
        return@coroutineScope results
    }
    
    /**
     * 首字母缩写查询策略
     * @param input 输入的首字母缩写
     * @param stage 查询阶段
     * @param dictionaryTypes 要查询的词典类型列表
     * @param limit 结果限制数量
     * @return 候选词条列表
     */
    private suspend fun queryByInitials(
        input: String, 
        stage: Int, 
        dictionaryTypes: List<String>, 
        limit: Int
    ): List<CandidateEntry> = coroutineScope {
        val results = mutableListOf<CandidateEntry>()
        
        try {
            // 特殊处理双字母和多字母输入
            val inputLength = input.length
            val isShortInput = inputLength <= 2
            
            Timber.d("阶段${stage}查询首字母'${input}' (长度=${inputLength}), 词典=${dictionaryTypes.joinToString()}")
            
            // 并行查询多个词典
            val deferred = dictionaryTypes.map { type ->
                async {
                    // 查询指定词典类型的首字母匹配项
                    val entries = repository.searchByInitialLettersAndType(
                        initials = input,
                        type = type,
                        // 对于短输入和词典优先级高的类型，分配更多的结果限制
                        limit = when {
                            isShortInput && type in listOf("chars", "base") -> limit / 2
                            isShortInput -> limit / 4
                            else -> limit / dictionaryTypes.size
                        }
                    )
                    
                    // 转换为CandidateEntry，设置对应阶段和词典类型
                    entries.map {
                        CandidateEntry(
                            word = it.word,
                            pinyin = it.pinyin ?: "",
                            frequency = it.frequency,
                            type = type,
                            stage = stage,
                            matchType = 2, // 首字母匹配
                            // 词长奖励：根据词典类型和字数给予不同奖励
                            lengthBonus = when {
                                it.word.length > 5 -> 15 // 长词高奖励
                                it.word.length > 3 -> 10 // 中等长度词中等奖励
                                it.word.length > 1 -> 5  // 双字词低奖励
                                else -> 0              // 单字无奖励
                            } + when (type) {
                                // 给予特定词典类型额外的优先级奖励
                                "chars" -> 10
                                "base" -> 8
                                "correlation" -> 6
                                "associational" -> 4
                                "place", "people" -> 2
                                else -> 0
                            }
                        )
                    }
                }
            }
            
            // 等待所有查询完成并合并结果
            val allResults = deferred.awaitAll().flatten()
            results.addAll(allResults)
            
            // 如果结果不足且输入较短，尝试额外查询策略
            if (results.size < 5 && inputLength <= 3) {
                Timber.d("首字母'$input'结果不足，尝试额外查询策略")
                
                // 根据当前阶段确定要额外查询的词典
                val extraTypes = when (stage) {
                    1 -> listOf("correlation", "associational")
                    2 -> listOf("place", "people", "poetry")
                    3 -> listOf("corrections", "compatible")
                    else -> emptyList()
                }.filter { it !in dictionaryTypes }
                
                if (extraTypes.isNotEmpty()) {
                    // 并行查询额外词典
                    val extraDeferred = extraTypes.map { type ->
                        async {
                            val entries = repository.searchByInitialLettersAndType(
                                initials = input,
                                type = type,
                                limit = limit / extraTypes.size
                            )
                            
                            entries.map {
                                CandidateEntry(
                                    word = it.word,
                                    pinyin = it.pinyin ?: "",
                                    frequency = it.frequency,
                                    type = type,
                                    stage = stage + 1, // 设置为下一阶段
                                    matchType = 2, // 首字母匹配
                                    lengthBonus = when {
                                        it.word.length > 5 -> 12 // 长词次高奖励
                                        it.word.length > 3 -> 8  // 中等长度词次中等奖励
                                        it.word.length > 1 -> 4  // 双字词次低奖励
                                        else -> 0               // 单字无奖励
                                    }
                                )
                            }
                        }
                    }
                    
                    // 等待额外查询完成并合并结果
                    val extraResults = extraDeferred.awaitAll().flatten()
                    results.addAll(extraResults)
                    Timber.d("额外查询返回${extraResults.size}个结果")
                }
            }
            
            // 对于单字母或双字母输入，如果结果还是不足，可以尝试模糊匹配
            if (results.size < 3 && inputLength <= 2 && stage >= 3) {
                Timber.d("首字母'$input'结果严重不足，尝试模糊匹配")
                
                // 使用字符拼接模式查询
                val fuzzyResults = repository.searchFuzzyByInitial(
                    initial = input,
                    limit = limit / 2
                ).map {
                    CandidateEntry(
                        word = it.word,
                        pinyin = it.pinyin ?: "",
                        frequency = it.frequency,
                        type = "fuzzy",
                        stage = 4, // 设置为兜底阶段
                        matchType = 3, // 模糊匹配
                        lengthBonus = 0 // 无长度奖励
                    )
                }
                
                results.addAll(fuzzyResults)
                Timber.d("模糊匹配返回${fuzzyResults.size}个结果")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "首字母缩写查询出错: ${e.message}")
        }
        
        return@coroutineScope results
    }
    
    /**
     * 拼音查询策略
     */
    private suspend fun queryByPinyin(
        input: String, 
        stage: Int, 
        dictionaryTypes: List<String>, 
        limit: Int
    ): List<CandidateEntry> = coroutineScope {
        val results = mutableListOf<CandidateEntry>()
        
        try {
            // 并行查询多个词典
            val deferred = dictionaryTypes.map { type ->
                async {
                    // 查询指定词典类型的拼音匹配项
                    val exactMatches = repository.searchExactPinyinByType(
                        pinyin = input,
                        type = type,
                        limit = limit / (dictionaryTypes.size * 2)
                    )
                    
                    // 查询拼音前缀匹配项
                    val prefixMatches = repository.searchPinyinPrefixByType(
                        pinyinPrefix = input,
                        type = type,
                        limit = limit / (dictionaryTypes.size * 2)
                    )
                    
                    // 转换为CandidateEntry，设置对应阶段、匹配类型和词典类型
                    val exactEntries = exactMatches.map {
                        CandidateEntry(
                            word = it.word,
                            pinyin = it.pinyin ?: "",
                            frequency = it.frequency,
                            type = type,
                            stage = stage,
                            matchType = 0, // 精确匹配
                            lengthBonus = if (it.word.length > 3) 10 else 0
                        )
                    }
                    
                    val prefixEntries = prefixMatches.map {
                        CandidateEntry(
                            word = it.word,
                            pinyin = it.pinyin ?: "",
                            frequency = it.frequency,
                            type = type,
                            stage = stage,
                            matchType = 1, // 前缀匹配
                            lengthBonus = if (it.word.length > 3) 10 else 0
                        )
                    }
                    
                    exactEntries + prefixEntries
                }
            }
            
            // 等待所有查询完成并合并结果
            val allResults = deferred.awaitAll().flatten()
            results.addAll(allResults)
            
        } catch (e: Exception) {
            Timber.e(e, "拼音查询出错: ${e.message}")
        }
        
        return@coroutineScope results
    }
    
    /**
     * 检查是否为单字母输入
     */
    private fun isSingleChar(input: String): Boolean {
        return input.length == 1 && input.matches(Regex("[a-z]"))
    }
    
    /**
     * 检查是否为首字母缩写输入
     * 根据输入长度和特征识别不同类型的首字母缩写
     */
    private fun isInitialAbbreviation(input: String): Boolean {
        // 如果输入为空或含有空格，不是首字母缩写
        if (input.isEmpty() || input.contains(' ')) {
            return false
        }
        
        // 所有字符必须是小写字母
        if (!input.all { it.isLowerCase() }) {
            return false
        }
        
        // 对于单字母输入，始终认为是首字母
        if (input.length == 1) {
            return true
        }
        
        // 对于2字母输入，使用宽松策略：只要不是有效拼音音节就视为首字母缩写
        if (input.length == 2) {
            return !repository.isValidPinyin(input)
        }
        
        // 对于3字母输入，检查是否是首字母组合模式
        if (input.length == 3) {
            // 1. 检查是否是常见的音节组合（不是首字母缩写）
            val commonSyllables = listOf("shi", "zhi", "chi", "ang", "eng", "ing", "ong")
            if (commonSyllables.contains(input)) {
                return false
            }
            
            // 2. 尝试拆分为多个音节，如果无法拆分则可能是首字母缩写
            val syllables = repository.splitPinyin(input)
            if (syllables.isEmpty() || syllables.joinToString("") != input) {
                return true
            }
            
            // 3. 虽然可以拆分为音节，但检查是否更像首字母缩写
            // 例如：wxs 既可以拆分为 w+xs 也可以是首字母缩写 w+x+s
            val potentialAsAcronym = realm.query(Entry::class, "initialLetters == $0", input).count().find()
            val potentialAsPinyin = realm.query(Entry::class, "pinyin BEGINSWITH $0", syllables.joinToString(" ")).count().find()
            
            // 如果作为首字母缩写有更多匹配，则优先视为首字母缩写
            return potentialAsAcronym >= potentialAsPinyin
        }
        
        // 对于更长的输入，更严格地检查
        // 如果完全匹配拼音，则不是首字母缩写
        if (repository.isValidPinyin(input)) {
            return false
        }
        
        // 检查是否存在匹配该首字母缩写的词条
        val acronymMatches = realm.query(Entry::class, "initialLetters == $0", input).count().find()
        
        // 如果有首字母匹配，则视为首字母缩写
        if (acronymMatches > 0) {
            return true
        }
        
        // 尝试将长输入视为多个首字母的组合
        // 例如：wxsrf 可能是 w+x+s+r+f 的首字母组合
        return input.length <= 8 && input.all { it in "abcdefghijklmnopqrstuvwxyz" }
    }
} 