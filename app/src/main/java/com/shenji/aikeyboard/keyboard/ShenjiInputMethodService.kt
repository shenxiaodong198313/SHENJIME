package com.shenji.aikeyboard.keyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.data.PinyinSplitter
import com.shenji.aikeyboard.data.WordFrequency
import com.shenji.aikeyboard.model.Candidate
import io.realm.kotlin.ext.query
import kotlinx.coroutines.*
import timber.log.Timber

class ShenjiInputMethodService : InputMethodService() {
    
    private val TAG = "ShenjiIME"
    
    // 键盘视图
    private lateinit var keyboardView: View
    
    // 候选词容器
    private lateinit var candidatesContainer: LinearLayout
    
    // 默认候选词视图
    private lateinit var defaultCandidatesView: LinearLayout
    
    // 候选词线性布局（横向滚动）
    private lateinit var candidatesView: LinearLayout
    
    // 展开候选词按钮
    private lateinit var expandCandidatesButton: Button
    
    // 当前输入的拼音
    private var composingText = StringBuilder()
    
    // 协程作用域
    private val coroutineScope = MainScope()
    
    // 拼音分词器
    private val pinyinSplitter = PinyinSplitter()
    
    // 判断是否为调试模式
    private val isDebugMode = true
    
    // 初始查询限制数量（直接显示）
    private val initialQueryLimit = 20
    
    // 单字显示的最大数量
    private val maxSingleCharCount = 10
    
    // 当前查询Job，用于取消旧查询
    private var currentQueryJob: Job? = null
    
    // 当前输入阶段
    private enum class InputStage {
        INITIAL_LETTER,      // 首字母阶段
        PINYIN_COMPLETION,   // 拼音补全阶段
        SYLLABLE_SPLIT,      // 音节拆分阶段
        ACRONYM,             // 首字母缩写阶段
        UNKNOWN              // 未知阶段
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("神迹输入法服务已创建")
        Timber.d("输入法服务生命周期: onCreate")
    }
    
    override fun onCreateInputView(): View {
        Timber.d("输入法服务生命周期: onCreateInputView - 开始创建键盘视图")
        
        try {
            // 加载键盘布局
            keyboardView = layoutInflater.inflate(R.layout.keyboard_layout, null)
            
            // 初始化候选词区域
            candidatesContainer = keyboardView.findViewById(R.id.candidates_container)
            defaultCandidatesView = keyboardView.findViewById(R.id.default_candidates_view)
            candidatesView = keyboardView.findViewById(R.id.candidates_view)
            expandCandidatesButton = keyboardView.findViewById(R.id.expand_candidates_button)
            
            // 设置展开按钮点击事件
            expandCandidatesButton.setOnClickListener {
                // 临时显示一个Toast提示，后续会替换为展开候选词网格
                Toast.makeText(this, "展开候选词功能 - 正在开发中", Toast.LENGTH_SHORT).show()
                Timber.d("点击了展开候选词按钮")
            }
            
            // 设置字母按键监听器
            setupLetterKeys()
            
            // 设置功能按键监听器
            setupFunctionKeys()
            
            Timber.d("键盘视图创建成功")
            return keyboardView
        } catch (e: Exception) {
            Timber.e(e, "键盘视图创建失败: ${e.message}")
            // 返回一个空的视图作为备选方案
            return LinearLayout(this)
        }
    }
    
    // 设置字母按键监听器
    private fun setupLetterKeys() {
        val letterIds = listOf(
            R.id.key_a, R.id.key_b, R.id.key_c, R.id.key_d, R.id.key_e, 
            R.id.key_f, R.id.key_g, R.id.key_h, R.id.key_i, R.id.key_j,
            R.id.key_k, R.id.key_l, R.id.key_m, R.id.key_n, R.id.key_o,
            R.id.key_p, R.id.key_q, R.id.key_r, R.id.key_s, R.id.key_t,
            R.id.key_u, R.id.key_v, R.id.key_w, R.id.key_x, R.id.key_y,
            R.id.key_z
        )
        
        letterIds.forEach { id ->
            keyboardView.findViewById<Button>(id)?.setOnClickListener { v ->
                val letter = (v as Button).text.toString().lowercase()
                onInputLetter(letter)
            }
        }
    }
    
