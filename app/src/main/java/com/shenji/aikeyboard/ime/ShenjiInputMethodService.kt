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
    private lateinit var candidatesLayout: LinearLayout
    
    // 是否是大写模式
    private var isCapsOn = false
    
    // 当前输入的文本
    private var currentComposing = StringBuilder()
    
    // 协程作用域
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    // 当前查询任务
    private var queryJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("神迹输入法服务已创建")
        
        // 确保词典管理器已初始化
        if (!DictionaryManager.instance.isLoaded()) {
            DictionaryManager.instance.initialize()
        }
    }

    override fun onCreateInputView(): View {
        // 加载键盘视图
        val inputView = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardView = inputView.findViewById(R.id.keyboard_view)
        candidatesLayout = inputView.findViewById(R.id.candidates_layout)
        
        // 初始化键盘
        keyboard = Keyboard(this, R.xml.keyboard_layout)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)
        
        return inputView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        
        // 清空当前输入
        currentComposing.clear()
        clearCandidates()
        
        // 根据输入类型调整键盘
        attribute?.inputType?.let { inputType ->
            // 根据输入类型调整键盘布局
            when {
                inputType and InputType.TYPE_CLASS_NUMBER != 0 -> {
                    // 数字键盘
                    Timber.d("使用数字键盘")
                }
                inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 -> {
                    // 密码输入
                    Timber.d("使用密码键盘")
                }
                else -> {
                    // 默认键盘
                    Timber.d("使用默认键盘")
                }
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
    }

    // 按键释放处理
    override fun onRelease(primaryCode: Int) {
        // 按键释放处理
    }

    // 按键点击处理
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection ?: return
        
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                // 删除键
                val selectedText = inputConnection.getSelectedText(0)
                if (selectedText.isNullOrEmpty()) {
                    // 没有选中文本，删除前一个字符
                    if (currentComposing.isNotEmpty()) {
                        currentComposing.deleteCharAt(currentComposing.length - 1)
                        updateCandidates()
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
                keyboardView.invalidateAllKeys()
            }
            Keyboard.KEYCODE_DONE -> {
                // 完成键
                if (currentComposing.isNotEmpty()) {
                    inputConnection.commitText(currentComposing.toString(), 1)
                    currentComposing.clear()
                    clearCandidates()
                }
                inputConnection.performEditorAction(EditorInfo.IME_ACTION_DONE)
            }
            -100 -> {
                // 自定义切换键
                switchToNextInputMethod(false)
            }
            32 -> {
                // 空格键
                if (currentComposing.isNotEmpty()) {
                    // 提交当前输入
                    inputConnection.commitText(currentComposing.toString(), 1)
                    currentComposing.clear()
                    clearCandidates()
                } else {
                    // 直接输入空格
                    inputConnection.commitText(" ", 1)
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
            val candidates = withContext(Dispatchers.IO) {
                DictionaryManager.instance.searchWords(prefix, 10)
            }
            
            showCandidates(candidates)
        }
    }
    
    /**
     * 显示候选词
     */
    private fun showCandidates(candidates: List<WordFrequency>) {
        candidatesLayout.removeAllViews()
        
        if (candidates.isEmpty()) {
            // 如果没有候选词，显示当前输入的文本
            if (currentComposing.isNotEmpty()) {
                addCandidate(currentComposing.toString(), -1)
            }
            return
        }
        
        // 显示候选词
        for (candidate in candidates) {
            addCandidate(candidate.word, candidate.frequency)
        }
    }
    
    /**
     * 添加候选词到候选区
     */
    private fun addCandidate(word: String, frequency: Int) {
        val textView = TextView(this)
        textView.text = word
        textView.textSize = 18f
        textView.setPadding(16, 8, 16, 8)
        
        // 点击候选词的处理
        textView.setOnClickListener {
            val inputConnection = currentInputConnection ?: return@setOnClickListener
            inputConnection.commitText(word, 1)
            currentComposing.clear()
            clearCandidates()
        }
        
        candidatesLayout.addView(textView)
    }
    
    /**
     * 清空候选词
     */
    private fun clearCandidates() {
        candidatesLayout.removeAllViews()
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