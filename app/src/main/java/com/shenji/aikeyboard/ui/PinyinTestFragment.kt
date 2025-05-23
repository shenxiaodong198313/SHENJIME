package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.adapter.CandidateAdapter
import com.shenji.aikeyboard.model.Candidate
import com.shenji.aikeyboard.pinyin.InputType
import com.shenji.aikeyboard.viewmodel.PinyinTestViewModel
import com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter
import com.shenji.aikeyboard.utils.PinyinCacheTestHelper
import com.shenji.aikeyboard.utils.PinyinOptimizationTestSuite
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 拼音测试Fragment - 用于测试拼音分词和候选词查询
 * 使用标准化拼音查询模块
 * 新增功能：拼音拆分性能监控
 */
class PinyinTestFragment : Fragment() {

    private lateinit var viewModel: PinyinTestViewModel
    private lateinit var inputEditText: EditText
    private lateinit var currentInputTextView: TextView
    private lateinit var stageTextView: TextView
    private lateinit var splitResultTextView: TextView
    private lateinit var queryConditionTextView: TextView
    private lateinit var queryProcessTextView: TextView
    private lateinit var candidateStatsTextView: TextView
    private lateinit var candidatesRecyclerView: RecyclerView
    private lateinit var copyResultButton: View
    private lateinit var clearButton: View
    
    // 性能监控相关UI组件
    private lateinit var performanceStatsTextView: TextView
    private lateinit var resetPerformanceButton: Button
    private lateinit var clearCacheButton: Button
    private lateinit var runCacheTestButton: Button
    private lateinit var runFullTestSuiteButton: Button
    