    // 设置功能按键监听器
    private fun setupFunctionKeys() {
        // 删除键
        keyboardView.findViewById<Button>(R.id.key_delete)?.setOnClickListener {
            onDelete()
        }
        
        // 空格键
        keyboardView.findViewById<Button>(R.id.key_space)?.setOnClickListener {
            onSpace()
        }
        
        // 回车键
        keyboardView.findViewById<Button>(R.id.key_enter)?.setOnClickListener {
            onEnter()
        }
        
        // 符号键
        keyboardView.findViewById<Button>(R.id.key_symbol)?.setOnClickListener {
            // 暂不实现符号键盘
            Timber.d("符号键盘暂未实现")
        }
        
        // 逗号键
        keyboardView.findViewById<Button>(R.id.key_comma)?.setOnClickListener {
            commitText(",")
        }
        
        // 句号键
        keyboardView.findViewById<Button>(R.id.key_period)?.setOnClickListener {
            commitText(".")
        }
        
        // Shift键
        keyboardView.findViewById<Button>(R.id.key_shift)?.setOnClickListener {
            // 暂不实现大小写切换
            Timber.d("大小写切换暂未实现")
        }
    }
    
    // 处理字母输入
    private fun onInputLetter(letter: String) {
        // 添加字母到拼音组合中
        composingText.append(letter)
        
        // 更新输入框中显示的拼音
        currentInputConnection?.setComposingText(composingText, 1)
        
        // 显示候选词区域
        showCandidates()
        
        // 处理输入并生成候选词
        processInput(composingText.toString())
    }
    
