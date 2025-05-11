package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.databinding.ActivityLogDetailBinding
import com.shenji.aikeyboard.logger.LogManager
import timber.log.Timber

class LogDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupUI()
        loadLogContent()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.log_details)
    }
    
    private fun setupUI() {
        // 设置按钮点击事件
        binding.btnCopyLog.setOnClickListener {
            copyLogToClipboard()
        }
    }
    
    /**
     * 加载日志内容
     */
    private fun loadLogContent() {
        Timber.d("加载日志内容")
        val logContent = LogManager.readCrashLog()
        
        if (logContent != null && logContent.isNotEmpty()) {
            binding.tvLogContent.text = logContent
        } else {
            binding.tvLogContent.text = getString(R.string.no_logs)
        }
    }
    
    /**
     * 复制日志内容到剪贴板
     */
    private fun copyLogToClipboard() {
        val logContent = binding.tvLogContent.text.toString()
        
        if (logContent.isNotEmpty() && logContent != getString(R.string.no_logs)) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("神迹输入法日志", logContent)
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(this, getString(R.string.log_copied), Toast.LENGTH_SHORT).show()
            Timber.d("日志内容已复制到剪贴板")
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 