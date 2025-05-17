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
        
        try {
            binding = ActivityInputTestBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            setupToolbar()
            setupUI()
            checkRealmStatus()
            setupLoggers()
            
            // 记录活动创建成功
            logMessage("输入测试界面已创建")
        } catch (e: Exception) {
            // 如果布局加载失败，使用简单视图并报告错误
            Timber.e(e, "创建输入测试界面失败: ${e.message}")
            setContentView(R.layout.activity_main)
            Toast.makeText(this, "初始化界面失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish() // 关闭活动
        }
    }
    
    private fun setupToolbar() {
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.apply {
                title = getString(R.string.input_test_title)
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
            }
        } catch (e: Exception) {
            Timber.e(e, "设置工具栏失败: ${e.message}")
        }
    }
    
    private fun setupUI() {
        try {
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
            
            // 初始化日志区域
            binding.tvLogs.text = ""
            logMessage("UI初始化完成")
        } catch (e: Exception) {
            Timber.e(e, "设置UI元素失败: ${e.message}")
        }
    }
    
    private fun checkRealmStatus() {
        lifecycleScope.launch {
            try {
                // 初始化状态文本
                binding.tvRealmStatus.text = getString(R.string.realm_status, "检查中...")
                
                val isRealmConnected = withContext(Dispatchers.IO) {
                    try {
                        // 安全获取DictionaryManager实例
                        val dictManager = try {
                            DictionaryManager.instance
                        } catch (e: Exception) {
                            logMessage("DictionaryManager未初始化或获取失败")
                            return@withContext false
                        }
                        
                        val isInitialized = dictManager.isLoaded()
                        logMessage("Realm词典初始化状态: $isInitialized")
                        return@withContext isInitialized
                    } catch (e: Exception) {
                        logMessage("检查Realm状态出错: ${e.message}")
                        Timber.e(e, "检查Realm状态出错")
                        return@withContext false
                    }
                }
                
                // 更新UI显示
                withContext(Dispatchers.Main) {
                    binding.tvRealmStatus.text = getString(R.string.realm_status, if (isRealmConnected) "已连接" else "未连接")
                }
            } catch (e: Exception) {
                Timber.e(e, "检查Realm状态过程中发生异常")
                binding.tvRealmStatus.text = getString(R.string.realm_status, "检查失败")
            }
        }
    }
    
    private fun setupLoggers() {
        try {
            // 设置展示日志的文本视图
            binding.tvLogs.setOnClickListener {
                // 允许日志滚动查看
                binding.tvLogs.isVerticalScrollBarEnabled = true
            }
            logMessage("日志系统初始化完成")
        } catch (e: Exception) {
            Timber.e(e, "设置日志系统失败: ${e.message}")
        }
    }
    
    /**
     * 记录日志消息
     */
    private fun logMessage(message: String) {
        try {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(System.currentTimeMillis())
            val logEntry = "[$timestamp] $message\n"
            
            Timber.d(message)
            
            // 添加到日志构建器
            imeLogBuilder.append(logEntry)
            
            // 在UI线程更新日志显示
            runOnUiThread {
                try {
                    binding.tvLogs.append(logEntry)
                    
                    // 滚动到底部
                    val scrollAmount = binding.tvLogs.layout?.getLineTop(binding.tvLogs.lineCount) ?: 0
                    if (scrollAmount > binding.tvLogs.height) {
                        binding.tvLogs.scrollTo(0, scrollAmount - binding.tvLogs.height)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "更新日志UI失败")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "记录日志失败: ${e.message}")
        }
    }
    
    /**
     * 显示键盘
     */
    private fun showKeyboard(view: View) {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            view.requestFocus()
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            logMessage("请求显示键盘")
        } catch (e: Exception) {
            Timber.e(e, "显示键盘失败: ${e.message}")
            Toast.makeText(this, "无法显示键盘", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 复制日志到剪贴板
     */
    private fun copyLogsToClipboard() {
        try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("IME Logs", binding.tvLogs.text)
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
            logMessage("日志已复制到剪贴板")
        } catch (e: Exception) {
            Timber.e(e, "复制日志到剪贴板失败: ${e.message}")
            Toast.makeText(this, "复制日志失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 清除日志
     */
    private fun clearLogs() {
        try {
            imeLogBuilder.clear()
            binding.tvLogs.text = ""
            Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show()
            logMessage("日志已清除")
        } catch (e: Exception) {
            Timber.e(e, "清除日志失败: ${e.message}")
        }
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
    
    override fun onDestroy() {
        logMessage("输入测试界面关闭")
        super.onDestroy()
    }
} 