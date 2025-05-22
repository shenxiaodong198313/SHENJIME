package com.shenji.aikeyboard.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shenji.aikeyboard.model.Candidate
import com.shenji.aikeyboard.pinyin.InputType
import com.shenji.aikeyboard.pinyin.PinyinCandidate
import com.shenji.aikeyboard.pinyin.PinyinQueryEngine
import com.shenji.aikeyboard.pinyin.PinyinQueryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 拼音测试工具的ViewModel，处理核心业务逻辑
 * 使用标准化拼音查询模块进行处理
 */
class PinyinTestViewModel : ViewModel() {

    // 拼音查询引擎
    private val pinyinQueryEngine = PinyinQueryEngine.getInstance()
    
    // 当前查询结果，用于统计来源信息
    var currentQueryResult: PinyinQueryResult? = null
        private set
    
    // 输入状态流，用于防抖处理
    private val _inputFlow = MutableStateFlow("")
    val inputFlow: StateFlow<String> = _inputFlow

    // 已确认的文本（模拟输入法环境中已选择的文字）
    private var confirmedText = ""
    
    // 当前正在输入的文本（未确认的拼音）
    private var currentInput = ""

    // 当前输入类型
    private val _inputType = MutableLiveData<InputType>()
    val inputType: LiveData<InputType> = _inputType

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
        val phraseCount: Int = 0,
        val fromTrieCount: Int = 0,    // 从Trie树获取的候选词数量
        val fromDatabaseCount: Int = 0 // 从数据库获取的候选词数量
    )

    init {
        // 初始化空结果
        _candidates.value = emptyList()
        _matchRule.value = ""
        _syllableSplit.value = emptyList()
        _queryCondition.value = ""
        _queryProcess.value = ""
        _candidateStats.value = CandidateStats()
    }

    /**
     * 更新输入
     * 在真实输入法中，通过composingText可以获取当前正在编辑的文本
     * 在模拟工具中，我们需要提取出最新输入的拼音
     */
    fun updateInput(input: String) {
        if (input.isEmpty()) {
            _inputFlow.value = ""
            confirmedText = ""
            currentInput = ""
            return
        }
        
        // 从输入框中提取出最新的拼音输入部分
        // 正则表达式匹配连续的英文字母和数字
        val regex = Regex("[a-zA-Z0-9]+")
        val matches = regex.findAll(input)
        
        // 获取最后一个匹配项（最新的拼音输入）
        val lastMatch = matches.lastOrNull()
        
        if (lastMatch != null) {
            // 用户当前正在输入的拼音部分
            currentInput = lastMatch.value.trim().lowercase()
            
            // 可能的已确认文本（最后一个拼音之前的部分）
            val endIndex = lastMatch.range.first
            if (endIndex > 0) {
                confirmedText = input.substring(0, endIndex)
            } else {
                confirmedText = ""
            }
            
            // 更新输入流
            _inputFlow.value = currentInput
            Timber.d("输入更新: 已确认文本='$confirmedText', 当前输入='$currentInput'")
        } else {
            // 没有拼音输入
            currentInput = ""
            confirmedText = input
            _inputFlow.value = ""
        }
    }

    /**
     * 清除输入
     */
    fun clearInput() {
        _inputFlow.value = ""
        confirmedText = ""
        currentInput = ""
        _candidates.value = emptyList()
        _matchRule.value = ""
        _syllableSplit.value = emptyList()
        _queryCondition.value = ""
        _queryProcess.value = ""
        _candidateStats.value = CandidateStats()
        _inputType.value = InputType.UNKNOWN
    }

    /**
     * 处理输入，执行分词和查询操作
     * 只处理当前输入的拼音部分，不包括已确认的文本
     */
    fun processInput(input: String) {
        viewModelScope.launch {
            try {
                // 只处理当前输入的部分
                currentInput = input
                
                if (currentInput.isEmpty()) {
                    clearInput()
                    return@launch
                }
                
                // 先通过标准查询引擎获取输入分析和查询过程等信息
                val queryResult = pinyinQueryEngine.query(currentInput, 0, true)
                
                // 保存查询结果的元数据，用于UI显示
                _inputType.value = queryResult.inputType
                _matchRule.value = when (queryResult.inputType) {
                    InputType.INITIAL_LETTER -> "单字符首字母匹配"
                    InputType.PINYIN_SYLLABLE -> "单音节拼音匹配"
                    InputType.SYLLABLE_SPLIT -> "拼音音节拆分匹配"
                    InputType.ACRONYM -> "首字母缩写匹配"
                    InputType.DYNAMIC_SYLLABLE -> "动态音节识别匹配"
                    else -> "未知匹配方式"
                }
                _syllableSplit.value = queryResult.syllables
                _queryCondition.value = getQueryConditionText(queryResult)
                _queryProcess.value = queryResult.explanation
                
                // 现在使用PinyinIMEAdapter获取候选词，与键盘使用相同的查询逻辑
                val pinyinAdapter = com.shenji.aikeyboard.keyboard.PinyinIMEAdapter.getInstance()
                val wordFrequencyList = pinyinAdapter.getCandidates(currentInput, 20)
                
                // 将WordFrequency转换为Candidate对象
                val candidates = wordFrequencyList.map { wordFreq ->
                    val source = wordFreq.source ?: ""
                    val querySource = when {
                        source.contains("Trie") -> com.shenji.aikeyboard.pinyin.QuerySource.TRIE_INDEX
                        else -> com.shenji.aikeyboard.pinyin.QuerySource.REALM_DATABASE
                    }
                    
                    // 创建PinyinCandidate对象（用于统计来源）
                    val pinyinCandidate = PinyinCandidate(
                        word = wordFreq.word,
                        pinyin = "",  // 这些字段不重要，只需要保留word和querySource
                        frequency = wordFreq.frequency,
                        type = "",
                        querySource = querySource
                    )
                    
                    // 保存到当前查询结果中，用于统计
                    currentQueryResult = currentQueryResult?.copy(
                        candidates = (currentQueryResult?.candidates ?: emptyList()) + pinyinCandidate
                    )
                    
                    // 返回UI使用的Candidate对象
                    Candidate(
                        word = wordFreq.word,
                        pinyin = "",  // 暂时为空，之后可以从数据库获取完整信息
                        frequency = wordFreq.frequency,
                        type = "",
                        matchType = Candidate.MatchType.UNKNOWN,
                        source = if (source.contains("Trie")) "Trie树" else "数据库"
                    )
                }
                
                // 更新UI数据
                _candidates.value = candidates
                
                // 更新候选词统计
                updateCandidateStats(candidates)
                
                Timber.d("查询处理完成: 找到${candidates.size}个候选词")
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
     * 获取查询条件文本
     */
    private fun getQueryConditionText(result: PinyinQueryResult): String {
        return when (result.inputType) {
            InputType.INITIAL_LETTER -> "初始字母 = ${result.syllables.firstOrNull() ?: ""}"
            InputType.PINYIN_SYLLABLE -> "拼音音节 = ${result.syllables.firstOrNull() ?: ""}"
            InputType.SYLLABLE_SPLIT -> {
                val baseText = "音节拆分 = ${result.syllables.joinToString("+")}"
                if (result.allSyllableSplits.size > 1) {
                    "$baseText (共${result.allSyllableSplits.size}种拆分可能)"
                } else {
                    baseText
                }
            }
            InputType.ACRONYM -> "首字母缩写 = ${result.initialLetters}"
            InputType.DYNAMIC_SYLLABLE -> {
                val lastSyllable = result.syllables.lastOrNull()
                if (lastSyllable != null && lastSyllable.length == 1) {
                    // 有未完成部分，显示为音节+首字母
                    val completeSyllables = result.syllables.dropLast(1)
                    "动态识别 = ${completeSyllables.joinToString("+")} + 首字母'$lastSyllable'"
                } else {
                    // 全是完整音节
                    "动态识别 = ${result.syllables.joinToString("+")}"
                }
            }
            else -> "无法解析输入"
        }
    }
    
    /**
     * 更新候选词统计
     */
    private fun updateCandidateStats(candidates: List<Candidate>) {
        val singleCharCount = candidates.count { it.word.length == 1 }
        val phraseCount = candidates.count { it.word.length > 1 }
        
        // 计算不同来源的候选词数量
        val result = currentQueryResult // 获取当前查询结果
        val fromTrieCount = result?.candidates?.count { it.querySource == com.shenji.aikeyboard.pinyin.QuerySource.TRIE_INDEX } ?: 0
        val fromDatabaseCount = result?.candidates?.count { it.querySource == com.shenji.aikeyboard.pinyin.QuerySource.REALM_DATABASE } ?: 0
        
        _candidateStats.value = CandidateStats(
            totalCount = candidates.size,
            singleCharCount = singleCharCount,
            phraseCount = phraseCount,
            fromTrieCount = fromTrieCount,
            fromDatabaseCount = fromDatabaseCount
        )
    }
} 