package com.shenji.aikeyboard.keyboard

import android.animation.AnimatorInflater
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
    
    // 拼音显示TextView
    private lateinit var pinyinDisplay: TextView
    
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
    
    // 候选词缓存，缓存常用音节的查询结果
    private val candidateCache = mutableMapOf<String, List<Candidate>>()
    
    // 缓存的最大大小
    private val MAX_CACHE_SIZE = 30
    
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
            // 初始化拼音显示区域
            pinyinDisplay = keyboardView.findViewById(R.id.pinyin_display)
            
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
        // 数字键
        val numberIds = listOf(
            R.id.key_0, R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4,
            R.id.key_5, R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9
        )
        
        numberIds.forEach { id ->
            keyboardView.findViewById<Button>(id)?.setOnClickListener { v ->
                val number = (v as Button).text.toString()
                if (composingText.isEmpty()) {
                    // 没有拼音输入，直接输入数字
                    commitText(number)
                } else {
                    // 有拼音输入，追加到拼音中
                    onInputLetter(number)
                }
            }
        }
        
        // 删除键
        keyboardView.findViewById<Button>(R.id.key_delete)?.setOnClickListener {
            onDelete()
        }
        
        // 空格键
        keyboardView.findViewById<Button>(R.id.key_space)?.setOnClickListener {
            onSpace()
        }
        
        // 空格键长按
        keyboardView.findViewById<Button>(R.id.key_space)?.setOnLongClickListener {
            Toast.makeText(this, "语音输入功能即将上线", Toast.LENGTH_SHORT).show()
            true
        }
        
        // 回车键
        keyboardView.findViewById<Button>(R.id.key_enter)?.setOnClickListener {
            onEnter()
        }
        
        // 符号键
        keyboardView.findViewById<Button>(R.id.key_symbol)?.setOnClickListener {
            // 暂不实现符号键盘
            Toast.makeText(this, "符号键盘功能开发中", Toast.LENGTH_SHORT).show()
            Timber.d("符号键盘暂未实现")
        }
        
        // 分词键
        keyboardView.findViewById<Button>(R.id.key_split)?.setOnClickListener {
            if (composingText.isNotEmpty()) {
                // 尝试将当前输入拆分为音节
                val syllables = pinyinSplitter.splitPinyin(composingText.toString())
                if (syllables.isNotEmpty()) {
                    // 更新组合文本中的拼音为分词后的拼音
                    composingText.clear()
                    composingText.append(syllables.joinToString(" "))
                    // 更新输入框中显示的拼音
                    currentInputConnection?.setComposingText(composingText, 1)
                    // 更新拼音显示
                    updatePinyinDisplay(composingText.toString())
                    // 重新处理输入
                    processInput(composingText.toString())
                    Toast.makeText(this, "已分词: ${syllables.joinToString(" ")}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "无法分词当前输入", Toast.LENGTH_SHORT).show()
                }
            } else {
                commitText("|")
            }
        }
        
        // 句号键
        keyboardView.findViewById<Button>(R.id.key_period)?.setOnClickListener {
            commitText(".")
        }
        
        // Shift键
        keyboardView.findViewById<Button>(R.id.key_shift)?.setOnClickListener {
            // 暂不实现大小写切换
            Toast.makeText(this, "大小写切换功能开发中", Toast.LENGTH_SHORT).show()
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
        
        // 更新拼音显示
        updatePinyinDisplay(composingText.toString())
        
        // 添加日志跟踪拼音显示
        Timber.d("输入拼音: ${composingText}, 格式化后: ${formatPinyin(composingText.toString())}")
        
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
                
                // 更新拼音显示
                updatePinyinDisplay(composingText.toString())
            }
        } else {
            // 如果没有拼音，执行标准删除操作
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

        // 1. 如果输入只有一个字母，直接归类为首字母阶段
        if (input.length == 1 && input.matches(Regex("^[a-z]$"))) {
            return InputStage.INITIAL_LETTER
        }

        // 2. 如果输入完全匹配一个拼音音节，则为拼音补全阶段
        if (isValidPinyin(input)) {
            return InputStage.PINYIN_COMPLETION
        }

        // 3. 优先检查是否可以拆分为有效音节
        if (canSplitToValidSyllables(input)) {
            return InputStage.SYLLABLE_SPLIT
        }
        
        // 4. 尝试寻找部分音节匹配
        val partialMatch = findPartialMatch(input)
        if (partialMatch.isNotEmpty()) {
            // 找到部分匹配，仍然归类为音节拆分阶段
            return InputStage.SYLLABLE_SPLIT
        }

        // 5. 所有音节匹配尝试都失败，则视为首字母缩写阶段
        return InputStage.ACRONYM
    }

    /**
     * 寻找部分音节匹配（例如"nih"拆分为"ni"+"h"）
     */
    private fun findPartialMatch(input: String): List<String> {
        // 查找最长的有效音节前缀
        for (i in input.length downTo 1) {
            val prefix = input.substring(0, i)
            if (pinyinSplitter.getPinyinSyllables().contains(prefix)) {
                // 找到有效前缀
                val result = mutableListOf(prefix)
                // 将剩余部分作为一个单独部分（可能是下一个音节的开始）
                if (i < input.length) {
                    result.add(input.substring(i))
                }
                return result
            }
        }
        return emptyList()
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
        
        // 清空候选词区域
        coroutineScope.launch {
            withContext(Dispatchers.Main) {
                candidatesView.removeAllViews()
                
                // 对于长输入，添加一个"加载中"提示
                if (input.length > 3) {
                    val loadingView = TextView(this@ShenjiInputMethodService)
                    loadingView.text = "查询中..."
                    loadingView.setTextAppearance(R.style.CandidateWord)
                    candidatesView.addView(loadingView)
                }
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
                        
                        // 优先尝试使用拼音分词器拆分
                        val syllables = pinyinSplitter.splitPinyin(input)
                        
                        // 如果常规拆分失败，尝试部分匹配
                        if (syllables.isEmpty()) {
                            val partialMatch = findPartialMatch(input)
                            if (partialMatch.isNotEmpty()) {
                                Timber.d("常规拆分失败，使用部分匹配: ${partialMatch.joinToString("+")}")
                                querySplitCandidates(partialMatch)
                            } else {
                                Timber.d("无法拆分音节")
                                // 显示无法拆分提示
                                withContext(Dispatchers.Main) {
                                    candidatesView.removeAllViews()
                                    val textView = TextView(this@ShenjiInputMethodService)
                                    textView.text = "无法拆分音节"
                                    textView.setTextAppearance(R.style.CandidateWord)
                                    candidatesView.addView(textView)
                                }
                            }
                        } else {
                            Timber.d("常规拆分成功: ${syllables.joinToString("+")}")
                            querySplitCandidates(syllables)
                        }
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
        
        // 检查是否是单个有效音节
        val isSingleSyllable = isValidPinyin(input) && !input.contains(" ")
        Timber.d("处理拼音输入: '$input', 是否为单个音节: $isSingleSyllable")

        // 缓存命中检查（针对单音节）
        if (isSingleSyllable && candidateCache.containsKey(input)) {
            Timber.d("缓存命中: '$input'")
            val cachedResults = candidateCache[input] ?: emptyList()
            if (cachedResults.isNotEmpty()) {
                // 直接使用缓存的结果
                updateCandidateView(cachedResults)
                return
            }
        }

        // 快速查询前20个结果
        val quickResults = withContext(Dispatchers.IO) {
            try {
                // 如果是单个有效音节，优先精确匹配
                if (isSingleSyllable) {
                    Timber.d("检测到单个有效音节'$input'，执行精确匹配")
                    val singleChars = realm.query<Entry>("type == $0 AND pinyin == $1",
                        "chars", input)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(20)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.PINYIN_PREFIX) }
                    
                    Timber.d("单字精确匹配结果: ${singleChars.size}个")
                    
                    // 对于单音节，直接返回精确匹配结果
                    filterDuplicates(singleChars)
                } else {
                    // 原有逻辑保持不变，先查询单字词典
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
                }
            } catch (e: Exception) {
                Timber.e(e, "查询拼音补全候选词异常")
                emptyList()
            }
        }
        
        // 记录结果数量
        Timber.d("拼音补全查询结果: ${quickResults.size}个候选词")
        
        // 更新UI显示快速查询结果
        val sortedQuickResults = sortCandidates(quickResults)
        updateCandidateView(sortedQuickResults)
        
        // 更新缓存（针对单音节），只缓存有结果的查询
        if (isSingleSyllable && sortedQuickResults.isNotEmpty()) {
            Timber.d("更新缓存: '$input' (${sortedQuickResults.size}个候选词)")
            
            // 如果缓存过大，清理最早的条目
            if (candidateCache.size >= MAX_CACHE_SIZE) {
                val oldestKey = candidateCache.keys.firstOrNull()
                if (oldestKey != null) {
                    candidateCache.remove(oldestKey)
                    Timber.d("缓存已满，移除最早的缓存项: '$oldestKey'")
                }
            }
            
            // 添加到缓存
            candidateCache[input] = sortedQuickResults
        }
        
        // 对于单音节查询，已经足够，不需要异步查询更多结果
        if (isSingleSyllable || !currentQueryJob?.isActive!!) return
        
        // 非单音节情况下才进行异步查询更多结果
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
        
        // 将音节连接为完整的拼音字符串（带空格）
        val fullPinyin = syllables.joinToString(" ")
        Timber.d("完整拼音查询: '$fullPinyin'")
        
        // 快速查询
        val quickResults = withContext(Dispatchers.IO) {
            try {
                // 首先尝试精确匹配完整拼音
                val exactEntries = realm.query<Entry>("pinyin == $0", fullPinyin)
                    .find()
                
                Timber.d("完整拼音精确匹配结果: ${exactEntries.size}个")
                
                // 如果精确匹配没有结果，尝试前缀匹配
                if (exactEntries.isEmpty() && syllables.size >= 2) {
                    Timber.d("精确匹配无结果，尝试前缀匹配")
                    // 查询以这些音节开头的词条
                    val prefixEntries = realm.query<Entry>("pinyin BEGINSWITH $0", fullPinyin)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(initialQueryLimit)
                    
                    Timber.d("前缀匹配结果: ${prefixEntries.size}个")
                    prefixEntries.map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                } else {
                    // 使用精确匹配结果
                    exactEntries
                        .sortedByDescending { it.frequency }
                        .take(initialQueryLimit)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                }
            } catch (e: Exception) {
                Timber.e(e, "查询音节拆分候选词异常: ${e.message}")
                emptyList()
            }
        }

        // 记录查询结果
        Timber.d("音节拆分查询结果: ${quickResults.size}个候选词")
        
        // 去重并更新UI
        val filteredResults = filterDuplicates(quickResults)
        val sortedResults = sortCandidates(filteredResults)
        updateCandidateView(sortedResults)
        
        // 如果快速查询结果不足，且当前查询未取消，异步查询更多结果
        if (filteredResults.size < 5 && currentQueryJob?.isActive!!) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 尝试笛卡尔积组合（仅当结果太少时）
                    if (syllables.size > 1 && filteredResults.isEmpty()) {
                        Timber.d("结果不足，尝试音节组合")
                        
                        // 查询每个音节对应的字
                        val entriesForSyllables = syllables.map { syllable ->
                            realm.query<Entry>("pinyin == $0 AND type == $1", syllable, "chars")
                                .find()
                                .sortedByDescending { it.frequency }
                                .take(3) // 减少每个音节的候选项，避免组合爆炸
                        }
                        
                        // 只有当每个音节都有匹配的字时才组合
                        if (entriesForSyllables.none { it.isEmpty() }) {
                            // 组合前3个音节（避免组合过多）
                            val combinations = cartesianProduct(entriesForSyllables.take(3))
                                .take(20)
                                .map { entries ->
                                    val word = entries.joinToString("") { it.word }
                                    val frequency = entries.sumOf { it.frequency }
                                    Candidate(
                                        word = word, 
                                        frequency = frequency,
                                        matchType = Candidate.MatchType.SYLLABLE_SPLIT
                                    )
                                }
                            
                            if (combinations.isNotEmpty() && currentQueryJob?.isActive!!) {
                                Timber.d("生成${combinations.size}个组合候选词")
                                val allResults = (filteredResults + combinations).distinctBy { it.word }
                                val finalResults = sortCandidates(allResults)
                                updateCandidateView(finalResults)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "异步处理音节拆分候选词异常")
                }
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
                    
                    // 第一个候选词默认蓝色背景
                    if (index == 0) {
                        textView.setBackgroundResource(R.drawable.candidate_word_selected_bg)
                        textView.setTextColor(resources.getColor(android.R.color.white, null))
                    }
                    
                    // 设置点击监听器
                    textView.setOnClickListener { view ->
                        // 播放闪烁动画
                        val animator = AnimatorInflater.loadAnimator(
                            this@ShenjiInputMethodService,
                            R.anim.candidate_flash
                        )
                        animator.setTarget(view)
                        animator.start()
                        
                        // 提交文本
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
    
    // 更新拼音显示
    private fun updatePinyinDisplay(pinyin: String) {
        // 将拼音格式化，音节之间用单引号分隔
        val formattedPinyin = formatPinyin(pinyin)
        // 无论如何，都显示原始输入，确保拼音显示区域不为空
        pinyinDisplay.text = formattedPinyin
    }
    
    // 格式化拼音，在音节之间添加单引号
    private fun formatPinyin(pinyin: String): String {
        // 使用拼音分词器拆分拼音
        val syllables = pinyinSplitter.splitPinyin(pinyin)
        // 如果能正确拆分音节，则用单引号连接
        if (syllables.isNotEmpty()) {
            return syllables.joinToString("'")
        }
        // 否则直接返回原始输入
        return pinyin
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
        Timber.d("神迹输入法服务已销毁")
        // 清理资源
        coroutineScope.cancel()
        // 清理缓存
        candidateCache.clear()
        Timber.d("输入法服务生命周期: onDestroy - 已清理资源")
    }
    
    /**
     * 定期清理不需要的缓存，避免占用过多内存
     * 每50次查询后执行一次清理
     */
    private fun cleanupCacheIfNeeded() {
        // 只保留最常用的10个缓存项
        if (candidateCache.size > 10) {
            val keysToRemove = candidateCache.keys.sorted().dropLast(10)
            for (key in keysToRemove) {
                candidateCache.remove(key)
            }
            Timber.d("已清理${keysToRemove.size}个缓存项，剩余${candidateCache.size}个")
        }
    }
} 