    private val candidateAdapter = CandidateAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pinyin_test, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        initViewModel()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        
        // 初始化性能监控显示
        updatePerformanceStats()
    }

    private fun initViews(view: View) {
        inputEditText = view.findViewById(R.id.input_edit_text)
        currentInputTextView = view.findViewById(R.id.current_input_text_view)
        stageTextView = view.findViewById(R.id.stage_text_view)
        splitResultTextView = view.findViewById(R.id.split_result_text_view)
        queryConditionTextView = view.findViewById(R.id.query_condition_text_view)
        queryProcessTextView = view.findViewById(R.id.query_process_text_view)
        candidateStatsTextView = view.findViewById(R.id.candidate_stats_text_view)
        candidatesRecyclerView = view.findViewById(R.id.candidates_recycler_view)
        copyResultButton = view.findViewById(R.id.copy_result_button)
        clearButton = view.findViewById(R.id.clear_button)
        
        // 性能监控相关UI组件
        performanceStatsTextView = view.findViewById(R.id.performance_stats_text_view)
        resetPerformanceButton = view.findViewById(R.id.reset_performance_button)
        clearCacheButton = view.findViewById(R.id.clear_cache_button)
        runCacheTestButton = view.findViewById(R.id.run_cache_test_button)
        runFullTestSuiteButton = view.findViewById(R.id.run_full_test_suite_button)
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[PinyinTestViewModel::class.java]
    }

    private fun setupRecyclerView() {
        candidatesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        candidatesRecyclerView.adapter = candidateAdapter
    }

    @OptIn(FlowPreview::class)
    private fun setupListeners() {
        // 输入框文本变化监听
        inputEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // 不需要实现
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // 不需要实现
            }

            override fun afterTextChanged(s: Editable?) {
                // 将文本发送到ViewModel
                viewModel.updateInput(s.toString())
                
                // 更新性能统计显示
                updatePerformanceStats()
            }
        })
        
        // 设置输入流监听，带防抖动
        viewModel.inputFlow
            .debounce(300) // 300ms防抖
            .onEach { input ->
                // 更新当前输入提示
                lifecycleScope.launch {
                    currentInputTextView.text = "当前输入: $input"
                }
                
                // 处理输入
                if (input.isNotEmpty()) {
                    try {
                        lifecycleScope.launch {
                            viewModel.processInput(input)
                            // 处理完成后更新性能统计
                            updatePerformanceStats()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "处理拼音输入异常")
                    }
                }
            }
            .launchIn(lifecycleScope)
        
        // 清除按钮点击事件
        clearButton.setOnClickListener {
            inputEditText.setText("")
            viewModel.clearInput()
            updatePerformanceStats()
        }
        
        // 复制结果按钮点击事件
        copyResultButton.setOnClickListener {
            copyTestResult()
        }
        
        // 性能监控按钮事件
        resetPerformanceButton.setOnClickListener {
            UnifiedPinyinSplitter.resetPerformanceStats()
            updatePerformanceStats()
            Timber.d("性能统计已重置")
        }
        
        clearCacheButton.setOnClickListener {
            UnifiedPinyinSplitter.clearCache()
            updatePerformanceStats()
            Timber.d("拼音拆分缓存已清空")
        }
        
        runCacheTestButton.setOnClickListener {
            runCachePerformanceTest()
        }

        runFullTestSuiteButton.setOnClickListener {
            runFullTestSuite()
        }

        // 当点击候选词时模拟输入法中选择候选词的行为
        candidateAdapter.setOnItemClickListener { candidate ->
            val currentText = inputEditText.text.toString()
            val currentPosition = inputEditText.selectionStart
            
            // 模拟输入法选择候选词的行为
            // 在当前位置插入选中的候选词和一个空格，表示已确认
            val confirmedText = if (currentText.isEmpty()) {
                candidate.word + " "
            } else {
                // 如果当前有输入，将其替换为候选词并添加空格
                val parts = currentText.split(" ")
                if (parts.size > 1) {
                    // 已有确认文本，替换最后一部分
                    parts.dropLast(1).joinToString(" ") + " " + candidate.word + " "
                } else {
                    // 没有确认文本，直接替换
                    candidate.word + " "
                }
            }
            
            // 更新输入框
            inputEditText.setText(confirmedText)
            // 将光标移动到末尾
            inputEditText.setSelection(confirmedText.length)
        }
    }

    /**
     * 更新性能统计显示
     */
    private fun updatePerformanceStats() {
        try {
            val stats = UnifiedPinyinSplitter.getPerformanceStats()
            performanceStatsTextView.text = stats.toString()
        } catch (e: Exception) {
            Timber.e(e, "更新性能统计显示失败")
            performanceStatsTextView.text = "性能统计获取失败: ${e.message}"
        }
    }

    /**
     * 运行缓存性能测试
     */
    private fun runCachePerformanceTest() {
        lifecycleScope.launch {
            try {
                // 禁用测试按钮，防止重复点击
                runCacheTestButton.isEnabled = false
                runCacheTestButton.text = "测试中..."
                
                // 显示测试开始信息
                performanceStatsTextView.text = "正在运行缓存性能测试，请稍候..."
                
                // 执行测试
                val testResult = PinyinCacheTestHelper.runCachePerformanceTest()
                
                // 显示测试结果
                performanceStatsTextView.text = testResult
                
                Timber.d("缓存性能测试完成")
                
            } catch (e: Exception) {
                Timber.e(e, "缓存性能测试失败")
                performanceStatsTextView.text = "缓存性能测试失败: ${e.message}"
            } finally {
                // 恢复测试按钮
                runCacheTestButton.isEnabled = true
                runCacheTestButton.text = "运行缓存性能测试"
            }
        }
    }

    /**
     * 运行完整测试套件
     */
    private fun runFullTestSuite() {
        lifecycleScope.launch {
            try {
                // 禁用测试按钮，防止重复点击
                runFullTestSuiteButton.isEnabled = false
                runFullTestSuiteButton.text = "测试中..."
                
                // 显示测试开始信息
                performanceStatsTextView.text = "正在运行完整测试套件，请稍候...\n这可能需要几秒钟时间。"
                
                // 执行完整测试套件
                val testResult = PinyinOptimizationTestSuite.runFullTestSuite()
                
                // 显示测试结果
                performanceStatsTextView.text = testResult.comprehensiveReport
                
                Timber.d("完整测试套件完成")
                
            } catch (e: Exception) {
                Timber.e(e, "完整测试套件失败")
                performanceStatsTextView.text = "完整测试套件失败: ${e.message}"
            } finally {
                // 恢复测试按钮
                runFullTestSuiteButton.isEnabled = true
                runFullTestSuiteButton.text = "运行完整测试套件"
            }
        }
    }

    private fun observeViewModel() {
        // 观察输入类型
        viewModel.inputType.observe(viewLifecycleOwner) { type ->
            stageTextView.text = "当前类型: ${getInputTypeDisplayName(type)}"
        }
        
        // 观察匹配规则
        viewModel.matchRule.observe(viewLifecycleOwner) { rule ->
            if (rule.isNotEmpty()) {
                stageTextView.text = "匹配规则: $rule"
            }
        }
        
        // 观察音节拆分结果
        viewModel.syllableSplit.observe(viewLifecycleOwner) { syllables ->
            if (syllables.isEmpty()) {
                splitResultTextView.text = "音节拆分: 无法拆分"
            } else {
                splitResultTextView.text = "音节拆分: ${syllables.joinToString(" + ")}"
            }
        }
        
        // 新增：观察分段拆分结果
        viewModel.segmentedSplit.observe(viewLifecycleOwner) { segments ->
            if (segments.isNotEmpty()) {
                val segmentText = StringBuilder()
                segmentText.append("分段拆分:\n")
                segments.forEachIndexed { index, segment ->
                    segmentText.append("  分段${index + 1}: ${segment.joinToString(" + ")}\n")
                }
                
                // 如果有分段拆分结果，显示在音节拆分下方
                val currentSplitText = splitResultTextView.text.toString()
                if (!currentSplitText.contains("分段拆分:")) {
                    splitResultTextView.text = "$currentSplitText\n\n$segmentText"
                }
            }
        }
        
        // 观察查询条件
        viewModel.queryCondition.observe(viewLifecycleOwner) { condition ->
            queryConditionTextView.text = "查询条件: $condition"
        }
        
        // 观察查询过程
        viewModel.queryProcess.observe(viewLifecycleOwner) { process ->
            queryProcessTextView.text = process
        }
        
        // 观察候选词统计
        viewModel.candidateStats.observe(viewLifecycleOwner) { stats ->
            candidateStatsTextView.text = "候选词统计: 总计${stats.totalCount}个 (单字${stats.singleCharCount}个, 词组${stats.phraseCount}个)"
            
            // 添加来源信息到查询过程区域下方
            val querySourceInfo = "来源: Trie树${stats.fromTrieCount}个, 数据库${stats.fromDatabaseCount}个"
            
            // 在查询过程下方的区域显示来源信息
            queryProcessTextView.let {
                val currentText = it.text.toString()
                
                // 检查是否已经包含来源信息，避免重复添加
                if (!currentText.contains("来源: Trie树")) {
                    // 如果有查询过程，添加换行后再显示来源信息
                    if (currentText.isNotEmpty()) {
                        it.text = "$currentText\n\n$querySourceInfo"
                    } else {
                        it.text = querySourceInfo
                    }
                }
            }
        }
        
        // 观察候选词列表
        viewModel.candidates.observe(viewLifecycleOwner) { candidates ->
            candidateAdapter.submitList(candidates)
        }
    }
    
    /**
     * 获取输入类型的显示名称
     */
    private fun getInputTypeDisplayName(type: InputType): String {
        return when (type) {
            InputType.INITIAL_LETTER -> "首字母"
            InputType.PINYIN_SYLLABLE -> "拼音音节"
            InputType.SYLLABLE_SPLIT -> "音节拆分"
            InputType.ACRONYM -> "首字母缩写"
            else -> "未知"
        }
    }
    
    /**
     * 复制测试结果到剪贴板
     */
    private fun copyTestResult() {
        val input = inputEditText.text.toString()
        val stageText = stageTextView.text.toString()
        val splitText = splitResultTextView.text.toString()
        val queryCondition = queryConditionTextView.text.toString()
        val queryProcess = queryProcessTextView.text.toString()
        val candidateStats = candidateStatsTextView.text.toString()
        
        val result = StringBuilder()
        result.append("拼音测试结果\n")
        result.append("==============\n")
        result.append("用户输入: $input\n")
        result.append("$stageText\n")
        result.append("$splitText\n")
        result.append("$queryCondition\n")
        result.append("$candidateStats\n\n")
        result.append("查询过程:\n")
        result.append("$queryProcess\n\n")
        result.append("候选词列表:\n")
        
        // 获取候选词列表
        val candidates = viewModel.candidates.value ?: emptyList()
        candidates.forEachIndexed { index, candidate ->
            result.append("${index + 1}. ${candidate.word} (拼音: ${candidate.pinyin}, 词频: ${candidate.frequency}, 类型: ${candidate.type})\n")
        }
        
        // 复制到剪贴板
        val clipboard = requireContext().getSystemService(android.content.ClipboardManager::class.java)
        val clip = android.content.ClipData.newPlainText("拼音测试结果", result.toString())
        clipboard.setPrimaryClip(clip)
        
        // 提示用户已复制
        android.widget.Toast.makeText(requireContext(), "已复制测试结果", android.widget.Toast.LENGTH_SHORT).show()
    }
} 