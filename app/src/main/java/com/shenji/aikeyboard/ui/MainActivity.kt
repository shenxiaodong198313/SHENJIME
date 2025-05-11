package com.shenji.aikeyboard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
import kotlin.math.min

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
        
        // 设置词典处理按钮
        binding.btnProcessChars.setOnClickListener {
            processSingleDictionary("chars", -1)
        }
        
        binding.btnProcessBase1.setOnClickListener {
            processSingleDictionary("base", 0)
        }
        
        binding.btnProcessBase2.setOnClickListener {
            processSingleDictionary("base", 1)
        }
        
        binding.btnProcessBase3.setOnClickListener {
            processSingleDictionary("base", 2)
        }
        
        binding.btnProcessBase4.setOnClickListener {
            processSingleDictionary("base", 3)
        }
        
        binding.btnProcessBase5.setOnClickListener {
            processSingleDictionary("base", 4)
        }
        
        binding.btnProcessBase6.setOnClickListener {
            processSingleDictionary("base", 5)
        }
        
        binding.btnProcessBase7.setOnClickListener {
            processSingleDictionary("base", 6)
        }
        
        // 设置合并词典按钮
        binding.btnMergeDictionaries.setOnClickListener {
            mergeDictionaries()
        }
        
        // 设置导出预编译词典按钮
        binding.btnExportDict.setOnClickListener {
            showExportSettingsDialog()
        }
        
        // 设置取消按钮
        binding.btnCancelExport.setOnClickListener {
            currentJob?.cancel()
            binding.tvDictStatus.text = "正在取消操作..."
        }
        
        // 隐藏进度相关元素
        binding.btnCancelExport.visibility = View.GONE
        binding.pbDictExport.visibility = View.GONE
        binding.tvDictStatus.visibility = View.GONE
    }
    
    // 当前操作的协程作业
    private var currentJob: Job? = null
    
    /**
     * 处理单个词典
     */
    private fun processSingleDictionary(type: String, subsetIndex: Int) {
        try {
            // 显示设置对话框
            val dialogView = layoutInflater.inflate(R.layout.dialog_export_settings, null)
            val etBatchSize = dialogView.findViewById<EditText>(R.id.etBatchSize)
            val etMaxNodesPerBatch = dialogView.findViewById<EditText>(R.id.etMaxNodesPerBatch)
            val etNumWorkers = dialogView.findViewById<EditText>(R.id.etNumWorkers)
            
            // 加载上次的设置或使用默认值
            val sharedPrefs = getSharedPreferences("export_settings", Context.MODE_PRIVATE)
            etBatchSize.setText(sharedPrefs.getInt("batch_size", 500).toString())
            etMaxNodesPerBatch.setText(sharedPrefs.getInt("max_nodes_per_batch", 2000).toString())
            etNumWorkers.setText(sharedPrefs.getInt("num_workers", 2).toString())
            
            val dictTypeText = if (type == "base" && subsetIndex >= 0) {
                "Base词典 (${subsetIndex+1}/7)"
            } else {
                "${type.replaceFirstChar { it.uppercase() }}词典"
            }
            
            // 配置设置对话框
            val settingsDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("处理$dictTypeText")
                .setView(dialogView)
                .setMessage("请设置处理参数，较大的值可能加快处理但会增加内存使用")
                .setPositiveButton("开始处理") { _, _ -> 
                    // 保存设置
                    val batchSize = etBatchSize.text.toString().toIntOrNull() ?: 500
                    val maxNodesPerBatch = etMaxNodesPerBatch.text.toString().toIntOrNull() ?: 2000
                    val numWorkers = etNumWorkers.text.toString().toIntOrNull() ?: 2
                    
                    // 保存设置到SharedPreferences
                    sharedPrefs.edit().apply {
                        putInt("batch_size", batchSize)
                        putInt("max_nodes_per_batch", maxNodesPerBatch)
                        putInt("num_workers", numWorkers)
                        apply()
                    }
                    
                    // 显示警告对话框
                    showProcessWarningDialog(type, subsetIndex, batchSize, maxNodesPerBatch)
                }
                .setNegativeButton("取消", null)
                .create()
                
            settingsDialog.show()
        } catch (e: Exception) {
            Timber.e(e, "设置处理环境失败: ${e.message}")
            Toast.makeText(this, "启动处理失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 显示处理警告对话框
     */
    private fun showProcessWarningDialog(type: String, subsetIndex: Int, batchSize: Int, maxNodesPerBatch: Int) {
        val dictTypeText = if (type == "base" && subsetIndex >= 0) {
            "Base词典 (${subsetIndex+1}/7)"
        } else {
            "${type.replaceFirstChar { it.uppercase() }}词典"
        }
        
        // 配置防止闪退的对话框
        val warningDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("处理$dictTypeText")
            .setMessage("此操作将处理${dictTypeText}并保存到临时文件。\n\n" +
                    "⚠️ 注意：\n" +
                    "1. 处理过程中请勿退出应用或切换到其他页面\n" +
                    "2. 请保持手机电量充足并断开其他占用资源的应用\n" +
                    "3. 处理过程可能需要几分钟时间\n" +
                    "4. 建议在处理前清理手机内存\n\n" +
                    "当前设置：\n" +
                    "- 批次大小: $batchSize\n" +
                    "- 每批最大节点: $maxNodesPerBatch")
            .setPositiveButton("开始处理") { _, _ -> 
                performSingleDictionaryProcess(type, subsetIndex, batchSize, maxNodesPerBatch)
            }
            .setNegativeButton("取消", null)
            .create()
            
        warningDialog.show()
    }
    
    /**
     * 执行单个词典处理
     */
    private fun performSingleDictionaryProcess(type: String, subsetIndex: Int, batchSize: Int, maxNodesPerBatch: Int) {
        try {
            // 保持屏幕常亮
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // 显示进度UI
            binding.pbDictExport.visibility = View.VISIBLE
            binding.tvDictStatus.visibility = View.VISIBLE
            binding.pbDictExport.isIndeterminate = false
            binding.pbDictExport.progress = 0
            
            val dictTypeText = if (type == "base" && subsetIndex >= 0) {
                "Base词典 (${subsetIndex+1}/7)"
            } else {
                "${type.replaceFirstChar { it.uppercase() }}词典"
            }
            
            binding.tvDictStatus.text = "正在初始化${dictTypeText}处理..."
            
            // 禁用所有按钮
            disableAllButtons()
            
            // 显示取消按钮
            binding.btnCancelExport.visibility = View.VISIBLE
            
            // 在后台线程执行处理
            currentJob = lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, exception ->
                Timber.e(exception, "处理词典失败 (异常处理器捕获): ${exception.message}")
                
                // 将异常信息写入日志文件
                try {
                    val logFile = File(getExternalFilesDir(null), "process_error_log.txt")
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
                    binding.pbDictExport.isIndeterminate = false
                    binding.pbDictExport.progress = 0
                    binding.tvDictStatus.text = "处理失败: ${exception.message}\n" +
                            "错误详情已保存到: ${getExternalFilesDir(null)}/process_error_log.txt"
                    
                    // 重新启用按钮
                    enableAllButtons()
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
                        binding.tvDictStatus.text = "即将开始处理${dictTypeText}..."
                    }
                    
                    // 开始处理
                    val outputFile = dictionaryManager.processSingleDictionary(
                        type = type,
                        subsetIndex = subsetIndex,
                        batchSize = batchSize,
                        maxNodesPerBatch = maxNodesPerBatch
                    ) { progress ->
                        // 更新进度条和进度文本
                        withContext(Dispatchers.Main) {
                            binding.pbDictExport.progress = progress
                            binding.tvDictStatus.text = "处理中: $progress%"
                            
                            // 每5%进度更新一次内存情况
                            if (progress % 5 == 0) {
                                val curFreeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024
                                Timber.d("处理进度: $progress%, 可用内存: ${curFreeMemory}MB")
                            }
                        }
                        
                        // 检查是否请求取消
                        if (!isActive) {
                            throw CancellationException("处理操作已取消")
                        }
                    }
                    
                    // 处理完成，更新UI
                    withContext(Dispatchers.Main) {
                        binding.tvDictStatus.text = "处理成功!\n" +
                                "文件路径: $outputFile"
                        binding.btnCancelExport.visibility = View.GONE
                        enableAllButtons()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        withContext(Dispatchers.Main) {
                            binding.tvDictStatus.text = "处理已取消"
                            binding.pbDictExport.progress = 0
                            enableAllButtons()
                        }
                    } else {
                        throw e
                    }
                } finally {
                    // 关闭屏幕常亮
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            
            // 设置取消按钮
            binding.btnCancelExport.setOnClickListener {
                currentJob?.cancel()
                binding.tvDictStatus.text = "正在取消处理..."
            }
            
        } catch (e: Exception) {
            Timber.e(e, "设置处理环境失败: ${e.message}")
            Toast.makeText(this, "启动处理失败: ${e.message}", Toast.LENGTH_LONG).show()
            
            // 重新启用按钮
            enableAllButtons()
            
            // 关闭屏幕常亮
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    /**
     * 合并词典
     */
    private fun mergeDictionaries() {
        try {
            // 配置警告对话框
            val warningDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("合并所有词典")
                .setMessage("此操作将合并所有已处理的词典文件，生成最终的预编译Trie树。\n\n" +
                        "⚠️ 注意：\n" +
                        "1. 合并过程中请勿退出应用或切换到其他页面\n" +
                        "2. 请保持手机电量充足并断开其他占用资源的应用\n" +
                        "3. 合并过程可能需要几分钟时间\n" +
                        "4. 建议在合并前清理手机内存\n\n" +
                        "确定要开始合并吗？")
                .setPositiveButton("开始合并") { _, _ -> 
                    performMergeDictionaries()
                }
                .setNegativeButton("取消", null)
                .create()
                
            warningDialog.show()
        } catch (e: Exception) {
            Timber.e(e, "设置合并环境失败: ${e.message}")
            Toast.makeText(this, "启动合并失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 执行合并词典
     */
    private fun performMergeDictionaries() {
        try {
            // 保持屏幕常亮
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // 显示进度UI
            binding.pbDictExport.visibility = View.VISIBLE
            binding.tvDictStatus.visibility = View.VISIBLE
            binding.pbDictExport.isIndeterminate = false
            binding.pbDictExport.progress = 0
            
            binding.tvDictStatus.text = "正在初始化合并操作..."
            
            // 禁用所有按钮
            disableAllButtons()
            
            // 显示取消按钮
            binding.btnCancelExport.visibility = View.VISIBLE
            
            // 在后台线程执行合并
            currentJob = lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, exception ->
                Timber.e(exception, "合并词典失败 (异常处理器捕获): ${exception.message}")
                
                // 将异常信息写入日志文件
                try {
                    val logFile = File(getExternalFilesDir(null), "merge_error_log.txt")
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
                    binding.pbDictExport.isIndeterminate = false
                    binding.pbDictExport.progress = 0
                    binding.tvDictStatus.text = "合并失败: ${exception.message}\n" +
                            "错误详情已保存到: ${getExternalFilesDir(null)}/merge_error_log.txt"
                    
                    // 重新启用按钮
                    enableAllButtons()
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
                        binding.tvDictStatus.text = "即将开始合并所有词典..."
                    }
                    
                    // 开始合并
                    val outputFile = dictionaryManager.mergeAllDictionaries { progress ->
                        // 更新进度条和进度文本
                        withContext(Dispatchers.Main) {
                            binding.pbDictExport.progress = progress
                            binding.tvDictStatus.text = "合并中: $progress%"
                            
                            // 每5%进度更新一次内存情况
                            if (progress % 5 == 0) {
                                val curFreeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024
                                Timber.d("合并进度: $progress%, 可用内存: ${curFreeMemory}MB")
                            }
                        }
                        
                        // 检查是否请求取消
                        if (!isActive) {
                            throw CancellationException("合并操作已取消")
                        }
                    }
                    
                    // 合并完成，更新UI
                    withContext(Dispatchers.Main) {
                        binding.tvDictStatus.text = "合并成功!\n" +
                                "文件路径: $outputFile"
                        binding.btnCancelExport.visibility = View.GONE
                        enableAllButtons()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        withContext(Dispatchers.Main) {
                            binding.tvDictStatus.text = "合并已取消"
                            binding.pbDictExport.progress = 0
                            enableAllButtons()
                        }
                    } else {
                        throw e
                    }
                } finally {
                    // 关闭屏幕常亮
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            
            // 设置取消按钮
            binding.btnCancelExport.setOnClickListener {
                currentJob?.cancel()
                binding.tvDictStatus.text = "正在取消合并..."
            }
            
        } catch (e: Exception) {
            Timber.e(e, "设置合并环境失败: ${e.message}")
            Toast.makeText(this, "启动合并失败: ${e.message}", Toast.LENGTH_LONG).show()
            
            // 重新启用按钮
            enableAllButtons()
            
            // 关闭屏幕常亮
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    /**
     * 取消当前操作
     */
    private fun cancelCurrentOperation() {
        currentJob?.cancel()
        binding.tvDictStatus.text = "操作已取消"
        binding.pbDictExport.progress = 0
        binding.btnCancelExport.visibility = View.GONE
        enableAllButtons()
    }
    
    /**
     * 禁用所有按钮
     */
    private fun disableAllButtons() {
        binding.btnSettings.isEnabled = false
        binding.btnLogs.isEnabled = false
        binding.btnDictManager.isEnabled = false
        binding.btnProcessChars.isEnabled = false
        binding.btnProcessBase1.isEnabled = false
        binding.btnProcessBase2.isEnabled = false
        binding.btnProcessBase3.isEnabled = false
        binding.btnProcessBase4.isEnabled = false
        binding.btnProcessBase5.isEnabled = false
        binding.btnProcessBase6.isEnabled = false
        binding.btnProcessBase7.isEnabled = false
        binding.btnMergeDictionaries.isEnabled = false
    }
    
    /**
     * 启用所有按钮
     */
    private fun enableAllButtons() {
        binding.btnSettings.isEnabled = true
        binding.btnLogs.isEnabled = true
        binding.btnDictManager.isEnabled = true
        binding.btnProcessChars.isEnabled = true
        binding.btnProcessBase1.isEnabled = true
        binding.btnProcessBase2.isEnabled = true
        binding.btnProcessBase3.isEnabled = true
        binding.btnProcessBase4.isEnabled = true
        binding.btnProcessBase5.isEnabled = true
        binding.btnProcessBase6.isEnabled = true
        binding.btnProcessBase7.isEnabled = true
        binding.btnMergeDictionaries.isEnabled = true
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
    
    /**
     * 显示导出设置对话框
     */
    private fun showExportSettingsDialog() {
        try {
            // 显示设置对话框
            val dialogView = layoutInflater.inflate(R.layout.dialog_export_settings, null)
            val etBatchSize = dialogView.findViewById<EditText>(R.id.etBatchSize)
            val etMaxNodesPerBatch = dialogView.findViewById<EditText>(R.id.etMaxNodesPerBatch)
            val etNumWorkers = dialogView.findViewById<EditText>(R.id.etNumWorkers)
            
            // 加载上次的设置或使用默认值
            val sharedPrefs = getSharedPreferences("export_settings", Context.MODE_PRIVATE)
            etBatchSize.setText(sharedPrefs.getInt("batch_size", 500).toString())
            etMaxNodesPerBatch.setText(sharedPrefs.getInt("max_nodes_per_batch", 2000).toString())
            etNumWorkers.setText(sharedPrefs.getInt("num_workers", 2).toString())
            
            // 配置设置对话框
            val settingsDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("导出预编译Trie树")
                .setView(dialogView)
                .setMessage("请设置导出参数，较大的值可能加快导出但会增加内存使用")
                .setPositiveButton("开始导出") { _, _ -> 
                    // 保存设置
                    val batchSize = etBatchSize.text.toString().toIntOrNull() ?: 500
                    val maxNodesPerBatch = etMaxNodesPerBatch.text.toString().toIntOrNull() ?: 2000
                    val numWorkers = etNumWorkers.text.toString().toIntOrNull() ?: 2
                    
                    // 保存设置到SharedPreferences
                    sharedPrefs.edit().apply {
                        putInt("batch_size", batchSize)
                        putInt("max_nodes_per_batch", maxNodesPerBatch)
                        putInt("num_workers", numWorkers)
                        apply()
                    }
                    
                    // 显示警告对话框
                    showExportWarningDialog(batchSize, maxNodesPerBatch, numWorkers)
                }
                .setNegativeButton("取消", null)
                .create()
                
            settingsDialog.show()
        } catch (e: Exception) {
            Timber.e(e, "设置导出环境失败: ${e.message}")
            Toast.makeText(this, "启动导出失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 显示导出警告对话框
     */
    private fun showExportWarningDialog(batchSize: Int, maxNodesPerBatch: Int, numWorkers: Int) {
        // 配置防止闪退的对话框
        val warningDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("导出预编译Trie树")
            .setMessage("此操作将导出所有高频词库(chars和base)到预编译Trie树。\n\n" +
                    "⚠️ 注意：\n" +
                    "1. 导出过程中请勿退出应用或切换到其他页面\n" +
                    "2. 请保持手机电量充足并断开其他占用资源的应用\n" +
                    "3. 导出过程可能需要几分钟时间\n" +
                    "4. 建议在导出前清理手机内存\n\n" +
                    "当前设置：\n" +
                    "- 批次大小: $batchSize\n" +
                    "- 每批最大节点: $maxNodesPerBatch\n" +
                    "- 工作线程数: $numWorkers")
            .setPositiveButton("开始导出") { _, _ -> 
                performExportDictionary(batchSize, maxNodesPerBatch, numWorkers)
            }
            .setNegativeButton("取消", null)
            .create()
            
        warningDialog.show()
    }
    
    /**
     * 执行词典导出
     */
    private fun performExportDictionary(batchSize: Int, maxNodesPerBatch: Int, numWorkers: Int) {
        try {
            // 保持屏幕常亮
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // 显示进度UI
            binding.pbDictExport.visibility = View.VISIBLE
            binding.tvDictStatus.visibility = View.VISIBLE
            binding.pbDictExport.isIndeterminate = false
            binding.pbDictExport.progress = 0
            
            binding.tvDictStatus.text = "正在初始化导出环境..."
            
            // 禁用所有按钮
            disableAllButtons()
            
            // 显示取消按钮
            binding.btnCancelExport.visibility = View.VISIBLE
            
            // 在后台线程执行导出
            currentJob = lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, exception ->
                Timber.e(exception, "导出词典失败 (异常处理器捕获): ${exception.message}")
                
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
                    binding.pbDictExport.isIndeterminate = false
                    binding.pbDictExport.progress = 0
                    binding.tvDictStatus.text = "导出失败: ${exception.message}\n" +
                            "错误详情已保存到: ${getExternalFilesDir(null)}/export_error_log.txt"
                    
                    // 重新启用按钮
                    enableAllButtons()
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
                        binding.tvDictStatus.text = "即将开始导出预编译Trie树..."
                    }
                    
                    // 开始导出
                    val outputFile = dictionaryManager.exportHighFrequencyDictionaryToTrie(
                        batchSize = batchSize,
                        maxNodesPerBatch = maxNodesPerBatch,
                        numWorkers = numWorkers
                    ) { progress ->
                        // 更新进度条和进度文本
                        withContext(Dispatchers.Main) {
                            binding.pbDictExport.progress = progress
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
                    withContext(Dispatchers.Main) {
                        binding.tvDictStatus.text = "导出成功!\n" +
                                "文件路径: $outputFile"
                        binding.btnCancelExport.visibility = View.GONE
                        enableAllButtons()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        withContext(Dispatchers.Main) {
                            binding.tvDictStatus.text = "导出已取消"
                            binding.pbDictExport.progress = 0
                            enableAllButtons()
                        }
                    } else {
                        throw e
                    }
                } finally {
                    // 关闭屏幕常亮
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            
            // 设置取消按钮
            binding.btnCancelExport.setOnClickListener {
                currentJob?.cancel()
                binding.tvDictStatus.text = "正在取消导出..."
            }
            
        } catch (e: Exception) {
            Timber.e(e, "设置导出环境失败: ${e.message}")
            Toast.makeText(this, "启动导出失败: ${e.message}", Toast.LENGTH_LONG).show()
            
            // 重新启用按钮
            enableAllButtons()
            
            // 关闭屏幕常亮
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
} 