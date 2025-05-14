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
     * 拆分拼音为音节
     * 例如："nihao" -> "ni hao"
     */
    private fun splitPinyinIntoSyllables(pinyin: String): String {
        if (pinyin.isBlank()) return pinyin
        
        // 如果已经包含空格，直接返回
        if (pinyin.contains(" ")) return pinyin
        
        try {
            // 中文拼音音节列表（按长度排序，优先匹配较长的）
            val pinyinSyllables = listOf(
                // 常见四字母及以上音节
                "zhuang", "chuang", "shuang", "zhang", "chang", "shang", "zheng", "cheng", "sheng",
                "zhong", "chong", "jiang", "qiang", "xiang", "zhou", "chou", "shou", "zhen", "chen", "shen",
                "zhan", "chan", "shan", "bing", "ping", "ding", "ting", "ning", "ling", "jing", "qing", "xing", "ying",
                "zeng", "ceng", "seng", "duan", "tuan", "nuan", "luan", "guan", "kuan", "huan", "quan", "xuan", "yuan",
                // 常见三字母音节
                "zhi", "chi", "shi", "ang", "eng", "ing", "ong", "bai", "pai", "mai", "dai", "tai", "nai", "lai", "gai", "kai", "hai", "zai", "cai", "sai",
                "ban", "pan", "man", "fan", "dan", "tan", "nan", "lan", "gan", "kan", "han", "zan", "can", "san",
                "bao", "pao", "mao", "dao", "tao", "nao", "lao", "gao", "kao", "hao", "zao", "cao", "sao",
                "bie", "pie", "mie", "die", "tie", "nie", "lie", "jie", "qie", "xie", "yan", "jin", "qin", "xin",
                "bin", "pin", "min", "nin", "lin", "jin", "qin", "xin", "yin", "jiu", "qiu", "xiu",
                "bei", "pei", "mei", "fei", "dei", "tei", "nei", "lei", "gei", "kei", "hei", "zei", "cei", "sei",
                "ben", "pen", "men", "fen", "den", "nen", "gen", "ken", "hen", "zen", "cen", "sen",
                "zhu", "chu", "shu", "zhe", "che", "she", "zha", "cha", "sha", "zou", "cou", "sou", "zui", "cui", "sui", "zun", "cun", "sun",
                "zhuo", "chuo", "shuo", "zhen", "chen", "shen",
                // k开头音节补充
                "kuai", "kuan", "kuang",
                // 常见双字母音节
                "ba", "pa", "ma", "fa", "da", "ta", "na", "la", "ga", "ka", "ha", "za", "ca", "sa",
                "bo", "po", "mo", "fo", "lo", "wo", "yo", "zo", "co", "so",
                "bi", "pi", "mi", "di", "ti", "ni", "li", "ji", "qi", "xi", "yi",
                "bu", "pu", "mu", "fu", "du", "tu", "nu", "lu", "gu", "ku", "hu", "zu", "cu", "su", "wu", "yu",
                "ai", "ei", "ui", "ao", "ou", "iu", "ie", "ue", "ve", "er", "an", "en", "in", "un", "vn",
                "wu", "yu", "ju", "qu", "xu", "zi", "ci", "si", "ge", "he", "ne", "le", "me", "de", "te",
                "re", "ze", "ce", "se", "ye", "zh", "ch", "sh",
                // 单字母音节
                "a", "o", "e", "i", "u", "v"
            )
            
            // 贪婪匹配：从输入的开始位置尝试匹配最长的拼音音节
            var result = ""
            var position = 0
            
            while (position < pinyin.length) {
                var matched = false
                
                // 尝试匹配最长的音节
                for (syllable in pinyinSyllables) {
                    if (position + syllable.length <= pinyin.length &&
                        pinyin.substring(position, position + syllable.length) == syllable) {
                        // 匹配到音节，添加到结果中
                        result += if (result.isEmpty()) syllable else " $syllable"
                        position += syllable.length
                        matched = true
                        break
                    }
                }
                
                // 如果没有匹配到任何音节，只好单字符处理
                if (!matched) {
                    val char = pinyin[position]
                    result += if (result.isEmpty()) char.toString() else " $char"
                    position++
                }
            }
            
            Timber.d("拼音分词转换: '$pinyin' -> '$result'")
            return result
            
        } catch (e: Exception) {
            Timber.e(e, "拼音分词失败: ${e.message}")
            return pinyin // 出错返回原始拼音
        }
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
        
        // 显示候选词（限制为前10个）
        val displayCandidates = candidates.take(10)
        Timber.d("显示 ${displayCandidates.size} 个候选词")
        displayCandidates.forEachIndexed { index, candidate ->
            Timber.d("添加候选词 #$index: ${candidate.word}")
            addHorizontalCandidate(candidate.word, candidate.frequency, index)
        }
    }
    
    /**
     * 显示扩展候选词视图
     */
    private fun showExpandedCandidates(candidates: List<WordFrequency>) {
        // 防止空指针异常
        if (!::expandedCandidatesContainer.isInitialized) {
            Timber.w("expandedCandidatesContainer尚未初始化，无法显示扩展候选词")
            return
        }
        
        Timber.d("开始显示扩展候选词，清空当前扩展候选区")
        expandedCandidatesContainer.removeAllViews()
        
        // 添加拼音标签
        val pinyinLabelView = TextView(this)
        pinyinLabelView.text = currentComposing.toString()
        pinyinLabelView.textSize = 14f
        pinyinLabelView.setTextColor(getColor(R.color.pinyin_text))
        pinyinLabelView.setPadding(20, 10, 10, 10)
        expandedCandidatesContainer.addView(pinyinLabelView)
        
        if (candidates.isEmpty()) {
            // 如果没有候选词，显示当前输入的文本
            if (currentComposing.isNotEmpty()) {
                Timber.d("没有扩展候选词，显示当前输入: ${currentComposing}")
                val candidateView = layoutInflater.inflate(R.layout.candidate_item, expandedCandidatesContainer, false) as TextView
                candidateView.text = currentComposing.toString()
                candidateView.setOnClickListener {
                    submitCandidate(0)
                }
                expandedCandidatesContainer.addView(candidateView)
            }
            return
        }
        
        // 添加一个分类标题
        val categoriesView = LinearLayout(this)
        categoriesView.orientation = LinearLayout.HORIZONTAL
        categoriesView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        
        // 添加分类选项卡
        val categories = arrayOf("拼音", "笔画", "英文", "人名")
        categories.forEachIndexed { index, category ->
            val tabView = TextView(this)
            tabView.text = category
            tabView.textSize = 14f
            tabView.gravity = android.view.Gravity.CENTER
            tabView.setPadding(16, 12, 16, 12)
            
            // 第一个选项卡（拼音）高亮
            if (index == 0) {
                tabView.setTextColor(getColor(R.color.candidate_text_selected))
                tabView.setBackgroundResource(R.drawable.candidate_item_background)
            } else {
                tabView.setTextColor(getColor(R.color.pinyin_text))
            }
            
            // 设置宽度
            val layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            tabView.layoutParams = layoutParams
            
            categoriesView.addView(tabView)
        }
        
        expandedCandidatesContainer.addView(categoriesView)
        
        // 添加分隔线
        val dividerView = View(this)
        dividerView.setBackgroundColor(getColor(R.color.divider))
        val dividerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            1
        )
        dividerView.layoutParams = dividerParams
        expandedCandidatesContainer.addView(dividerView)
        
        // 分组显示，4个候选词一行
        val displayRows = (candidates.size + 3) / 4
        for (row in 0 until displayRows) {
            val rowLayout = LinearLayout(this)
            rowLayout.orientation = LinearLayout.HORIZONTAL
            rowLayout.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            
            // 添加一行中的候选词（最多4个）
            for (col in 0 until 4) {
                val index = row * 4 + col
                if (index < candidates.size) {
                    val candidate = candidates[index]
                    val candidateView = TextView(this)
                    candidateView.text = candidate.word
                    candidateView.textSize = 16f
                    candidateView.setTextColor(getColor(R.color.candidate_text))
                    candidateView.gravity = android.view.Gravity.CENTER
                    candidateView.setPadding(0, 16, 0, 16)
                    
                    // 设置宽度平均分配
                    val layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f
                    )
                    candidateView.layoutParams = layoutParams
                    
                    candidateView.setOnClickListener {
                        submitCandidate(index)
                    }
                    
                    rowLayout.addView(candidateView)
                } else {
                    // 添加空白占位
                    val emptyView = View(this)
                    val layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.0f
                    )
                    emptyView.layoutParams = layoutParams
                    rowLayout.addView(emptyView)
                }
            }
            
            expandedCandidatesContainer.addView(rowLayout)
            
            // 添加行分隔线
            if (row < displayRows - 1) {
                val rowDivider = View(this)
                rowDivider.setBackgroundColor(getColor(R.color.divider))
                val dividerParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1
                )
                rowDivider.layoutParams = dividerParams
                expandedCandidatesContainer.addView(rowDivider)
            }
        }
        
        // 添加底部导航栏
        val navigationBar = LinearLayout(this)
        navigationBar.orientation = LinearLayout.HORIZONTAL
        navigationBar.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        navigationBar.setPadding(0, 16, 0, 16)
        
        // 添加全/单按钮
        val toggleButton = TextView(this)
        toggleButton.text = "全·单"
        toggleButton.textSize = 14f
        toggleButton.setTextColor(getColor(R.color.pinyin_text))
        toggleButton.gravity = android.view.Gravity.CENTER
        toggleButton.setPadding(16, 8, 16, 8)
        toggleButton.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        navigationBar.addView(toggleButton)
        
        // 添加中间的空白
        val spaceView = View(this)
        spaceView.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            1.0f
        )
        navigationBar.addView(spaceView)
        
        // 添加返回按钮
        val returnButton = TextView(this)
        returnButton.text = "返回"
        returnButton.textSize = 16f
        returnButton.setTextColor(getColor(R.color.candidate_text_selected))
        returnButton.gravity = android.view.Gravity.CENTER
        returnButton.setPadding(16, 8, 16, 8)
        returnButton.setBackgroundResource(R.drawable.candidate_item_background)
        returnButton.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        returnButton.setOnClickListener {
            toggleExpandedCandidates() // 点击返回按钮收起候选词区域
            
            // 额外的检查，确保键盘状态正确设置
            if (!isExpandedCandidatesVisible && ::keyboardView.isInitialized) {
                keyboardView.post {
                    keyboardView.visibility = View.VISIBLE
                    Timber.d("返回按钮点击后确保键盘视图可见")
                }
            }
        }
        navigationBar.addView(returnButton)
        
        expandedCandidatesContainer.addView(navigationBar)
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
            inputConnection.commitText(selectedText, 1)
            Timber.d("提交候选词: $selectedText")
        } else if (currentComposing.isNotEmpty()) {
            // 没有候选词或索引超出范围，提交当前输入
            inputConnection.commitText(currentComposing.toString(), 1)
            Timber.d("无候选词，直接提交: ${currentComposing.toString()}")
        }
        
        // 清空当前输入和候选词
        currentComposing.clear()
        updatePinyinText()
        clearCandidates()
        currentCandidates = emptyList()
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
} 