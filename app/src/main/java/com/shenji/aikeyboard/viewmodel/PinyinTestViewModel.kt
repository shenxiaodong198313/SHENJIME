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
        val phraseCount: Int = 0
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
                if (currentInput.isEmpty()) {
                    clearInput()
                    return@launch
                }

                // 使用标准化模块查询，只查询当前输入的拼音部分
                val queryResult = pinyinQueryEngine.query(currentInput, 20, true)
                
                // 更新UI数据
                updateUIWithQueryResult(queryResult)
                
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
     * 更新UI显示
     */
    private fun updateUIWithQueryResult(result: PinyinQueryResult) {
        // 1. 更新输入类型
        _inputType.value = result.inputType
        
        // 2. 设置匹配规则文本描述
        _matchRule.value = when (result.inputType) {
            InputType.INITIAL_LETTER -> "单字符首字母匹配"
            InputType.PINYIN_SYLLABLE -> "单音节拼音匹配"
            InputType.SYLLABLE_SPLIT -> "拼音音节拆分匹配"
            InputType.ACRONYM -> "首字母缩写匹配"
            else -> "未知匹配方式"
        }
        
        // 3. 更新音节拆分结果
        _syllableSplit.value = result.syllables
        
        // 4. 更新查询条件
        _queryCondition.value = when (result.inputType) {
            InputType.INITIAL_LETTER -> "初始字母 = ${result.syllables.firstOrNull() ?: ""}"
            InputType.PINYIN_SYLLABLE -> "拼音音节 = ${result.syllables.firstOrNull() ?: ""}"
            InputType.SYLLABLE_SPLIT -> "音节拆分 = ${result.syllables.joinToString("+")}"
            InputType.ACRONYM -> "首字母缩写 = ${result.initialLetters}"
            else -> "无法解析输入"
        }
        
        // 5. 更新查询过程
        _queryProcess.value = result.explanation
        
        // 6. 转换并更新候选词
        val candidates = result.candidates.map { pinyinCandidate ->
            convertToCandidateModel(pinyinCandidate)
        }
        _candidates.value = candidates
        
        // 7. 更新候选词统计
        updateCandidateStats(candidates)
    }
    
    /**
     * 将标准模块的PinyinCandidate转换为UI使用的Candidate模型
     */
    private fun convertToCandidateModel(pinyinCandidate: PinyinCandidate): Candidate {
        return Candidate(
            word = pinyinCandidate.word,
            pinyin = pinyinCandidate.pinyin,
            type = pinyinCandidate.type,
            frequency = pinyinCandidate.frequency,
            initialLetters = pinyinCandidate.initialLetters,
            matchType = when (pinyinCandidate.matchType) {
                com.shenji.aikeyboard.pinyin.MatchType.INITIAL_LETTER -> Candidate.MatchType.INITIAL_LETTER
                com.shenji.aikeyboard.pinyin.MatchType.PINYIN_SYLLABLE -> Candidate.MatchType.PINYIN_PREFIX
                com.shenji.aikeyboard.pinyin.MatchType.SYLLABLE_SPLIT -> Candidate.MatchType.SYLLABLE_SPLIT
                com.shenji.aikeyboard.pinyin.MatchType.ACRONYM -> Candidate.MatchType.ACRONYM
                else -> Candidate.MatchType.UNKNOWN
            }
        )
    }
    
    /**
     * 更新候选词统计
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
} 