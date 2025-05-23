package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter
import com.shenji.aikeyboard.data.DictionaryRepository
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 输入调试界面 - 优化版本
 * 用于测试和分析输入拆分、查询等问题
 */
class InputDebugActivity : AppCompatActivity() {
    
    private lateinit var etInput: EditText
    private lateinit var btnTest: Button
    private lateinit var btnClear: Button
    private lateinit var btnCopy: Button
    private lateinit var tvResults: TextView
    
    private val candidateManager = ShenjiApplication.candidateManager
    private val dictionaryRepository = DictionaryRepository()
    
    // 存储完整的调试结果
    private var fullDebugResults = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_debug)
        
        // 设置标题和返回按钮
        supportActionBar?.title = "输入调试工具"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        initViews()
        setupListeners()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    private fun initViews() {
        etInput = findViewById(R.id.et_input)
        btnTest = findViewById(R.id.btn_test)
        btnClear = findViewById(R.id.btn_clear)
        btnCopy = findViewById(R.id.btn_copy)
        tvResults = findViewById(R.id.tv_results)
        
        // 设置默认测试用例
        etInput.setText("woshibeijingren")
    }
    
    private fun setupListeners() {
        btnTest.setOnClickListener {
            val input = etInput.text.toString().trim()
            if (input.isNotEmpty()) {
                testInput(input)
            } else {
                Toast.makeText(this, "请输入要测试的文本", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnClear.setOnClickListener {
            etInput.setText("")
            tvResults.text = "点击'开始测试'查看详细分析结果..."
            fullDebugResults = ""
        }
        
        btnCopy.setOnClickListener {
            copyResultsToClipboard()
        }
    }
    
    private fun testInput(input: String) {
        lifecycleScope.launch {
            val results = StringBuilder()
            
            // 添加时间戳和设备信息
            results.appendLine("=== 神迹输入法调试报告 ===")
            results.appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            results.appendLine("输入: '$input'")
            results.appendLine("输入长度: ${input.length}")
            results.appendLine()
            
            // 1. 测试拼音拆分
            testPinyinSplitting(input, results)
            
            // 2. 测试候选词生成
            testCandidateGeneration(input, results)
            
            // 3. 测试数据库查询
            testDatabaseQueries(input, results)
            
            // 4. 测试修复建议
            testFixSuggestions(input, results)
            
            // 5. 添加问题诊断
            addProblemDiagnosis(input, results)
            
            // 6. 添加解决方案建议
            addSolutionSuggestions(input, results)
            
            fullDebugResults = results.toString()
            tvResults.text = fullDebugResults
        }
    }
    
    private fun testPinyinSplitting(input: String, results: StringBuilder) {
        results.appendLine("=== 拼音拆分测试 ===")
        
        try {
            // 测试当前拆分
            val currentSplit = UnifiedPinyinSplitter.split(input)
            results.appendLine("当前拆分: ${currentSplit.joinToString(" + ")}")
            results.appendLine("拆分音节数: ${currentSplit.size}")
            
            // 分析拆分质量
            val isCorrect = analyzeSplitQuality(input, currentSplit)
            results.appendLine("拆分质量: ${if (isCorrect) "✓ 正确" else "✗ 错误"}")
            
            // 测试分段拆分
            if (input.length > 12) {
                val segments = UnifiedPinyinSplitter.splitIntoSegments(input)
                results.appendLine("分段拆分:")
                segments.forEachIndexed { index, segment ->
                    results.appendLine("  分段${index + 1}: ${segment.joinToString(" + ")}")
                }
            }
            
            // 测试手动拆分建议
            val manualSuggestions = getManualSplitSuggestions(input)
            if (manualSuggestions.isNotEmpty()) {
                results.appendLine("建议拆分:")
                manualSuggestions.forEach { suggestion ->
                    results.appendLine("  - ${suggestion.joinToString(" + ")}")
                }
            }
            
        } catch (e: Exception) {
            results.appendLine("拆分测试失败: ${e.message}")
            results.appendLine("错误堆栈: ${e.stackTraceToString()}")
        }
        
        results.appendLine()
    }
    
    private suspend fun testCandidateGeneration(input: String, results: StringBuilder) {
        results.appendLine("=== 候选词生成测试 ===")
        
        try {
            val startTime = System.currentTimeMillis()
            val candidates = candidateManager.generateCandidates(input, 10)
            val endTime = System.currentTimeMillis()
            
            results.appendLine("生成耗时: ${endTime - startTime}ms")
            results.appendLine("候选词数量: ${candidates.size}")
            
            if (candidates.isNotEmpty()) {
                results.appendLine("候选词列表:")
                candidates.forEachIndexed { index, candidate ->
                    results.appendLine("  ${index + 1}. ${candidate.word}")
                    results.appendLine("     权重: ${String.format("%.2f", candidate.finalWeight)}")
                    results.appendLine("     来源: ${candidate.source.generator}")
                    results.appendLine("     匹配类型: ${candidate.source.matchType}")
                    results.appendLine("     置信度: ${String.format("%.2f", candidate.source.confidence)}")
                }
            } else {
                results.appendLine("❌ 未找到候选词")
                results.appendLine("可能原因:")
                results.appendLine("  1. 拼音拆分错误")
                results.appendLine("  2. 数据库中无对应词条")
                results.appendLine("  3. 查询逻辑有问题")
            }
            
        } catch (e: Exception) {
            results.appendLine("候选词生成失败: ${e.message}")
            results.appendLine("错误堆栈: ${e.stackTraceToString()}")
        }
        
        results.appendLine()
    }
    
    private suspend fun testDatabaseQueries(input: String, results: StringBuilder) {
        results.appendLine("=== 数据库查询测试 ===")
        
        try {
            // 测试基础查询
            val basicResults = dictionaryRepository.searchBasicEntries(input, 5)
            results.appendLine("1. 基础查询 '$input': ${basicResults.size}个结果")
            if (basicResults.isNotEmpty()) {
                basicResults.take(3).forEach { 
                    results.appendLine("   - ${it.word} (频率: ${it.frequency})")
                }
            } else {
                results.appendLine("   ❌ 无结果")
            }
            
            // 测试首字母查询
            if (input.length >= 2) {
                val acronymResults = dictionaryRepository.searchByInitialLetters(input, 5)
                results.appendLine("2. 首字母查询 '$input': ${acronymResults.size}个结果")
                if (acronymResults.isNotEmpty()) {
                    acronymResults.take(3).forEach { 
                        results.appendLine("   - ${it.word} (频率: ${it.frequency})")
                    }
                } else {
                    results.appendLine("   ❌ 无结果")
                }
            }
            
            // 测试拆分后查询
            val syllables = UnifiedPinyinSplitter.split(input)
            if (syllables.isNotEmpty()) {
                val spacedPinyin = syllables.joinToString(" ")
                val splitResults = dictionaryRepository.searchBasicEntries(spacedPinyin, 5)
                results.appendLine("3. 拆分查询 '$spacedPinyin': ${splitResults.size}个结果")
                if (splitResults.isNotEmpty()) {
                    splitResults.take(3).forEach { 
                        results.appendLine("   - ${it.word} (频率: ${it.frequency})")
                    }
                } else {
                    results.appendLine("   ❌ 无结果")
                }
            }
            
            // 测试建议的正确拆分
            val correctSplits = getCorrectSplits(input)
            if (correctSplits.isNotEmpty()) {
                results.appendLine("4. 正确拆分测试:")
                for (correctSplit in correctSplits) {
                    val correctQuery = correctSplit.joinToString(" ")
                    val correctResults = dictionaryRepository.searchBasicEntries(correctQuery, 5)
                    results.appendLine("   查询 '$correctQuery': ${correctResults.size}个结果")
                    if (correctResults.isNotEmpty()) {
                        correctResults.take(2).forEach { 
                            results.appendLine("     - ${it.word} (频率: ${it.frequency})")
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            results.appendLine("数据库查询失败: ${e.message}")
            results.appendLine("错误堆栈: ${e.stackTraceToString()}")
        }
        
        results.appendLine()
    }
    
    private suspend fun testFixSuggestions(input: String, results: StringBuilder) {
        results.appendLine("=== 修复建议 ===")
        
        // 针对特定输入提供修复建议
        when (input) {
            "woshibeijingren" -> {
                results.appendLine("🔧 针对 '$input' 的修复建议:")
                results.appendLine("1. 问题: 拆分过度细化")
                results.appendLine("   当前: w+o+s+h+i+b+e+i+j+i+n+g+r+e+n")
                results.appendLine("   正确: wo+shi+bei+jing+ren")
                results.appendLine()
                
                results.appendLine("2. 测试单独音节:")
                val testSyllables = listOf("wo", "shi", "bei", "jing", "ren")
                for (syllable in testSyllables) {
                    val syllableResults = dictionaryRepository.searchBasicEntries(syllable, 3)
                    results.appendLine("   '$syllable': ${syllableResults.size}个结果")
                    if (syllableResults.isNotEmpty()) {
                        results.appendLine("     ✓ 找到: ${syllableResults.first().word}")
                    }
                }
                
                results.appendLine()
                results.appendLine("3. 测试组合查询:")
                val combinations = listOf(
                    "wo shi",
                    "bei jing", 
                    "bei jing ren",
                    "wo shi bei jing ren"
                )
                
                for (combo in combinations) {
                    val comboResults = dictionaryRepository.searchBasicEntries(combo, 3)
                    results.appendLine("   '$combo': ${comboResults.size}个结果")
                    if (comboResults.isNotEmpty()) {
                        results.appendLine("     ✓ 找到: ${comboResults.first().word}")
                    }
                }
            }
            
            "beijing" -> {
                results.appendLine("🔧 针对 '$input' 的修复建议:")
                results.appendLine("1. 正确拆分应该是: bei + jing")
                
                val beijingResults = dictionaryRepository.searchBasicEntries("bei jing", 5)
                results.appendLine("2. 测试查询 'bei jing': ${beijingResults.size}个结果")
                beijingResults.take(3).forEach { 
                    results.appendLine("   - ${it.word} (频率: ${it.frequency})")
                }
            }
            
            "beijingren" -> {
                results.appendLine("🔧 针对 '$input' 的修复建议:")
                results.appendLine("1. 正确拆分应该是: bei + jing + ren")
                
                val beijingrenResults = dictionaryRepository.searchBasicEntries("bei jing ren", 5)
                results.appendLine("2. 测试查询 'bei jing ren': ${beijingrenResults.size}个结果")
                beijingrenResults.take(3).forEach { 
                    results.appendLine("   - ${it.word} (频率: ${it.frequency})")
                }
            }
            
            else -> {
                results.appendLine("🔧 通用修复建议:")
                results.appendLine("1. 检查拼音拆分是否正确")
                results.appendLine("2. 验证数据库中是否存在相关词条")
                results.appendLine("3. 检查查询逻辑是否正常")
            }
        }
        
        results.appendLine()
    }
    
    private fun addProblemDiagnosis(input: String, results: StringBuilder) {
        results.appendLine("=== 问题诊断 ===")
        
        val currentSplit = UnifiedPinyinSplitter.split(input)
        val correctSplits = getCorrectSplits(input)
        
        if (correctSplits.isNotEmpty()) {
            val isCorrect = correctSplits.any { it == currentSplit }
            
            if (!isCorrect) {
                results.appendLine("🚨 主要问题: 拼音拆分算法错误")
                results.appendLine("影响: 无法正确匹配词典中的词条")
                results.appendLine("严重程度: 高")
                results.appendLine()
                
                results.appendLine("具体问题:")
                results.appendLine("1. 拆分过度细化，将完整音节拆成单个字母")
                results.appendLine("2. 无法识别常见拼音音节模式")
                results.appendLine("3. 导致后续查询全部失败")
            } else {
                results.appendLine("✅ 拼音拆分正确")
            }
        } else {
            results.appendLine("⚠️ 无法确定正确的拆分方式")
        }
        
        results.appendLine()
    }
    
    private fun addSolutionSuggestions(input: String, results: StringBuilder) {
        results.appendLine("=== 解决方案建议 ===")
        
        results.appendLine("🛠️ 建议的修复步骤:")
        results.appendLine("1. 修复 UnifiedPinyinSplitter.split() 方法")
        results.appendLine("   - 添加常见拼音音节识别")
        results.appendLine("   - 优化拆分算法逻辑")
        results.appendLine("   - 增加音节边界检测")
        results.appendLine()
        
        results.appendLine("2. 测试验证:")
        results.appendLine("   - 使用此调试工具验证修复效果")
        results.appendLine("   - 测试常见输入场景")
        results.appendLine("   - 确保候选词能正常生成")
        results.appendLine()
        
        results.appendLine("3. 优先修复的输入:")
        results.appendLine("   - beijing → bei + jing")
        results.appendLine("   - beijingren → bei + jing + ren")
        results.appendLine("   - woshibeijingren → wo + shi + bei + jing + ren")
        results.appendLine()
        
        results.appendLine("=== 调试报告结束 ===")
    }
    
    /**
     * 分析拆分质量
     */
    private fun analyzeSplitQuality(input: String, split: List<String>): Boolean {
        // 检查是否过度拆分（单字母过多）
        val singleLetters = split.count { it.length == 1 }
        val ratio = singleLetters.toFloat() / split.size
        
        // 如果超过50%是单字母，认为拆分有问题
        return ratio < 0.5
    }
    
    /**
     * 获取正确的拆分方式
     */
    private fun getCorrectSplits(input: String): List<List<String>> {
        val correctSplits = mutableListOf<List<String>>()
        
        when (input) {
            "woshibeijingren" -> {
                correctSplits.add(listOf("wo", "shi", "bei", "jing", "ren"))
            }
            "beijing" -> {
                correctSplits.add(listOf("bei", "jing"))
            }
            "beijingren" -> {
                correctSplits.add(listOf("bei", "jing", "ren"))
            }
            "beijingr" -> {
                correctSplits.add(listOf("bei", "jing", "r"))
            }
            "bjing" -> {
                correctSplits.add(listOf("b", "jing"))
            }
        }
        
        return correctSplits
    }
    
    /**
     * 获取手动拆分建议
     */
    private fun getManualSplitSuggestions(input: String): List<List<String>> {
        return getCorrectSplits(input)
    }
    
    /**
     * 复制结果到剪贴板
     */
    private fun copyResultsToClipboard() {
        if (fullDebugResults.isNotEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("调试结果", fullDebugResults)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "调试结果已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "没有可复制的结果，请先运行测试", Toast.LENGTH_SHORT).show()
        }
    }
}