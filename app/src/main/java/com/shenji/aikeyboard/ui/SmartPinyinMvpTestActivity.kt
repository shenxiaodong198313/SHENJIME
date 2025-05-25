package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
 * 4. 实时性能监控
 */
class SmartPinyinMvpTestActivity : AppCompatActivity() {
    
    private lateinit var generateTestButton: Button
    private lateinit var clearCacheButton: Button
    private lateinit var copyAnalysisButton: Button
    private lateinit var loadMoreButton: Button
    private lateinit var backButton: Button
    
    private lateinit var currentInputTextView: TextView
    private lateinit var candidatesTextView: TextView
    private lateinit var analysisTextView: TextView
    private lateinit var performanceTextView: TextView
    
    private val smartEngine = SmartPinyinEngine.getInstance()
    
    // 测试用例池
    private val testCases = listOf(
        // 单字符测试
        "n", "w", "b", "h", "s", "z", "x", "y",
        
        // 短输入测试
        "ni", "wo", "ba", "ha", "sh", "zh", "xi", "ya",
        "nh", "bj", "sh", "zg", "xy", "ws",
        
        // 中等输入测试
        "nihao", "shijie", "weixin", "baidu", "zhongguo",
        "nhao", "sjie", "wxin", "bdu", "zgguo",
        "niha", "shij", "weix", "baid", "zhong",
        
        // 长输入测试
        "nihaoshijie", "wofaxianwenti", "zhongguorenmin",
        "woshibeijingren", "jintiantianqihenhao", "womenyiqilai",
        
        // 混合输入测试
        "wodepy", "woshibjr", "nhmr", "sjhh", "zgrmghs",
        "nhsjhh", "wfxwt", "jtqhh", "wmyql"
    )
    
    private var currentTestInput = ""
    private var currentAnalysisText = ""
    
