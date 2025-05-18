package com.shenji.aikeyboard.keyboard

import android.animation.AnimatorInflater
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
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
import java.util.*

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
    
    // 工具栏视图
    private lateinit var toolbarView: LinearLayout
    
    // 拼音显示TextView
    private lateinit var pinyinDisplay: TextView
    
    // 应用名称显示TextView
    private lateinit var appNameDisplay: TextView
    
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
    
    // 长按删除键自动删除的处理器和任务
    private val deleteHandler = Handler(Looper.getMainLooper())
    private val deleteRunnable = object : Runnable {
        override fun run() {
            onDelete()
            // 持续触发删除操作，实现长按连续删除效果
            deleteHandler.postDelayed(this, DELETE_REPEAT_DELAY)
        }
    }
    
    // 长按删除键的延迟时间（毫秒）
    private val DELETE_INITIAL_DELAY = 400L  // 长按后首次触发的延迟
    private val DELETE_REPEAT_DELAY = 50L   // 连续触发的间隔
    
    // 当前输入阶段
    private enum class InputStage {
        INITIAL_LETTER,      // 首字母阶段
        PINYIN_COMPLETION,   // 拼音补全阶段
        SYLLABLE_SPLIT,      // 音节拆分阶段
        ACRONYM,             // 首字母缩写阶段
        MIXED_INITIAL_SYLLABLE, // 首字母+音节混合模式
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
            appNameDisplay = keyboardView.findViewById(R.id.app_name_display)
            // 初始化工具栏
            toolbarView = keyboardView.findViewById(R.id.toolbar_view)
            
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
        
        // 添加删除键长按监听
        keyboardView.findViewById<Button>(R.id.key_delete)?.setOnLongClickListener { 
            // 启动长按删除定时器
            deleteHandler.postDelayed(deleteRunnable, DELETE_INITIAL_DELAY)
            true
        }
        
        // 删除键触摸监听，用于检测长按结束
        keyboardView.findViewById<Button>(R.id.key_delete)?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 停止自动删除
                    deleteHandler.removeCallbacks(deleteRunnable)
                }
            }
            false // 返回false以不干扰点击事件
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
                // 如果拼音为空，只隐藏候选词区域，保留拼音栏
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
        // 清空拼音显示区域
        if (areViewComponentsInitialized()) {
            pinyinDisplay.text = ""
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
            // 显示候选词区域时隐藏工具栏
            toolbarView.visibility = View.GONE
        }
    }
    
    // 隐藏候选词区域
    private fun hideCandidates() {
        if (areViewComponentsInitialized()) {
            // 只隐藏候选词部分，保留拼音栏
            defaultCandidatesView.visibility = View.GONE
            // 隐藏候选词区域时显示工具栏
            toolbarView.visibility = View.VISIBLE
        }
    }
    
    // 完全隐藏整个候选词容器（包括拼音栏）
    private fun hideEntireCandidateContainer() {
        if (areViewComponentsInitialized()) {
            candidatesContainer.visibility = View.GONE
            // 工具栏也需要隐藏，因为它是候选词容器的一部分
            toolbarView.visibility = View.GONE
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
     * 对候选词进行排序处理
     * @param candidates 候选词列表
     * @param syllableCount 音节数量，默认为1（单个音节）
     * @return 排序后的候选词列表
     */
    private fun sortCandidates(candidates: List<Candidate>, syllableCount: Int = 1): List<Candidate> {
        if (candidates.isEmpty()) return emptyList()
        
        // 根据音节数量进行分组
        // 1. 与音节数量完全匹配的词组（优先级最高）
        val matchingSyllableWords = candidates.filter { it.word.length == syllableCount && it.word.length > 1 }
            .sortedByDescending { it.frequency }
            
        // 2. 单字（当音节数量为1时，这部分会与上面重叠）
        val singleChars = candidates.filter { it.word.length == 1 }
            .sortedByDescending { it.frequency }
            .take(maxSingleCharCount) // 限制单字数量
            
        // 3. 其他词组（优先级最低）
        val otherPhrases = candidates.filter { it.word.length > 1 && it.word.length != syllableCount }
            .sortedByDescending { it.frequency }
            
        // 合并结果：音节匹配词组 > 单字 > 其他词组
        // 当syllableCount=1时，单字会优先（因为它们同时也是匹配音节数量的词）
        return if (syllableCount > 1) {
            matchingSyllableWords + singleChars + otherPhrases
        } else {
            singleChars + otherPhrases
        }
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

        // 2. 检查整个输入是否是一个完整的有效音节
        val isFullSyllable = isValidPinyin(input)
        
        // 3. 如果是完整有效音节，优先作为拼音补全阶段处理
        if (isFullSyllable) {
            Timber.d("输入'$input'是一个完整有效音节，优先按拼音补全阶段处理")
            return InputStage.PINYIN_COMPLETION
        }
        
        // 4. 检查是否是"首字母+音节"混合模式
        val mixedSplit = pinyinSplitter.checkMixedInitialAndSyllable(input)
        if (mixedSplit.isNotEmpty()) {
            Timber.d("输入'$input'符合首字母+音节模式: ${mixedSplit.joinToString("+")}")
            return InputStage.MIXED_INITIAL_SYLLABLE
        }
        
        // 5. 检查是否可以拆分为有效音节
        val multipleSplits = pinyinSplitter.getMultipleSplits(input)
        if (multipleSplits.isNotEmpty()) {
            // 记录拆分结果到日志
            multipleSplits.forEachIndexed { index, split ->
                Timber.d("拆分方式${index+1}: ${split.joinToString("+")}")
            }
            Timber.d("输入'$input'可以拆分为多种方式，归类为音节拆分阶段")
            return InputStage.SYLLABLE_SPLIT
        }
        
        // 6. 如果无法拆分为任何有效音节，检查是否有部分匹配
        val partialMatch = findPartialMatch(input)
        if (partialMatch.isNotEmpty()) {
            Timber.d("输入'$input'无法完全拆分，但找到部分匹配: ${partialMatch.joinToString("+")}")
            return InputStage.SYLLABLE_SPLIT
        }

        // 7. 所有音节匹配尝试都失败，则视为首字母缩写阶段
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
                        // 更新拼音显示为完整音节
                        updatePinyinDisplay(input, forceFullSyllable = true)
                        queryPinyinCandidates(input)
                    }
                    InputStage.MIXED_INITIAL_SYLLABLE -> {
                        Timber.d("当前阶段: 首字母+音节混合阶段")
                        val mixedSplit = pinyinSplitter.checkMixedInitialAndSyllable(input)
                        updatePinyinDisplay(input, mixedSplit)
                        queryMixedInitialSyllableCandidates(input, mixedSplit)
                    }
                    InputStage.SYLLABLE_SPLIT -> {
                        Timber.d("当前阶段: 音节拆分阶段")
                        
                        // 获取多种拆分方式
                        val multipleSplits = pinyinSplitter.getMultipleSplits(input)
                        
                        // 如果有多种拆分方式，使用多种拆分查询
                        if (multipleSplits.isNotEmpty()) {
                            // 更新拼音显示，使用第一种拆分方式（优先级最高）
                            updatePinyinDisplay(input, multipleSplits.first())
                            queryWithMultipleSplits(input, multipleSplits)
                        } else {
                            // 如果没有找到拆分方式，尝试部分匹配
                            val partialMatch = findPartialMatch(input)
                            if (partialMatch.isNotEmpty()) {
                                Timber.d("常规拆分失败，使用部分匹配: ${partialMatch.joinToString("+")}")
                                updatePinyinDisplay(input, partialMatch)
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
        
        // 检查原始输入是否本身就是一个有效音节
        val originalInput = if (syllables.size > 1) {
            syllables.joinToString("")
        } else {
            syllables[0]
        }
        
        val isOriginalInputValidSyllable = pinyinSplitter.getPinyinSyllables().contains(originalInput)
        
        // 检查是否是连续输入形成的新音节情况
        val lastValidSyllable = findLastValidSyllable(syllables)
        
        // 快速查询
        val quickResults = withContext(Dispatchers.IO) {
            try {
                // 存储所有查询结果
                val allResults = mutableListOf<Candidate>()
                
                // 1. 如果检测到连续输入形成的新音节，优先查询该音节的候选词
                if (lastValidSyllable.isNotEmpty()) {
                    Timber.d("查询最后形成的有效音节: '$lastValidSyllable'")
                    val lastSyllableEntries = realm.query<Entry>("pinyin == $0", lastValidSyllable)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(initialQueryLimit / 2)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.PINYIN_PREFIX) }
                    
                    Timber.d("最后有效音节匹配结果: ${lastSyllableEntries.size}个")
                    // 添加最高优先级
                    allResults.addAll(lastSyllableEntries)
                }
                
                // 2. 尝试精确匹配完整拼音（拆分后的音节组合）
                val exactEntries = realm.query<Entry>("pinyin == $0", fullPinyin)
                    .find()
                
                Timber.d("完整拼音精确匹配结果: ${exactEntries.size}个")
                
                // 添加精确匹配结果
                if (exactEntries.isNotEmpty()) {
                    val remainingLimit = (initialQueryLimit - allResults.size).coerceAtLeast(0)
                    if (remainingLimit > 0) {
                        allResults.addAll(
                            exactEntries
                                .sortedByDescending { it.frequency }
                                .take(remainingLimit / 2) // 为其他查询方式预留空间
                                .map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                        )
                    }
                }
                
                // 3. 如果原始输入本身是一个有效的拼音音节，查询它作为单个音节的结果
                if (isOriginalInputValidSyllable && lastValidSyllable != originalInput) {
                    Timber.d("原始输入'$originalInput'本身是个有效音节，尝试作为整体查询")
                    val remainingLimit = (initialQueryLimit - allResults.size).coerceAtLeast(0)
                    if (remainingLimit > 0) {
                        val originalEntries = realm.query<Entry>("pinyin == $0", originalInput)
                            .find()
                            .sortedByDescending { it.frequency }
                            .take(remainingLimit / 2)
                            .map { Candidate.fromEntry(it, Candidate.MatchType.PINYIN_PREFIX) }
                        
                        Timber.d("原始输入作为整体匹配结果: ${originalEntries.size}个")
                        allResults.addAll(originalEntries)
                    }
                }
                
                // 4. 如果前面的结果不足，尝试前缀匹配
                val remainingLimit = (initialQueryLimit - allResults.size).coerceAtLeast(0)
                if (remainingLimit > 0 && syllables.size >= 2) {
                    Timber.d("已有结果不足，尝试前缀匹配")
                    // 查询以这些音节开头的词条
                    val prefixEntries = realm.query<Entry>("pinyin BEGINSWITH $0", fullPinyin)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(remainingLimit)
                    
                    Timber.d("前缀匹配结果: ${prefixEntries.size}个")
                    allResults.addAll(
                        prefixEntries.map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                    )
                }
                
                allResults
            } catch (e: Exception) {
                Timber.e(e, "查询音节拆分候选词异常: ${e.message}")
                emptyList()
            }
        }

        // 记录查询结果
        Timber.d("音节拆分查询结果: ${quickResults.size}个候选词")
        
        // 去重并更新UI
        val filteredResults = filterDuplicates(quickResults)
        val sortedResults = sortCandidates(filteredResults, syllables.size)
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
                                val finalResults = sortCandidates(allResults, syllables.size)
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
     * 查找最后形成的有效音节
     * 例如用户输入"zaiy"，拆分为["zai", "y"]，可能是用户先输入了"zai"，
     * 然后又输入了"y"，这种情况下最后形成的有效音节是"y"
     */
    private fun findLastValidSyllable(syllables: List<String>): String {
        if (syllables.isEmpty()) return ""
        
        // 检查最后一个音节是否有效
        val lastSyllable = syllables.last()
        if (isValidPinyin(lastSyllable)) {
            return lastSyllable
        }
        
        // 如果最后一个音节不是有效音节，检查倒数第二个
        if (syllables.size >= 2) {
            val secondLastSyllable = syllables[syllables.size - 2]
            if (isValidPinyin(secondLastSyllable)) {
                return secondLastSyllable
            }
        }
        
        // 还可以检查原始输入的末尾部分是否形成有效音节
        val input = syllables.joinToString("")
        val lastTwoChars = if (input.length >= 2) input.substring(input.length - 2) else ""
        val lastThreeChars = if (input.length >= 3) input.substring(input.length - 3) else ""
        val lastFourChars = if (input.length >= 4) input.substring(input.length - 4) else ""
        
        return when {
            isValidPinyin(lastFourChars) -> lastFourChars
            isValidPinyin(lastThreeChars) -> lastThreeChars
            isValidPinyin(lastTwoChars) -> lastTwoChars
            else -> ""
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
                    
                    // 第一个候选词使用蓝色文字（不使用蓝色背景）
                    if (index == 0) {
                        textView.setTextColor(resources.getColor(R.color.colorPrimary, null))
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
    private fun updatePinyinDisplay(pinyin: String, forceFullSyllable: Boolean = false) {
        // 对于需要强制显示为完整音节的情况，直接显示原始输入
        if (forceFullSyllable) {
            pinyinDisplay.text = pinyin
            return
        }
        
        // 将拼音格式化，音节之间用单引号分隔
        val formattedPinyin = formatPinyin(pinyin)
        // 无论如何，都显示原始输入，确保拼音显示区域不为空
        pinyinDisplay.text = formattedPinyin
    }
    
    // 使用指定的拆分结果更新拼音显示
    private fun updatePinyinDisplay(pinyin: String, syllables: List<String>) {
        // 将音节用单引号连接
        val formatted = syllables.joinToString("'")
        pinyinDisplay.text = formatted
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
    
    /**
     * 使用多种拆分方式查询候选词
     */
    private suspend fun queryWithMultipleSplits(input: String, multipleSplits: List<List<String>>) {
        if (multipleSplits.isEmpty()) {
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
        Timber.d("多种拆分方式, 共${multipleSplits.size}种")
        
        // 快速查询
        val quickResults = withContext(Dispatchers.IO) {
            try {
                // 存储所有查询结果
                val allResults = mutableListOf<Candidate>()
                
                // 查询限制数量
                val limitPerMethod = (initialQueryLimit / (multipleSplits.size + 1)).coerceAtLeast(3)
                
                // 1. 首先检查输入是否接近一个完整音节
                // 例如"zai"或"zaih"
                val closeToFullSyllable = findClosestValidSyllable(input)
                if (closeToFullSyllable.isNotEmpty()) {
                    Timber.d("输入'$input'接近完整音节'$closeToFullSyllable'，优先查询")
                    val fullSyllableEntries = realm.query<Entry>("pinyin == $0", closeToFullSyllable)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(limitPerMethod)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.PINYIN_PREFIX) }
                    
                    Timber.d("接近完整音节匹配结果: ${fullSyllableEntries.size}个")
                    allResults.addAll(fullSyllableEntries)
                }
                
                // 2. 依次处理每种拆分方式
                for ((index, syllables) in multipleSplits.withIndex()) {
                    // 将音节连接为完整的拼音字符串（带空格）
                    val fullPinyin = syllables.joinToString(" ")
                    Timber.d("处理拆分方式${index+1}: ${syllables.joinToString("+")}, 查询'$fullPinyin'")
                    
                    // a. 首先尝试精确匹配完整拼音
                    val exactEntries = realm.query<Entry>("pinyin == $0", fullPinyin)
                        .find()
                    
                    Timber.d("拆分方式${index+1}的精确匹配结果: ${exactEntries.size}个")
                    
                    if (exactEntries.isNotEmpty()) {
                        val remainingLimit = limitPerMethod.coerceAtLeast(1)
                        allResults.addAll(
                            exactEntries
                                .sortedByDescending { it.frequency }
                                .take(remainingLimit)
                                .map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                        )
                    }
                    
                    // b. 对于第一种拆分方式，如果精确匹配没有结果，尝试前缀匹配
                    if (index == 0 && exactEntries.isEmpty() && syllables.size >= 2) {
                        Timber.d("第一种拆分方式精确匹配无结果，尝试前缀匹配")
                        val prefixEntries = realm.query<Entry>("pinyin BEGINSWITH $0", fullPinyin)
                            .find()
                            .sortedByDescending { it.frequency }
                            .take(limitPerMethod)
                        
                        Timber.d("前缀匹配结果: ${prefixEntries.size}个")
                        allResults.addAll(
                            prefixEntries.map { Candidate.fromEntry(it, Candidate.MatchType.SYLLABLE_SPLIT) }
                        )
                    }
                }
                
                // 3. 如果结果太少，尝试查询单个音节（取第一种拆分方式的第一个音节）
                if (allResults.size < 5 && multipleSplits[0].isNotEmpty()) {
                    val firstSyllable = multipleSplits[0][0]
                    Timber.d("结果不足，查询第一个音节'$firstSyllable'")
                    
                    val singleSyllableEntries = realm.query<Entry>("pinyin == $0", firstSyllable)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(limitPerMethod)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.PINYIN_PREFIX) }
                    
                    Timber.d("单音节查询结果: ${singleSyllableEntries.size}个")
                    allResults.addAll(singleSyllableEntries)
                }
                
                allResults
            } catch (e: Exception) {
                Timber.e(e, "多种拆分查询候选词异常: ${e.message}")
                emptyList()
            }
        }

        // 记录查询结果
        Timber.d("多种拆分查询结果: ${quickResults.size}个候选词")
        
        // 去重并更新UI
        val filteredResults = filterDuplicates(quickResults)
        val sortedResults = sortCandidates(filteredResults, multipleSplits.first().size)
        updateCandidateView(sortedResults)
        
        // 如果快速查询结果不足，且当前查询未取消，异步查询更多结果
        if (filteredResults.size < 5 && currentQueryJob?.isActive!!) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 尝试笛卡尔积组合（仅当结果太少时）
                    if (multipleSplits[0].size > 1 && filteredResults.isEmpty()) {
                        Timber.d("结果不足，尝试音节组合")
                        
                        // 查询每个音节对应的字
                        val entriesForSyllables = multipleSplits[0].map { syllable ->
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
                                val finalResults = sortCandidates(allResults, multipleSplits.first().size)
                                updateCandidateView(finalResults)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "异步处理多种拆分候选词异常")
                }
            }
        }
    }
    
    /**
     * 查找与输入最接近的有效音节
     * 例如输入"zai"，返回"zai"；输入"zaih"，可能返回"zai"
     */
    private fun findClosestValidSyllable(input: String): String {
        // 如果输入本身是有效音节，直接返回
        if (isValidPinyin(input)) {
            return input
        }
        
        // 检查去掉最后一个字符后是否是有效音节（例如"zaih" -> "zai"）
        if (input.length > 1) {
            val withoutLast = input.substring(0, input.length - 1)
            if (isValidPinyin(withoutLast)) {
                return withoutLast
            }
        }
        
        // 从输入末尾开始，寻找最长的有效音节
        for (len in minOf(input.length, 4) downTo 2) { // 最长考虑4个字符，最短2个字符
            val substring = input.substring(input.length - len)
            if (isValidPinyin(substring)) {
                return substring
            }
        }
        
        // 无法找到接近的有效音节
        return ""
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
            // 显示拼音栏（包含应用名称），但隐藏候选词区域
            candidatesContainer.visibility = View.VISIBLE
            defaultCandidatesView.visibility = View.GONE
            // 显示工具栏
            toolbarView.visibility = View.VISIBLE
            
            Timber.d("输入视图初始化完成，显示拼音栏和工具栏，隐藏候选词区域")
            
            // 延迟更新应用名称显示，给UI时间完成初始化
            coroutineScope.launch {
                try {
                    // 延迟300毫秒再更新应用名称，避免过早进行此操作
                    delay(300)
                    updateAppNameDisplay()
                } catch (e: Exception) {
                    Timber.e(e, "延迟更新应用名称失败")
                }
            }
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
            // 完全隐藏整个候选词容器（包括拼音栏）
            hideEntireCandidateContainer()
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
        // 确保移除所有删除回调
        deleteHandler.removeCallbacks(deleteRunnable)
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
    
    /**
     * 检查是否有访问使用情况统计的权限
     */
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, 
            Process.myUid(), 
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    /**
     * 请求权限-跳转到设置界面
     * 确保在主线程执行
     */
    private suspend fun requestUsageStatsPermission() {
        withContext(Dispatchers.Main) {
            Timber.d("请求使用情况统计权限")
            // 在主线程上显示Toast
            Toast.makeText(this@ShenjiInputMethodService, getString(R.string.usage_stats_permission_required), Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } catch (e: Exception) {
                Timber.e(e, "无法打开使用情况统计权限设置")
            }
        }
    }
    
    /**
     * 获取当前前台应用的名称和图标
     * 安全处理权限和异常，避免崩溃
     */
    private suspend fun getCurrentForegroundAppInfo(): Pair<String, android.graphics.drawable.Drawable?> {
        // 检查权限，但不立即显示Toast
        if (!hasUsageStatsPermission()) {
            // 仅记录日志，不立即请求权限
            Timber.d("没有使用情况统计权限")
            return Pair("需要权限", null) // 返回一个提示文本，不引发UI操作
        }
        
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            // 获取过去1分钟内的使用统计
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 60 * 1000, time
            )
            
            if (stats != null) {
                var recentStat: UsageStats? = null
                for (stat in stats) {
                    if (recentStat == null || stat.lastTimeUsed > recentStat.lastTimeUsed) {
                        recentStat = stat
                    }
                }
                
                if (recentStat != null) {
                    // 获取应用名称和图标
                    val packageManager = packageManager
                    return try {
                        val appInfo = packageManager.getApplicationInfo(recentStat.packageName, 0)
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        val appIcon = packageManager.getApplicationIcon(recentStat.packageName)
                        Pair(appName, appIcon)
                    } catch (e: PackageManager.NameNotFoundException) {
                        Timber.e(e, "无法获取应用名称")
                        Pair(recentStat.packageName, null)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "获取前台应用失败")
        }
        
        return Pair("", null)
    }
    
    /**
     * 更新应用名称显示
     * 安全地处理异常，避免应用崩溃
     */
    private fun updateAppNameDisplay() {
        try {
            coroutineScope.launch {
                try {
                    // 在IO线程获取应用名称和图标
                    val appInfo = withContext(Dispatchers.IO) {
                        getCurrentForegroundAppInfo()
                    }
                    
                    val appName = appInfo.first
                    val appIcon = appInfo.second
                    
                    // 在主线程更新UI
                    withContext(Dispatchers.Main) {
                        // 如果应用名是"需要权限"，表示没有使用情况统计权限
                        if (appName == "需要权限" && !hasUsageStatsPermission()) {
                            // 添加视觉提示，让用户知道可以点击
                            appNameDisplay.text = "需要权限 ⚙️"
                            appNameDisplay.setBackgroundResource(R.drawable.bg_permission_needed)
                            
                            // 添加轻微动画效果，提示用户可点击
                            val alphaAnim = android.animation.ObjectAnimator.ofFloat(
                                appNameDisplay, "alpha", 0.6f, 1.0f
                            )
                            alphaAnim.duration = 800
                            alphaAnim.repeatCount = android.animation.ObjectAnimator.INFINITE
                            alphaAnim.repeatMode = android.animation.ObjectAnimator.REVERSE
                            alphaAnim.start()
                            
                            // 没有权限时，点击后请求权限
                            appNameDisplay.setOnClickListener {
                                alphaAnim.cancel() // 停止动画
                                appNameDisplay.alpha = 1.0f // 重置透明度
                                coroutineScope.launch {
                                    requestUsageStatsPermission()
                                }
                            }
                        } else if (appName.isNotEmpty()) {
                            // 新的显示格式：已启用[App名称]增强输入
                            // 限制整体长度，避免太长
                            val shortAppName = if (appName.length > 4) appName.substring(0, 4) + "..." else appName
                            val displayName = "已启用${shortAppName}增强输入"
                            
                            // 设置文本
                            appNameDisplay.text = displayName
                            
                            // 尝试设置图标
                            if (appIcon != null) {
                                try {
                                    // 创建缩小版本的图标（16dp x 16dp）
                                    val scaledDrawable = android.graphics.drawable.BitmapDrawable(
                                        resources,
                                        android.graphics.Bitmap.createScaledBitmap(
                                            getBitmapFromDrawable(appIcon),
                                            16.dpToPx(),
                                            16.dpToPx(),
                                            true
                                        )
                                    )
                                    
                                    // 设置图标在文本左侧
                                    scaledDrawable.setBounds(0, 0, 16.dpToPx(), 16.dpToPx())
                                    appNameDisplay.setCompoundDrawables(scaledDrawable, null, null, null)
                                    appNameDisplay.compoundDrawablePadding = 4.dpToPx()
                                } catch (e: Exception) {
                                    Timber.e(e, "设置应用图标失败")
                                    appNameDisplay.setCompoundDrawables(null, null, null, null)
                                }
                            } else {
                                appNameDisplay.setCompoundDrawables(null, null, null, null)
                            }
                            
                            appNameDisplay.setBackgroundResource(android.R.color.transparent)
                            appNameDisplay.alpha = 1.0f // 确保透明度正常
                            // 有权限时，清除点击事件
                            appNameDisplay.setOnClickListener(null)
                        } else {
                            // 无应用名时的备用显示
                            appNameDisplay.text = "神迹输入法"
                            appNameDisplay.setBackgroundResource(android.R.color.transparent)
                            appNameDisplay.setCompoundDrawables(null, null, null, null)
                            appNameDisplay.alpha = 1.0f
                            appNameDisplay.setOnClickListener(null)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "更新应用名称显示异常")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "启动更新应用名称协程失败")
        }
    }
    
    /**
     * 将Drawable转换为Bitmap
     */
    private fun getBitmapFromDrawable(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
    
    /**
     * dp转换为px的扩展函数
     */
    private fun Int.dpToPx(): Int {
        val scale = resources.displayMetrics.density
        return (this * scale + 0.5f).toInt()
    }

    /**
     * 查询首字母+音节混合模式的候选词
     * @param input 原始输入
     * @param syllables 拆分后的音节列表，第一个是首字母，后面是音节
     */
    private suspend fun queryMixedInitialSyllableCandidates(input: String, syllables: List<String>) {
        if (syllables.isEmpty() || syllables.size < 2) {
            Timber.d("无效的首字母+音节拆分结果")
            return
        }
        
        val realm = ShenjiApplication.realm
        Timber.d("处理首字母+音节混合查询: ${syllables.joinToString("+")}")
        
        val initial = syllables[0] // 首字母
        val remainingSyllables = syllables.subList(1, syllables.size) // 剩余音节
        
        // 选择适当的词典类型，根据音节数量决定查询哪些词典
        val dictionaryTypes = when (remainingSyllables.size) {
            1 -> listOf("chars", "base", "place", "people") // 单音节优先查单字、基础词、地名、人名词典
            2, 3 -> listOf("base", "place", "people", "chars") // 2-3音节优先查基础词、地名、人名词典
            else -> listOf("correlation", "associational", "base", "chars") // 4+音节查大词组词典
        }
        
        // 快速查询
        val quickResults = withContext(Dispatchers.IO) {
            try {
                val allResults = mutableListOf<Candidate>()
                
                // 首先，尝试查询首字母匹配的同时后续音节也匹配的词条
                for (dictType in dictionaryTypes) {
                    // 构建查询条件：首字母匹配+后续音节匹配
                    val syllablePattern = remainingSyllables.joinToString(" ")
                    
                    // 匹配条件: 首字母以initial开头，且完整拼音中包含syllablePattern
                    val entries = realm.query<Entry>("type == $0 AND initialLetters BEGINSWITH $1 AND pinyin CONTAINS $2",
                        dictType, initial, syllablePattern)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(5) // 每个词典取前5个
                        .map { Candidate.fromEntry(it, Candidate.MatchType.ACRONYM) }
                    
                    Timber.d("词典'$dictType'首字母+音节匹配结果: ${entries.size}个")
                    allResults.addAll(entries)
                    
                    // 如果已经找到足够多的结果，提前退出循环
                    if (allResults.size >= initialQueryLimit) break
                }
                
                // 如果结果太少，尝试更宽松的匹配
                if (allResults.size < 5) {
                    // 查询仅匹配首字母的词条
                    val acronymEntries = realm.query<Entry>("initialLetters BEGINSWITH $0", initial)
                        .find()
                        .sortedByDescending { it.frequency }
                        .take(initialQueryLimit - allResults.size)
                        .map { Candidate.fromEntry(it, Candidate.MatchType.ACRONYM) }
                    
                    Timber.d("补充首字母匹配结果: ${acronymEntries.size}个")
                    allResults.addAll(acronymEntries)
                }
                
                allResults
            } catch (e: Exception) {
                Timber.e(e, "查询首字母+音节匹配候选词异常")
                emptyList()
            }
        }
        
        // 记录查询结果
        Timber.d("首字母+音节混合查询结果: ${quickResults.size}个候选词")
        
        // 去重并更新UI
        val filteredResults = filterDuplicates(quickResults)
        val sortedResults = sortCandidates(filteredResults, remainingSyllables.size)
        updateCandidateView(sortedResults)
        
        // 如果快速查询结果不足，且当前查询未取消，异步查询更多结果
        if (filteredResults.size < 5 && currentQueryJob?.isActive!!) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 如果结果不足，尝试使用更通用的查询
                    val moreResults = realm.query<Entry>("initialLetters BEGINSWITH $0", initial)
                        .find()
                        .sortedByDescending { it.frequency }
                        .drop(initialQueryLimit) // 跳过已查询的部分
                        .take(20) // 额外查询更多结果
                        .map { Candidate.fromEntry(it, Candidate.MatchType.ACRONYM) }
                    
                    val moreUniqueResults = filterDuplicates(moreResults)
                    
                    // 合并结果并更新UI
                    if (moreUniqueResults.isNotEmpty() && currentQueryJob?.isActive!!) {
                        val combinedResults = (filteredResults + moreUniqueResults).distinctBy { it.word }
                        val sortedMoreResults = sortCandidates(combinedResults, remainingSyllables.size)
                        updateCandidateView(sortedMoreResults)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "异步处理首字母+音节匹配候选词异常")
                }
            }
        }
    }
} 