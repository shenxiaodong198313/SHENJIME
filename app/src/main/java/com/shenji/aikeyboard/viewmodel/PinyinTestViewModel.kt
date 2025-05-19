package com.shenji.aikeyboard.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.data.PinyinSplitterOptimized
import com.shenji.aikeyboard.model.Candidate
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 拼音测试工具的ViewModel，处理核心业务逻辑
 */
class PinyinTestViewModel : ViewModel() {

    // 输入状态流，用于防抖处理
    private val _inputFlow = MutableStateFlow("")
    val inputFlow: StateFlow<String> = _inputFlow

    // 拼音分词器 - 使用优化版本
    private val pinyinSplitter = PinyinSplitterOptimized()

    // 判断是否为调试模式
    private val isDebugMode = true

    // 当前输入阶段
    private val _inputStage = MutableLiveData<InputStage>()
    val inputStage: LiveData<InputStage> = _inputStage

    // 匹配规则
    private val _matchRule = MutableLiveData<String>()
    val matchRule: LiveData<String> = _matchRule

    // 音节拆分结果
    private val _syllableSplit = MutableLiveData<List<String>>()
    val syllableSplit: LiveData<List<String>> = _syllableSplit

    // 查询条件
    private val _queryCondition = MutableLiveData<String>()
    val queryCondition: LiveData<String> = _queryCondition
    
    // 查询过程
    private val _queryProcess = MutableLiveData<String>()
    val queryProcess: LiveData<String> = _queryProcess
    
    // 候选词统计信息
    private val _candidateStats = MutableLiveData<CandidateStats>()
    val candidateStats: LiveData<CandidateStats> = _candidateStats

    // 候选词列表
    private val _candidates = MutableLiveData<List<Candidate>>()
    val candidates: LiveData<List<Candidate>> = _candidates
    
    /**
     * 候选词统计数据类
     */
    data class CandidateStats(
        val totalCount: Int = 0,
        val singleCharCount: Int = 0,
        val phraseCount: Int = 0
    )

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
     * 更新输入
     */
    fun updateInput(input: String) {
        _inputFlow.value = input.trim().toLowerCase()
    }

    /**
     * 清除输入
     */
    fun clearInput() {
        _inputFlow.value = ""
        _candidates.value = emptyList()
        _matchRule.value = ""
        _syllableSplit.value = emptyList()
        _queryCondition.value = ""
        _queryProcess.value = ""
        _candidateStats.value = CandidateStats()
        _inputStage.value = InputStage.UNKNOWN
    }

