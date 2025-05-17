package com.shenji.aikeyboard.keyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.shenji.aikeyboard.R
import timber.log.Timber

class ShenjiInputMethodService : InputMethodService() {
    
    private val TAG = "ShenjiIME"
    
    // 键盘视图
    private lateinit var keyboardView: View
    
    // 候选词容器
    private lateinit var candidatesContainer: LinearLayout
    
    // 候选词视图
    private lateinit var candidatesView: LinearLayout
    
    // 当前输入的拼音
    private var composingText = StringBuilder()
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("神迹输入法服务已创建")
        Timber.d("输入法服务生命周期: onCreate")
    }
    
    override fun onCreateInputView(): View {
        Timber.d("输入法服务生命周期: onCreateInputView")
        
        try {
            // 加载键盘布局
            keyboardView = layoutInflater.inflate(R.layout.keyboard_layout, null)
            
            // 初始化候选词区域
            candidatesContainer = keyboardView.findViewById(R.id.candidates_container)
            candidatesView = keyboardView.findViewById(R.id.candidates_view)
            
            // 设置字母按键监听器
            setupLetterKeys()
            
            // 设置功能按键监听器
            setupFunctionKeys()
            
            Timber.d("键盘视图创建成功")
            return keyboardView
        } catch (e: Exception) {
            Timber.e(e, "键盘视图创建失败")
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
        
        // 查询候选词并显示
        searchCandidates(composingText.toString())
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
                
                // 重新查询候选词
                searchCandidates(composingText.toString())
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
               ::candidatesView.isInitialized
    }
    
    // 安全显示候选词区域
    private fun showCandidates() {
        if (areViewComponentsInitialized()) {
            candidatesContainer.visibility = View.VISIBLE
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
    
    // 查询候选词并显示（简化版）
    private fun searchCandidates(pinyin: String) {
        // 清空现有候选词
        candidatesView.removeAllViews()
        
        // 简单模拟候选词（实际应用中应从词典查询）
        val mockCandidates = when (pinyin) {
            "ni" -> listOf("你", "尼", "呢", "妮", "拟")
            "nihao" -> listOf("你好", "你号", "你耗", "尼好", "尼豪")
            "wo" -> listOf("我", "窝", "握", "沃", "卧")
            "ta" -> listOf("他", "她", "它", "塔", "踏")
            "shi" -> listOf("是", "时", "事", "市", "式", "士", "世", "示")
            else -> listOf("候选词1", "候选词2", "候选词3")
        }
        
        // 显示候选词
        for (candidate in mockCandidates) {
            val textView = TextView(this)
            textView.text = candidate
            textView.setTextAppearance(R.style.CandidateWord)
            
            // 设置点击监听器
            textView.setOnClickListener {
                commitText(candidate)
            }
            
            candidatesView.addView(textView)
        }
        
        // 如果没有候选词，显示提示
        if (mockCandidates.isEmpty()) {
            val textView = TextView(this)
            textView.text = getString(R.string.no_candidates)
            textView.setTextAppearance(R.style.CandidateWord)
            candidatesView.addView(textView)
        }
    }
    
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Timber.d("输入视图已启动")
        
        // 重置状态
        composingText.clear()
        
        // 安全检查视图是否已初始化
        if (areViewComponentsInitialized()) {
            hideCandidates()
        }
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        Timber.d("输入已结束")
        
        // 重置状态
        composingText.clear()
        
        // 安全检查视图是否已初始化
        if (areViewComponentsInitialized()) {
            hideCandidates()
        }
    }
} 