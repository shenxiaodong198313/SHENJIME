package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.keyboard.ContinuousPinyinEngine
import com.shenji.aikeyboard.keyboard.SmartPinyinEngine
import com.shenji.aikeyboard.model.WordFrequency
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.system.measureTimeMillis

/**
 * 连续拼音测试Activity
 * 
 * 用于测试和验证连续拼音功能的实现效果
 * 包含预设测试用例和自定义输入测试
 */
class ContinuousPinyinTestActivity : AppCompatActivity() {
    
    private lateinit var inputEditText: EditText
    private lateinit var testButton: Button
    private lateinit var clearButton: Button
    private lateinit var resultTextView: TextView
    private lateinit var performanceTextView: TextView
    private lateinit var candidatesRecyclerView: RecyclerView
    
    private lateinit var smartEngine: SmartPinyinEngine
    private lateinit var continuousEngine: ContinuousPinyinEngine
    
    private val currentCandidates = mutableListOf<WordFrequency>()
    private lateinit var candidatesAdapter: CandidatesAdapter
    
    // 预设测试用例
    private val testCases = listOf(
        "woshiyigenvhai" to "我是一个女孩",
        "woshiyigenanren" to "我是一个男人", 
        "jintiandianqihenhao" to "今天天气很好",
        "womenyiqichifan" to "我们一起吃饭",
        "zheshiyibenhenhaodeshui" to "这是一本很好的书",
        "tashiyigecongmingdehaizi" to "他是一个聪明的孩子",
        "womingtianyaoqushangban" to "我明天要去上班",
        "zhegegongzuohennanduo" to "这个工作很难做",
        "woxihuanhepengyouyiqiwanr" to "我喜欢和朋友一起玩儿",
        "zheshiyijiahenhaodefandian" to "这是一家很好的饭店"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_continuous_pinyin_test)
        
        initViews()
        initEngines()
        setupRecyclerView()
        setupListeners()
        
