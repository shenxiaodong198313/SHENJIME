package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.keyboard.SmartPinyinEngine
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.random.Random

/**
 * SmartPinyinEngine MVP测试界面
 * 
 * 功能：
 * 1. 随机生成单个输入测试用例
 * 2. 显示查询结果和详细分析
 * 3. 支持复制查询逻辑信息
 * 4. 按拼音长度分类测试
 */
class SmartPinyinMvpTestActivity : AppCompatActivity() {
    
    private lateinit var toolbar: Toolbar
    private lateinit var generateTestButton: Button
    private lateinit var loadMoreButton: Button
    private lateinit var customInputEditText: EditText
    private lateinit var customTestButton: Button
    
    private lateinit var candidatesTextView: TextView
    private lateinit var analysisTextView: TextView
    
    // 按拼音长度分类的测试按钮
    private lateinit var lengthTest1to2Button: Button
    private lateinit var lengthTest1to3Button: Button
    private lateinit var lengthTest1to4Button: Button
    private lateinit var lengthTest1to5Button: Button
    private lateinit var lengthTest1to6Button: Button
    private lateinit var dictionaryStatusView: DictionaryStatusView
    
    private val smartEngine = SmartPinyinEngine.getInstance()
    
    // 测试用例池
    private val testCases = listOf(
        // 单字符测试
        "n", "w", "b", "h", "s", "z", "x", "y",
        
        // 短输入测试
        "ni", "wo", "ba", "ha", "sh", "zh", "xi", "ya",
        "nh", "bj", "sh", "zg", "xy", "ws",
        
        // 通用*du测试（验证算法通用性）
        "bdu", "sdu", "adu", "ydu", "hdu", "ldu", "mdu", "ndu",
        "zhdu", "chdu", "shdu", "rdu", "zdu", "cdu",
        
        // 通用*an测试
        "ban", "san", "han", "lan", "man", "nan", "wan", "yan",
        "zhan", "chan", "shan", "ran", "zan", "can",
        
        // 中等输入测试
        "nihao", "shijie", "weixin", "baidu", "zhongguo",
        "nhao", "sjie", "wxin", "bdu", "zgguo",
        "niha", "shij", "weix", "baid", "zhong",
        
        // 长输入测试
        "nihaoshijie", "wofaxianwenti", "zhongguorenmin",
        "woshibeijingren", "jintiantianqihenhao", "womenyiqilai",
        
        // 混合输入测试
        "wodepy", "woshibjr", "nhmr", "sjhh", "zgrmghs",
        "nhsjhh", "wfxwt", "jtqhh", "wmyql", "zhrmghg", "zgrmjfj"
    )
    
    private var currentTestInput = ""
    private var currentAnalysisText = ""
    