    /**
     * 处理输入，执行分词和查询操作
     */
    fun processInput(input: String) {
        viewModelScope.launch {
            try {
                if (input.isEmpty()) {
                    clearInput()
                    return@launch
                }

                // 1. 判断当前输入阶段
                val stage = classifyInputStage(input)
                _inputStage.value = stage

                // 2. 根据不同阶段执行相应的查询
                when (stage) {
                    InputStage.INITIAL_LETTER -> {
                        _matchRule.value = "单字符首字母匹配"
                        _syllableSplit.value = emptyList()
                        _queryCondition.value = "初始字母 = $input"
                        queryInitialLetterCandidates(input)
                    }
                    InputStage.PINYIN_COMPLETION -> {
                        _matchRule.value = "完整拼音音节匹配"
                        _syllableSplit.value = listOf(input)
                        _queryCondition.value = "拼音前缀 = $input"
                        queryPinyinCandidates(input)
                    }
                    InputStage.SYLLABLE_SPLIT -> {
                        val syllables = pinyinSplitter.splitPinyin(input)
                        _matchRule.value = "音节拆分匹配"
                        _syllableSplit.value = syllables
                        _queryCondition.value = "音节拆分 = ${syllables.joinToString("+")}"
                        querySplitCandidates(syllables)
                    }
                    InputStage.ACRONYM -> {
                        _matchRule.value = "首字母缩写匹配"
                        _syllableSplit.value = emptyList()
                        _queryCondition.value = "首字母缩写 = $input"
                        queryAcronymCandidates(input)
                    }
                    else -> {
                        _matchRule.value = "未知匹配方式"
                        _syllableSplit.value = emptyList()
                        _queryCondition.value = "无法解析输入"
                        _queryProcess.value = "无法解析输入，跳过查询"
                        _candidates.value = emptyList()
                        _candidateStats.value = CandidateStats()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "处理输入异常")
                _matchRule.value = "处理异常: ${e.message}"
                _queryProcess.value = "处理异常: ${e.message}"
                _candidates.value = emptyList()
                _candidateStats.value = CandidateStats()
            }
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

        // 单个完整拼音音节，直接归类为拼音补全阶段，并且标记为单音节
        if (isValidPinyin(input) && !input.contains(" ")) {
            return InputStage.PINYIN_COMPLETION // 拼音补全阶段
        }

        return when {
            canSplitToValidSyllables(input) -> InputStage.SYLLABLE_SPLIT // 音节拆分阶段
            else -> InputStage.ACRONYM // 首字母缩写阶段
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
     * 过滤重复候选词，只保留高词频的
     */
    private fun filterDuplicates(candidates: List<Candidate>): List<Candidate> {
        val processStep = StringBuilder("去重处理:\n")
        val uniqueCandidates = mutableMapOf<String, Candidate>()
        
        candidates.forEach { candidate ->
            val existingCandidate = uniqueCandidates[candidate.word]
            if (existingCandidate == null || existingCandidate.frequency < candidate.frequency) {
                if (existingCandidate != null) {
                    processStep.append("- 替换 '${candidate.word}' (词频 ${existingCandidate.frequency} -> ${candidate.frequency})\n")
                }
                uniqueCandidates[candidate.word] = candidate
            }
        }
        
        processStep.append("- 去重前: ${candidates.size}个, 去重后: ${uniqueCandidates.size}个\n")
        
        // 添加到现有的查询过程信息
        val currentProcess = _queryProcess.value ?: ""
        _queryProcess.value = currentProcess + processStep.toString()
        
        return uniqueCandidates.values.toList()
    }
    
    /**
     * 统计候选词数量
     */
    private fun updateCandidateStats(candidates: List<Candidate>) {
        val singleCharCount = candidates.count { it.word.length == 1 }
        val phraseCount = candidates.count { it.word.length > 1 }
        
        _candidateStats.value = CandidateStats(
            totalCount = candidates.size,
            singleCharCount = singleCharCount,
            phraseCount = phraseCount
        )
    }

    /**
     * 查询首字母阶段的候选词
     */
    private suspend fun queryInitialLetterCandidates(input: String) {
        val realm = ShenjiApplication.realm
        val processStep = StringBuilder("查询过程:\n")

        val results = withContext(Dispatchers.IO) {
            try {
                processStep.append("1. 查询单字词典中匹配首字母的条目\n")
                // 查询单字词典中匹配首字母的
                val query = realm.query<Entry>("type == $0 AND initialLetters BEGINSWITH $1", 
                    "chars", input)
                
                // 记录查询条件
                processStep.append("- 查询条件: type='chars' AND initialLetters BEGINSWITH '$input'\n")
                
                val singleChars = query.find()
                
                processStep.append("- 单字匹配结果: ${singleChars.size}个（仅查询chars表）\n")
                processStep.append("2. 单字有结果，不查询短语词典\n")
                
                // 直接返回单字结果，不再查询其他表
                val candidates = singleChars
                    .sortedByDescending { it.frequency }
                    .take(20)
                    .map { Candidate.fromEntry(it, Candidate.MatchType.INITIAL_LETTER) }
                
                processStep.append("- 获取排序后的前20个结果\n")
                candidates
            } catch (e: Exception) {
                Timber.e(e, "查询首字母候选词异常")
                processStep.append("查询异常: ${e.message}\n")
                emptyList()
            }
        }

        if (isDebugMode) {
            Timber.d("首字母查询结果: ${results.size}个候选词")
            processStep.append("3. 查询完成，获取候选词: ${results.size}个\n")
        }
        
        // 去重候选词
        val filteredResults = filterDuplicates(results)
        
        // 更新查询过程
        _queryProcess.value = processStep.toString()
        
        // 更新候选词统计
        updateCandidateStats(filteredResults)
        
        // 更新候选词列表
        _candidates.value = filteredResults
    }

    /**
     * 查询拼音补全阶段的候选词
     */
    private suspend fun queryPinyinCandidates(input: String) {
        val realm = ShenjiApplication.realm
        val processStep = StringBuilder("查询过程:\n")

        // 检查是否是单个有效音节
        val isSingleSyllable = isValidPinyin(input) && !input.contains(" ")

        val results = withContext(Dispatchers.IO) {
            try {
                // 如果是单个有效音节，只查询单字词典
                if (isSingleSyllable) {
                    processStep.append("1. 检测到单个有效音节'$input'，只查询单字词典\n")
                    val singleChars = realm.query<Entry>("type == $0 AND pinyin == $1",
                        "chars", input)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(20)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.PINYIN_PREFIX) }
                    
                    processStep.append("- 单字精确匹配结果: ${singleChars.size}个\n")
                    singleChars
                } else {
                    // 原有逻辑保持不变
                    processStep.append("1. 查询单字词典中匹配拼音的条目\n")
                    // 先查询单字词典
                    val singleChars = realm.query<Entry>("type == $0 AND pinyin BEGINSWITH $1",
                        "chars", input)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(10)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.PINYIN_PREFIX) }
                        
                    processStep.append("- 单字匹配结果: ${singleChars.size}个\n")

                    processStep.append("2. 查询短语词典中匹配拼音的条目\n")
                    // 再查询短语词典
                    val phrases = realm.query<Entry>("type != $0 AND pinyin BEGINSWITH $1",
                        "chars", input)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(10)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.PINYIN_PREFIX) }
                        
                    processStep.append("- 短语匹配结果: ${phrases.size}个\n")

                    // 合并并按词频排序
                    (singleChars + phrases).sortedByDescending { it.frequency }
                }
            } catch (e: Exception) {
                Timber.e(e, "查询拼音补全候选词异常")
                processStep.append("查询异常: ${e.message}\n")
                emptyList()
            }
        }

        if (isDebugMode) {
            Timber.d("拼音补全查询结果: ${results.size}个候选词")
            processStep.append("3. 查询完成，获取候选词: ${results.size}个\n")
        }
        
        // 去重候选词
        val filteredResults = filterDuplicates(results)
        
        // 更新查询过程
        _queryProcess.value = processStep.toString()
        
        // 更新候选词统计
        updateCandidateStats(filteredResults)
        
        // 更新候选词列表
        _candidates.value = filteredResults
    }

    /**
     * 查询音节拆分阶段的候选词
     */
    private suspend fun querySplitCandidates(syllables: List<String>) {
        if (syllables.isEmpty()) {
            _candidates.value = emptyList()
            _candidateStats.value = CandidateStats()
            _queryProcess.value = "音节拆分为空，跳过查询"
            return
        }

        val realm = ShenjiApplication.realm
        val processStep = StringBuilder("查询过程:\n")
        processStep.append("1. 音节拆分结果: ${syllables.joinToString("+")}\n")

        val results = withContext(Dispatchers.IO) {
            try {
                // 将音节连接为完整的拼音字符串（带空格）
                val fullPinyin = syllables.joinToString(" ")
                processStep.append("2. 构建完整拼音查询: '$fullPinyin'\n")
                
                // 直接查询完整拼音匹配的词条
                val entries = realm.query<Entry>("pinyin == $0", fullPinyin)
                    .find()
                    .sortedByDescending { it.frequency }
                processStep.append("- 完整拼音精确匹配结果: ${entries.size}个\n")
                
                // 如果精确匹配没有结果，尝试前缀匹配
                val candidates = if (entries.isEmpty() && syllables.size >= 2) {
                    processStep.append("3. 精确匹配无结果，尝试前缀匹配\n")
                    // 查询以这些音节开头的词条
                    val prefixMatches = realm.query<Entry>("pinyin BEGINSWITH $0", fullPinyin)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(20)
                    
                    processStep.append("- 前缀匹配结果: ${prefixMatches.size}个\n")
                    prefixMatches.map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                } else {
                    processStep.append("3. 使用精确匹配结果\n")
                    entries.take(20).map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                }
                
                candidates
            } catch (e: Exception) {
                Timber.e(e, "查询音节拆分候选词异常")
                processStep.append("查询异常: ${e.message}\n")
                emptyList()
            }
        }

        if (isDebugMode) {
            Timber.d("音节拆分查询结果: ${results.size}个候选词")
            processStep.append("4. 查询完成，获取候选词: ${results.size}个\n")
        }
        
        // 去重候选词
        val filteredResults = filterDuplicates(results)
        
        // 更新查询过程
        _queryProcess.value = processStep.toString()
        
        // 更新候选词统计
        updateCandidateStats(filteredResults)
        
        // 更新候选词列表
        _candidates.value = filteredResults
    }

    /**
     * 查询首字母缩写阶段的候选词
     */
    private suspend fun queryAcronymCandidates(input: String) {
        val realm = ShenjiApplication.realm
        val processStep = StringBuilder("查询过程:\n")
        processStep.append("1. 查询首字母缩写'$input'对应的条目\n")

        val results = withContext(Dispatchers.IO) {
            try {
                val entries = realm.query<Entry>("initialLetters == $0", input)
                    .find()
                processStep.append("- 匹配结果: ${entries.size}个\n")
                
                entries
                    .sortedByDescending { it.frequency }
                    .take(20)
                    .map { Candidate.fromEntry(it, Candidate.MatchType.ACRONYM) }
            } catch (e: Exception) {
                Timber.e(e, "查询首字母缩写候选词异常")
                processStep.append("查询异常: ${e.message}\n")
                emptyList()
            }
        }

        if (isDebugMode) {
            Timber.d("首字母缩写查询结果: ${results.size}个候选词")
            processStep.append("2. 查询完成，获取候选词: ${results.size}个\n")
        }
        
        // 去重候选词
        val filteredResults = filterDuplicates(results)
        
        // 更新查询过程
        _queryProcess.value = processStep.toString()
        
        // 更新候选词统计
        updateCandidateStats(filteredResults)
        
        // 更新候选词列表
        _candidates.value = filteredResults
    }

    /**
     * 执行笛卡尔积，生成所有可能的组合
     * 注：此方法已不再使用，保留以备后续参考
     * @deprecated 改用直接词典查询
     */
    @Deprecated("改用直接词典查询，避免内存溢出问题")
    private fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
        if (lists.isEmpty()) return emptyList()
        if (lists.size == 1) return lists[0].map { listOf(it) }
        
        // 限制最大组合数量，避免内存溢出
        val estimatedCombinations = lists.fold(1L) { acc, list -> acc * list.size }
        if (estimatedCombinations > 1000) {
            Timber.w("笛卡尔积可能产生过多组合: $estimatedCombinations > 1000，将限制数量")
            // 通过减少每个列表的大小来限制组合
            val trimmedLists = lists.map { list -> 
                if (list.size > 5) list.take(5) else list 
            }
            return cartesianProductImpl(trimmedLists)
        }
        
        return cartesianProductImpl(lists)
    }
    
    /**
     * 笛卡尔积实际实现
     * @deprecated 改用直接词典查询
     */
    @Deprecated("改用直接词典查询，避免内存溢出问题")
    private fun <T> cartesianProductImpl(lists: List<List<T>>): List<List<T>> {
        if (lists.isEmpty()) return emptyList()
        if (lists.size == 1) return lists[0].map { listOf(it) }
        
        val result = mutableListOf<List<T>>()
        val head = lists[0]
        val tail = lists.subList(1, lists.size)
        
        // 通过限制处理的元素数量降低内存消耗
        val processLimit = 20
        val headList = if (head.size > processLimit) head.take(processLimit) else head
        val tailProduct = cartesianProductImpl(tail)
        
        // 创建一个固定上限的结果集合
        val maxResults = 100
        for (headItem in headList) {
            for (tailItems in tailProduct) {
                // 达到上限时提前返回
                if (result.size >= maxResults) {
                    Timber.d("达到结果上限($maxResults)，提前返回")
                    return result
                }
                result.add(listOf(headItem) + tailItems)
            }
        }
        
        return result
    }
} 