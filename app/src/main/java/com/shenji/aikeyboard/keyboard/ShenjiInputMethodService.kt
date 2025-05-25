package com.shenji.aikeyboard.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.model.WordFrequency
import kotlinx.coroutines.*
import timber.log.Timber
import android.graphics.Color
import kotlinx.coroutines.Dispatchers

class ShenjiInputMethodService : InputMethodService() {
    
    private val TAG = "ShenjiIME"
    
    // 键盘视图
    private lateinit var keyboardView: View
    
    // 候选词视图（独立）
    private lateinit var candidatesViewLayout: View
    
    // 候选词容器
    private lateinit var candidatesContainer: LinearLayout
    
    // 默认候选词视图
    private lateinit var defaultCandidatesView: LinearLayout
    
    // 候选词线性布局（横向滚动）
    private lateinit var candidatesView: LinearLayout
    
    // 展开候选词按钮
    private lateinit var expandCandidatesButton: TextView
    
    // 工具栏视图
    private lateinit var toolbarView: LinearLayout
    
    // 拼音显示TextView
    private lateinit var pinyinDisplay: TextView
    
    // 应用名称显示TextView
    private lateinit var appNameDisplay: TextView
    
    // 应用图标ImageView
    private lateinit var appIcon: ImageView
    
    // 当前输入的拼音
    private var composingText = StringBuilder()
    
    // 当前候选词列表
    private var candidates = listOf<WordFrequency>()
    
    // 标记是否刚提交过候选词，用于处理连续输入
    private var justCommittedText = false
    
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
    
    // 🎯 新增：当前查询任务，用于取消过期的查询（防止闪烁）
    private var currentQueryJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("神迹输入法服务已创建")
        Timber.d("输入法服务生命周期: onCreate")
        
