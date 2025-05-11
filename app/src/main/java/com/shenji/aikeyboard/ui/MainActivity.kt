package com.shenji.aikeyboard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.databinding.ActivityMainBinding
import com.shenji.aikeyboard.data.DictionaryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupUI()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }
    
    override fun onResume() {
        super.onResume()
        // 更新输入法状态
        updateInputMethodStatus()
    }
    
    private fun setupUI() {
        // 设置按钮点击事件
        binding.btnSettings.setOnClickListener {
            openInputMethodSettings()
        }
        
        binding.btnLogs.setOnClickListener {
            openLogDetail()
        }
        
        binding.btnDictManager.setOnClickListener {
            openDictManager()
        }
        
        // 添加预编译词典按钮的点击处理
        binding.btnPrecompileDict.setOnClickListener {
            startPrecompileDictionary()
        }
        
        // 隐藏导出相关元素
        binding.btnCancelExport.visibility = View.GONE
        binding.btnDictExport.visibility = View.GONE
        binding.progressDict.visibility = View.GONE
        binding.tvDictStatus.visibility = View.GONE
    }
    
    /**
     * 开始导出词典到预编译Trie树 - 高性能版本
     */
    private fun startPrecompileDictionary() {
        // 配置防止闪退的对话框
        val warningDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("导出预编译Trie树")
            .setMessage("此操作将使用多线程高性能模式导出词典。\n\n" +
                    "⚠️ 注意：\n" +
                    "1. 导出过程中请勿退出应用或切换到其他页面\n" +
                    "2. 请保持手机电量充足并断开其他占用资源的应用\n" +
                    "3. 导出过程可能需要几分钟时间\n" +
                    "4. 建议在导出前清理手机内存")
            .setPositiveButton("开始导出") { _, _ -> 
                performHighPerformanceExport() 
            }
            .setNegativeButton("取消", null)
            .create()
            
        warningDialog.show()
    }
    
    /**
     * 执行高性能导出
     */
    private fun performHighPerformanceExport() {
        try {
            // 保持屏幕常亮
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // 显示进度UI
            binding.progressDict.visibility = View.VISIBLE
            binding.tvDictStatus.visibility = View.VISIBLE
            binding.progressDict.isIndeterminate = false
            binding.progressDict.progress = 0
            binding.pbDictExport.progress = 0
            binding.tvDictStatus.text = "正在初始化超低内存导出模式..."
            
            // 禁用按钮防止重复点击
            binding.btnPrecompileDict.isEnabled = false
            binding.btnSettings.isEnabled = false
            binding.btnLogs.isEnabled = false
            binding.btnDictManager.isEnabled = false
            
            // 在后台线程执行导出
            val exportJob = lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, exception ->
                Timber.e(exception, "导出预编译Trie树失败 (异常处理器捕获): ${exception.message}")
                
                // 将异常信息写入日志文件
                try {
                    val logFile = File(getExternalFilesDir(null), "export_error_log.txt")
                    PrintWriter(FileWriter(logFile, true)).use { writer ->
                        writer.println("--- 错误报告 ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())} ---")
                        writer.println("错误信息: ${exception.message}")
                        exception.printStackTrace(writer)
                        writer.println("--------------------------------------------")
                    }
                } catch (logEx: Exception) {
                    Timber.e(logEx, "写入错误日志失败")
                }
                
                // 在主线程显示错误
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.progressDict.isIndeterminate = false
                    binding.progressDict.progress = 0
                    binding.pbDictExport.progress = 0
                    binding.tvDictStatus.text = "导出预编译Trie树失败: ${exception.message}\n" +
                            "错误详情已保存到: ${getExternalFilesDir(null)}/export_error_log.txt"
                    
                    // 重新启用按钮
                    binding.btnPrecompileDict.isEnabled = true
                    binding.btnSettings.isEnabled = true
                    binding.btnLogs.isEnabled = true
                    binding.btnDictManager.isEnabled = true
                    binding.btnCancelExport.visibility = View.GONE
                    
                    // 关闭屏幕常亮
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }) {
                try {
                    withContext(Dispatchers.Main) {
                        binding.tvDictStatus.text = "正在准备环境..."
                    }
                    
                    // 主动触发GC，释放尽可能多的内存
                    System.gc()
                    delay(1000) // 给GC一些时间
                    
                    withContext(Dispatchers.Main) {
                        binding.tvDictStatus.text = "正在初始化词典管理器..."
                    }
                    
                    // 获取DictionaryManager实例
                    val dictionaryManager = DictionaryManager.instance
                    
                    withContext(Dispatchers.Main) {
                        binding.tvDictStatus.text = "即将开始超低内存导出..."
                    }
                    
                    // 添加导出取消逻辑和错误恢复机制
                    var exportProcessJob: Job? = null
                    
                    // 启用取消按钮
                    withContext(Dispatchers.Main) {
                        binding.btnCancelExport.visibility = View.VISIBLE
                        binding.btnCancelExport.setOnClickListener {
                            exportProcessJob?.cancel()
                            binding.tvDictStatus.text = "导出已取消"
                            binding.pbDictExport.progress = 0
                            binding.btnCancelExport.visibility = View.GONE
                            binding.btnPrecompileDict.isEnabled = true
                        }
                    }
                    
                    // 准备错误处理
                    var errorMessage: String? = null
                    
                    try {
                        // 开始导出高频词库到预编译Trie树，并显示进度
                        val startTime = System.currentTimeMillis()
                        
                        // 创建协程作业，支持取消
                        exportProcessJob = launch {
                            try {
                                // 检查可用内存
                                val maxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024
                                val totalMemory = Runtime.getRuntime().totalMemory() / 1024 / 1024
                                val freeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024
                                
                                Timber.d("内存状况 - 最大: ${maxMemory}MB, 已用: ${totalMemory}MB, 可用: ${freeMemory}MB")
                                
                                if (freeMemory < 10) {
                                    withContext(Dispatchers.Main) {
                                        binding.tvDictStatus.text = "警告: 可用内存不足，继续导出可能会失败"
                                        delay(3000)
                                    }
                                }
                                
                                // 开始导出，使用极低内存模式
                                val resultFile = dictionaryManager.exportHighFrequencyDictionaryToTrie { progress ->
                                    // 更新进度条和进度文本
                                    withContext(Dispatchers.Main) {
                                        binding.pbDictExport.progress = progress
                                        binding.progressDict.progress = progress
                                        binding.tvDictStatus.text = "导出中: $progress%"
                                        
                                        // 每5%进度更新一次内存情况
                                        if (progress % 5 == 0) {
                                            val curFreeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024
                                            Timber.d("导出进度: $progress%, 可用内存: ${curFreeMemory}MB")
                                        }
                                    }
                                    
                                    // 检查是否请求取消
                                    if (!isActive) {
                                        throw CancellationException("导出操作已取消")
                                    }
                                }
                                
                                // 导出完成，更新UI
                                val endTime = System.currentTimeMillis()
                                val timeCostMs = endTime - startTime
                                
                                withContext(Dispatchers.Main) {
                                    binding.tvDictStatus.text = "导出成功，耗时: ${formatTime(timeCostMs)}\n" +
                                            "文件路径: $resultFile"
                                    binding.btnCancelExport.visibility = View.GONE
                                    binding.btnPrecompileDict.isEnabled = true
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) {
                                    throw e
                                } else {
                                    errorMessage = "导出失败: ${e.message}"
                                    Timber.e(e, "导出词典失败")
                                }
                            }
                        }
                        
                        // 等待导出完成或取消
                        exportProcessJob.join()
                        
                    } catch (e: CancellationException) {
                        // 导出被用户取消，不需要做任何事情
                        Timber.d("导出词典被用户取消")
                    } catch (e: Exception) {
                        errorMessage = "导出失败: ${e.message}"
                        Timber.e(e, "导出词典失败")
                    } finally {
                        // 清理UI
                        withContext(Dispatchers.Main) {
                            binding.btnCancelExport.visibility = View.GONE
                            binding.btnPrecompileDict.isEnabled = true
                            
                            // 显示错误信息（如果有）
                            if (errorMessage != null) {
                                binding.tvDictStatus.text = errorMessage
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "设置导出环境失败: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "启动导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    
                    // 重新启用按钮
                    withContext(Dispatchers.Main) {
                        binding.btnPrecompileDict.isEnabled = true
                        binding.btnSettings.isEnabled = true
                        binding.btnLogs.isEnabled = true
                        binding.btnDictManager.isEnabled = true
                        binding.btnPrecompileDict.text = "导出预编译Trie树"
                    }
                    
                    // 关闭屏幕常亮
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            
            // 添加取消按钮
            binding.btnPrecompileDict.isEnabled = true
            binding.btnPrecompileDict.text = "取消导出"
            binding.btnPrecompileDict.setOnClickListener {
                // 取消导出任务
                exportJob.cancel()
                binding.btnPrecompileDict.isEnabled = false
                binding.tvDictStatus.text = "正在取消导出任务..."
                
                // 重置按钮
                lifecycleScope.launch {
                    delay(2000) // 等待取消完成
                    binding.btnPrecompileDict.text = "导出预编译Trie树"
                    binding.btnPrecompileDict.setOnClickListener { startPrecompileDictionary() }
                }
            }
            
            // 恢复原始按钮状态的逻辑
            exportJob.invokeOnCompletion {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.btnPrecompileDict.text = "导出预编译Trie树"
                    binding.btnPrecompileDict.setOnClickListener { startPrecompileDictionary() }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "设置导出环境失败: ${e.message}")
            Toast.makeText(this, "启动导出失败: ${e.message}", Toast.LENGTH_LONG).show()
            
            // 重新启用按钮
            binding.btnPrecompileDict.isEnabled = true
            binding.btnSettings.isEnabled = true
            binding.btnLogs.isEnabled = true
            binding.btnDictManager.isEnabled = true
            binding.btnPrecompileDict.text = "导出预编译Trie树"
            
            // 关闭屏幕常亮
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    /**
     * 格式化时间（毫秒转为易读形式）
     */
    private fun formatTime(timeMs: Long): String {
        val seconds = timeMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> String.format("%d小时%d分钟", hours, minutes % 60)
            minutes > 0 -> String.format("%d分钟%d秒", minutes, seconds % 60)
            else -> String.format("%d秒", seconds)
        }
    }
    
    /**
     * 将字节大小转换为可读性好的字符串形式
     */
    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
    
    /**
     * 更新输入法状态
     */
    private fun updateInputMethodStatus() {
        val isEnabled = isInputMethodEnabled()
        val isSelected = isInputMethodSelected()
        
        binding.tvStatus.text = when {
            isSelected -> getString(R.string.ime_status).replace("未启用", "已启用并设为默认")
            isEnabled -> getString(R.string.ime_status).replace("未启用", "已启用但非默认")
            else -> getString(R.string.ime_status)
        }
    }
    
    /**
     * 检查输入法是否已启用
     */
    private fun isInputMethodEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val imeId = "com.shenji.aikeyboard/.ime.ShenjiInputMethodService"
        
        for (info in imm.enabledInputMethodList) {
            if (info.id == imeId) {
                return true
            }
        }
        return false
    }
    
    /**
     * 检查输入法是否被选为默认
     */
    private fun isInputMethodSelected(): Boolean {
        val currentImeId = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val imeId = "com.shenji.aikeyboard/.ime.ShenjiInputMethodService"
        return currentImeId == imeId
    }
    
    /**
     * 打开输入法设置
     */
    private fun openInputMethodSettings() {
        try {
            Timber.d("打开输入法设置")
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: Exception) {
            Timber.e(e, "打开输入法设置失败")
        }
    }
    
    /**
     * 打开日志详情
     */
    private fun openLogDetail() {
        Timber.d("打开日志详情")
        val intent = Intent(this, LogDetailActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * 打开词典管理界面
     */
    private fun openDictManager() {
        Timber.d("打开词典管理")
        val intent = Intent(this, DictionaryManagerActivity::class.java)
        startActivity(intent)
    }
} 