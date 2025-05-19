package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.databinding.ActivityPinyinTestToolBinding
import com.shenji.aikeyboard.viewmodel.PinyinTestViewModel
import com.shenji.aikeyboard.viewmodel.PinyinTestViewModel.InputStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class PinyinTestToolActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinyinTestToolBinding
    private lateinit var viewModel: PinyinTestViewModel
    private lateinit var candidateAdapter: CandidateAdapter

    @OptIn(FlowPreview::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinyinTestToolBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViewModel()
        setupUI()
        setupInputListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "拼音输入法测试工具"
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[PinyinTestViewModel::class.java]
    }

    private fun setupUI() {
        // 设置候选词列表
        candidateAdapter = CandidateAdapter()
        binding.candidateList.apply {
            layoutManager = LinearLayoutManager(this@PinyinTestToolActivity)
            adapter = candidateAdapter
        }

        // 清除按钮
        binding.btnClear.setOnClickListener {
            binding.inputField.text?.clear()
            viewModel.clearInput()
        }
    }

    @OptIn(FlowPreview::class)
    private fun setupInputListeners() {
        // 使用Flow进行防抖处理
        binding.inputField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s != null) {
                    viewModel.updateInput(s.toString())
                }
            }
        })

        // 处理输入的防抖动流
        viewModel.inputFlow
            .debounce(150) // 150ms防抖
            .onEach { input -> 
                if (input.isNotEmpty()) {
                    Timber.d("处理输入: $input")
                    viewModel.processInput(input)
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun observeViewModel() {
        // 观察输入阶段
        viewModel.inputStage.observe(this) { stage ->
            when (stage) {
                InputStage.INITIAL_LETTER -> {
                    binding.stageDisplay.text = "当前阶段: 首字母阶段"
                    binding.stageIcon.setImageResource(R.drawable.ic_stage_initial)
                }
                InputStage.PINYIN_COMPLETION -> {
                    binding.stageDisplay.text = "当前阶段: 拼音补全阶段"
                    binding.stageIcon.setImageResource(R.drawable.ic_stage_pinyin)
                }
                InputStage.SYLLABLE_SPLIT -> {
                    binding.stageDisplay.text = "当前阶段: 音节拆分阶段"
                    binding.stageIcon.setImageResource(R.drawable.ic_stage_split)
                }
                InputStage.ACRONYM -> {
                    binding.stageDisplay.text = "当前阶段: 首字母缩写阶段"
                    binding.stageIcon.setImageResource(R.drawable.ic_stage_acronym)
                }
                else -> {
                    binding.stageDisplay.text = "当前阶段: 未知"
                    binding.stageIcon.setImageResource(R.drawable.ic_stage_unknown)
                }
            }
        }

        // 观察匹配规则
        viewModel.matchRule.observe(this) { rule ->
            binding.matchDisplay.text = "匹配规则: $rule"
        }

        // 观察拆分结果
        viewModel.syllableSplit.observe(this) { syllables ->
            if (syllables.isNotEmpty()) {
                binding.splitResultDisplay.visibility = View.VISIBLE
                binding.splitResultDisplay.text = "拆分结果: ${syllables.joinToString(" + ")}"
            } else {
                binding.splitResultDisplay.visibility = View.GONE
            }
        }

        // 观察查询条件
        viewModel.queryCondition.observe(this) { condition ->
            binding.queryDisplay.text = "查询条件: $condition"
        }
        
        // 观察查询过程
        viewModel.queryProcess.observe(this) { process ->
            binding.queryProcessDisplay.text = process.ifEmpty { "尚未执行查询" }
        }
        
        // 观察候选词统计
        viewModel.candidateStats.observe(this) { stats ->
            binding.candidateStatsDisplay.text = "候选词统计: 总计 ${stats.totalCount} 个（单字 ${stats.singleCharCount} 个，词语 ${stats.phraseCount} 个）"
        }

        // 观察候选词
        viewModel.candidates.observe(this) { candidates ->
            candidateAdapter.updateCandidates(candidates)
            if (candidates.isEmpty()) {
                binding.noResultsText.visibility = View.VISIBLE
            } else {
                binding.noResultsText.visibility = View.GONE
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
} 