    // 当前候选词列表（支持懒加载）
    private val currentCandidates = mutableListOf<com.shenji.aikeyboard.model.WordFrequency>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smart_pinyin_mvp_test)
        
        initViews()
        setupToolbar()
        setupListeners()
        
        Timber.d("SmartPinyinEngine MVP测试界面已启动")
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        generateTestButton = findViewById(R.id.generateTestButton)
        loadMoreButton = findViewById(R.id.loadMoreButton)
        customInputEditText = findViewById(R.id.customInputEditText)
        customTestButton = findViewById(R.id.customTestButton)
        
        candidatesTextView = findViewById(R.id.candidatesTextView)
        analysisTextView = findViewById(R.id.analysisTextView)
        
        // 按拼音长度分类的测试按钮
        lengthTest1to2Button = findViewById(R.id.lengthTest1to2Button)
        lengthTest1to3Button = findViewById(R.id.lengthTest1to3Button)
        lengthTest1to4Button = findViewById(R.id.lengthTest1to4Button)
        lengthTest1to5Button = findViewById(R.id.lengthTest1to5Button)
        lengthTest1to6Button = findViewById(R.id.lengthTest1to6Button)
        dictionaryStatusView = findViewById(R.id.dictionary_status_view)
        
        // 设置初始状态
        candidatesTextView.text = "候选词结果将在这里显示..."
        analysisTextView.text = "查询分析将在这里显示..."
        
        // 初始化词库状态监控
        try {
            dictionaryStatusView.refreshStatus()
            Timber.d("词库状态监控组件初始化成功")
        } catch (e: Exception) {
            Timber.e(e, "词库状态监控组件初始化失败")
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "候选词引擎测试"
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_mvp_test, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_clear_cache -> {
                clearCache()
                true
            }
            R.id.action_copy_analysis -> {
                copyAnalysisToClipboard()
                true
            }
            R.id.action_vu_conversion_test -> {
                openVUConversionTest()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupListeners() {
        // 生成测试按钮
        generateTestButton.setOnClickListener {
            generateRandomTest()
        }
        
        // 查看更多按钮
        loadMoreButton.setOnClickListener {
            loadMoreCandidates()
        }
        
        // 自定义输入测试
        customTestButton.setOnClickListener {
            performCustomTest()
        }
        
        // 输入框回车键监听
        customInputEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                performCustomTest()
                true
            } else {
                false
            }
        }
        
        // 按拼音长度分类的测试按钮
        lengthTest1to2Button.setOnClickListener { performLengthTest(1, 2) }
        lengthTest1to3Button.setOnClickListener { performLengthTest(1, 3) }
        lengthTest1to4Button.setOnClickListener { performLengthTest(1, 4) }
        lengthTest1to5Button.setOnClickListener { performLengthTest(1, 5) }
        lengthTest1to6Button.setOnClickListener { performLengthTest(1, 6) }
    }
    
    /**
     * 生成随机测试用例
     */
    private fun generateRandomTest() {
        // 随机选择一个测试用例
        currentTestInput = testCases[Random.nextInt(testCases.size)]
        
        // 更新按钮文本显示当前测试的拼音
        generateTestButton.text = "🎯 当前测试: $currentTestInput"
        
        // 执行查询测试
        performQueryTest(currentTestInput)
    }
    
    /**
     * 执行自定义输入测试
     */
    private fun performCustomTest() {
        val input = customInputEditText.text.toString().trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入拼音", Toast.LENGTH_SHORT).show()
            return
        }
        
        currentTestInput = input
        generateTestButton.text = "🎯 当前测试: $currentTestInput"
        
        // 执行查询测试
        performQueryTest(currentTestInput)
        
        // 隐藏软键盘
        customInputEditText.clearFocus()
    }
    
    /**
     * 打开v/ü转换测试页面
     */
    private fun openVUConversionTest() {
        try {
            val intent = Intent(this, VUConversionTestActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "打开v/ü转换测试页面失败")
            Toast.makeText(this, "无法打开v/ü转换测试页面: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 执行查询测试
     */
    private fun performQueryTest(input: String) {
        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                
                // 1. 获取候选词（首批25个）
                val candidates = smartEngine.getCandidates(input, 25, 0)
                
                // 2. 获取查询分析
                val analysis = smartEngine.getQueryAnalysis(input)
                
                val totalTime = System.currentTimeMillis() - startTime
                
                // 3. 显示结果
                displayTestResults(candidates, analysis, totalTime)
                
            } catch (e: Exception) {
                Timber.e(e, "查询测试失败: $input")
                candidatesTextView.text = "❌ 查询失败: ${e.message}"
                analysisTextView.text = "错误详情: ${e.stackTraceToString()}"
            }
        }
    }
    
    /**
     * 加载更多候选词
     */
    private fun loadMoreCandidates() {
        lifecycleScope.launch {
            try {
                // 获取当前已显示的候选词数量
                val currentCount = currentCandidates.size
                
                // 直接获取更多候选词（使用offset参数）
                val moreCandidates = smartEngine.getCandidates(currentTestInput, 25, currentCount)
                
                if (moreCandidates.isNotEmpty()) {
                    currentCandidates.addAll(moreCandidates)
                    updateCandidatesDisplay()
                    Toast.makeText(this@SmartPinyinMvpTestActivity, "已加载 ${moreCandidates.size} 个更多候选词", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SmartPinyinMvpTestActivity, "没有更多候选词了", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "加载更多候选词失败")
                Toast.makeText(this@SmartPinyinMvpTestActivity, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 显示测试结果
     */
    private fun displayTestResults(
        candidates: List<com.shenji.aikeyboard.model.WordFrequency>,
        analysis: SmartPinyinEngine.QueryAnalysis,
        totalTime: Long
    ) {
        // 更新当前候选词列表
        currentCandidates.clear()
        currentCandidates.addAll(candidates)
        
        // 显示候选词结果
        updateCandidatesDisplay(totalTime)
        
        // 显示详细分析
        displayAnalysis(analysis)
    }
    
    /**
     * 更新候选词显示
     */
    private fun updateCandidatesDisplay(totalTime: Long? = null) {
        val candidatesText = buildString {
            val timeInfo = if (totalTime != null) "，总耗时${totalTime}ms" else ""
            appendLine("🎯 候选词结果 (${currentCandidates.size}个$timeInfo):")
            appendLine("=".repeat(60))
            if (currentCandidates.isNotEmpty()) {
                currentCandidates.forEachIndexed { index, candidate ->
                    appendLine("${index + 1}. ${candidate.word} (频率: ${candidate.frequency})")
                }
                
                // 添加"查看更多"提示
                appendLine()
                appendLine("💡 点击下方按钮查看更多候选词...")
            } else {
                appendLine("❌ 未找到候选词")
            }
        }
        candidatesTextView.text = candidatesText
    }
    
    /**
     * 显示分析信息
     */
    private fun displayAnalysis(analysis: SmartPinyinEngine.QueryAnalysis) {
        // 显示详细分析
        currentAnalysisText = buildString {
            appendLine("🔍 SmartPinyinEngine 查询分析:")
            appendLine("=".repeat(60))
            appendLine("📝 输入内容: \"$currentTestInput\"")
            appendLine("📊 输入类型: ${analysis.inputType}")
            appendLine("🎯 查询策略: ${analysis.queryStrategy}")
            appendLine("⚡ 查询耗时: ${analysis.queryTime}ms")
            appendLine("📈 结果数量: ${analysis.resultCount}")
            appendLine("💾 缓存命中: ${if (analysis.cacheHit) "是" else "否"}")
            appendLine("🌲 Trie状态: ${analysis.trieStatus}")
            appendLine("🔧 分段数量: ${analysis.segmentCount}")
            appendLine("📋 分段结果: ${analysis.segments.joinToString(" + ")}")
            appendLine()
            appendLine("📋 查询逻辑详情:")
            appendLine(getQueryLogicDetails(analysis))
        }
        analysisTextView.text = currentAnalysisText
    }
    
    /**
     * 获取查询逻辑详情
     */
    private fun getQueryLogicDetails(analysis: SmartPinyinEngine.QueryAnalysis): String {
        return buildString {
            when (analysis.queryStrategy) {
                SmartPinyinEngine.QueryStrategy.CHARS_BASE_PRIORITY -> {
                    appendLine("1. 检测到短输入（1-3分段）")
                    appendLine("2. 优先查询单字字典（CHARS）")
                    appendLine("3. 查询2-3字基础词组（BASE）")
                    appendLine("4. 补充地理位置词典（PLACE）")
                    appendLine("5. 补充人名词典（PEOPLE）")
                }
                SmartPinyinEngine.QueryStrategy.ABBREVIATION_MATCH -> {
                    appendLine("1. 检测到缩写输入")
                    appendLine("2. 使用通用缩写匹配算法")
                    appendLine("3. 查询BASE词典中的缩写词组")
                    appendLine("4. 补充地名和人名缩写")
                    appendLine("5. 如需要，补充单字候选")
                }
                SmartPinyinEngine.QueryStrategy.CORRELATION_PRIORITY -> {
                    appendLine("1. 检测到中等输入（4分段）")
                    appendLine("2. 优先查询4字词组词典（CORRELATION）")
                    appendLine("3. 查询5字以上词组词典（ASSOCIATIONAL）")
                    appendLine("4. 补充地理位置词典（PLACE）")
                    appendLine("5. 补充人名词典（PEOPLE）")
                }
                SmartPinyinEngine.QueryStrategy.ASSOCIATIONAL_PRIORITY -> {
                    appendLine("1. 检测到长输入（5-6分段）")
                    appendLine("2. 优先查询5字以上词组词典（ASSOCIATIONAL）")
                    appendLine("3. 补充地理位置词典（PLACE）")
                    appendLine("4. 补充人名词典（PEOPLE）")
                    appendLine("5. 补充诗词词典（POETRY）")
                }
                SmartPinyinEngine.QueryStrategy.STOP_QUERY -> {
                    appendLine("1. 检测到超长输入（7+分段）")
                    appendLine("2. 为了性能考虑，停止查询")
                    appendLine("3. 建议用户缩短输入长度")
                }
            }
            appendLine()
            appendLine("💡 优化建议:")
            when (analysis.inputType) {
                SmartPinyinEngine.InputType.SINGLE_CHAR -> {
                    appendLine("- 单字符查询已优化，性能最佳")
                }
                SmartPinyinEngine.InputType.ABBREVIATION -> {
                    appendLine("- 缩写查询使用通用算法，无硬编码")
                    appendLine("- 支持Trie+Realm混合查询")
                }
                SmartPinyinEngine.InputType.SHORT_INPUT -> {
                    if (analysis.trieStatus.contains("✗")) {
                        appendLine("- 建议加载基础Trie以提升性能")
                    } else {
                        appendLine("- 当前策略已优化")
                    }
                }
                SmartPinyinEngine.InputType.MEDIUM_INPUT -> {
                    if (!analysis.cacheHit) {
                        appendLine("- 建议利用缓存机制")
                    }
                }
                SmartPinyinEngine.InputType.LONG_INPUT -> {
                    appendLine("- 长输入使用专门的长词组词典")
                    appendLine("- 性能已优化")
                }
                SmartPinyinEngine.InputType.OVER_LIMIT -> {
                    appendLine("- 输入过长，已停止查询以保证性能")
                    appendLine("- 建议缩短输入长度到6个分段以内")
                }
            }
        }
    }
    
    /**
     * 清理缓存
     */
    private fun clearCache() {
        smartEngine.clearCache()
        Toast.makeText(this, "缓存已清理", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 复制分析到剪贴板 - 包含候选词结果
     */
    private fun copyAnalysisToClipboard() {
        if (currentAnalysisText.isNotEmpty()) {
            val fullContent = buildString {
                // 添加候选词结果
                appendLine("🎯 候选词结果 (${currentCandidates.size}个):")
                appendLine("=".repeat(60))
                if (currentCandidates.isNotEmpty()) {
                    currentCandidates.forEachIndexed { index, candidate ->
                        appendLine("${index + 1}. ${candidate.word} (频率: ${candidate.frequency})")
                    }
                } else {
                    appendLine("❌ 未找到候选词")
                }
                appendLine()
                appendLine()
                
                // 添加分析内容
                append(currentAnalysisText)
            }
            
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SmartPinyinEngine完整分析", fullContent)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "完整分析内容（含候选词）已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "没有可复制的分析内容", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 执行按拼音长度分类的测试
     */
    private fun performLengthTest(minLength: Int, maxLength: Int) {
        // 根据长度范围筛选测试用例
        val lengthFilteredCases = testCases.filter { it.length in minLength..maxLength }
        
        if (lengthFilteredCases.isEmpty()) {
            Toast.makeText(this, "没有找到长度在${minLength}~${maxLength}范围内的测试用例", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 随机选择一个符合长度要求的测试用例
        val selectedCase = lengthFilteredCases[Random.nextInt(lengthFilteredCases.size)]
        currentTestInput = selectedCase
        
        // 更新按钮文本显示当前测试的拼音
        generateTestButton.text = "📏 长度${minLength}~${maxLength}: $selectedCase"
        
        // 执行查询测试
        performQueryTest(selectedCase)
        
        // 显示提示
        Toast.makeText(this, "正在测试长度${minLength}~${maxLength}: $selectedCase", Toast.LENGTH_SHORT).show()
    }
} 