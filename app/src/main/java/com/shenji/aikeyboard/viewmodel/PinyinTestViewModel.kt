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
import com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter
import com.shenji.aikeyboard.data.CandidateManager
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.ShenjiApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 拼音测试工具的ViewModel，处理核心业务逻辑
 * 使用统一拼音拆分器和候选词管理器
 */
class PinyinTestViewModel : ViewModel() {

    // 候选词管理器
    private val candidateManager = CandidateManager(DictionaryRepository())
    
    // 词典仓库
    private val dictionaryRepository = DictionaryRepository()
    
    // 拼音查询引擎（保留用于兼容）
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
    
    // 新增：观察分段拆分结果
    private val _segmentedSplit = MutableLiveData<List<List<String>>>()
    val segmentedSplit: LiveData<List<List<String>>> = _segmentedSplit

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
     * 处理输入并生成候选词
     */
    suspend fun processInput(input: String) = withContext(Dispatchers.IO) {
        try {
            val normalizedInput = input.trim().lowercase()
            
            if (normalizedInput.isEmpty()) {
                clearResults()
                return@withContext
            }
            
            // 分类输入类型
            val type = classifyInput(normalizedInput)
            withContext(Dispatchers.Main) {
                _inputType.value = type
            }
            
            // 生成匹配规则描述
            val rule = generateMatchRule(normalizedInput, type)
            withContext(Dispatchers.Main) {
                _matchRule.value = rule
            }
            
            // 执行音节拆分
            val syllables = when (type) {
                InputType.SYLLABLE_SPLIT -> {
                    UnifiedPinyinSplitter.split(normalizedInput)
                }
                else -> emptyList()
            }
            
            withContext(Dispatchers.Main) {
                _syllableSplit.value = syllables
            }
            
            // 新增：执行分段拆分（对于长输入）
            val segments = if (normalizedInput.length > 12) {
                UnifiedPinyinSplitter.splitIntoSegments(normalizedInput)
            } else {
                emptyList()
            }
            
            withContext(Dispatchers.Main) {
                _segmentedSplit.value = segments
            }
            
            // 生成查询条件描述
            val condition = generateQueryCondition(normalizedInput, type, syllables, segments)
            withContext(Dispatchers.Main) {
                _queryCondition.value = condition
            }
            
            // 开始查询过程记录
            val processBuilder = StringBuilder()
            
            // 记录拆分过程
            if (syllables.isNotEmpty()) {
                processBuilder.append("音节拆分结果: ${syllables.joinToString(" + ")}\n")
            }
            
            // 记录分段拆分过程
            if (segments.isNotEmpty()) {
                processBuilder.append("分段拆分结果:\n")
                segments.forEachIndexed { index, segment ->
                    processBuilder.append("  分段${index + 1}: ${segment.joinToString(" + ")}\n")
                }
                processBuilder.append("\n")
            }
            
            // 查询候选词
            val startTime = System.currentTimeMillis()
            val candidates = candidateManager.generateCandidates(normalizedInput, 20)
            val queryTime = System.currentTimeMillis() - startTime
            
            // 记录查询过程
            processBuilder.append("查询耗时: ${queryTime}ms\n")
            processBuilder.append("找到候选词: ${candidates.size}个\n")
            
            if (segments.isNotEmpty()) {
                processBuilder.append("\n分段匹配详情:\n")
                segments.forEachIndexed { index, segment ->
                    val segmentPinyin = segment.joinToString(" ")
                    processBuilder.append("  分段${index + 1} '$segmentPinyin' 的候选词:\n")
                    
                    // 这里可以添加每个分段的具体匹配结果
                    val segmentCandidates = candidates.filter { candidate ->
                        // 简单的匹配逻辑，实际可能需要更复杂的判断
                        segment.any { syllable -> 
                            candidate.word.length <= segment.size * 2 // 粗略估计
                        }
                    }.take(3)
                    
                    segmentCandidates.forEach { candidate ->
                        processBuilder.append("    - ${candidate.word} (权重: ${candidate.frequency})\n")
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                _queryProcess.value = processBuilder.toString()
            }
            
            // 转换为Candidate对象
            val candidateList = candidates.map { wordFreq ->
                // 查询完整的词条信息
                val entries = dictionaryRepository.queryByWord(wordFreq.word)
                if (entries.isNotEmpty()) {
                    val entry = entries.first()
                    Candidate(
                        word = entry.word,
                        pinyin = entry.pinyin,
                        frequency = entry.frequency,
                        type = entry.type
                    )
                } else {
                    // 如果查询不到，使用默认值
                    Candidate(
                        word = wordFreq.word,
                        pinyin = "",
                        frequency = wordFreq.frequency,
                        type = "unknown"
                    )
                }
            }
            
            // 统计候选词信息
            val stats = CandidateStats(
                totalCount = candidateList.size,
                singleCharCount = candidateList.count { it.word.length == 1 },
                phraseCount = candidateList.count { it.word.length > 1 },
                fromTrieCount = 0, // 暂时设为0，因为当前主要使用数据库
                fromDatabaseCount = candidateList.size
            )
            
            withContext(Dispatchers.Main) {
                _candidates.value = candidateList
                _candidateStats.value = stats
            }
            
        } catch (e: Exception) {
            Timber.e(e, "处理输入异常: $input")
            withContext(Dispatchers.Main) {
                _queryProcess.value = "处理输入时发生异常: ${e.message}"
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

    /**
     * 生成查询条件描述
     */
    private fun generateQueryCondition(
        input: String, 
        type: InputType, 
        syllables: List<String>,
        segments: List<List<String>>
    ): String {
        return when (type) {
            InputType.INITIAL_LETTER -> "首字母查询 = $input"
            InputType.ACRONYM -> "首字母缩写查询 = $input"
            InputType.PINYIN_SYLLABLE -> "拼音音节查询 = $input"
            InputType.SYLLABLE_SPLIT -> {
                if (segments.isNotEmpty()) {
                    val segmentDescriptions = segments.mapIndexed { index, segment ->
                        "分段${index + 1}: ${segment.joinToString(" ")}"
                    }
                    "分段匹配查询:\n${segmentDescriptions.joinToString("\n")}"
                } else if (syllables.isNotEmpty()) {
                    "音节拆分查询 = ${syllables.joinToString(" ")}"
                } else {
                    "音节拆分查询 = 无法拆分"
                }
            }
            else -> "未知查询类型"
        }
    }
    
    /**
     * 清除结果
     */
    private suspend fun clearResults() {
        withContext(Dispatchers.Main) {
            _candidates.value = emptyList()
            _matchRule.value = ""
            _syllableSplit.value = emptyList()
            _segmentedSplit.value = emptyList()
            _queryCondition.value = ""
            _queryProcess.value = ""
            _candidateStats.value = CandidateStats()
            _inputType.value = InputType.UNKNOWN
        }
    }
    
    /**
     * 分类输入类型
     */
    private fun classifyInput(input: String): InputType {
        return when {
            input.isEmpty() -> InputType.UNKNOWN
            input.length == 1 && input.matches(Regex("[a-z]")) -> InputType.INITIAL_LETTER
            input.all { it in 'a'..'z' } && input.length <= 4 && !UnifiedPinyinSplitter.isValidSyllable(input) -> InputType.ACRONYM
            UnifiedPinyinSplitter.isValidSyllable(input) -> InputType.PINYIN_SYLLABLE
            else -> InputType.SYLLABLE_SPLIT
        }
    }
    
    /**
     * 生成匹配规则描述
     */
    private fun generateMatchRule(input: String, type: InputType): String {
        return when (type) {
            InputType.INITIAL_LETTER -> "单字符首字母匹配"
            InputType.ACRONYM -> "首字母缩写匹配"
            InputType.PINYIN_SYLLABLE -> "单音节拼音匹配"
            InputType.SYLLABLE_SPLIT -> {
                if (input.length > 12) {
                    "长句子分段拆分匹配"
                } else {
                    "拼音音节拆分匹配"
                }
            }
            else -> "未知匹配方式"
        }
    }
} 