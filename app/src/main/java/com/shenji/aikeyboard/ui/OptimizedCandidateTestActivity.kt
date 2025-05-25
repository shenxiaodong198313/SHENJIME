package com.shenji.aikeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.keyboard.OptimizedCandidateEngine
import com.shenji.aikeyboard.ui.DictionaryStatusView
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 智能候选词引擎测试界面
 * 
 * 功能：
 * 1. 实时候选词查询测试
 * 2. 输入策略分析展示
 * 3. 多策略查询结果对比
 * 4. 性能统计和缓存管理
 */
class OptimizedCandidateTestActivity : AppCompatActivity() {
    
    private lateinit var inputEditText: EditText
    private lateinit var candidatesTextView: TextView
    private lateinit var analysisTextView: TextView
    private lateinit var performanceTextView: TextView
    private lateinit var clearCacheButton: Button
    private lateinit var testSamplesButton: Button
    private lateinit var mvpTestButton: Button
    private lateinit var dictionaryStatusView: DictionaryStatusView
    
    private val candidateEngine = OptimizedCandidateEngine.getInstance()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_optimized_candidate_test)
        
        initViews()
        setupListeners()
        updatePerformanceStats()
        
        Timber.d("智能候选词测试界面已启动")
    }
    
    private fun initViews() {
        inputEditText = findViewById(R.id.inputEditText)
        candidatesTextView = findViewById(R.id.candidatesTextView)
        analysisTextView = findViewById(R.id.analysisTextView)
        performanceTextView = findViewById(R.id.performanceTextView)
        clearCacheButton = findViewById(R.id.clearCacheButton)
        testSamplesButton = findViewById(R.id.testSamplesButton)
        mvpTestButton = findViewById(R.id.mvpTestButton)
        dictionaryStatusView = findViewById(R.id.dictionary_status_view)
        
        // 设置初始提示
        candidatesTextView.text = "请输入拼音进行测试..."
        analysisTextView.text = "输入策略分析将在这里显示"
        
        // 初始化词库状态监控
        try {
            dictionaryStatusView.refreshStatus()
            Timber.d("词库状态监控组件初始化成功")
        } catch (e: Exception) {
            Timber.e(e, "词库状态监控组件初始化失败")
        }
    }
    
    private fun setupListeners() {
        // 实时输入监听
        inputEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString()?.trim() ?: ""
                if (input.isNotEmpty()) {
                    performQuery(input)
                } else {
                    clearResults()
                }
            }
        })
        
        // 清理缓存按钮
        clearCacheButton.setOnClickListener {
            candidateEngine.clearCache()
            updatePerformanceStats()
            Timber.d("缓存已清理")
        }
        
        // 测试样本按钮
        testSamplesButton.setOnClickListener {
            runTestSamples()
        }
        
        // MVP测试按钮
        mvpTestButton.setOnClickListener {
            runMVPTest()
        }
    }
    
    /**
     * 执行查询
     */
    private fun performQuery(input: String) {
        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                
                // 1. 获取候选词
                val candidates = candidateEngine.getCandidates(input, 15)
                
                // 2. 获取输入分析
                val analysis = candidateEngine.getInputAnalysis(input)
                
                val queryTime = System.currentTimeMillis() - startTime
                
                // 3. 显示结果
                displayResults(candidates.map { "${it.word} (${it.frequency})" }, analysis, queryTime)
                
                // 4. 更新性能统计
                updatePerformanceStats()
                
            } catch (e: Exception) {
                Timber.e(e, "查询失败: $input")
                candidatesTextView.text = "查询失败: ${e.message}"
            }
        }
    }
    
    /**
     * 显示查询结果
     */
    private fun displayResults(candidates: List<String>, analysis: String, queryTime: Long) {
        // 显示候选词
        val candidatesText = if (candidates.isNotEmpty()) {
            buildString {
                appendLine("🎯 候选词结果 (${candidates.size}个，耗时${queryTime}ms):")
                appendLine("=".repeat(50))
                candidates.forEachIndexed { index, candidate ->
                    appendLine("${index + 1}. $candidate")
                }
            }
        } else {
            "❌ 未找到候选词"
        }
        
        candidatesTextView.text = candidatesText
        
        // 显示输入分析
        val analysisText = buildString {
            appendLine("🔍 输入策略分析:")
            appendLine("=".repeat(50))
            appendLine(analysis)
        }
        
        analysisTextView.text = analysisText
    }
    
    /**
     * 清理结果显示
     */
    private fun clearResults() {
        candidatesTextView.text = "请输入拼音进行测试..."
        analysisTextView.text = "输入策略分析将在这里显示"
    }
    
    /**
     * 更新性能统计
     */
    private fun updatePerformanceStats() {
        val stats = candidateEngine.getPerformanceStats()
        performanceTextView.text = stats
    }
    
    /**
     * 运行测试样本
     */
    private fun runTestSamples() {
        lifecycleScope.launch {
            val testCases = listOf(
                // 单字测试
                "ni" to "单字拼音",
                "hao" to "单字拼音",
                
                // 词组测试
                "nihao" to "词组拼音",
                "shijie" to "词组拼音",
                "weixin" to "词组拼音(微信测试)",
                
                // 缩写测试
                "bj" to "首字母缩写",
                "sh" to "首字母缩写",
                "zg" to "首字母缩写",
                
                // 长句测试
                "nihaoshijie" to "长句拼音",
                "wofaxianwenti" to "长句拼音",
                "zhongguorenmin" to "长句拼音",
                
                // 模糊拼音测试
                "zhi" to "模糊拼音",
                "chi" to "模糊拼音",
                "shi" to "模糊拼音",
                
                // 复杂输入测试
                "wofaxianshujukuyouwenti" to "复杂长句",
                "nihaoshijiehenmeihao" to "复杂长句"
            )
            
            val results = StringBuilder()
            results.appendLine("🧪 批量测试结果:")
            results.appendLine("=".repeat(60))
            
            for ((input, description) in testCases) {
                try {
                    val startTime = System.currentTimeMillis()
                    val candidates = candidateEngine.getCandidates(input, 5)
                    val queryTime = System.currentTimeMillis() - startTime
                    
                    results.appendLine("📝 $description: '$input'")
                    results.appendLine("   耗时: ${queryTime}ms, 结果: ${candidates.size}个")
                    
                    if (candidates.isNotEmpty()) {
                        val topCandidates = candidates.take(3).joinToString(", ") { it.word }
                        results.appendLine("   候选词: $topCandidates")
                    } else {
                        results.appendLine("   ❌ 无候选词")
                        
                        // 特别调试weixin
                        if (input == "weixin") {
                            results.appendLine("   🔍 调试weixin:")
                            debugWeixin(results)
                        }
                    }
                    
                    results.appendLine()
                    
                } catch (e: Exception) {
                    results.appendLine("❌ $description '$input' 测试失败: ${e.message}")
                    results.appendLine()
                }
            }
            
            // 显示测试结果
            candidatesTextView.text = results.toString()
            
            // 显示最终性能统计
            updatePerformanceStats()
            
            Timber.d("批量测试完成")
        }
    }
    
    /**
     * 调试weixin查询
     */
    private suspend fun debugWeixin(results: StringBuilder) {
        try {
            // 检查BASE Trie是否加载
            val trieManager = com.shenji.aikeyboard.data.trie.TrieManager.instance
            val isBaseLoaded = trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieType.BASE)
            results.appendLine("     BASE Trie已加载: $isBaseLoaded")
            
            if (!isBaseLoaded) {
                results.appendLine("     正在加载BASE Trie...")
                val loadSuccess = trieManager.loadTrieToMemory(com.shenji.aikeyboard.data.trie.TrieType.BASE)
                results.appendLine("     BASE Trie加载结果: $loadSuccess")
            }
            
            // 直接查询BASE Trie
            val baseResults = trieManager.searchByPrefix(com.shenji.aikeyboard.data.trie.TrieType.BASE, "weixin", 10)
            results.appendLine("     BASE Trie直接查询结果: ${baseResults.size}个")
            if (baseResults.isNotEmpty()) {
                baseResults.take(3).forEach { 
                    results.appendLine("       - ${it.word} (${it.frequency})")
                }
            }
            
            // 检查wei前缀
            val weiResults = trieManager.searchByPrefix(com.shenji.aikeyboard.data.trie.TrieType.BASE, "wei", 5)
            results.appendLine("     'wei'前缀查询结果: ${weiResults.size}个")
            if (weiResults.isNotEmpty()) {
                weiResults.take(3).forEach { 
                    results.appendLine("       - ${it.word} (${it.frequency})")
                }
            }
            
        } catch (e: Exception) {
            results.appendLine("     调试失败: ${e.message}")
        }
    }
    
    /**
     * 运行MVP测试
     */
    private fun runMVPTest() {
        try {
            Timber.d("启动SmartPinyinEngine MVP测试")
            val intent = Intent(this, SmartPinyinMvpTestActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "启动MVP测试失败")
            candidatesTextView.text = "启动MVP测试失败: ${e.message}"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("智能候选词测试界面已关闭")
    }
} 