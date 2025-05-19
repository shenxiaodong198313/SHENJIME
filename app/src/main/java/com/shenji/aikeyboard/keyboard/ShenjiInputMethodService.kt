package com.shenji.aikeyboard.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.WordFrequency
import kotlinx.coroutines.launch
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
    
    // 工具栏视图
    private lateinit var toolbarView: LinearLayout
    
    // 拼音显示TextView
    private lateinit var pinyinDisplay: TextView
    
    // 应用名称显示TextView
    private lateinit var appNameDisplay: TextView
    
    // 当前输入的拼音
    private var composingText = StringBuilder()
    
    // 当前候选词列表
    private var candidates = listOf<WordFrequency>()
    
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
                Toast.makeText(this, "分词功能已停用", Toast.LENGTH_SHORT).show()
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
        
        // 更新拼音显示
        updatePinyinDisplay(composingText.toString())
        
        // 显示候选词区域并获取候选词
        loadCandidates(composingText.toString())
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
                
                // 更新拼音显示和候选词
                updatePinyinDisplay(composingText.toString())
                loadCandidates(composingText.toString())
            }
        } else {
            // 如果没有拼音，执行标准删除操作
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }
    
    // 处理空格操作
    private fun onSpace() {
        if (composingText.isNotEmpty() && candidates.isNotEmpty()) {
            // 如果有拼音输入和候选词，选择第一个候选词
            val firstCandidate = candidates.firstOrNull()?.word ?: composingText.toString()
            commitText(firstCandidate)
        } else if (composingText.isNotEmpty()) {
            // 如果只有拼音输入，直接提交拼音
            commitText(composingText.toString())
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
        // 清空候选词
        candidates = emptyList()
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
    
    // 更新拼音显示区域
    private fun updatePinyinDisplay(pinyin: String) {
        if (::pinyinDisplay.isInitialized) {
            pinyinDisplay.text = pinyin
        }
    }
    
    // 加载候选词
    private fun loadCandidates(input: String) {
        if (input.isEmpty()) {
            hideCandidates()
            return
        }
        
        // 先显示候选词区域
        showCandidates()
        
        // 使用CandidateManager异步获取候选词
        Handler(Looper.getMainLooper()).post {
            try {
                // 启动异步任务获取候选词
                Thread {
                    try {
                        // 调用全局候选词管理器获取候选词
                        val candidateManager = ShenjiApplication.candidateManager
                        
                        // 在主线程中处理结果
                        Handler(Looper.getMainLooper()).post {
                            runCatching {
                                // 使用kotlinx.coroutines获取候选词
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    try {
                                        val result = candidateManager.generateCandidates(input, 20)
                                        if (result.isNotEmpty()) {
                                            // 更新成员变量
                                            candidates = result
                                            // 显示候选词
                                            updateCandidatesView(result)
                                            Timber.d("成功加载候选词: ${result.size}个")
                                        } else {
                                            Timber.d("未找到候选词")
                                            candidates = emptyList()
                                            clearCandidatesView()
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "加载候选词失败")
                                        Toast.makeText(this@ShenjiInputMethodService, "加载候选词失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }.onFailure { e ->
                                Timber.e(e, "启动协程失败")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "获取候选词线程失败")
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(this@ShenjiInputMethodService, "获取候选词失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            } catch (e: Exception) {
                Timber.e(e, "启动候选词获取任务失败")
            }
        }
    }
    
    // 更新候选词视图
    private fun updateCandidatesView(wordList: List<WordFrequency>) {
        if (!areViewComponentsInitialized()) return
        
        // 清空现有的候选词
        candidatesView.removeAllViews()
        
        // 添加每个候选词按钮
        wordList.forEachIndexed { index, word ->
            val candidateButton = Button(this)
            candidateButton.text = word.word
            candidateButton.textSize = 16f
            candidateButton.setPadding(16, 8, 16, 8)
            
            // 设置左右margin
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            params.setMargins(4, 0, 4, 0)
            candidateButton.layoutParams = params
            
            // 设置点击事件
            candidateButton.setOnClickListener {
                commitText(word.word)
            }
            
            // 添加到候选词容器
            candidatesView.addView(candidateButton)
        }
    }
    
    // 清空候选词视图
    private fun clearCandidatesView() {
        if (areViewComponentsInitialized()) {
            candidatesView.removeAllViews()
        }
    }
    
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // 获取并显示当前应用名称
        if (::appNameDisplay.isInitialized) {
            val packageName = info?.packageName ?: ""
            appNameDisplay.text = getAppNameFromPackage(packageName)
            Timber.d("当前应用: ${appNameDisplay.text}")
        }
    }
    
    // 获取应用名称
    private fun getAppNameFromPackage(packageName: String): String {
        if (packageName.isEmpty()) return ""
        
        val packageManager = packageManager
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            return packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            Timber.e(e, "获取应用名称失败")
            return packageName
        }
    }
}