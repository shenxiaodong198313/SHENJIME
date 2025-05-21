package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.model.Candidate
import com.shenji.aikeyboard.pinyin.InputType
import com.shenji.aikeyboard.viewmodel.PinyinTestViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

/**
 * 拼音测试Fragment - 用于测试拼音分词和候选词查询
 * 使用标准化拼音查询模块
 */
class PinyinTestFragment : Fragment() {

    private lateinit var viewModel: PinyinTestViewModel
    private lateinit var inputEditText: EditText
    private lateinit var stageTextView: TextView
    private lateinit var splitResultTextView: TextView
    private lateinit var queryConditionTextView: TextView
    private lateinit var queryProcessTextView: TextView
    private lateinit var candidateStatsTextView: TextView
    private lateinit var candidatesRecyclerView: RecyclerView
    private lateinit var copyResultButton: View
    private lateinit var clearButton: View
    
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
        
        // 初始化ViewModel
        viewModel = ViewModelProvider(this)[PinyinTestViewModel::class.java]
        
        // 初始化视图
        initViews(view)
        
        // 设置监听器
        setupListeners()
        
        // 设置观察者
        observeViewModel()
    }
    
    private fun initViews(view: View) {
        inputEditText = view.findViewById(R.id.input_edit_text)
        stageTextView = view.findViewById(R.id.stage_text_view)
        splitResultTextView = view.findViewById(R.id.split_result_text_view)
        queryConditionTextView = view.findViewById(R.id.query_condition_text_view)
        queryProcessTextView = view.findViewById(R.id.query_process_text_view)
        candidateStatsTextView = view.findViewById(R.id.candidate_stats_text_view)
        candidatesRecyclerView = view.findViewById(R.id.candidates_recycler_view)
        copyResultButton = view.findViewById(R.id.copy_result_button)
        clearButton = view.findViewById(R.id.clear_button)
        
        // 设置RecyclerView
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
            }
        })
        
        // 设置输入流监听，带防抖动
        viewModel.inputFlow
            .debounce(300) // 300ms防抖
            .onEach { input ->
                // 处理输入
                if (input.isNotEmpty()) {
                    try {
                        viewModel.processInput(input)
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
        }
        
        // 复制结果按钮点击事件
        copyResultButton.setOnClickListener {
            copyTestResult()
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