    // 当前候选词列表（支持懒加载）
    private val currentCandidates = mutableListOf<com.shenji.aikeyboard.model.WordFrequency>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smart_pinyin_mvp_test)
        
        initViews()
        setupListeners()
        updatePerformanceStats()
        
        Timber.d("SmartPinyinEngine MVP测试界面已启动")
    }
    
    private fun initViews() {
        generateTestButton = findViewById(R.id.generateTestButton)
        clearCacheButton = findViewById(R.id.clearCacheButton)
        copyAnalysisButton = findViewById(R.id.copyAnalysisButton)
        loadMoreButton = findViewById(R.id.loadMoreButton)
        backButton = findViewById(R.id.backButton)
        
        currentInputTextView = findViewById(R.id.currentInputTextView)
        candidatesTextView = findViewById(R.id.candidatesTextView)
        analysisTextView = findViewById(R.id.analysisTextView)
        performanceTextView = findViewById(R.id.performanceTextView)
        
        // 设置初始状态
        currentInputTextView.text = "点击按钮生成随机测试用例"
        candidatesTextView.text = "候选词结果将在这里显示..."
        analysisTextView.text = "查询分析将在这里显示..."
    }
    
    private fun setupListeners() {
        // 生成测试按钮
        generateTestButton.setOnClickListener {
            generateRandomTest()
        }
        
        // 清理缓存按钮
        clearCacheButton.setOnClickListener {
            smartEngine.clearCache()
            updatePerformanceStats()
            Toast.makeText(this, "缓存已清理", Toast.LENGTH_SHORT).show()
        }
        
        // 复制分析按钮
        copyAnalysisButton.setOnClickListener {
            copyAnalysisToClipboard()
        }
        
        // 查看更多按钮
        loadMoreButton.setOnClickListener {
            loadMoreCandidates()
        }
        
        // 返回按钮
        backButton.setOnClickListener {
            finish()
        }
    }
    
    /**
     * 生成随机测试用例
     */
    private fun generateRandomTest() {
        // 随机选择一个测试用例
        currentTestInput = testCases[Random.nextInt(testCases.size)]
        
        currentInputTextView.text = "🎯 当前测试输入: \"$currentTestInput\""
        
        // 执行查询测试
        performQueryTest(currentTestInput)
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
                
                // 4. 更新性能统计
                updatePerformanceStats()
                
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
                
                // 检查是否还有更多
                val hasMore = smartEngine.hasMoreCandidates(currentTestInput, currentCount)
                if (!hasMore) {
                    Toast.makeText(this@SmartPinyinMvpTestActivity, "没有更多候选词了", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // 获取更多候选词
                val moreCandidates = smartEngine.getMoreCandidates(currentTestInput, currentCount, 25)
                
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
            appendLine()
            appendLine("🔧 分割方案:")
            if (analysis.segmentations.isNotEmpty()) {
                analysis.segmentations.forEachIndexed { index, seg ->
                    appendLine("  ${index + 1}. \"$seg\"")
                }
            } else {
                appendLine("  无分割方案")
            }
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
                SmartPinyinEngine.QueryStrategy.CHAR_TRIE_ONLY -> {
                    appendLine("1. 检测到单字符输入")
                    appendLine("2. 直接查询单字Trie")
                    appendLine("3. 如果单字Trie未加载，回退到数据库查询单字")
                }
                SmartPinyinEngine.QueryStrategy.TRIE_PRIORITY -> {
                    appendLine("1. 检测到短输入，且基础Trie已加载")
                    appendLine("2. 优先查询单字Trie (最多3个)")
                    appendLine("3. 查询基础Trie补充结果")
                    appendLine("4. 如果结果不足50%，补充数据库查询")
                }
                SmartPinyinEngine.QueryStrategy.HYBRID_QUERY -> {
                    appendLine("1. 检测到中等长度输入")
                    appendLine("2. 尝试完整匹配 (基础Trie)")
                    appendLine("3. 数据库查询补充")
                    appendLine("4. 如果结果不足，进行智能分割查询")
                }
                SmartPinyinEngine.QueryStrategy.DATABASE_PRIORITY -> {
                    appendLine("1. 检测到长输入或复杂输入")
                    appendLine("2. 直接使用数据库查询")
                    appendLine("3. 利用数据库索引优化性能")
                }
                SmartPinyinEngine.QueryStrategy.PROGRESSIVE_FILTER -> {
                    appendLine("1. 检测到渐进式输入模式")
                    appendLine("2. 查找最长前缀缓存")
                    appendLine("3. 基于前缀结果进行过滤")
                    appendLine("4. 如果无前缀缓存，回退到混合查询")
                }
                SmartPinyinEngine.QueryStrategy.ABBREVIATION_MATCH -> {
                    appendLine("1. 检测到缩写输入模式（2-4个辅音字母）")
                    appendLine("2. 优先查询BASE Trie中的词语")
                    appendLine("3. 使用拼音首字母匹配算法")
                    appendLine("4. 如果结果不足，补充查询单字Trie")
                    appendLine("5. 按频率排序返回结果")
                }
            }
            appendLine()
            appendLine("💡 优化建议:")
            when (analysis.inputType) {
                SmartPinyinEngine.InputType.SINGLE_CHAR -> {
                    appendLine("- 单字符查询已优化，性能最佳")
                }
                SmartPinyinEngine.InputType.SHORT_INPUT -> {
                    if (analysis.trieStatus.contains("未加载")) {
                        appendLine("- 建议加载基础Trie以提升性能")
                    } else {
                        appendLine("- 当前策略已优化")
                    }
                }
                SmartPinyinEngine.InputType.MEDIUM_INPUT -> {
                    if (!analysis.cacheHit) {
                        appendLine("- 建议利用渐进式输入缓存")
                    }
                }
                SmartPinyinEngine.InputType.LONG_INPUT -> {
                    appendLine("- 长输入建议使用数据库查询")
                    appendLine("- 考虑智能分割优化")
                }
                SmartPinyinEngine.InputType.MIXED_INPUT -> {
                    appendLine("- 复杂输入建议优化分割算法")
                }
            }
        }
    }
    
    /**
     * 复制分析到剪贴板
     */
    private fun copyAnalysisToClipboard() {
        if (currentAnalysisText.isNotEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SmartPinyinEngine分析", currentAnalysisText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "分析内容已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "没有可复制的分析内容", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 更新性能统计
     */
    private fun updatePerformanceStats() {
        performanceTextView.text = smartEngine.getPerformanceStats()
    }
} 