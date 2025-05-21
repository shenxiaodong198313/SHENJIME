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
     */
    fun updateInput(input: String) {
        _inputFlow.value = input.trim().lowercase()
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
        _inputType.value = InputType.UNKNOWN
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

                // 使用标准化模块查询
                val queryResult = pinyinQueryEngine.query(input, 20, true)
                
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