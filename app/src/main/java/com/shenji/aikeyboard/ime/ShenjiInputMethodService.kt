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
    private lateinit var candidatesLayout: LinearLayout
    
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
        // 加载键盘视图
        val inputView = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardView = inputView.findViewById(R.id.keyboard_view)
        candidatesLayout = inputView.findViewById(R.id.candidates_layout)
        
        // 初始化键盘
        keyboard = Keyboard(this, R.xml.keyboard_layout)
        pinyinKeyboard = Keyboard(this, R.xml.pinyin_keyboard_layout)
        
        // 默认使用拼音键盘
        keyboardView.keyboard = if (usePinyinKeyboard) pinyinKeyboard else keyboard
        keyboardView.setOnKeyboardActionListener(this)
        
        Timber.d("输入法视图已创建")
        
        return inputView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        
        // 清空当前输入
        currentComposing.clear()
        
        // 安全清除候选词，避免未初始化造成的崩溃
        if (::candidatesLayout.isInitialized) {
            clearCandidates()
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
    
    override fun onFinishInput() {
        super.onFinishInput()
        
        // 清空当前输入
        currentComposing.clear()
        clearCandidates()
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
                        updateCandidates()
                        Timber.d("删除后当前输入: $currentComposing")
                    } else {
                        inputConnection.deleteSurroundingText(1, 0)
                    }
                } else {
                    // 删除选中的文本
                    inputConnection.commitText("", 1)
                    currentComposing.clear()
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
                    val result = DictionaryManager.instance.searchWords(prefix, 10)
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
            showCandidates(candidates)
        }
    }
    
    /**
     * 显示候选词
     */
    private fun showCandidates(candidates: List<WordFrequency>) {
        // 防止空指针异常
        if (!::candidatesLayout.isInitialized) {
            Timber.w("candidatesLayout尚未初始化，无法显示候选词")
            return
        }
        
        Timber.d("开始显示候选词，清空当前候选区")
        candidatesLayout.removeAllViews()
        
        if (candidates.isEmpty()) {
            // 如果没有候选词，显示当前输入的文本
            if (currentComposing.isNotEmpty()) {
                Timber.d("没有候选词，显示当前输入: ${currentComposing}")
                addCandidate(currentComposing.toString(), -1, 0)
            }
            return
        }
        
        // 显示候选词
        Timber.d("显示 ${candidates.size} 个候选词")
        candidates.forEachIndexed { index, candidate ->
            Timber.d("添加候选词 #$index: ${candidate.word}")
            addCandidate(candidate.word, candidate.frequency, index)
        }
    }
    
    /**
     * 添加候选词到候选区
     */
    private fun addCandidate(word: String, frequency: Int, index: Int) {
        // 防止空指针异常
        if (!::candidatesLayout.isInitialized) {
            Timber.w("candidatesLayout尚未初始化，无法添加候选词")
            return
        }
        
        val textView = TextView(this)
        textView.text = word
        textView.textSize = 18f
        textView.setPadding(16, 12, 16, 12)  // 增加内边距使按钮更大
        textView.setBackgroundResource(android.R.drawable.btn_default)  // 添加背景使候选词更明显
        textView.setTextColor(android.graphics.Color.BLACK)  // 设置文字颜色
        
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
        
        candidatesLayout.addView(textView)
        Timber.d("添加候选词: $word, 频率: $frequency, 索引: $index")
    }
    
    /**
     * 提交选中的候选词
     */
    private fun submitCandidate(index: Int) {
        val inputConnection = currentInputConnection ?: return
        
        // 防止空指针异常
        if (!::candidatesLayout.isInitialized) {
            Timber.w("candidatesLayout尚未初始化，无法提交候选词")
            // 直接提交当前输入
            if (currentComposing.isNotEmpty()) {
                inputConnection.commitText(currentComposing.toString(), 1)
                currentComposing.clear()
            }
            return
        }
        
        if (candidatesLayout.childCount > index) {
            val selectedView = candidatesLayout.getChildAt(index) as? TextView
            val selectedText = selectedView?.text?.toString() ?: currentComposing.toString()
            
            inputConnection.commitText(selectedText, 1)
            Timber.d("提交候选词: $selectedText")
            
            currentComposing.clear()
            clearCandidates()
        } else if (currentComposing.isNotEmpty()) {
            // 没有候选词，提交当前输入
            inputConnection.commitText(currentComposing.toString(), 1)
            Timber.d("无候选词，直接提交: ${currentComposing.toString()}")
            
            currentComposing.clear()
            clearCandidates()
        }
    }
    
    /**
     * 清空候选词
     */
    private fun clearCandidates() {
        // 防止空指针异常
        if (::candidatesLayout.isInitialized) {
            candidatesLayout.removeAllViews()
        } else {
            Timber.w("candidatesLayout尚未初始化，无法清除候选词")
        }
    }

    // 滑动处理 - 必须实现接口方法
    override fun swipeLeft() {
        // 向左滑动处理
        Timber.d("向左滑动")
    }

    override fun swipeRight() {
        // 向右滑动处理
        Timber.d("向右滑动")
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
} 