    // 处理删除操作
    private fun onDelete() {
        if (composingText.isNotEmpty()) {
            // 删除拼音中的最后一个字母
            composingText.deleteCharAt(composingText.length - 1)
            
            if (composingText.isEmpty()) {
                // 如果拼音为空，隐藏候选词区域
                hideCandidates()
                
                // 结束组合文本状态
                currentInputConnection?.finishComposingText()
            } else {
                // 更新输入框中显示的拼音
                currentInputConnection?.setComposingText(composingText, 1)
                
                // 重新处理输入
                processInput(composingText.toString())
            }
        } else {
            // 如果没有组合文本，删除前一个字符
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }
    
    // 处理空格操作
    private fun onSpace() {
        if (composingText.isNotEmpty()) {
            // 如果有拼音输入，选择第一个候选词
            selectFirstCandidate()
        } else {
            // 否则插入空格
            commitText(" ")
        }
    }
    
    // 处理回车操作
    private fun onEnter() {
        if (composingText.isNotEmpty()) {
            // 如果有拼音输入，直接提交拼音并清空
            commitText(composingText.toString())
            composingText.clear()
            hideCandidates()
        } else {
            // 发送回车键事件
            val ic = currentInputConnection
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }
    
    // 提交文本到输入框
    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
        // 重置组合文本
        composingText.clear()
        // 隐藏候选词区域（安全检查）
        if (areViewComponentsInitialized()) {
            hideCandidates()
        }
    }
    
    // 辅助方法：检查视图组件是否已初始化
    private fun areViewComponentsInitialized(): Boolean {
        return ::keyboardView.isInitialized && 
               ::candidatesContainer.isInitialized && 
               ::defaultCandidatesView.isInitialized &&
               ::candidatesView.isInitialized
    }
    
    // 安全显示候选词区域
    private fun showCandidates() {
        if (areViewComponentsInitialized()) {
            candidatesContainer.visibility = View.VISIBLE
            defaultCandidatesView.visibility = View.VISIBLE
        }
    }
    
    // 隐藏候选词区域
    private fun hideCandidates() {
        if (areViewComponentsInitialized()) {
            candidatesContainer.visibility = View.GONE
        }
    }
    
    // 选择第一个候选词
    private fun selectFirstCandidate() {
        if (candidatesView.childCount > 0) {
            val firstCandidate = candidatesView.getChildAt(0) as TextView
            commitText(firstCandidate.text.toString())
        } else {
            // 如果没有候选词，直接提交拼音
            commitText(composingText.toString())
        }
    }
    
    /**
     * 对候选词进行排序处理：先单字（按词频），后词语（按词频）
     */
    private fun sortCandidates(candidates: List<Candidate>): List<Candidate> {
        // 先将候选词分为单字和词语两组
        val singleChars = candidates.filter { it.word.length == 1 }
            .sortedByDescending { it.frequency }
            .take(maxSingleCharCount) // 限制单字数量
            
        val phrases = candidates.filter { it.word.length > 1 }
            .sortedByDescending { it.frequency }
            
        // 合并：先单字，后词语
        return singleChars + phrases
    }
    
    /**
     * 过滤重复候选词，只保留高词频的
     */
    private fun filterDuplicates(candidates: List<Candidate>): List<Candidate> {
        val uniqueCandidates = mutableMapOf<String, Candidate>()
        
        candidates.forEach { candidate ->
            val existingCandidate = uniqueCandidates[candidate.word]
            if (existingCandidate == null || existingCandidate.frequency < candidate.frequency) {
                uniqueCandidates[candidate.word] = candidate
            }
        }
        
        Timber.d("候选词去重: ${candidates.size}个 -> ${uniqueCandidates.size}个")
        
        return uniqueCandidates.values.toList()
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

        return when {
            isValidPinyin(input) -> InputStage.PINYIN_COMPLETION // 拼音补全阶段
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
     * 处理输入，执行分词和查询操作
     */
    private fun processInput(input: String) {
        // 取消之前的查询任务
        currentQueryJob?.cancel()
        
        // 清空候选词区域（不再显示"加载中"）
        coroutineScope.launch {
            withContext(Dispatchers.Main) {
                candidatesView.removeAllViews()
            }
        }
        
        // 创建新的查询任务
        currentQueryJob = coroutineScope.launch {
            try {
                if (input.isEmpty()) {
                    return@launch
                }

                // 1. 判断当前输入阶段
                val stage = classifyInputStage(input)
                
                // 2. 根据不同阶段执行相应的查询
                when (stage) {
                    InputStage.INITIAL_LETTER -> {
                        Timber.d("当前阶段: 首字母阶段")
                        queryInitialLetterCandidates(input)
                    }
                    InputStage.PINYIN_COMPLETION -> {
                        Timber.d("当前阶段: 拼音补全阶段")
                        queryPinyinCandidates(input)
                    }
                    InputStage.SYLLABLE_SPLIT -> {
                        Timber.d("当前阶段: 音节拆分阶段")
                        val syllables = pinyinSplitter.splitPinyin(input)
                        querySplitCandidates(syllables)
                    }
                    InputStage.ACRONYM -> {
                        Timber.d("当前阶段: 首字母缩写阶段")
                        queryAcronymCandidates(input)
                    }
                    else -> {
                        Timber.d("当前阶段: 未知")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "处理输入异常")
                withContext(Dispatchers.Main) {
                    candidatesView.removeAllViews()
                    val textView = TextView(this@ShenjiInputMethodService)
                    textView.text = "处理异常"
                    textView.setTextAppearance(R.style.CandidateWord)
                    candidatesView.addView(textView)
                }
            }
        }
    }
    
    /**
     * 查询首字母阶段的候选词
     */
    private suspend fun queryInitialLetterCandidates(input: String) {
        val realm = ShenjiApplication.realm

        // 快速查询（前20个结果）
        val quickResults = withContext(Dispatchers.IO) {
            try {
                // 查询单字词典中匹配首字母的
                val singleChars = realm.query<Entry>("type == $0 AND initialLetters BEGINSWITH $1", 
                    "chars", input)
                    .find()
                
                Timber.d("单字匹配结果: ${singleChars.size}个")
                
                // 如果没有结果，查询短语词典    
                if (singleChars.isEmpty()) {
                    val phrases = realm.query<Entry>("initialLetters BEGINSWITH $0", input)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(initialQueryLimit)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.INITIAL_LETTER) }
                    
                    filterDuplicates(phrases)
                } else {
                    val chars = singleChars
                        .sortedByDescending { it.frequency }
                        .take(initialQueryLimit)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.INITIAL_LETTER) }
                    
                    filterDuplicates(chars)
                }
            } catch (e: Exception) {
                Timber.e(e, "查询首字母候选词异常")
                emptyList()
            }
        }
        
        // 更新UI显示快速查询结果
        val sortedQuickResults = sortCandidates(quickResults)
        updateCandidateView(sortedQuickResults)
        
        // 异步查询更多结果
        if (!currentQueryJob?.isActive!!) return
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val moreResults = try {
                    // 查询更多结果
                    val allEntries = realm.query<Entry>("initialLetters BEGINSWITH $0", input)
                        .find()
                        .sortedByDescending { it.frequency }
                        .drop(initialQueryLimit) // 跳过已经查询的部分
                        .take(100) // 额外查询更多结果
                        .map { Candidate.fromEntry(it, Candidate.MatchType.INITIAL_LETTER) }
                    
                    filterDuplicates(allEntries)
                } catch (e: Exception) {
                    Timber.e(e, "异步查询更多首字母候选词异常")
                    emptyList()
                }
                
                // 合并结果并更新UI
                if (moreResults.isNotEmpty() && currentQueryJob?.isActive!!) {
                    val combinedResults = (quickResults + moreResults).distinctBy { it.word }
                    val sortedResults = sortCandidates(combinedResults)
                    updateCandidateView(sortedResults)
                }
            } catch (e: Exception) {
                Timber.e(e, "异步处理首字母候选词异常")
            }
        }
    }

    /**
     * 查询拼音补全阶段的候选词
     */
    private suspend fun queryPinyinCandidates(input: String) {
        val realm = ShenjiApplication.realm

        // 快速查询前20个结果
        val quickResults = withContext(Dispatchers.IO) {
            try {
                // 先查询单字词典
                val singleChars = realm.query<Entry>("type == $0 AND pinyin BEGINSWITH $1",
                    "chars", input)
                    .find()
                    .sortedByDescending { it.frequency }
                    .take(maxSingleCharCount)
                    .map { Candidate.fromEntry(it, Candidate.MatchType.PINYIN_PREFIX) }
                    
                Timber.d("单字匹配结果: ${singleChars.size}个")

                // 再查询短语词典（取数量凑够初始查询限制）
                val phrasesLimit = (initialQueryLimit - singleChars.size).coerceAtLeast(0)
                val phrases = if (phrasesLimit > 0) {
                    realm.query<Entry>("type != $0 AND pinyin BEGINSWITH $1",
                        "chars", input)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(phrasesLimit)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.PINYIN_PREFIX) }
                } else {
                    emptyList()
                }
                    
                Timber.d("短语匹配结果: ${phrases.size}个")

                // 合并并去重
                filterDuplicates(singleChars + phrases)
            } catch (e: Exception) {
                Timber.e(e, "查询拼音补全候选词异常")
                emptyList()
            }
        }
        
        // 更新UI显示快速查询结果
        val sortedQuickResults = sortCandidates(quickResults)
        updateCandidateView(sortedQuickResults)
        
        // 异步查询更多结果
        if (!currentQueryJob?.isActive!!) return
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // 查询更多短语
                val moreResults = realm.query<Entry>("pinyin BEGINSWITH $0", input)
                    .find()
                    .sortedByDescending { it.frequency }
                    .drop(initialQueryLimit) // 跳过已经查询的部分
                    .take(100) // 额外查询更多结果
                    .map { Candidate.fromEntry(it, Candidate.MatchType.PINYIN_PREFIX) }
                
                val moreUniqueResults = filterDuplicates(moreResults)
                
                // 合并结果并更新UI
                if (moreUniqueResults.isNotEmpty() && currentQueryJob?.isActive!!) {
                    val combinedResults = (quickResults + moreUniqueResults).distinctBy { it.word }
                    val sortedResults = sortCandidates(combinedResults)
                    updateCandidateView(sortedResults)
                }
            } catch (e: Exception) {
                Timber.e(e, "异步处理拼音补全候选词异常")
            }
        }
    }

    /**
     * 查询音节拆分阶段的候选词
     */
    private suspend fun querySplitCandidates(syllables: List<String>) {
        if (syllables.isEmpty()) {
            withContext(Dispatchers.Main) {
                candidatesView.removeAllViews()
                val textView = TextView(this@ShenjiInputMethodService)
                textView.text = "无法拆分音节"
                textView.setTextAppearance(R.style.CandidateWord)
                candidatesView.addView(textView)
            }
            return
        }

        val realm = ShenjiApplication.realm
        Timber.d("音节拆分结果: ${syllables.joinToString("+")}")
        
        // 构建拼音查询条件
        val pinyinQuery = syllables.joinToString("+")
        Timber.d("拼音查询条件: $pinyinQuery")
        
        // 快速查询
        val quickResults = withContext(Dispatchers.IO) {
            try {
                val results = mutableListOf<Candidate>()
                
                // 1. 首先查询基础词典(base)中匹配的词语
                val baseEntries = realm.query<Entry>(
                    "type == $0 AND pinyin == $1", 
                    "base", 
                    syllables.joinToString(" ")
                ).find()
                Timber.d("基础词典匹配结果: ${baseEntries.size}个")
                
                if (baseEntries.isNotEmpty()) {
                    val baseCandidates = baseEntries
                        .sortedByDescending { it.frequency }
                        .take(initialQueryLimit)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                    results.addAll(baseCandidates)
                }
                
                // 2. 如果基础词典结果不足，继续查询地名词典(place)
                if (results.size < initialQueryLimit) {
                    val placeEntries = realm.query<Entry>(
                        "type == $0 AND pinyin == $1", 
                        "place", 
                        syllables.joinToString(" ")
                    ).find()
                    Timber.d("地名词典匹配结果: ${placeEntries.size}个")
                    
                    if (placeEntries.isNotEmpty()) {
                        val placeCandidates = placeEntries
                            .sortedByDescending { it.frequency }
                            .take(initialQueryLimit - results.size)
                            .map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                        results.addAll(placeCandidates)
                    }
                }
                
                // 3. 如果结果仍然不足，继续查询人名词典(people)
                if (results.size < initialQueryLimit) {
                    val peopleEntries = realm.query<Entry>(
                        "type == $0 AND pinyin == $1", 
                        "people", 
                        syllables.joinToString(" ")
                    ).find()
                    Timber.d("人名词典匹配结果: ${peopleEntries.size}个")
                    
                    if (peopleEntries.isNotEmpty()) {
                        val peopleCandidates = peopleEntries
                            .sortedByDescending { it.frequency }
                            .take(initialQueryLimit - results.size)
                            .map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                        results.addAll(peopleCandidates)
                    }
                }
                
                // 4. 如果仍然没有足够结果，查询其他所有词典
                if (results.size < initialQueryLimit) {
                    val otherEntries = realm.query<Entry>(
                        "pinyin == $0 AND type != $1 AND type != $2 AND type != $3", 
                        syllables.joinToString(" "),
                        "base", "place", "people"
                    ).find()
                    Timber.d("其他词典匹配结果: ${otherEntries.size}个")
                    
                    if (otherEntries.isNotEmpty()) {
                        val otherCandidates = otherEntries
                            .sortedByDescending { it.frequency }
                            .take(initialQueryLimit - results.size)
                            .map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                        results.addAll(otherCandidates)
                    }
                }
                
                // 5. 如果依然没有找到完全匹配的结果，尝试使用BEGINSWITH查询
                if (results.isEmpty()) {
                    Timber.d("精确匹配无结果，尝试前缀匹配")
                    val prefixEntries = realm.query<Entry>(
                        "pinyin BEGINSWITH $0", 
                        syllables.joinToString(" ")
                    ).find()
                    Timber.d("前缀匹配结果: ${prefixEntries.size}个")
                    
                    if (prefixEntries.isNotEmpty()) {
                        val prefixCandidates = prefixEntries
                            .sortedByDescending { it.frequency }
                            .take(initialQueryLimit)
                            .map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                        results.addAll(prefixCandidates)
                    }
                }
                
                // 6. 只有在以上所有方法都无法找到候选词的情况下，再使用笛卡尔积逐字组合
                if (results.isEmpty() && syllables.size > 1) {
                    Timber.d("无法找到词典匹配，使用音节组合")
                    
                    // 查询每个音节对应的字
                    val entriesForSyllables = syllables.map { syllable ->
                        val entries = realm.query<Entry>("pinyin == $0 AND type == $1", syllable, "chars")
                            .find()
                            .sortedByDescending { it.frequency }
                            .take(5) // 限制每个音节最多取前5个
                        Timber.d("音节'$syllable'匹配结果: ${entries.size}个")
                        entries
                    }
                    
                    // 如果每个音节都有匹配的字，才进行组合
                    if (!entriesForSyllables.any { it.isEmpty() }) {
                        // 执行笛卡尔积，生成组合
                        val combinations = cartesianProduct(entriesForSyllables)
                            .take(initialQueryLimit)
                            
                        Timber.d("组合数量: ${combinations.size}个")
                        
                        // 转换为候选词
                        val candidates = combinations.map { entries ->
                            val word = entries.joinToString("") { it.word }
                            val pinyin = entries.joinToString(" ") { it.pinyin }
                            val frequency = entries.sumOf { it.frequency }
                            
                            Candidate(
                                word = word,
                                pinyin = pinyin,
                                frequency = frequency,
                                type = "音节组合",
                                matchType = Candidate.MatchType.SYLLABLE_SPLIT
                            )
                        }
                        
                        results.addAll(candidates)
                    }
                }
                
                filterDuplicates(results)
            } catch (e: Exception) {
                Timber.e(e, "查询音节拆分候选词异常", e)
                emptyList()
            }
        }
        
        // 更新UI显示快速查询结果
        val sortedQuickResults = sortCandidates(quickResults)
        updateCandidateView(sortedQuickResults)
        
        // 异步查询更多结果
        if (!currentQueryJob?.isActive!!) return
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val moreResults = mutableListOf<Candidate>()
                
                // 查询更多结果，跳过已经查询的结果
                val allEntries = realm.query<Entry>("pinyin == $0", syllables.joinToString(" "))
                    .find()
                    .sortedByDescending { it.frequency }
                    .drop(initialQueryLimit) // 跳过已处理的
                    .take(100) // 额外处理一批
                    .map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                
                moreResults.addAll(allEntries)
                
                // 如果额外的结果为空，尝试模糊匹配
                if (moreResults.isEmpty()) {
                    val fuzzyEntries = realm.query<Entry>("pinyin CONTAINS $0", syllables.joinToString(" "))
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(100)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                    
                    moreResults.addAll(fuzzyEntries)
                }
                
                val moreUniqueResults = filterDuplicates(moreResults)
                
                // 合并结果并更新UI
                if (moreUniqueResults.isNotEmpty() && currentQueryJob?.isActive!!) {
                    val combinedResults = (quickResults + moreUniqueResults).distinctBy { it.word }
                    val sortedResults = sortCandidates(combinedResults)
                    updateCandidateView(sortedResults)
                }
            } catch (e: Exception) {
                Timber.e(e, "异步处理音节拆分候选词异常", e)
            }
        }
    }

    /**
     * 查询首字母缩写阶段的候选词
     */
    private suspend fun queryAcronymCandidates(input: String) {
        val realm = ShenjiApplication.realm
        Timber.d("查询首字母缩写'$input'对应的条目")

        // 快速查询前20个结果
        val quickResults = withContext(Dispatchers.IO) {
            try {
                val entries = realm.query<Entry>("initialLetters == $0", input)
                    .find()
                    .sortedByDescending { it.frequency }
                    .take(initialQueryLimit)
                    .map { Candidate.fromEntry(it, Candidate.MatchType.ACRONYM) }
                
                Timber.d("首字母缩写匹配结果: ${entries.size}个")
                filterDuplicates(entries)
            } catch (e: Exception) {
                Timber.e(e, "查询首字母缩写候选词异常")
                emptyList()
            }
        }
        
        // 更新UI显示快速查询结果
        val sortedQuickResults = sortCandidates(quickResults)
        updateCandidateView(sortedQuickResults)
        
        // 异步查询更多结果
        if (!currentQueryJob?.isActive!!) return
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val moreResults = realm.query<Entry>("initialLetters == $0", input)
                    .find()
                    .sortedByDescending { it.frequency }
                    .drop(initialQueryLimit) // 跳过已查询的部分
                    .take(100) // 额外查询更多结果
                    .map { Candidate.fromEntry(it, Candidate.MatchType.ACRONYM) }
                
                val moreUniqueResults = filterDuplicates(moreResults)
                
                // 合并结果并更新UI
                if (moreUniqueResults.isNotEmpty() && currentQueryJob?.isActive!!) {
                    val combinedResults = (quickResults + moreUniqueResults).distinctBy { it.word }
                    val sortedResults = sortCandidates(combinedResults)
                    updateCandidateView(sortedResults)
                }
            } catch (e: Exception) {
                Timber.e(e, "异步处理首字母缩写候选词异常")
            }
        }
    }
    
    /**
     * 执行笛卡尔积，生成所有可能的组合
     */
    private fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
        if (lists.isEmpty()) return emptyList()
        if (lists.size == 1) return lists[0].map { listOf(it) }
        
        val result = mutableListOf<List<T>>()
        val head = lists[0]
        val tail = lists.subList(1, lists.size)
        val tailProduct = cartesianProduct(tail)
        
        for (headItem in head) {
            for (tailItems in tailProduct) {
                result.add(listOf(headItem) + tailItems)
            }
        }
        
        return result
    }
    
    // 更新候选词视图
    private suspend fun updateCandidateView(candidates: List<Candidate>) {
        withContext(Dispatchers.Main) {
            candidatesView.removeAllViews()
            
            if (candidates.isNotEmpty()) {
                for ((index, candidate) in candidates.withIndex()) {
                    val textView = TextView(this@ShenjiInputMethodService)
                    textView.text = candidate.word
                    textView.setTextAppearance(R.style.CandidateWord)
                    
                    // 设置左右margin，确保有适当的间距
                    val layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                    layoutParams.marginStart = resources.getDimensionPixelSize(R.dimen.candidate_word_margin)
                    layoutParams.marginEnd = resources.getDimensionPixelSize(R.dimen.candidate_word_margin)
                    
                    // 设置点击监听器
                    textView.setOnClickListener {
                        commitText(candidate.word)
                    }
                    
                    candidatesView.addView(textView, layoutParams)
                    
                    // 除了最后一个候选词，其他候选词后添加分隔线
                    if (index < candidates.size - 1) {
                        val divider = View(this@ShenjiInputMethodService)
                        val dividerParams = LinearLayout.LayoutParams(
                            1,
                            resources.getDimensionPixelSize(R.dimen.candidate_divider_height)
                        )
                        divider.layoutParams = dividerParams
                        divider.setBackgroundColor(resources.getColor(R.color.divider, null))
                        candidatesView.addView(divider)
                    }
                }
            } else {
                // 无候选词显示提示
                val textView = TextView(this@ShenjiInputMethodService)
                textView.text = getString(R.string.no_candidates)
                textView.setTextAppearance(R.style.CandidateWord)
                candidatesView.addView(textView)
            }
        }
    }
    
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Timber.d("输入法服务生命周期: onStartInputView - 输入视图已启动")
        Timber.d("输入视图信息: inputType=${info?.inputType}, restarting=$restarting")
        
        // 记录更多输入类型信息用于调试
        val inputTypeStr = when (info?.inputType?.and(InputType.TYPE_MASK_CLASS)) {
            InputType.TYPE_CLASS_TEXT -> "TYPE_CLASS_TEXT"
            InputType.TYPE_CLASS_NUMBER -> "TYPE_CLASS_NUMBER"
            InputType.TYPE_CLASS_PHONE -> "TYPE_CLASS_PHONE"
            InputType.TYPE_CLASS_DATETIME -> "TYPE_CLASS_DATETIME"
            else -> "UNKNOWN"
        }
        Timber.d("输入类型: $inputTypeStr")
        
        // 重置状态
        composingText.clear()
        
        // 安全检查视图是否已初始化
        if (areViewComponentsInitialized()) {
            hideCandidates()
            Timber.d("输入视图初始化完成，隐藏候选词区域")
        } else {
            Timber.e("输入视图组件未完全初始化")
        }
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        Timber.d("输入法服务生命周期: onFinishInput - 输入已结束")
        
        // 重置状态
        composingText.clear()
        
        // 取消当前查询任务
        currentQueryJob?.cancel()
        currentQueryJob = null
        
        // 安全检查视图是否已初始化
        if (areViewComponentsInitialized()) {
            hideCandidates()
        } else {
            Timber.e("输入视图组件未完全初始化，无法正确清理")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 取消所有协程
        coroutineScope.cancel()
        Timber.d("输入法服务已销毁")
    }
} 