        // 🔧 新增：输入法服务自启动初始化机制
        initializeInputMethodService()
    }
    
    /**
     * 输入法服务自启动初始化机制
     * 确保覆盖安装后输入法服务能够自动加载必要的词典数据
     */
    private fun initializeInputMethodService() {
        try {
            Timber.d("🚀 开始输入法服务自启动初始化...")
            
            // 检查应用是否已经初始化
            val isAppInitialized = try {
                ShenjiApplication.instance
                ShenjiApplication.appContext
                true
            } catch (e: Exception) {
                Timber.w("应用尚未完全初始化: ${e.message}")
                false
            }
            
            if (!isAppInitialized) {
                Timber.w("应用未初始化，跳过输入法服务初始化")
                return
            }
            
            // 在后台线程中执行初始化，避免阻塞输入法服务启动
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    // 1. 确保Realm数据库可用
                    ensureRealmInitialized()
                    
                    // 2. 确保TrieManager已初始化
                    ensureTrieManagerInitialized()
                    
                    // 3. 自动加载核心词典（chars和base）
                    autoLoadCoreTrieDictionaries()
                    
                    // 4. 预热候选词引擎
                    preheatCandidateEngine()
                    
                    Timber.i("✅ 输入法服务自启动初始化完成")
                    
                } catch (e: Exception) {
                    Timber.e(e, "❌ 输入法服务自启动初始化失败: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "输入法服务初始化异常: ${e.message}")
        }
    }
    
    /**
     * 确保Realm数据库已初始化
     */
    private suspend fun ensureRealmInitialized() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // 检查Realm是否已初始化
            val isRealmInitialized = try {
                ShenjiApplication.realm
                true
            } catch (e: Exception) {
                false
            }
            
            if (!isRealmInitialized) {
                Timber.d("🔧 Realm未初始化，开始初始化...")
                // 这里可以添加Realm初始化逻辑，但通常在Application中已经处理
                Timber.w("Realm需要在Application中初始化")
            } else {
                Timber.d("✅ Realm数据库已可用")
            }
        } catch (e: Exception) {
            Timber.e(e, "检查Realm状态失败: ${e.message}")
        }
    }
    
    /**
     * 确保TrieManager已初始化
     */
    private suspend fun ensureTrieManagerInitialized() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val trieManager = ShenjiApplication.trieManager
            
            if (!trieManager.isInitialized()) {
                Timber.d("🔧 TrieManager未初始化，开始初始化...")
                trieManager.init()
                Timber.d("✅ TrieManager初始化完成")
            } else {
                Timber.d("✅ TrieManager已初始化")
            }
        } catch (e: Exception) {
            Timber.e(e, "TrieManager初始化失败: ${e.message}")
        }
    }
    
    /**
     * 自动加载核心词典（chars和base）
     * 这是输入法正常工作的最低要求
     */
    private suspend fun autoLoadCoreTrieDictionaries() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val trieManager = ShenjiApplication.trieManager
            val coreTypes = listOf(
                com.shenji.aikeyboard.data.trie.TrieType.CHARS,
                com.shenji.aikeyboard.data.trie.TrieType.BASE
            )
            
            for (trieType in coreTypes) {
                try {
                    if (!trieManager.isTrieLoaded(trieType)) {
                        Timber.d("🔧 自动加载${getTrieDisplayName(trieType)}词典...")
                        val startTime = System.currentTimeMillis()
                        
                        val loaded = trieManager.loadTrieToMemory(trieType)
                        val endTime = System.currentTimeMillis()
                        
                        if (loaded) {
                            Timber.i("✅ ${getTrieDisplayName(trieType)}词典自动加载成功，耗时${endTime - startTime}ms")
                        } else {
                            Timber.e("❌ ${getTrieDisplayName(trieType)}词典自动加载失败")
                        }
                    } else {
                        Timber.d("✅ ${getTrieDisplayName(trieType)}词典已加载")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "${getTrieDisplayName(trieType)}词典加载异常: ${e.message}")
                }
            }
            
            // 记录加载状态
            val loadedTypes = trieManager.getLoadedTrieTypes()
            Timber.i("📚 当前已加载词典: ${loadedTypes.map { getTrieDisplayName(it) }}")
            
        } catch (e: Exception) {
            Timber.e(e, "核心词典自动加载失败: ${e.message}")
        }
    }
    
    /**
     * 预热候选词引擎
     * 通过执行一次简单查询来确保引擎正常工作
     */
    private suspend fun preheatCandidateEngine() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            Timber.d("🔥 开始预热候选词引擎...")
            val startTime = System.currentTimeMillis()
            
            // 执行一个简单的测试查询
            val engineAdapter = InputMethodEngineAdapter.getInstance()
            val testResults = engineAdapter.getCandidates("ni", 5)
            
            val endTime = System.currentTimeMillis()
            
            if (testResults.isNotEmpty()) {
                Timber.i("✅ 候选词引擎预热成功，耗时${endTime - startTime}ms，测试查询返回${testResults.size}个结果")
                Timber.d("🔥 测试结果: ${testResults.take(3).map { it.word }}")
            } else {
                Timber.w("⚠️ 候选词引擎预热完成，但测试查询无结果，可能词典未完全加载")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "候选词引擎预热失败: ${e.message}")
        }
    }
    
    /**
     * 获取Trie类型的显示名称
     */
    private fun getTrieDisplayName(trieType: com.shenji.aikeyboard.data.trie.TrieType): String {
        return when (trieType) {
            com.shenji.aikeyboard.data.trie.TrieType.CHARS -> "单字"
            com.shenji.aikeyboard.data.trie.TrieType.BASE -> "基础词典"
            com.shenji.aikeyboard.data.trie.TrieType.CORRELATION -> "关联词典"
            com.shenji.aikeyboard.data.trie.TrieType.ASSOCIATIONAL -> "联想词典"
            com.shenji.aikeyboard.data.trie.TrieType.PLACE -> "地名词典"
            com.shenji.aikeyboard.data.trie.TrieType.PEOPLE -> "人名词典"
            com.shenji.aikeyboard.data.trie.TrieType.POETRY -> "诗词词典"
            com.shenji.aikeyboard.data.trie.TrieType.CORRECTIONS -> "纠错词典"
            com.shenji.aikeyboard.data.trie.TrieType.COMPATIBLE -> "兼容词典"
        }
    }
    
    override fun onCreateInputView(): View {
        Timber.d("输入法服务生命周期: onCreateInputView - 开始创建键盘视图")
        
        try {
            // 创建主容器，包含候选词和键盘
            val mainContainer = LinearLayout(this)
            mainContainer.orientation = LinearLayout.VERTICAL
            mainContainer.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            mainContainer.setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0")) // 调试背景色
            
            // 加载候选词布局
            candidatesViewLayout = layoutInflater.inflate(R.layout.candidates_view, null)
            
            // 初始化候选词区域
            candidatesContainer = candidatesViewLayout.findViewById(R.id.candidates_container)
            defaultCandidatesView = candidatesViewLayout.findViewById(R.id.default_candidates_view)
            candidatesView = candidatesViewLayout.findViewById(R.id.candidates_view)
            expandCandidatesButton = candidatesViewLayout.findViewById(R.id.expand_candidates_button)
            
            // 初始化拼音显示区域
            pinyinDisplay = candidatesViewLayout.findViewById(R.id.pinyin_display)
            appNameDisplay = candidatesViewLayout.findViewById(R.id.app_name_display)
            appIcon = candidatesViewLayout.findViewById(R.id.app_icon)
            // 初始化工具栏
            toolbarView = candidatesViewLayout.findViewById(R.id.toolbar_view)
            
            // 设置展开按钮点击事件
            expandCandidatesButton.setOnClickListener {
                Toast.makeText(this, "展开候选词功能 - 正在开发中", Toast.LENGTH_SHORT).show()
                Timber.d("点击了展开候选词按钮")
            }
            
            // 设置工具栏图标点击事件
            setupToolbarIcons()
            
            // 加载键盘布局
            keyboardView = layoutInflater.inflate(R.layout.keyboard_layout, null)
            
            // 设置字母按键监听器
            setupLetterKeys()
            
            // 设置功能按键监听器
            setupFunctionKeys()
            
            // 设置候选词视图布局参数
            val candidatesLayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            candidatesViewLayout.layoutParams = candidatesLayoutParams
            
            // 设置键盘视图布局参数
            val keyboardLayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            keyboardView.layoutParams = keyboardLayoutParams
            
            // 创建分隔线 - 修复：使用更细的分隔线，避免遮挡候选词
            val separator = View(this)
            separator.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1 // 改为1dp高度，减少遮挡
            )
            separator.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0")) // 改为浅灰色，不那么突兀
            
            // 将候选词视图和键盘视图添加到主容器（确保顺序正确）
            mainContainer.addView(candidatesViewLayout, 0) // 候选词在顶部
            mainContainer.addView(separator, 1)            // 分隔线
            mainContainer.addView(keyboardView, 2)         // 键盘在底部
            
            Timber.d("🎯 布局层级: 候选词(index=0) -> 分隔线(index=1) -> 键盘(index=2)")
            
            Timber.d("🎯 整合视图创建成功：候选词+键盘")
            return mainContainer
        } catch (e: Exception) {
            Timber.e(e, "键盘视图创建失败: ${e.message}")
            // 返回一个空的视图作为备选方案
            return LinearLayout(this)
        }
    }
    
    override fun onCreateCandidatesView(): View? {
        Timber.d("🎯 onCreateCandidatesView被调用，但我们已经在InputView中整合了候选词")
        // 返回null，表示不使用独立的候选词视图
        return null
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
    
    /**
     * 设置工具栏图标点击事件
     */
    private fun setupToolbarIcons() {
        // 话术库图标
        candidatesViewLayout.findViewById<ImageView>(R.id.speech_library_icon)?.setOnClickListener {
            Toast.makeText(this, "话术库功能即将上线", Toast.LENGTH_SHORT).show()
            Timber.d("点击了话术库图标")
        }
        
        // AI助手图标
        candidatesViewLayout.findViewById<ImageView>(R.id.ai_assistant_icon)?.setOnClickListener {
            Toast.makeText(this, "AI助手功能即将上线", Toast.LENGTH_SHORT).show()
            Timber.d("点击了AI助手图标")
        }
    }
    
    // 处理字母输入
    private fun onInputLetter(letter: String) {
        // 检查是否刚刚提交了候选词，如果是则开始新的输入流程
        if (justCommittedText) {
            // 确保开始新的输入流程
            composingText.clear()
            justCommittedText = false
            // 再次确保输入连接上的组合文本被清除
            currentInputConnection?.finishComposingText()
            
            // 重置候选词滚动位置
            if (::candidatesViewLayout.isInitialized) {
                candidatesViewLayout.findViewById<HorizontalScrollView>(R.id.candidates_scroll_view)?.scrollTo(0, 0)
            }
        }
        
        // 添加字母到拼音组合中
        composingText.append(letter)
        
        // 输入框显示原始拼音（不带空格）
        currentInputConnection?.setComposingText(composingText, 1)
        
        // 显示候选词区域并获取候选词（包含拼音分段显示）
        loadCandidates(composingText.toString())
    }
    
    // 处理删除操作
    private fun onDelete() {
        if (composingText.isNotEmpty()) {
            // 删除拼音中的最后一个字母
            composingText.deleteCharAt(composingText.length - 1)
            
            if (composingText.isEmpty()) {
                // 如果拼音为空，清空拼音显示并隐藏候选词区域
                updatePinyinDisplay("")
                hideCandidates()
                
                // 结束组合文本状态
                currentInputConnection?.finishComposingText()
            } else {
                // 输入框显示原始拼音（不带空格）
                currentInputConnection?.setComposingText(composingText, 1)
                
                // 获取候选词并更新拼音显示（包含分段）
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
        // 记录之前的输入状态，用于日志
        val hadComposingText = composingText.isNotEmpty()
        
        try {
            // 🎯 取消当前查询任务，避免提交后还有查询结果覆盖
            currentQueryJob?.cancel()
            
            // 提交文本到输入框
            currentInputConnection?.commitText(text, 1)
            
            // 重置组合文本
            composingText.clear()
            
            // 清空拼音显示区域
            if (areViewComponentsInitialized()) {
                pinyinDisplay.text = ""
                hideCandidates()
                
                            // 重置候选词滚动位置
            if (::candidatesViewLayout.isInitialized) {
                candidatesViewLayout.findViewById<HorizontalScrollView>(R.id.candidates_scroll_view)?.scrollTo(0, 0)
            }
            }
            
            // 清空候选词
            candidates = emptyList()
            
            // 确保完全结束组合状态
            currentInputConnection?.finishComposingText()
            
            // 标记刚刚提交了候选词，下次输入时需要重置状态
            justCommittedText = true
            
            Timber.d("提交文本: '$text', 之前有输入: $hadComposingText")
        } catch (e: Exception) {
            Timber.e(e, "提交文本失败: ${e.message}")
        }
    }
    
    // 辅助方法：检查视图组件是否已初始化
    private fun areViewComponentsInitialized(): Boolean {
        return ::candidatesViewLayout.isInitialized &&
               ::candidatesContainer.isInitialized && 
               ::defaultCandidatesView.isInitialized &&
               ::candidatesView.isInitialized
    }
    
    // 添加调试方法：记录候选词视图状态
    private fun logCandidateViewState() {
        if (areViewComponentsInitialized()) {
            Timber.d("候选词视图状态:")
            Timber.d("- 候选词容器可见性: ${visibilityToString(candidatesContainer.visibility)}")
            Timber.d("- 默认候选词视图可见性: ${visibilityToString(defaultCandidatesView.visibility)}")
            Timber.d("- 候选词视图子项数量: ${candidatesView.childCount}")
            Timber.d("- 默认候选词视图高度: ${defaultCandidatesView.height}px")
            Timber.d("- 候选词视图宽度: ${candidatesView.width}px")
        }
    }
    
    // 辅助方法：将可见性值转换为字符串
    private fun visibilityToString(visibility: Int): String {
        return when (visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            View.GONE -> "GONE"
            else -> "UNKNOWN($visibility)"
        }
    }
    
    // 安全显示候选词区域
    private fun showCandidates() {
        if (areViewComponentsInitialized()) {
            // 显示候选词区域时隐藏工具栏
            defaultCandidatesView.visibility = View.VISIBLE
            toolbarView.visibility = View.GONE
            
            // 🔧 固定高度，防止界面跳动
            val fixedHeight = 46 // 固定46dp高度
            val candidatesParams = defaultCandidatesView.layoutParams
            candidatesParams.height = fixedHeight
            candidatesParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            defaultCandidatesView.layoutParams = candidatesParams
            
            // 设置候选词区域背景为透明
            defaultCandidatesView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            Timber.d("🎯 显示候选词区域，固定高度: ${fixedHeight}dp")
            logCandidateViewState()
        }
    }
    
    // 🎯 新增：显示候选词区域但不清空现有内容（防止闪烁）
    private fun showCandidatesWithoutClearing() {
        if (areViewComponentsInitialized()) {
            // 显示候选词区域时隐藏工具栏
            defaultCandidatesView.visibility = View.VISIBLE
            toolbarView.visibility = View.GONE
            
            // 🔧 固定高度和背景色，防止界面跳动和闪烁
            val fixedHeight = 46 // 固定46dp高度
            val candidatesParams = defaultCandidatesView.layoutParams
            candidatesParams.height = fixedHeight
            candidatesParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            defaultCandidatesView.layoutParams = candidatesParams
            
            // 🎯 关键：设置固定的背景色，避免透明背景导致的闪烁
            defaultCandidatesView.setBackgroundColor(android.graphics.Color.parseColor("#F8F8F8")) // 浅灰色背景
            
            Timber.d("🎯 显示候选词区域（不清空），固定高度: ${fixedHeight}dp，固定背景色")
        }
    }
    
    // 隐藏候选词区域
    private fun hideCandidates() {
        if (areViewComponentsInitialized()) {
            // 只隐藏候选词部分，保留拼音栏
            defaultCandidatesView.visibility = View.GONE
            // 隐藏候选词区域时显示工具栏
            toolbarView.visibility = View.VISIBLE
            
            Timber.d("🎯 隐藏候选词区域，显示工具栏")
        }
    }
    
    // 更新拼音显示区域
    private fun updatePinyinDisplay(pinyin: String) {
        if (::pinyinDisplay.isInitialized) {
            pinyinDisplay.text = pinyin
        }
    }
    
    // 加载候选词 - 优化版本，避免闪烁
    private fun loadCandidates(input: String) {
        if (input.isEmpty()) {
            hideCandidates()
            return
        }
        
        // 🔧 修复：不阻塞输入法使用，异步检查并加载Trie
        ensureTrieLoadedAsync()
        
        // 🎯 关键优化：先显示候选词区域，但不清空现有内容
        showCandidatesWithoutClearing()
        
        // 取消之前的查询任务，避免过期结果覆盖新结果
        currentQueryJob?.cancel()
        
        // 🚀 使用最新的SmartPinyinEngine通过适配器
        currentQueryJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                Timber.d("🔍 开始查询候选词: '$input'")
                
                // 🎯 显示加载状态（可选）
                showLoadingHint()
                
                val engineAdapter = InputMethodEngineAdapter.getInstance()
                val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // 添加详细的调试信息
                    Timber.d("🔍 调用引擎查询: '$input'")
                    val candidates = engineAdapter.getCandidates(input, 20)
                    Timber.d("🔍 引擎返回结果: ${candidates.size}个候选词")
                    if (candidates.isNotEmpty()) {
                        Timber.d("🔍 前5个候选词: ${candidates.take(5).map { "${it.word}(${it.frequency})" }}")
                    }
                    candidates
                }
                
                // 检查任务是否被取消
                if (!isActive) {
                    Timber.d("🔍 查询任务已取消: '$input'")
                    return@launch
                }
                
                // 更新拼音显示
                updatePinyinDisplayWithSegmentation(input)
                
                if (result.isNotEmpty()) {
                    candidates = result
                    updateCandidatesViewSmooth(result)
                    Timber.d("🎯 新引擎加载候选词成功: ${result.size}个")
                } else {
                    candidates = emptyList()
                    showNoResultsHintSmooth()
                    Timber.w("🎯 新引擎未找到候选词: '$input'")
                    
                    // 🔧 添加词典状态检查
                    val trieManager = com.shenji.aikeyboard.ShenjiApplication.trieManager
                    Timber.w("📚 词典状态检查:")
                    Timber.w("  - CHARS: ${if (trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieType.CHARS)) "已加载" else "未加载"}")
                    Timber.w("  - BASE: ${if (trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieType.BASE)) "已加载" else "未加载"}")
                    
                    // 🔧 尝试直接查询CHARS词典
                    if (trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieType.CHARS)) {
                        val directResults = trieManager.searchByPrefix(com.shenji.aikeyboard.data.trie.TrieType.CHARS, input, 5)
                        Timber.w("🔧 直接查询CHARS结果: ${directResults.size}个")
                        if (directResults.isNotEmpty()) {
                            Timber.w("🔧 直接查询结果: ${directResults.map { "${it.word}(${it.frequency})" }}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Timber.d("🔍 查询任务被取消: '$input'")
                } else {
                    Timber.e(e, "🎯 新引擎加载候选词失败: '$input'")
                    candidates = emptyList()
                    showNoResultsHintSmooth()
                }
            }
        }
    }
    
    /**
     * 更新拼音显示，包含分段拆分结果
     * 只更新键盘上的拼音显示区域，不影响输入框
     */
    private fun updatePinyinDisplayWithSegmentation(input: String) {
        try {
            Timber.d("开始更新键盘拼音显示，输入: '$input'")
            
            // 获取分段拆分结果
            val engineAdapter = InputMethodEngineAdapter.getInstance()
            val syllables = engineAdapter.getSegments(input)
            
            Timber.d("拼音拆分结果: ${syllables.joinToString("+")}")
            
            // 如果有分段结果，显示分段后的拼音（用上引号代替空格）
            val displayText = if (syllables.isNotEmpty() && syllables.size > 1) {
                val segmentedText = syllables.joinToString("'")
                Timber.d("键盘显示分段拼音: '$segmentedText'")
                segmentedText
            } else {
                // 如果没有分段结果或只有一个音节，显示原始输入
                Timber.d("键盘显示原始拼音: '$input'")
                input
            }
            
            // 更新键盘上的拼音显示区域
            if (::pinyinDisplay.isInitialized) {
                pinyinDisplay.text = displayText
                Timber.d("键盘拼音显示已更新: '$input' -> '$displayText'")
                
                // 强制刷新UI
                pinyinDisplay.invalidate()
                pinyinDisplay.requestLayout()
            } else {
                Timber.e("pinyinDisplay未初始化，无法更新显示")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "更新键盘拼音显示失败: ${e.message}")
            // 如果分段失败，回退到显示原始输入
            if (::pinyinDisplay.isInitialized) {
                pinyinDisplay.text = input
                Timber.d("回退到原始输入显示: '$input'")
            }
        }
    }
    
    // 🎯 新增：显示加载提示（可选）
    private fun showLoadingHint() {
        if (!areViewComponentsInitialized()) {
            return
        }
        
        try {
            // 如果当前没有候选词，显示加载提示
            if (candidatesView.childCount == 0) {
                val loadingText = TextView(this)
                loadingText.text = "正在查询..."
                loadingText.setTextColor(android.graphics.Color.parseColor("#666666")) // 深灰色文字
                loadingText.setTextSize(12f) // 小字体
                loadingText.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                loadingText.typeface = android.graphics.Typeface.DEFAULT
                
                val loadingParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                loadingParams.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                loadingParams.setMargins(pinyinDisplay.paddingStart, 0, 0, 0)
                loadingText.layoutParams = loadingParams
                
                candidatesView.addView(loadingText)
                Timber.d("🎯 显示加载提示")
            }
        } catch (e: Exception) {
            Timber.e(e, "显示加载提示失败: ${e.message}")
        }
    }
    
    // 🎯 新增：平滑更新候选词视图（避免闪烁）
    private fun updateCandidatesViewSmooth(wordList: List<WordFrequency>) {
        Timber.d("🎨 updateCandidatesViewSmooth 开始，候选词数量: ${wordList.size}")
        
        if (!areViewComponentsInitialized()) {
            Timber.e("🎨 视图组件未初始化，无法更新候选词")
            return
        }
        
        try {
            // 🎯 关键：先清空现有内容，然后立即添加新内容，减少闪烁时间
            candidatesView.removeAllViews()
            
            // 强制设置候选词容器可见性和固定背景
            defaultCandidatesView.visibility = View.VISIBLE
            toolbarView.visibility = View.GONE
            defaultCandidatesView.setBackgroundColor(android.graphics.Color.parseColor("#F8F8F8")) // 保持固定背景色
            
            // 🔧 修复：使用像素值设置固定高度，防止跳动
            val density = resources.displayMetrics.density
            val heightInPx = (46 * density).toInt()
            
            val params = defaultCandidatesView.layoutParams
            params.height = heightInPx
            params.width = LinearLayout.LayoutParams.MATCH_PARENT
            defaultCandidatesView.layoutParams = params
            
            // 🔧 确保candidatesView也有正确的高度
            val candidatesParams = candidatesView.layoutParams
            candidatesParams.height = heightInPx
            candidatesView.layoutParams = candidatesParams
            
            if (wordList.isEmpty()) {
                Timber.d("🎨 没有候选词，显示提示文本")
                showNoResultsHintSmooth()
                return
            }
            
            Timber.d("🎨 开始更新候选词视图，数量: ${wordList.size}")
            
            // 创建一个水平的LinearLayout来放置候选词
            val candidatesRow = LinearLayout(this)
            candidatesRow.orientation = LinearLayout.HORIZONTAL
            candidatesRow.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                heightInPx // 使用像素高度
            )
            candidatesRow.gravity = Gravity.CENTER_VERTICAL
            
            // 添加每个候选词按钮到水平布局
            wordList.forEachIndexed { index, word ->
                val candidateText = TextView(this)
                candidateText.text = word.word
                candidateText.gravity = Gravity.CENTER
                
                // 🎯 新样式：去掉背景色框，默认黑色文字，第一个候选词蓝色
                if (index == 0) {
                    candidateText.setTextColor(Color.parseColor("#2196F3")) // 第一个候选词蓝色
                } else {
                    candidateText.setTextColor(Color.parseColor("#333333")) // 其他候选词深灰色（接近黑色）
                }
                
                // 🎯 去掉背景色，设置透明背景
                candidateText.setBackgroundColor(Color.TRANSPARENT)
                
                candidateText.setTextSize(16f)
                candidateText.typeface = android.graphics.Typeface.DEFAULT // 去掉加粗
                candidateText.setPadding(12, 8, 12, 8) // 减少内边距
                
                val textParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    heightInPx // 使用像素高度
                )
                textParams.gravity = Gravity.CENTER_VERTICAL
                
                if (index == 0) {
                    // 🎯 修复：第一个候选词完全左对齐，不添加额外margin
                    textParams.setMargins(0, 4, 4, 4)
                } else {
                    textParams.setMargins(4, 4, 4, 4)
                }
                
                candidateText.layoutParams = textParams
                candidateText.setOnClickListener {
                    commitText(word.word)
                }
                
                candidatesRow.addView(candidateText)
            }
            
            // 将候选词行添加到候选词视图
            candidatesView.addView(candidatesRow)
            
            // 重置水平滚动位置到起始位置
            if (::candidatesViewLayout.isInitialized) {
                candidatesViewLayout.findViewById<HorizontalScrollView>(R.id.candidates_scroll_view)?.scrollTo(0, 0)
            }
            
            Timber.d("🎨 平滑更新候选词完成，使用像素高度: ${heightInPx}px，第一个候选词左对齐")
            
        } catch (e: Exception) {
            Timber.e(e, "平滑更新候选词视图失败: ${e.message}")
        }
    }
    
    // 🎯 新增：平滑显示无结果提示（避免闪烁）
    private fun showNoResultsHintSmooth() {
        if (!areViewComponentsInitialized()) {
            return
        }
        
        try {
            // 清空现有内容
            candidatesView.removeAllViews()
            
            // 确保容器可见且有固定背景
            defaultCandidatesView.visibility = View.VISIBLE
            toolbarView.visibility = View.GONE
            defaultCandidatesView.setBackgroundColor(android.graphics.Color.parseColor("#F8F8F8")) // 保持固定背景色
            
            // 🔧 修复：使用像素值设置固定高度
            val density = resources.displayMetrics.density
            val heightInPx = (46 * density).toInt() // 将46dp转换为像素
            
            val params = defaultCandidatesView.layoutParams
            params.height = heightInPx // 使用像素值
            params.width = LinearLayout.LayoutParams.MATCH_PARENT
            defaultCandidatesView.layoutParams = params
            
            // 🔧 修复：确保candidatesView也有正确的高度
            val candidatesParams = candidatesView.layoutParams
            candidatesParams.height = heightInPx
            candidatesView.layoutParams = candidatesParams
            
            // 创建提示文本
            val hintText = TextView(this)
            hintText.text = "请输入正确拼音"
            hintText.setTextColor(android.graphics.Color.parseColor("#999999"))
            hintText.setTextSize(14f)
            hintText.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            hintText.typeface = android.graphics.Typeface.DEFAULT
            
            // 🔧 修复：使用像素值设置提示文字高度，并去掉额外的margin
            val hintParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                heightInPx // 使用像素值，与容器高度一致
            )
            hintParams.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            // 🎯 修复：不添加额外margin，让提示文字与拼音完全左对齐
            hintParams.setMargins(0, 0, 0, 0)
            hintText.layoutParams = hintParams
            
            candidatesView.addView(hintText)
            
            Timber.d("🎨 平滑显示无结果提示，使用像素高度: ${heightInPx}px，完全左对齐")
            
        } catch (e: Exception) {
            Timber.e(e, "平滑显示无结果提示失败: ${e.message}")
        }
    }
    
    // 更新候选词视图
    private fun updateCandidatesView(wordList: List<WordFrequency>) {
        Timber.d("🎨 updateCandidatesView 开始，候选词数量: ${wordList.size}")
        
        if (!areViewComponentsInitialized()) {
            Timber.e("🎨 视图组件未初始化，无法更新候选词")
            return
        }
        
        try {
            // 清空现有的候选词
            candidatesView.removeAllViews()
            Timber.d("🎨 已清空现有候选词")
            
            // 强制设置候选词容器可见性
            defaultCandidatesView.visibility = View.VISIBLE
            toolbarView.visibility = View.GONE
            
            // 设置候选词视图背景为透明
            defaultCandidatesView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // 🔧 确保候选词视图固定高度，防止跳动
            val params = defaultCandidatesView.layoutParams
            params.height = LinearLayout.LayoutParams.MATCH_PARENT // 使用MATCH_PARENT填满可用空间
            params.width = LinearLayout.LayoutParams.MATCH_PARENT // 固定宽度
            defaultCandidatesView.layoutParams = params
            
            // 如果没有候选词，显示提示信息
            if (wordList.isEmpty()) {
                Timber.d("🎨 没有候选词，显示提示文本")
                showNoResultsHint()
                return
            }
            
            Timber.d("🎨 开始更新候选词视图，数量: ${wordList.size}")
            Timber.d("🎨 候选词列表: ${wordList.take(5).map { "${it.word}(${it.frequency})" }}")
            
            Timber.d("🎨 设置容器可见性完成，背景色已设置为透明")
            
            // 获取拼音显示区域的左边距，确保候选词与拼音完全对齐
            val pinyinPaddingStart = pinyinDisplay.paddingStart
            
            // 设置候选词容器内边距与拼音区域一致
            candidatesView.setPadding(0, candidatesView.paddingTop, candidatesView.paddingRight, candidatesView.paddingBottom)
            
            // 创建一个水平的LinearLayout来放置候选词
            val candidatesRow = LinearLayout(this)
            candidatesRow.orientation = LinearLayout.HORIZONTAL
            candidatesRow.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT // 使用MATCH_PARENT填满可用空间
            )
            candidatesRow.gravity = Gravity.CENTER_VERTICAL // 垂直居中
            
            // 添加每个候选词按钮到水平布局
            Timber.d("🎨 开始创建${wordList.size}个候选词TextView")
            wordList.forEachIndexed { index, word ->
                Timber.d("🎨 创建候选词[$index]: ${word.word}")
                
                val candidateText = TextView(this)
                
                // 显示候选词文本
                candidateText.text = word.word
                
                // 使用TextView而不是Button以减少默认样式的影响
                candidateText.gravity = Gravity.CENTER // 水平和垂直居中
                
                // 🎯 新样式：去掉背景色框，默认黑色文字，第一个候选词蓝色
                if (index == 0) {
                    candidateText.setTextColor(Color.parseColor("#2196F3")) // 第一个候选词蓝色
                } else {
                    candidateText.setTextColor(Color.parseColor("#333333")) // 其他候选词深灰色（接近黑色）
                }
                
                // 🎯 去掉背景色，设置透明背景
                candidateText.setBackgroundColor(Color.TRANSPARENT)
                
                // 设置文字大小和样式
                candidateText.setTextSize(16f) // 适中字体
                candidateText.typeface = android.graphics.Typeface.DEFAULT // 去掉加粗
                
                // 减少内边距
                candidateText.setPadding(12, 8, 12, 8)
                
                // 🔧 设置固定布局参数，防止跳动
                val textParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT // 使用MATCH_PARENT填满容器高度
                )
                textParams.gravity = Gravity.CENTER_VERTICAL // 垂直居中
                
                if (index == 0) {
                    // 🎯 修复：第一个候选词完全左对齐，不添加额外margin
                    textParams.setMargins(0, 4, 4, 4)
                } else {
                    // 其他候选词间距
                    textParams.setMargins(4, 4, 4, 4)
                }
                
                candidateText.layoutParams = textParams
                
                // 设置点击事件
                candidateText.setOnClickListener {
                    commitText(word.word)
                }
                
                // 添加到候选词行
                candidatesRow.addView(candidateText)
                Timber.d("🎨 候选词[$index]已添加到行中: '${word.word}', 文字颜色: ${candidateText.currentTextColor}, 背景: ${candidateText.background}")
            }
            
            Timber.d("🎨 所有候选词TextView创建完成")
            
            // 将候选词行添加到候选词视图
            candidatesView.addView(candidatesRow)
            Timber.d("🎨 候选词行已添加到candidatesView")
            
            // 重置水平滚动位置到起始位置
            if (::candidatesViewLayout.isInitialized) {
            candidatesViewLayout.findViewById<HorizontalScrollView>(R.id.candidates_scroll_view)?.scrollTo(0, 0)
        }
            Timber.d("🎨 滚动位置已重置")
            
            // 强制刷新UI
            candidatesContainer.invalidate()
            candidatesContainer.requestLayout()
            defaultCandidatesView.invalidate()
            defaultCandidatesView.requestLayout()
            candidatesView.invalidate()
            candidatesView.requestLayout()
            Timber.d("🎨 UI刷新完成")
            
            // 记录日志，确认候选词视图状态
            Timber.d("🎨 最终状态检查:")
            Timber.d("🎨 候选词容器可见性: ${this.candidatesContainer.visibility == View.VISIBLE}")
            Timber.d("🎨 默认候选词视图可见性: ${defaultCandidatesView.visibility == View.VISIBLE}")
            Timber.d("🎨 候选词视图子项数量: ${candidatesView.childCount}")
            Timber.d("🎨 候选词行子项数量: ${candidatesRow.childCount}")
            
            // 记录详细的候选词视图状态
            logCandidateViewState()
            
        } catch (e: Exception) {
            Timber.e(e, "更新候选词视图失败: ${e.message}")
        }
    }
    
    // 清空候选词视图
    private fun clearCandidatesView() {
        if (areViewComponentsInitialized()) {
            candidatesView.removeAllViews()
        }
    }
    
    /**
     * 显示无结果提示
     * 当查询不到候选词时，显示"请输入正确拼音"提示
     */
    private fun showNoResultsHint() {
        if (!areViewComponentsInitialized()) {
            return
        }
        
        try {
            // 获取拼音显示区域的左边距，确保提示文本与拼音完全对齐
            val pinyinPaddingStart = pinyinDisplay.paddingStart
            
            // 创建提示文本
            val hintText = TextView(this)
            hintText.text = "请输入正确拼音"
            hintText.setTextColor(android.graphics.Color.parseColor("#999999")) // 灰色文字
            hintText.setTextSize(14f) // 稍小的字体
            hintText.gravity = Gravity.CENTER_VERTICAL or Gravity.START // 左对齐，垂直居中
            hintText.typeface = android.graphics.Typeface.DEFAULT // 普通字体
            
            // 设置布局参数，与拼音左对齐
            val hintParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            hintParams.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            hintParams.setMargins(pinyinPaddingStart, 0, 0, 0) // 与拼音左对齐
            hintText.layoutParams = hintParams
            
            // 添加到候选词视图
            candidatesView.addView(hintText)
            
            // 强制刷新UI
            candidatesView.invalidate()
            candidatesView.requestLayout()
            
            Timber.d("🎨 显示无结果提示: '请输入正确拼音'")
            
        } catch (e: Exception) {
            Timber.e(e, "显示无结果提示失败: ${e.message}")
        }
    }
    
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        
        // 🔧 修复：异步确保Trie可用，不阻塞输入法启动
        ensureTrieLoadedAsync()
        
        // 获取并显示当前应用名称
        if (::appNameDisplay.isInitialized) {
            val packageName = info?.packageName ?: ""
            appNameDisplay.text = getAppNameFromPackage(packageName)
            Timber.d("当前应用: ${appNameDisplay.text}")
        }
        
        // 清空初始化状态，确保没有硬编码的"w"等字符
        composingText.clear()
        updatePinyinDisplay("")
        clearCandidatesView()
        candidates = emptyList()
        justCommittedText = false
        
        // 确保输入连接上的组合文本也被清除
        currentInputConnection?.finishComposingText()
        
        // 确保候选词视图正确初始化
        if (areViewComponentsInitialized()) {
            // 🔧 设置候选词视图布局参数，使用MATCH_PARENT确保有足够空间
            val params = defaultCandidatesView.layoutParams
            params.height = LinearLayout.LayoutParams.MATCH_PARENT // 使用MATCH_PARENT确保有足够空间
            params.width = LinearLayout.LayoutParams.MATCH_PARENT // 固定宽度
            defaultCandidatesView.layoutParams = params
            
            // 初始状态：显示工具栏，隐藏候选词
            toolbarView.visibility = View.VISIBLE
            defaultCandidatesView.visibility = View.GONE
            
            // 记录候选词视图状态
            logCandidateViewState()
        }
        
        Timber.d("🎯 初始化输入视图完成（整合模式），已清空所有状态")
    }
    
    override fun onInitializeInterface() {
        super.onInitializeInterface()
        // 初始化时确保状态清空
        composingText.clear()
        candidates = emptyList()
        justCommittedText = false
        Timber.d("输入法接口初始化，清空所有状态")
    }
    
    // 获取应用名称
    private fun getAppNameFromPackage(packageName: String): String {
        if (packageName.isEmpty()) return ""
        
        val packageManager = packageManager
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            return "${appName}已增强"
        } catch (e: Exception) {
            Timber.e(e, "获取应用名称失败")
            return "${packageName}已增强"
        }
    }
    
    /**
     * 异步确保Trie词典可用（不阻塞输入法使用）
     * 覆盖安装后的优雅处理：
     * 1. 输入法立即可用（通过数据库查询）
     * 2. 后台异步重新加载Trie内存
     * 3. 加载完成后自动切换到高性能Trie查询
     */
    private fun ensureTrieLoadedAsync() {
        try {
            val trieManager = ShenjiApplication.trieManager
            
            // 检查核心词典状态
            val charsLoaded = trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieType.CHARS)
            val baseLoaded = trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieType.BASE)
            
            if (!charsLoaded || !baseLoaded) {
                Timber.i("🔄 检测到Trie内存未加载，启动后台重建...")
                Timber.i("📊 当前状态 - CHARS: ${if (charsLoaded) "✓" else "✗"}, BASE: ${if (baseLoaded) "✓" else "✗"}")
                
                // 🚀 关键：不阻塞输入法，在后台异步加载
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        Timber.i("🔧 开始后台重建Trie内存...")
                        val startTime = System.currentTimeMillis()
                        
                        // 优先加载CHARS词典（单字查询）
                        if (!charsLoaded) {
                            Timber.d("🔧 重建CHARS词典...")
                            val charsSuccess = trieManager.loadTrieToMemory(com.shenji.aikeyboard.data.trie.TrieType.CHARS)
                            if (charsSuccess) {
                                Timber.i("✅ CHARS词典重建成功")
                            } else {
                                Timber.e("❌ CHARS词典重建失败")
                            }
                        }
                        
                        // 然后加载BASE词典（词组查询）
                        if (!baseLoaded) {
                            Timber.d("🔧 重建BASE词典...")
                            val baseSuccess = trieManager.loadTrieToMemory(com.shenji.aikeyboard.data.trie.TrieType.BASE)
                            if (baseSuccess) {
                                Timber.i("✅ BASE词典重建成功")
                            } else {
                                Timber.e("❌ BASE词典重建失败")
                            }
                        }
                        
                        val endTime = System.currentTimeMillis()
                        val loadedTypes = trieManager.getLoadedTrieTypes()
                        Timber.i("🎉 Trie内存重建完成，耗时${endTime - startTime}ms")
                        Timber.i("📚 已加载词典: ${loadedTypes.map { getTrieDisplayName(it) }}")
                        
                        // 重建完成后，可以选择性地预热引擎
                        try {
                            val engineAdapter = InputMethodEngineAdapter.getInstance()
                            val testResults = engineAdapter.getCandidates("ni", 3)
                            if (testResults.isNotEmpty()) {
                                Timber.i("🔥 Trie重建后引擎测试成功: ${testResults.size}个结果")
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Trie重建后引擎测试失败")
                        }
                        
                    } catch (e: Exception) {
                        Timber.e(e, "❌ 后台Trie重建失败: ${e.message}")
                    }
                }
            } else {
                Timber.d("✅ Trie内存状态正常，无需重建")
            }
        } catch (e: Exception) {
            Timber.e(e, "检查Trie状态失败: ${e.message}")
        }
    }
    
}