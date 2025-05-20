package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.StagedDictionaryRepository
import com.shenji.aikeyboard.data.WordFrequency
import com.shenji.aikeyboard.databinding.ActivityDictionaryDebugBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 词典调试活动，用于在手机上直接测试和查看词典查询结果的调试信息
 */
class DictionaryDebugActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDictionaryDebugBinding
    private val dictionaryRepository = StagedDictionaryRepository()
    private val stageAdapter = DebugStageAdapter()
    private val weightAdapter = DebugWeightAdapter()
    private val candidateAdapter = WordFrequencyAdapter()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerViews()
        setupSearchButton()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "词典调试"
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupRecyclerViews() {
        // 设置阶段结果列表
        binding.rvStages.apply {
            layoutManager = LinearLayoutManager(this@DictionaryDebugActivity)
            adapter = stageAdapter
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
        }
        
        // 设置权重列表
        binding.rvWeights.apply {
            layoutManager = LinearLayoutManager(this@DictionaryDebugActivity)
            adapter = weightAdapter
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
        }
        
        // 设置候选词列表
        binding.rvCandidates.apply {
            layoutManager = LinearLayoutManager(this@DictionaryDebugActivity)
            adapter = candidateAdapter
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
        }
    }
    
    private fun setupSearchButton() {
        binding.btnSearch.setOnClickListener {
            val input = binding.etInput.text.toString().trim()
            if (input.isNotEmpty()) {
                performSearch(input)
            } else {
                Toast.makeText(this, "请输入要查询的拼音或首字母", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun performSearch(input: String) {
        lifecycleScope.launch {
            try {
                // 显示进度条
                binding.progressBar.visibility = View.VISIBLE
                binding.cardDebugInfo.visibility = View.GONE
                binding.cardCandidates.visibility = View.GONE
                
                // 执行查询
                val candidateResults = withContext(Dispatchers.IO) {
                    dictionaryRepository.queryCandidates(input)
                }
                
                // 获取调试信息
                val debugInfo = dictionaryRepository.debugInfo
                
                // 更新UI
                updateDebugInfo(debugInfo)
                updateCandidateResults(candidateResults)
                
                // 隐藏进度条
                binding.progressBar.visibility = View.GONE
                binding.cardDebugInfo.visibility = View.VISIBLE
                binding.cardCandidates.visibility = View.VISIBLE
                
                Timber.d("词典查询完成: 输入='$input', 结果数量=${candidateResults.size}")
                
            } catch (e: Exception) {
                // 处理错误
                binding.progressBar.visibility = View.GONE
                Timber.e(e, "词典查询出错: ${e.message}")
                Toast.makeText(this@DictionaryDebugActivity, "查询出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateDebugInfo(debugInfo: StagedDictionaryRepository.DebugInfo) {
        // 更新输入文本
        binding.tvInput.text = debugInfo.input
        
        // 更新阶段结果
        stageAdapter.setData(debugInfo.stages)
        
        // 更新重复项
        if (debugInfo.duplicates.isEmpty()) {
            binding.tvDuplicates.text = "无重复项"
        } else {
            binding.tvDuplicates.text = debugInfo.duplicates
                .joinToString("\n") { "${it.first} -> ${it.second}" }
        }
        
        // 更新权重
        weightAdapter.setData(debugInfo.weights)
    }
    
    private fun updateCandidateResults(candidates: List<WordFrequency>) {
        candidateAdapter.setData(candidates)
    }
} 