        // 显示测试说明
        showTestInstructions()
    }
    
    private fun initViews() {
        inputEditText = findViewById(R.id.et_input)
        testButton = findViewById(R.id.btn_test)
        clearButton = findViewById(R.id.btn_clear)
        resultTextView = findViewById(R.id.tv_result)
        performanceTextView = findViewById(R.id.tv_performance)
        candidatesRecyclerView = findViewById(R.id.rv_candidates)
    }
    
    private fun initEngines() {
        smartEngine = SmartPinyinEngine.getInstance()
        continuousEngine = ContinuousPinyinEngine.getInstance()
    }
    
    private fun setupRecyclerView() {
        candidatesAdapter = CandidatesAdapter(currentCandidates) { candidate ->
            // 点击候选词的处理
            Toast.makeText(this, "选择: ${candidate.word}", Toast.LENGTH_SHORT).show()
        }
        
        candidatesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ContinuousPinyinTestActivity)
            adapter = candidatesAdapter
        }
    }
    
    private fun setupListeners() {
        testButton.setOnClickListener {
            val input = inputEditText.text.toString().trim()
            if (input.isNotEmpty()) {
                performTest(input)
            } else {
                runPresetTests()
            }
        }
        
        clearButton.setOnClickListener {
            clearResults()
        }
        
        // 预设测试用例按钮
        setupPresetTestButtons()
    }
    
    private fun setupPresetTestButtons() {
        val presetContainer = findViewById<androidx.appcompat.widget.LinearLayoutCompat>(R.id.ll_preset_tests)
        
        testCases.forEachIndexed { index, (input, expected) ->
            val button = Button(this).apply {
                text = "测试${index + 1}: ${input.take(8)}..."
                setOnClickListener {
                    inputEditText.setText(input)
                    performTest(input)
                }
            }
            presetContainer.addView(button)
        }
    }
    
    private fun performTest(input: String) {
        lifecycleScope.launch {
            try {
                resultTextView.text = "🔍 正在测试: $input"
                
                val totalTime = measureTimeMillis {
                    // 1. 测试SmartPinyinEngine（包含连续拼音功能）
                    val smartResults = smartEngine.getCandidates(input, 20)
                    
                    // 2. 测试ContinuousPinyinEngine（直接调用）
                    val continuousResult = continuousEngine.queryContinuous(input, 20)
                    
                    // 3. 显示结果
                    displayTestResults(input, smartResults, continuousResult)
                }
                
                // 4. 显示性能统计
                displayPerformanceStats(totalTime)
                
            } catch (e: Exception) {
                Timber.e(e, "测试失败: $input")
                resultTextView.text = "❌ 测试失败: ${e.message}"
            }
        }
    }
    
    private fun displayTestResults(
        input: String,
        smartResults: List<WordFrequency>,
        continuousResult: ContinuousPinyinEngine.ContinuousQueryResult
    ) {
        val resultText = buildString {
            appendLine("📊 测试结果: $input")
            appendLine()
            
            // SmartPinyinEngine结果
            appendLine("🎯 SmartPinyinEngine (${smartResults.size}个结果):")
            smartResults.take(10).forEachIndexed { index, candidate ->
                appendLine("  ${index + 1}. ${candidate.word} (${candidate.frequency})")
            }
            appendLine()
            
            // ContinuousPinyinEngine结果
            appendLine("🚀 ContinuousPinyinEngine:")
            appendLine("  分词方案: ${continuousResult.segmentationSchemes.size}种")
            continuousResult.segmentationSchemes.forEachIndexed { index, scheme ->
                appendLine("    方案${index + 1}: ${scheme.joinToString(" + ")}")
            }
            appendLine()
            
            appendLine("  最佳组合 (${continuousResult.bestCombinations.size}个):")
            continuousResult.bestCombinations.take(10).forEachIndexed { index, candidate ->
                appendLine("    ${index + 1}. ${candidate.word} (${candidate.frequency})")
            }
            appendLine()
            
            // 性能对比
            appendLine("⏱️ 性能:")
            appendLine("  连续拼音查询耗时: ${continuousResult.queryTime}ms")
            appendLine("  缓存命中: ${if (continuousResult.cacheHit) "是" else "否"}")
        }
        
        resultTextView.text = resultText
        
        // 更新候选词列表
        currentCandidates.clear()
        currentCandidates.addAll(smartResults)
        candidatesAdapter.notifyDataSetChanged()
    }
    
    private fun displayPerformanceStats(totalTime: Long) {
        val statsText = buildString {
            appendLine("📈 性能统计 (总耗时: ${totalTime}ms)")
            appendLine()
            appendLine(smartEngine.getPerformanceStats())
        }
        
        performanceTextView.text = statsText
    }
    
    private fun runPresetTests() {
        lifecycleScope.launch {
            resultTextView.text = "🔄 运行预设测试用例..."
            
            val allResults = mutableListOf<String>()
            
            for ((index, testCase) in testCases.withIndex()) {
                val (input, expected) = testCase
                
                try {
                    val time = measureTimeMillis {
                        val results = smartEngine.getCandidates(input, 10)
                        val topResult = results.firstOrNull()?.word ?: "无结果"
                        
                        val status = if (topResult.contains(expected.take(2))) "✅" else "❌"
                        allResults.add("测试${index + 1}: $input -> $topResult $status")
                    }
                    
                    allResults.add("  耗时: ${time}ms")
                    
                } catch (e: Exception) {
                    allResults.add("测试${index + 1}: $input -> 失败: ${e.message}")
                }
            }
            
            val finalResult = buildString {
                appendLine("🎯 预设测试用例结果:")
                appendLine()
                allResults.forEach { appendLine(it) }
                appendLine()
                appendLine("测试完成！")
            }
            
            resultTextView.text = finalResult
            displayPerformanceStats(0)
        }
    }
    
    private fun clearResults() {
        inputEditText.setText("")
        resultTextView.text = "等待测试..."
        performanceTextView.text = ""
        currentCandidates.clear()
        candidatesAdapter.notifyDataSetChanged()
        
        // 清理引擎缓存
        smartEngine.clearCache()
        continuousEngine.clearCache()
        
        Toast.makeText(this, "已清理结果和缓存", Toast.LENGTH_SHORT).show()
    }
    
    private fun showTestInstructions() {
        val instructions = """
            📋 连续拼音测试说明:
            
            1. 输入连续拼音（如：woshiyigenvhai）
            2. 点击"开始测试"查看结果
            3. 或点击预设测试用例按钮
            4. 空输入时点击"开始测试"运行所有预设用例
            
            🎯 测试目标:
            - 验证连续拼音分词准确性
            - 检查候选词质量
            - 评估查询性能
            - 对比不同引擎效果
        """.trimIndent()
        
        resultTextView.text = instructions
    }
    
    /**
     * 候选词适配器
     */
    private class CandidatesAdapter(
        private val candidates: List<WordFrequency>,
        private val onItemClick: (WordFrequency) -> Unit
    ) : RecyclerView.Adapter<CandidatesAdapter.ViewHolder>() {
        
        class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val wordTextView: TextView = itemView.findViewById(android.R.id.text1)
            val frequencyTextView: TextView = itemView.findViewById(android.R.id.text2)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val candidate = candidates[position]
            holder.wordTextView.text = "${position + 1}. ${candidate.word}"
            holder.frequencyTextView.text = "频率: ${candidate.frequency}"
            
            holder.itemView.setOnClickListener {
                onItemClick(candidate)
            }
        }
        
        override fun getItemCount(): Int = candidates.size
    }
} 