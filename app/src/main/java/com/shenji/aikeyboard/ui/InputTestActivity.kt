package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.databinding.ActivityInputTestBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class InputTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInputTestBinding
    private val imeLogBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInputTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupUI()
        checkRealmStatus()
        setupLoggers()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.input_test_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }
    
    private fun setupUI() {
        // 设置输入框点击事件
        binding.etInput.setOnClickListener {
            showKeyboard(it)
        }
        
        // 设置复制日志按钮
        binding.btnCopyLogs.setOnClickListener {
            copyLogsToClipboard()
        }
        
        // 设置清除日志按钮
        binding.btnClearLogs.setOnClickListener {
            clearLogs()
        }
    }
    
    private fun checkRealmStatus() {
        lifecycleScope.launch {
            val isRealmConnected = withContext(Dispatchers.IO) {
                try {
                    val isInitialized = DictionaryManager.instance.isLoaded()
                    logMessage("Realm词典初始化状态: $isInitialized")
                    return@withContext isInitialized
                } catch (e: Exception) {
                    logMessage("检查Realm状态出错: ${e.message}")
                    e.printStackTrace()
                    return@withContext false
                }
            }
            
            // 更新UI显示
            withContext(Dispatchers.Main) {
                binding.tvRealmStatus.text = "Realm词典状态: " + if (isRealmConnected) "已连接" else "未连接"
            }
        }
    }
    
    private fun setupLoggers() {
        // 设置展示日志的文本视图
        binding.tvLogs.setOnClickListener {
            // 允许日志滚动查看
            binding.tvLogs.isVerticalScrollBarEnabled = true
        }
    }
    
    /**
     * 记录日志消息
     */
    private fun logMessage(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(System.currentTimeMillis())
        val logEntry = "[$timestamp] $message\n"
        
        Timber.d(message)
        
        // 添加到日志构建器
        imeLogBuilder.append(logEntry)
        
        // 在UI线程更新日志显示
        runOnUiThread {
            binding.tvLogs.append(logEntry)
            
            // 滚动到底部
            val scrollAmount = binding.tvLogs.layout.getLineTop(binding.tvLogs.lineCount) - binding.tvLogs.height
            if (scrollAmount > 0) {
                binding.tvLogs.scrollTo(0, scrollAmount)
            }
        }
    }
    
    /**
     * 显示键盘
     */
    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view.requestFocus()
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
    
    /**
     * 复制日志到剪贴板
     */
    private fun copyLogsToClipboard() {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("IME Logs", binding.tvLogs.text)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 清除日志
     */
    private fun clearLogs() {
        imeLogBuilder.clear()
        binding.tvLogs.text = ""
        Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show()
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
} 