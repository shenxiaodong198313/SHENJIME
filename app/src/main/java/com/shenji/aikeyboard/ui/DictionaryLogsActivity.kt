package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.databinding.ActivityDictionaryLogsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

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
        setupButtons()
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
     * 设置按钮点击事件
     */
    private fun setupButtons() {
        // 复制日志按钮
        binding.btnCopyLogs.setOnClickListener {
            copyLogsToClipboard()
        }
        
        // 清空日志按钮
        binding.btnClearLogs.setOnClickListener {
            showClearLogsConfirmDialog()
        }
    }
    
    /**
     * 复制日志到剪贴板
     */
    private fun copyLogsToClipboard() {
        val logs = DictionaryManager.instance.getLogs()
        if (logs.isEmpty()) {
            Toast.makeText(this, "暂无日志可复制", Toast.LENGTH_SHORT).show()
            return
        }
        
        val logText = logs.joinToString("\n")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("词典日志", logText)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
        Timber.d("日志已复制到剪贴板")
    }
    
    /**
     * 显示清空日志确认对话框
     */
    private fun showClearLogsConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("清空日志")
            .setMessage("确定要清空所有日志记录吗？此操作不可恢复。")
            .setPositiveButton("确定") { _, _ ->
                clearLogs()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 清空日志
     */
    private fun clearLogs() {
        DictionaryManager.instance.clearLogs()
        loadLogs()
        Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
        Timber.d("日志已清空")
    }
    
    /**
     * 加载日志
     */
    private fun loadLogs() {
        val logs = DictionaryManager.instance.getLogs()
        
        if (logs.isEmpty()) {
            binding.tvNoLogs.visibility = View.VISIBLE
            binding.rvLogs.visibility = View.GONE
            
            // 如果日志为空但Trie树已加载，显示加载状态信息
            if (DictionaryManager.instance.isLoaded()) {
                // 获取各词典的加载信息
                val loadedCounts = DictionaryManager.instance.typeLoadedCountMap
                val totalLoadedCount = DictionaryManager.instance.getTotalLoadedCount()
                val trieStatusText = StringBuilder()
                
                trieStatusText.append("Trie树已加载，共包含${totalLoadedCount}个词条。\n\n")
                trieStatusText.append("词典加载情况：\n")
                
                // 依次添加各个词典的加载状态
                for (type in DictionaryManager.TRIE_DICT_TYPES) {
                    val count = loadedCounts[type] ?: 0
                    val typeName = when(type) {
                        "base" -> "基础词库"
                        "correlation" -> "关联词库" 
                        "people" -> "人名词库"
                        "corrections" -> "错音词库"
                        "compatible" -> "兼容词库"
                        else -> "${type}词库"
                    }
                    
                    if (count > 0) {
                        trieStatusText.append("· $typeName：已加载 $count 条\n")
                    } else {
                        trieStatusText.append("· $typeName：未加载\n")
                    }
                }
                
                trieStatusText.append("\n目前没有其他日志记录。")
                binding.tvNoLogs.text = trieStatusText.toString()
                binding.tvNoLogs.visibility = View.VISIBLE
            }
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