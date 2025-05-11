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
import com.shenji.aikeyboard.R
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

    override fun onCreate() {
        super.onCreate()
        Timber.d("神迹输入法服务已创建")
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
                    inputConnection.deleteSurroundingText(1, 0)
                } else {
                    // 删除选中的文本
                    inputConnection.commitText("", 1)
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
                inputConnection.performEditorAction(EditorInfo.IME_ACTION_DONE)
            }
            -100 -> {
                // 自定义切换键
                switchToNextInputMethod(false)
            }
            else -> {
                // 普通字符输入
                var code = primaryCode.toChar()
                if (isCapsOn && code in 'a'..'z') {
                    code = code.uppercaseChar()
                }
                inputConnection.commitText(code.toString(), 1)
            }
        }
    }

    // 文本变化处理
    override fun onText(text: CharSequence?) {
        val inputConnection = currentInputConnection ?: return
        text?.let {
            inputConnection.commitText(it, 1)
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