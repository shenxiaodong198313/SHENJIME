package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.databinding.ActivityDictionaryLogsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 词典日志查看Activity
 */
class DictionaryLogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDictionaryLogsBinding
    private lateinit var logAdapter: DictionaryLogAdapter
    
    // 自动刷新任务
    private var autoRefreshJob = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        loadLogs()
        
        // 启动自动刷新
        startAutoRefresh()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun setupRecyclerView() {
        logAdapter = DictionaryLogAdapter()
        binding.rvLogs.apply {
            layoutManager = LinearLayoutManager(this@DictionaryLogsActivity).apply {
                stackFromEnd = true // 显示最新的日志在底部
            }
            adapter = logAdapter
        }
    }
    
    /**
     * 加载日志
     */
    private fun loadLogs() {
        val logs = DictionaryManager.instance.getLogs()
        
        if (logs.isEmpty()) {
            binding.tvNoLogs.visibility = View.VISIBLE
            binding.rvLogs.visibility = View.GONE
        } else {
            binding.tvNoLogs.visibility = View.GONE
            binding.rvLogs.visibility = View.VISIBLE
            logAdapter.submitList(logs)
            binding.rvLogs.scrollToPosition(logs.size - 1) // 滚动到最新日志
        }
    }
    
    /**
     * 开始自动刷新
     */
    private fun startAutoRefresh() {
        autoRefreshJob = true
        lifecycleScope.launch(Dispatchers.IO) {
            while (autoRefreshJob && !isFinishing) {
                withContext(Dispatchers.Main) {
                    loadLogs()
                }
                delay(2000) // 每2秒刷新一次
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        autoRefreshJob = false
    }
} 