package com.shenji.aikeyboard.ime

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ScrollView
import android.view.LayoutInflater
import android.widget.GridLayout
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.data.WordFrequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.shenji.aikeyboard.utils.PinyinUtils

/**
 * 神迹输入法服务，继承自InputMethodService，实现基本的输入法功能
 */
class ShenjiInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard
    private lateinit var pinyinKeyboard: Keyboard
    
    // 候选词相关视图
    private lateinit var candidatesLayout: LinearLayout
    private lateinit var candidatesContainer: LinearLayout
    private lateinit var candidatesScrollView: HorizontalScrollView
    
    // 拼音显示
    private lateinit var pinyinTextView: TextView
    
    // 展开候选词相关视图
    private lateinit var expandButton: ImageButton
    private lateinit var expandedCandidatesScroll: ScrollView
    private lateinit var expandedCandidatesContainer: LinearLayout
    private lateinit var candidatesGrid: GridLayout
    private var isExpandedCandidatesVisible = false
    
    // 全拼/单字切换相关
    private var isSingleCharMode = false
    private lateinit var toggleFullSingleButton: TextView
    
    // 用于单字模式的拼音分割
    private var pinyinSyllables = listOf<String>()
    private var currentSyllableIndex = 0
    
    // 是否是大写模式
    private var isCapsOn = false
    
    // 当前输入的文本
    private var currentComposing = StringBuilder()
    
    // 协程作用域
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    // 当前查询任务
    private var queryJob: Job? = null
    
    // 是否使用拼音键盘
    private var usePinyinKeyboard = true
    
    // 当前候选词列表
    private var currentCandidates = listOf<WordFrequency>()

    override fun onCreate() {
        super.onCreate()
        Timber.d("神迹输入法服务已创建")
        
        // 强制初始化词典管理器
        try {
            // 确保词典管理器已初始化
            Timber.d("开始初始化词典管理器")
            DictionaryManager.init()  // 调用词典管理器的init方法
            Timber.d("词典管理器初始化完成: ${DictionaryManager.instance.isLoaded()}")
        } catch (e: Exception) {
            Timber.e(e, "词典管理器初始化失败")
        }
    }

    override fun onCreateInputView(): View {
        Timber.d("创建输入视图")
        val inputView = layoutInflater.inflate(R.layout.keyboard_view, null)
        
        // 初始化键盘视图
        keyboardView = inputView.findViewById(R.id.keyboard_view)
        keyboard = Keyboard(this, R.xml.keyboard_layout)
        pinyinKeyboard = Keyboard(this, R.xml.pinyin_keyboard_layout)
        
        // 设置键盘适配器
        keyboardView.keyboard = if (usePinyinKeyboard) pinyinKeyboard else keyboard
        keyboardView.setOnKeyboardActionListener(this)
        
        // 初始化拼音显示区域
        pinyinTextView = inputView.findViewById(R.id.pinyin_text)
        
        // 初始化候选词布局
        candidatesLayout = inputView.findViewById(R.id.candidates_layout)
        candidatesContainer = inputView.findViewById(R.id.candidates_container)
        
        // 获取水平滚动视图
        candidatesScrollView = inputView.findViewById(R.id.candidates_horizontal_scroll)
        Timber.d("初始化候选词滚动区域: ${if (::candidatesScrollView.isInitialized) "成功" else "失败"}")
        
        // 初始化展开按钮
        expandButton = inputView.findViewById(R.id.expand_candidates_button)
        Timber.d("初始化展开按钮: ${if (::expandButton.isInitialized) "成功" else "失败"}")
        expandButton.setOnClickListener {
            Timber.d("点击展开按钮")
            toggleExpandedCandidates()
        }
        
        // 初始化展开候选词区域
        expandedCandidatesScroll = inputView.findViewById(R.id.expanded_candidates_scroll)
        expandedCandidatesContainer = inputView.findViewById(R.id.expanded_candidates_container)
        Timber.d("初始化展开候选词区域: ${if (::expandedCandidatesScroll.isInitialized) "成功" else "失败"}")
        
        return inputView
    }
    
    /**
     * 切换展开的候选词区域显示状态
     */
    private fun toggleExpandedCandidates() {
        isExpandedCandidatesVisible = !isExpandedCandidatesVisible
        
        Timber.d("切换展开候选词状态: $isExpandedCandidatesVisible")
        
        if (isExpandedCandidatesVisible) {
            // 显示扩展候选词
            if (::expandedCandidatesScroll.isInitialized) {
                // 清空容器
                expandedCandidatesContainer.removeAllViews()
                
                // 加载新的候选词网格布局
                val expandedView = layoutInflater.inflate(R.layout.expanded_candidates_grid, expandedCandidatesContainer, false)
                expandedCandidatesContainer.addView(expandedView)
                
                // 初始化网格布局和按钮
                candidatesGrid = expandedView.findViewById(R.id.candidates_grid)
                
                // 初始化拼音显示
                val expandedPinyinText = expandedView.findViewById<TextView>(R.id.expanded_pinyin_text)
                expandedPinyinText.text = currentComposing.toString()
                
                // 初始化全/单切换按钮
                toggleFullSingleButton = expandedView.findViewById(R.id.toggle_full_single)
                toggleFullSingleButton.text = if (isSingleCharMode) "全/单" else "全·单"
                toggleFullSingleButton.setOnClickListener {
                    toggleSingleCharMode()
                }
                
                // 初始化返回按钮
                val returnButton = expandedView.findViewById<TextView>(R.id.return_button)
                returnButton.setOnClickListener {
                    toggleExpandedCandidates()
                }
                
                expandedCandidatesScroll.visibility = View.VISIBLE
                expandButton.setImageResource(android.R.drawable.arrow_up_float)
                
                // 调整键盘视图位置
                if (::keyboardView.isInitialized) {
                    keyboardView.visibility = View.GONE
                }
                
                // 填充扩展候选词
                if (isSingleCharMode) {
                    // 单字模式
                    splitPinyinAndShowSingleChars()
                } else {
                    // 全拼模式
                    showCandidatesInGrid(currentCandidates)
                }
                
                Timber.d("展开候选词区域，显示${currentCandidates.size}个候选词")
            } else {
                Timber.e("expandedCandidatesScroll未初始化，无法显示展开候选词")
            }
        } else {
            // 隐藏扩展候选词
            if (::expandedCandidatesScroll.isInitialized) {
                expandedCandidatesScroll.visibility = View.GONE
                expandButton.setImageResource(android.R.drawable.arrow_down_float)
                
                // 恢复键盘视图可见性
                if (::keyboardView.isInitialized) {
                    keyboardView.visibility = View.VISIBLE
                    Timber.d("已恢复键盘视图可见性")
                } else {
                    Timber.e("keyboardView未初始化，无法恢复键盘视图可见性")
                }
                
                Timber.d("收起候选词区域")
            } else {
                Timber.e("expandedCandidatesScroll未初始化，无法隐藏展开候选词")
            }
        }
    }

    /**
     * 切换全拼/单字模式
     */
    private fun toggleSingleCharMode() {
        isSingleCharMode = !isSingleCharMode
        
        // 更新切换按钮文本
        if (::toggleFullSingleButton.isInitialized) {
            toggleFullSingleButton.text = if (isSingleCharMode) "全/单" else "全·单"
        }
        
        if (isSingleCharMode) {
            // 切换到单字模式
            splitPinyinAndShowSingleChars()
        } else {
            // 切换回全拼模式
            showCandidatesInGrid(currentCandidates)
        }
    }
    
    /**
     * 拆分拼音并显示单字候选
     */
    private fun splitPinyinAndShowSingleChars() {
        val pinyin = currentComposing.toString().trim()
        
        if (pinyin.isEmpty()) return
        
        // 分割拼音
        pinyinSyllables = splitPinyinIntoSyllables(pinyin).split(" ")
        currentSyllableIndex = 0
        
        // 显示第一个音节的单字候选
        showSingleCharCandidates(pinyinSyllables[currentSyllableIndex])
    }
    
    /**
     * 显示指定拼音的单字候选词
     */
    private fun showSingleCharCandidates(syllable: String) {
        // 清空网格
        if (::candidatesGrid.isInitialized) {
            candidatesGrid.removeAllViews()
        } else {
            return
        }
        
        coroutineScope.launch {
            val singleChars = withContext(Dispatchers.IO) {
                try {
                    // 查询单字
                    DictionaryManager.instance.searchWords(syllable, 20)
                        .filter { it.word.length == 1 } // 只保留单字
                } catch (e: Exception) {
                    Timber.e(e, "查询单字候选词出错")
                    emptyList()
                }
            }
            
            // 显示单字候选在网格中
            showCandidatesInGrid(singleChars)
        }
    }
    
    /**
     * 在网格中显示候选词
     */
    private fun showCandidatesInGrid(candidates: List<WordFrequency>) {
        if (!::candidatesGrid.isInitialized) return
        
        // 清空网格
        candidatesGrid.removeAllViews()
        
        if (candidates.isEmpty()) return
        
        // 添加候选词到网格
        candidates.forEachIndexed { index, candidate ->
            val candidateView = layoutInflater.inflate(R.layout.candidate_grid_item, candidatesGrid, false) as TextView
            candidateView.text = candidate.word
            
            candidateView.setOnClickListener {
                if (isSingleCharMode) {
                    handleSingleCharSelection(candidate.word)
                } else {
                    submitCandidate(index)
                }
            }
            
            candidatesGrid.addView(candidateView)
        }
    }
    
    /**
     * 处理单字候选词的选择
     */
    private fun handleSingleCharSelection(selectedChar: String) {
        // 添加选定的字符到输入
        val inputConnection = currentInputConnection ?: return
        
        // 如果还有下一个拼音音节
        if (currentSyllableIndex < pinyinSyllables.size - 1) {
            // 提交当前选择的字符
            inputConnection.commitText(selectedChar, 1)
            
            // 前进到下一个拼音音节
            currentSyllableIndex++
            
            // 显示下一个拼音音节的候选字
            showSingleCharCandidates(pinyinSyllables[currentSyllableIndex])
        } else {
            // 已经是最后一个音节了，提交并清空
            inputConnection.commitText(selectedChar, 1)
            
            // 清空状态
            currentComposing.clear()
            updatePinyinText()
            clearCandidates()
            currentCandidates = emptyList()
            
            // 关闭扩展候选词
            toggleExpandedCandidates()
        }
    }
    
    /**
     * 将无空格拼音转换为带空格的拼音音节（如beijing -> bei jing）
     * @deprecated 使用 PinyinSplitter.split() 替代
     */
    @Deprecated("使用PinyinSplitter.split()替代", ReplaceWith("com.shenji.aikeyboard.data.PinyinSplitter.split(pinyin)"))
    private fun splitPinyinIntoSyllables(pinyin: String): String {
        return com.shenji.aikeyboard.data.PinyinSplitter.split(pinyin)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        
        // 清空当前输入
        currentComposing.clear()
        updatePinyinText()
        
        // 安全清除候选词，避免未初始化造成的崩溃
        if (::candidatesLayout.isInitialized) {
            clearCandidates()
        }
        
        // 确保键盘视图是可见的
        if (::keyboardView.isInitialized && ::expandedCandidatesScroll.isInitialized) {
            if (isExpandedCandidatesVisible) {
                // 如果扩展候选词区域可见，则隐藏它并显示键盘
                isExpandedCandidatesVisible = false
                expandedCandidatesScroll.visibility = View.GONE
                if (::expandButton.isInitialized) {
                    expandButton.setImageResource(android.R.drawable.arrow_down_float)
                }
            }
            
            // 确保键盘可见
            keyboardView.visibility = View.VISIBLE
            Timber.d("onStartInput时恢复键盘视图可见性")
        }
        
        // 根据输入类型调整键盘
        attribute?.inputType?.let { inputType ->
            // 根据输入类型调整键盘布局
            when {
                inputType and InputType.TYPE_CLASS_NUMBER != 0 -> {
                    // 数字键盘
                    Timber.d("使用数字键盘")
                    usePinyinKeyboard = false
                }
                inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 -> {
                    // 密码输入
                    Timber.d("使用密码键盘")
                    usePinyinKeyboard = false
                }
                else -> {
                    // 默认键盘，使用拼音键盘
                    Timber.d("使用拼音键盘")
                    usePinyinKeyboard = true
                }
            }
            
            // 如果键盘视图已初始化，则更新键盘
            if (::keyboardView.isInitialized) {
                keyboardView.keyboard = if (usePinyinKeyboard) pinyinKeyboard else keyboard
            }
        }
    }
    
    /**
     * 更新拼音文本显示
     */
    private fun updatePinyinText() {
        if (::pinyinTextView.isInitialized) {
            if (currentComposing.isEmpty()) {
                pinyinTextView.text = ""
                pinyinTextView.hint = "点击此处输入文字"
            } else {
                pinyinTextView.text = currentComposing.toString()
                pinyinTextView.hint = ""
            }
        }
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        
        // 清空当前输入
        currentComposing.clear()
        updatePinyinText()
        clearCandidates()
        
        // 隐藏扩展候选词
        if (::expandedCandidatesScroll.isInitialized) {
            expandedCandidatesScroll.visibility = View.GONE
            isExpandedCandidatesVisible = false
            if (::expandButton.isInitialized) {
                expandButton.setImageResource(android.R.drawable.arrow_down_float)
            }
        }
    }

    // 按键按下处理
    override fun onPress(primaryCode: Int) {
        // 可以在这里添加按键反馈，如震动
        Timber.d("按键按下: $primaryCode")
    }

    // 按键释放处理
    override fun onRelease(primaryCode: Int) {
        // 按键释放处理
    }

    // 按键点击处理
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection ?: return
        
        Timber.d("按键点击: $primaryCode, 当前输入: $currentComposing")
        
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                // 删除键
                val selectedText = inputConnection.getSelectedText(0)
                if (selectedText.isNullOrEmpty()) {
                    // 没有选中文本，删除前一个字符
                    if (currentComposing.isNotEmpty()) {
                        currentComposing.deleteCharAt(currentComposing.length - 1)
                        updatePinyinText()
                        updateCandidates()
                        Timber.d("删除后当前输入: $currentComposing")
                    } else {
                        inputConnection.deleteSurroundingText(1, 0)
                    }
                } else {
                    // 删除选中的文本
                    inputConnection.commitText("", 1)
                    currentComposing.clear()
                    updatePinyinText()
                    clearCandidates()
                }
            }
            Keyboard.KEYCODE_SHIFT -> {
                // Shift键，切换大小写
                isCapsOn = !isCapsOn
                keyboard.isShifted = isCapsOn
                pinyinKeyboard.isShifted = isCapsOn
                keyboardView.invalidateAllKeys()
                Timber.d("切换大小写: $isCapsOn")
            }
            Keyboard.KEYCODE_DONE -> {
                // 完成键
                if (currentComposing.isNotEmpty()) {
                    inputConnection.commitText(currentComposing.toString(), 1)
                    currentComposing.clear()
                    updatePinyinText()
                    clearCandidates()
                    Timber.d("提交当前输入并完成")
                }
                inputConnection.performEditorAction(EditorInfo.IME_ACTION_DONE)
            }
            -100 -> {
                // 自定义切换键
                Timber.d("切换输入法")
                switchToNextInputMethod(false)
            }
            -101 -> {
                // 数字键盘切换
                Timber.d("切换到数字键盘")
                usePinyinKeyboard = !usePinyinKeyboard
                keyboardView.keyboard = if (usePinyinKeyboard) pinyinKeyboard else keyboard
            }
            32 -> {
                // 空格键
                if (currentComposing.isNotEmpty()) {
                    // 提交候选词中的第一个词（如果有）
                    submitCandidate(0)
                } else {
                    // 直接输入空格
                    inputConnection.commitText(" ", 1)
                    Timber.d("输入空格")
                }
            }
            else -> {
                // 普通字符输入
                var code = primaryCode.toChar()
                if (isCapsOn && code in 'a'..'z') {
                    code = code.uppercaseChar()
                }
                
                // 追加到当前输入
                currentComposing.append(code)
                updatePinyinText()
                Timber.d("追加字符: $code, 当前输入: $currentComposing")
                
                // 更新候选词
                updateCandidates()
            }
        }
    }

    // 文本变化处理
    override fun onText(text: CharSequence?) {
        val inputConnection = currentInputConnection ?: return
        text?.let {
            if (currentComposing.isNotEmpty()) {
                // 提交当前输入
                inputConnection.commitText(currentComposing.toString(), 1)
                currentComposing.clear()
                updatePinyinText()
            }
            inputConnection.commitText(it, 1)
            clearCandidates()
            Timber.d("直接输入文本: $it")
        }
    }
    
    /**
     * 更新候选词列表
     */
    private fun updateCandidates() {
        val prefix = currentComposing.toString()
        if (prefix.isEmpty()) {
            clearCandidates()
            return
        }
        
        // 取消之前的查询任务
        queryJob?.cancel()
        
        // 异步查询候选词
        queryJob = coroutineScope.launch {
            Timber.d("查询候选词: $prefix")
            
            val candidates = withContext(Dispatchers.IO) {
                try {
                    // 确保词典已加载
                    if (!DictionaryManager.instance.isLoaded()) {
                        Timber.d("词典未加载，尝试初始化")
                        DictionaryManager.init()  // 尝试重新初始化
                    }
                    
                    // 查询候选词 - 同时从内存和数据库搜索
                    val result = DictionaryManager.instance.searchWords(prefix, 20)
                    Timber.d("查询候选词结果: ${result.size}个 - ${result.joinToString { it.word }}")
                    
                    // 如果结果为空，尝试使用内置的特定缩写映射
                    if (result.isEmpty()) {
                        Timber.d("尝试使用内置缩写匹配")
                        // 创建一个基本的候选词列表（缩写映射）
                        val abbreviationCandidates = when (prefix) {
                            "bj" -> listOf(
                                WordFrequency("北京", 1000),
                                WordFrequency("宝鸡", 500),
                                WordFrequency("边界", 300)
                            )
                            "sh" -> listOf(
                                WordFrequency("上海", 1000),
                                WordFrequency("深圳", 800),
                                WordFrequency("社会", 500)
                            )
                            "zg" -> listOf(
                                WordFrequency("中国", 1000),
                                WordFrequency("总共", 500)
                            )
                            "gj" -> listOf(
                                WordFrequency("国家", 1000),
                                WordFrequency("工具", 600)
                            )
                            "ni" -> listOf(
                                WordFrequency("你", 1000),
                                WordFrequency("呢", 900),
                                WordFrequency("泥", 800),
                                WordFrequency("尼", 700),
                                WordFrequency("腻", 600),
                                WordFrequency("倪", 500)
                            )
                            "cuan" -> listOf( // 添加cuan的特殊映射用于测试
                                WordFrequency("窜", 800),
                                WordFrequency("篡", 700),
                                WordFrequency("蹿", 600)
                            )
                            else -> emptyList()
                        }
                        
                        if (abbreviationCandidates.isNotEmpty()) {
                            Timber.d("缩写匹配结果: ${abbreviationCandidates.size}个 - ${abbreviationCandidates.joinToString { it.word }}")
                        } else {
                            Timber.d("无内置缩写匹配")
                        }
                        
                        abbreviationCandidates
                    } else {
                        result
                    }
                } catch (e: Exception) {
                    Timber.e(e, "查询候选词出错")
                    // 返回输入本身作为候选词
                    listOf(WordFrequency(prefix, 100))
                }
            }
            
            Timber.d("候选词数量: ${candidates.size}, 显示候选词")
            currentCandidates = candidates
            
            // 显示候选词
            showCandidates(candidates)
            
            // 如果扩展候选词视图已显示，更新它
            if (isExpandedCandidatesVisible && ::expandedCandidatesContainer.isInitialized) {
                if (::candidatesGrid.isInitialized) {
                    // 使用新的网格布局显示
                    if (isSingleCharMode) {
                        splitPinyinAndShowSingleChars()
                    } else {
                        showCandidatesInGrid(candidates)
                    }
                    
                    // 更新拼音显示
                    val expandedPinyinText = expandedCandidatesContainer.findViewById<TextView>(R.id.expanded_pinyin_text)
                    if (expandedPinyinText != null) {
                        expandedPinyinText.text = currentComposing.toString()
                    }
                }
            }
        }
    }
    
    /**
     * 显示候选词
     */
    private fun showCandidates(candidates: List<WordFrequency>) {
        // 防止空指针异常
        if (!::candidatesContainer.isInitialized) {
            Timber.w("candidatesContainer尚未初始化，无法显示候选词")
            return
        }
        
        Timber.d("开始显示候选词，清空当前候选区")
        candidatesContainer.removeAllViews()
        
        if (candidates.isEmpty()) {
            // 如果没有候选词，显示当前输入的文本
            if (currentComposing.isNotEmpty()) {
                Timber.d("没有候选词，显示当前输入: ${currentComposing}")
                addHorizontalCandidate(currentComposing.toString(), -1, 0)
            }
            return
        }
        
        // 根据输入长度调整候选词显示风格
        val pinyin = currentComposing.toString().trim()
        val pinyinLength = pinyin.replace(" ", "").length
        
        // 直接显示候选词，保持原始排序（数据层已经按照精确匹配、词频等规则排序）
        Timber.d("显示${Math.min(candidates.size, 10)}个候选词，保持原排序")
        displayCandidates(candidates.take(10))
        
        // 根据情况添加提示标记
        when {
            // 2-3字符输入
            pinyinLength <= 3 -> {
                // 检查是否有更多候选词
                if (candidates.size > 10) {
                    addSuggestionHint()
                }
            }
            
            // 4字符输入
            pinyinLength == 4 -> {
                // 检查是否有长词
                val hasLongWords = candidates.any { it.word.length > 4 }
                if (hasLongWords || candidates.size > 10) {
                    addScrollHint()
                }
            }
            
            // 5字以上输入
            else -> {
                // 检查是否有额外候选词
                if (candidates.size > 10) {
                    addScrollHint()
                }
            }
        }
    }
    
    /**
     * 显示候选词列表
     */
    private fun displayCandidates(candidates: List<WordFrequency>) {
        candidates.forEachIndexed { index, candidate ->
            Timber.d("添加候选词 #$index: ${candidate.word}")
            addHorizontalCandidate(candidate.word, candidate.frequency, index)
        }
    }
    
    /**
     * 添加联想提示标记
     */
    private fun addSuggestionHint() {
        // 添加一个提示箭头，表示有更多联想候选词
        val hintView = TextView(this)
        hintView.text = "···" // 三个点表示有更多
        hintView.textSize = 16f
        hintView.setTextColor(getColor(R.color.candidate_other))
        hintView.setPadding(15, 10, 15, 10)
        
        // 点击展开更多候选词
        hintView.setOnClickListener {
            toggleExpandedCandidates()
        }
        
        candidatesContainer.addView(hintView)
    }
    
    /**
     * 添加滚动提示标记
     */
    private fun addScrollHint() {
        // 添加一个提示箭头，表示可以滚动查看更多
        val hintView = TextView(this)
        hintView.text = "→" // 箭头表示可以滚动
        hintView.textSize = 16f
        hintView.setTextColor(getColor(R.color.candidate_other))
        hintView.setPadding(15, 10, 15, 10)
        
        // 点击展开网格候选词视图
        hintView.setOnClickListener {
            toggleExpandedCandidates()
        }
        
        candidatesContainer.addView(hintView)
    }
    
    /**
     * 添加候选词到水平候选区
     */
    private fun addHorizontalCandidate(word: String, frequency: Int, index: Int) {
        // 防止空指针异常
        if (!::candidatesContainer.isInitialized) {
            Timber.w("candidatesContainer尚未初始化，无法添加候选词")
            return
        }
        
        val textView = layoutInflater.inflate(R.layout.candidate_item_horizontal, candidatesContainer, false) as TextView
        textView.text = word
        
        // 设置间距
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        layoutParams.marginEnd = 10  // 设置右边距
        textView.layoutParams = layoutParams
        
        // 点击候选词的处理
        textView.setOnClickListener {
            Timber.d("点击候选词: $word")
            submitCandidate(index)
        }
        
        candidatesContainer.addView(textView)
        Timber.d("添加候选词: $word, 频率: $frequency, 索引: $index")
    }
    
    /**
     * 提交选中的候选词
     */
    private fun submitCandidate(index: Int) {
        val inputConnection = currentInputConnection ?: return
        
        // 如果是单字模式且扩展候选词可见，不要关闭面板
        if (isExpandedCandidatesVisible && isSingleCharMode && index >= 0 && index < currentCandidates.size) {
            // 在单字模式下选择候选词的特殊处理
            val selectedText = currentCandidates[index].word
            handleSingleCharSelection(selectedText)
            return
        }
        
        // 隐藏扩展候选词视图
        if (isExpandedCandidatesVisible && ::expandedCandidatesScroll.isInitialized) {
            expandedCandidatesScroll.visibility = View.GONE
            isExpandedCandidatesVisible = false
            if (::expandButton.isInitialized) {
                expandButton.setImageResource(android.R.drawable.arrow_down_float)
            }
            
            // 确保键盘视图显示
            if (::keyboardView.isInitialized) {
                keyboardView.visibility = View.VISIBLE
                Timber.d("提交候选词时恢复键盘视图可见性")
            } else {
                Timber.e("提交候选词时keyboardView未初始化，无法恢复键盘视图可见性")
            }
        }
        
        // 如果索引在候选词列表范围内
        if (index >= 0 && index < currentCandidates.size) {
            val selectedText = currentCandidates[index].word
            // 使用handleCandidateSelection处理候选词选择逻辑
            handleCandidateSelection(selectedText)
        } else if (currentComposing.isNotEmpty()) {
            // 没有候选词或索引超出范围，提交当前输入
            handleCandidateSelection(currentComposing.toString())
        }
    }
    
    /**
     * 清空候选词
     */
    private fun clearCandidates() {
        // 清除水平候选词
        if (::candidatesContainer.isInitialized) {
            candidatesContainer.removeAllViews()
        } else {
            Timber.w("candidatesContainer尚未初始化，无法清除候选词")
        }
        
        // 清除扩展候选词
        if (::expandedCandidatesContainer.isInitialized) {
            expandedCandidatesContainer.removeAllViews()
        }
        
        // 重置当前候选词列表
        currentCandidates = emptyList()
    }

    // 滑动处理 - 左右滑动候选词区域
    override fun swipeLeft() {
        // 向左滑动处理 - 向右滚动候选词
        if (::candidatesScrollView.isInitialized && candidatesContainer.childCount > 0) {
            Timber.d("向左滑动，滚动候选词区域")
            // 获取当前滚动位置
            val currentX = candidatesScrollView.scrollX
            // 滚动一段距离（可以根据需要调整）
            candidatesScrollView.smoothScrollTo(currentX + 200, 0)
        }
    }

    override fun swipeRight() {
        // 向右滑动处理 - 向左滚动候选词
        if (::candidatesScrollView.isInitialized && candidatesContainer.childCount > 0) {
            Timber.d("向右滑动，滚动候选词区域")
            // 获取当前滚动位置
            val currentX = candidatesScrollView.scrollX
            // 滚动一段距离（可以根据需要调整）
            val newX = Math.max(0, currentX - 200)
            candidatesScrollView.smoothScrollTo(newX, 0)
        }
    }

    override fun swipeDown() {
        // 向下滑动处理
        // 可以用来隐藏键盘
        hideWindow()
        Timber.d("向下滑动，隐藏键盘")
    }

    override fun swipeUp() {
        // 向上滑动处理
        Timber.d("向上滑动")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 处理返回键
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 如果扩展候选词区域可见，则先隐藏它
            if (isExpandedCandidatesVisible && ::expandedCandidatesScroll.isInitialized) {
                expandedCandidatesScroll.visibility = View.GONE
                isExpandedCandidatesVisible = false
                if (::expandButton.isInitialized) {
                    expandButton.setImageResource(android.R.drawable.arrow_down_float)
                }
                
                // 确保键盘视图显示
                if (::keyboardView.isInitialized) {
                    keyboardView.visibility = View.VISIBLE
                    Timber.d("返回键：恢复键盘视图可见性")
                }
                
                // 已处理返回键
                return true
            }
        }
        
        return super.onKeyDown(keyCode, event)
    }

    /**
     * 处理候选词选择
     */
    private fun handleCandidateSelection(word: String) {
        val ic = currentInputConnection ?: return
        
        // 清除当前正在输入的拼音文本
        Timber.d("选择候选词：'$word'，当前输入：'$currentComposing'")
        
        if (!currentComposing.isNullOrEmpty()) {
            // 记录当前选择的词语和输入的拼音
            val pinyin = currentComposing.toString()
            Timber.d("用户查询候选词: '$pinyin'")
            
            val normalizedPinyin = PinyinUtils.normalize(pinyin)
            Timber.d("拼音转换: '$pinyin' -> '$normalizedPinyin'")
            
            // 将正在输入的拼音分词
            val pinyinSyllables = normalizedPinyin.split(" ")
            
            // 清空当前输入内容
            currentComposing.clear()
            updatePinyinText()
        }
        
        // 将选中的候选词添加到输入框
        ic.commitText(word, 1)
        
        // 清除候选词
        clearCandidates()
        
        // 隐藏扩展的候选词区域
        if (isExpandedCandidatesVisible) {
            expandedCandidatesScroll.visibility = View.GONE
            isExpandedCandidatesVisible = false
            if (::expandButton.isInitialized) {
                expandButton.setImageResource(android.R.drawable.arrow_down_float)
            }
        }
        
        Timber.d("候选词选择完成")
    }
} 