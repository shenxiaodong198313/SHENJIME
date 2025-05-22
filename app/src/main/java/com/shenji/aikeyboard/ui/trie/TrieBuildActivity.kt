package com.shenji.aikeyboard.ui.trie

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.trie.TrieBuilder
import com.shenji.aikeyboard.data.trie.TrieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Trie树构建Activity
 * 用于构建和管理拼音Trie树索引
 */
class TrieBuildActivity : AppCompatActivity() {
    
    // 卡片视图
    private lateinit var charsCard: CardView
    private lateinit var baseCard: CardView
    private lateinit var memoryCard: CardView
    
    // 单字词典UI元素
    private lateinit var charsStatusText: TextView
    private lateinit var charsFileInfo: TextView
    private lateinit var charsProgress: ProgressBar
    private lateinit var charsProgressText: TextView
    private lateinit var buildCharsButton: Button
    private lateinit var exportCharsButton: Button
    
    // 基础词典UI元素
    private lateinit var baseStatusText: TextView
    private lateinit var baseFileInfo: TextView
    private lateinit var baseProgress: ProgressBar
    private lateinit var baseProgressText: TextView
    private lateinit var buildBaseButton: Button
    private lateinit var exportBaseButton: Button
    
    // 内存信息
    private lateinit var memoryInfoText: TextView
    
    // 新增: Trie内存加载状态UI元素
    private lateinit var charsTrieLoadedStatus: TextView
    private lateinit var baseTrieLoadedStatus: TextView
    private lateinit var charsTrieStats: TextView
    private lateinit var baseTrieStats: TextView
    private lateinit var loadCharsTrieButton: Button
    private lateinit var unloadCharsTrieButton: Button
    private lateinit var loadBaseTrieButton: Button
    private lateinit var unloadBaseTrieButton: Button
    
    // 新增: 测试按钮
    private lateinit var testCharsTrieButton: Button
    private lateinit var testBaseTrieButton: Button
    private lateinit var testResultText: TextView
    
    // Trie树构建器
    private lateinit var trieBuilder: TrieBuilder
    
    // Trie树管理器
    private val trieManager = TrieManager.instance
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trie_build)
        
        // 设置返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Trie树构建工具"
        
        // 初始化Trie树构建器
        trieBuilder = TrieBuilder(this)
        
        // 初始化UI组件
        initViews()
        
        // 设置按钮点击事件
        setupButtonListeners()
        
        // 刷新Trie文件状态
        refreshTrieStatus()
        
        // 监听Trie内存加载状态
        observeTrieLoadingState()
    }
    
    /**
     * 初始化视图组件
     */
    private fun initViews() {
        // 卡片视图
        charsCard = findViewById(R.id.chars_card)
        baseCard = findViewById(R.id.base_card)
        memoryCard = findViewById(R.id.memory_card)
        
        // 单字词典UI
        charsStatusText = findViewById(R.id.chars_status_text)
        charsFileInfo = findViewById(R.id.chars_file_info)
        charsProgress = findViewById(R.id.chars_progress)
        charsProgressText = findViewById(R.id.chars_progress_text)
        buildCharsButton = findViewById(R.id.build_chars_button)
        exportCharsButton = findViewById(R.id.export_chars_button)
        
        // 基础词典UI
        baseStatusText = findViewById(R.id.base_status_text)
        baseFileInfo = findViewById(R.id.base_file_info)
        baseProgress = findViewById(R.id.base_progress)
        baseProgressText = findViewById(R.id.base_progress_text)
        buildBaseButton = findViewById(R.id.build_base_button)
        exportBaseButton = findViewById(R.id.export_base_button)
        
        // 内存信息
        memoryInfoText = findViewById(R.id.memory_info_text)
        
        // 新增: Trie内存加载状态UI
        charsTrieLoadedStatus = findViewById(R.id.chars_trie_loaded_status)
        baseTrieLoadedStatus = findViewById(R.id.base_trie_loaded_status)
        charsTrieStats = findViewById(R.id.chars_trie_stats)
        baseTrieStats = findViewById(R.id.base_trie_stats)
        loadCharsTrieButton = findViewById(R.id.load_chars_trie_button)
        unloadCharsTrieButton = findViewById(R.id.unload_chars_trie_button)
        loadBaseTrieButton = findViewById(R.id.load_base_trie_button)
        unloadBaseTrieButton = findViewById(R.id.unload_base_trie_button)
        
        // 新增: 测试按钮
        testCharsTrieButton = findViewById(R.id.test_chars_trie_button)
        testBaseTrieButton = findViewById(R.id.test_base_trie_button)
        testResultText = findViewById(R.id.test_result_text)
    }
    
    /**
     * 设置按钮点击事件
     */
    private fun setupButtonListeners() {
        // 构建单字Trie树
        buildCharsButton.setOnClickListener {
            buildCharsTrie()
        }
        
        // 导出单字Trie树
        exportCharsButton.setOnClickListener {
            exportTrie(TrieBuilder.TrieType.CHARS)
        }
        
        // 构建基础词典Trie树
        buildBaseButton.setOnClickListener {
            buildBaseTrie()
        }
        
        // 导出基础词典Trie树
        exportBaseButton.setOnClickListener {
            exportTrie(TrieBuilder.TrieType.BASE)
        }
        
        // 新增: 内存加载/卸载按钮
        loadCharsTrieButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val success = trieManager.loadTrieToMemory(TrieBuilder.TrieType.CHARS)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@TrieBuildActivity, "单字Trie树加载成功", Toast.LENGTH_SHORT).show()
                        // 立即更新UI状态，而不是等待下一个生命周期
                        updateCharsTrieLoadStatus(true)
                        // 更新内存使用情况
                        updateMemoryInfo()
                    } else {
                        Toast.makeText(this@TrieBuildActivity, "单字Trie树加载失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        unloadCharsTrieButton.setOnClickListener {
            trieManager.unloadTrie(TrieBuilder.TrieType.CHARS)
            Toast.makeText(this, "单字Trie树已卸载", Toast.LENGTH_SHORT).show()
            // 立即更新UI状态
            updateCharsTrieLoadStatus(false)
            // 更新内存使用情况
            updateMemoryInfo()
        }
        
        loadBaseTrieButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val success = trieManager.loadTrieToMemory(TrieBuilder.TrieType.BASE)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@TrieBuildActivity, "基础词典Trie树加载成功", Toast.LENGTH_SHORT).show()
                        // 立即更新UI状态
                        updateBaseTrieLoadStatus(true)
                        // 更新内存使用情况
                        updateMemoryInfo()
                    } else {
                        Toast.makeText(this@TrieBuildActivity, "基础词典Trie树加载失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        unloadBaseTrieButton.setOnClickListener {
            trieManager.unloadTrie(TrieBuilder.TrieType.BASE)
            Toast.makeText(this, "基础词典Trie树已卸载", Toast.LENGTH_SHORT).show()
            // 立即更新UI状态
            updateBaseTrieLoadStatus(false)
            // 更新内存使用情况
            updateMemoryInfo()
        }
        
        // 新增: 测试按钮
        testCharsTrieButton.setOnClickListener {
            testCharsTrie()
        }
        
        testBaseTrieButton.setOnClickListener {
            testBaseTrie()
        }
    }
    
    /**
     * 监听Trie内存加载状态
     */
    private fun observeTrieLoadingState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 监控单字Trie加载状态
                launch {
                    trieManager.isTrieLoaded(TrieBuilder.TrieType.CHARS).let { isLoaded ->
                        updateCharsTrieLoadStatus(isLoaded)
                    }
                }
                
                // 监控基础词典Trie加载状态
                launch {
                    trieManager.isTrieLoaded(TrieBuilder.TrieType.BASE).let { isLoaded ->
                        updateBaseTrieLoadStatus(isLoaded)
                    }
                }
            }
        }
    }
    
    /**
     * 更新单字Trie加载状态显示
     */
    private fun updateCharsTrieLoadStatus(isLoaded: Boolean) {
        runOnUiThread {
            if (isLoaded) {
                charsTrieLoadedStatus.text = "已加载"
                charsTrieLoadedStatus.setTextColor(getColor(R.color.teal_700))
                
                // 更新统计信息
                val stats = trieManager.getTrieMemoryStats(TrieBuilder.TrieType.CHARS)
                if (stats != null) {
                    val statsText = "节点数: ${stats.nodeCount}, 词条数: ${stats.wordCount}, " +
                            "估计内存: ${formatFileSize(trieManager.getTrieMemoryUsage(TrieBuilder.TrieType.CHARS))}"
                    charsTrieStats.text = "单字Trie统计: $statsText"
                } else {
                    charsTrieStats.text = "单字Trie统计: 无数据"
                }
                
                loadCharsTrieButton.isEnabled = false
                unloadCharsTrieButton.isEnabled = true
            } else {
                charsTrieLoadedStatus.text = "未加载"
                charsTrieLoadedStatus.setTextColor(getColor(android.R.color.holo_red_light))
                charsTrieStats.text = "单字Trie统计: 未加载"
                
                loadCharsTrieButton.isEnabled = trieManager.isTrieFileExists(TrieBuilder.TrieType.CHARS)
                unloadCharsTrieButton.isEnabled = false
            }
        }
    }
    
    /**
     * 更新基础词典Trie加载状态显示
     */
    private fun updateBaseTrieLoadStatus(isLoaded: Boolean) {
        runOnUiThread {
            if (isLoaded) {
                baseTrieLoadedStatus.text = "已加载"
                baseTrieLoadedStatus.setTextColor(getColor(R.color.teal_700))
                
                // 更新统计信息
                val stats = trieManager.getTrieMemoryStats(TrieBuilder.TrieType.BASE)
                if (stats != null) {
                    val statsText = "节点数: ${stats.nodeCount}, 词条数: ${stats.wordCount}, " +
                            "估计内存: ${formatFileSize(trieManager.getTrieMemoryUsage(TrieBuilder.TrieType.BASE))}"
                    baseTrieStats.text = "基础词典Trie统计: $statsText"
                } else {
                    baseTrieStats.text = "基础词典Trie统计: 无数据"
                }
                
                loadBaseTrieButton.isEnabled = false
                unloadBaseTrieButton.isEnabled = true
            } else {
                baseTrieLoadedStatus.text = "未加载"
                baseTrieLoadedStatus.setTextColor(getColor(android.R.color.holo_red_light))
                baseTrieStats.text = "基础词典Trie统计: 未加载"
                
                loadBaseTrieButton.isEnabled = trieManager.isTrieFileExists(TrieBuilder.TrieType.BASE)
                unloadBaseTrieButton.isEnabled = false
            }
        }
    }
    
    /**
     * 刷新Trie树状态
     */
    private fun refreshTrieStatus() {
        try {
            // 检查单字Trie树 - 使用与TrieManager相同的路径检查
            if (trieManager.isTrieFileExists(TrieBuilder.TrieType.CHARS)) {
                val charsFile = File(filesDir, "trie/chars_trie.dat")
                val lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(charsFile.lastModified()))
                val fileSize = formatFileSize(charsFile.length())
                
                charsStatusText.text = "状态: 已构建"
                charsFileInfo.text = "文件信息: $fileSize, 更新于 $lastModified"
                exportCharsButton.isEnabled = true
                loadCharsTrieButton.isEnabled = !trieManager.isTrieLoaded(TrieBuilder.TrieType.CHARS)
            } else {
                charsStatusText.text = "状态: 未构建"
                charsFileInfo.text = "文件信息: 未构建"
                exportCharsButton.isEnabled = false
                loadCharsTrieButton.isEnabled = false
            }
            
            // 检查基础词典Trie树 - 使用与TrieManager相同的路径检查
            if (trieManager.isTrieFileExists(TrieBuilder.TrieType.BASE)) {
                val baseFile = File(filesDir, "trie/base_trie.dat")
                val lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(baseFile.lastModified()))
                val fileSize = formatFileSize(baseFile.length())
                
                baseStatusText.text = "状态: 已构建"
                baseFileInfo.text = "文件信息: $fileSize, 更新于 $lastModified"
                exportBaseButton.isEnabled = true
                loadBaseTrieButton.isEnabled = !trieManager.isTrieLoaded(TrieBuilder.TrieType.BASE)
            } else {
                baseStatusText.text = "状态: 未构建"
                baseFileInfo.text = "文件信息: 未构建"
                exportBaseButton.isEnabled = false
                loadBaseTrieButton.isEnabled = false
            }
            
            // 更新内存加载状态
            updateCharsTrieLoadStatus(trieManager.isTrieLoaded(TrieBuilder.TrieType.CHARS))
            updateBaseTrieLoadStatus(trieManager.isTrieLoaded(TrieBuilder.TrieType.BASE))
            
            // 更新内存使用情况
            updateMemoryInfo()
        } catch (e: Exception) {
            Timber.e(e, "刷新Trie树状态失败: ${e.message}")
            Toast.makeText(this, "刷新状态失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 更新内存使用情况
     */
    private fun updateMemoryInfo() {
        val runtime = Runtime.getRuntime()
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        val totalMem = runtime.totalMemory() / (1024 * 1024)
        val freeMem = runtime.freeMemory() / (1024 * 1024)
        val usedMem = totalMem - freeMem
        
        memoryInfoText.text = "最大内存: $maxMem MB\n" +
                              "已分配: $totalMem MB\n" +
                              "已使用: $usedMem MB\n" +
                              "空闲: $freeMem MB"
    }
    
    /**
     * 构建单字Trie树
     */
    private fun buildCharsTrie() {
        // 显示进度条
        charsProgress.visibility = View.VISIBLE
        charsProgressText.visibility = View.VISIBLE
        charsProgress.progress = 0
        
        // 禁用构建按钮
        buildCharsButton.isEnabled = false
        
        // 开始构建
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 更新状态
                withContext(Dispatchers.Main) {
                    charsStatusText.text = "状态: 正在构建..."
                }
                
                // 构建单字Trie树
                val trie = trieBuilder.buildCharsTrie { progress, message ->
                    // 更新进度（不能在回调函数中直接使用withContext）
                    lifecycleScope.launch(Dispatchers.Main) {
                        charsProgress.progress = progress
                        charsProgressText.text = message
                    }
                }
                
                // 保存Trie树
                val file = trieBuilder.saveTrie(trie, TrieBuilder.TrieType.CHARS)
                
                // 自动尝试加载到内存
                val loadSuccess = trieManager.loadTrieToMemory(TrieBuilder.TrieType.CHARS)
                
                // 更新UI
                withContext(Dispatchers.Main) {
                    val lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(file.lastModified()))
                    val fileSize = formatFileSize(file.length())
                    
                    charsStatusText.text = "状态: 构建完成"
                    charsFileInfo.text = "文件信息: $fileSize, 更新于 $lastModified"
                    Toast.makeText(this@TrieBuildActivity, "单字Trie树构建成功", Toast.LENGTH_SHORT).show()
                    
                    // 隐藏进度条
                    charsProgress.visibility = View.INVISIBLE
                    charsProgressText.visibility = View.INVISIBLE
                    
                    // 启用按钮
                    buildCharsButton.isEnabled = true
                    exportCharsButton.isEnabled = true
                    
                    // 更新内存加载状态
                    if (loadSuccess) {
                        updateCharsTrieLoadStatus(true)
                        Toast.makeText(this@TrieBuildActivity, "单字Trie树已自动加载到内存", Toast.LENGTH_SHORT).show()
                    }
                    
                    // 更新内存信息
                    updateMemoryInfo()
                    
                    // 刷新整体状态
                    refreshTrieStatus()
                }
            } catch (e: Exception) {
                Timber.e(e, "构建单字Trie树失败")
                
                // 更新UI
                withContext(Dispatchers.Main) {
                    charsStatusText.text = "状态: 构建失败"
                    charsProgressText.text = "错误: ${e.message}"
                    Toast.makeText(this@TrieBuildActivity, "构建失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    
                    // 隐藏进度条
                    charsProgress.visibility = View.INVISIBLE
                    
                    // 启用按钮
                    buildCharsButton.isEnabled = true
                }
            }
        }
    }
    
    /**
     * 构建基础词典Trie树
     */
    private fun buildBaseTrie() {
        // 显示进度条
        baseProgress.visibility = View.VISIBLE
        baseProgressText.visibility = View.VISIBLE
        baseProgress.progress = 0
        
        // 禁用构建按钮
        buildBaseButton.isEnabled = false
        
        // 创建构建状态对话框
        val builder = AlertDialog.Builder(this)
        builder.setTitle("基础词典Trie树构建状态")
        val statusView = layoutInflater.inflate(R.layout.dialog_build_status, null)
        val statusText = statusView.findViewById<TextView>(R.id.build_status_text)
        val memoryText = statusView.findViewById<TextView>(R.id.memory_usage_text)
        statusText.text = "准备构建基础词典Trie树..."
        builder.setView(statusView)
        builder.setCancelable(false)
        builder.setNegativeButton("后台构建") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
        
        // 更新内存使用状态的定时器
        val memoryUpdateTimer = java.util.Timer()
        memoryUpdateTimer.scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                runOnUiThread {
                    val runtime = Runtime.getRuntime()
                    val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                    val totalMemory = runtime.totalMemory() / (1024 * 1024)
                    val maxMemory = runtime.maxMemory() / (1024 * 1024)
                    memoryText.text = "内存使用: ${usedMemory}MB / ${totalMemory}MB (最大: ${maxMemory}MB)"
                }
            }
        }, 0, 1000) // 每秒更新一次内存使用情况
        
        // 创建错误记录器
        val errorRecords = mutableListOf<String>()
        
        // 开始构建
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 更新状态
                withContext(Dispatchers.Main) {
                    baseStatusText.text = "状态: 正在构建..."
                    statusText.text = "正在初始化构建环境..."
                }
                
                // 构建基础词典Trie树
                val trie = trieBuilder.buildBaseTrie { progress, message ->
                    // 更新进度
                    lifecycleScope.launch(Dispatchers.Main) {
                        baseProgress.progress = progress
                        baseProgressText.text = message
                        statusText.text = message
                        
                        // 如果消息中包含"异常"或"错误"，记录到错误列表
                        if (message.contains("异常") || message.contains("错误") || progress < 0) {
                            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            val errorRecord = "[$timestamp] $message"
                            errorRecords.add(errorRecord)
                            Timber.e("构建错误: $errorRecord")
                        }
                    }
                }
                
                // 保存Trie树
                val file = trieBuilder.saveTrie(trie, TrieBuilder.TrieType.BASE)
                
                // 自动尝试加载到内存
                val loadSuccess = trieManager.loadTrieToMemory(TrieBuilder.TrieType.BASE)
                
                // 停止内存更新定时器
                memoryUpdateTimer.cancel()
                
                // 更新UI
                withContext(Dispatchers.Main) {
                    val lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(file.lastModified()))
                    val fileSize = formatFileSize(file.length())
                    
                    baseStatusText.text = "状态: 构建完成"
                    baseFileInfo.text = "文件信息: $fileSize, 更新于 $lastModified"
                    
                    // 关闭状态对话框
                    dialog.dismiss()
                    
                    // 显示构建完成对话框
                    val successDialogBuilder = AlertDialog.Builder(this@TrieBuildActivity)
                    successDialogBuilder.setTitle("构建完成")
                    val message = StringBuilder()
                    message.append("基础词典Trie树构建成功！\n\n")
                    message.append("文件大小: $fileSize\n")
                    message.append("更新时间: $lastModified\n\n")
                    
                    if (errorRecords.isNotEmpty()) {
                        message.append("构建过程中有${errorRecords.size}个警告/错误，可在日志中查看详情。\n")
                    }
                    
                    if (loadSuccess) {
                        message.append("Trie树已自动加载到内存。")
                    } else {
                        message.append("Trie树未能自动加载到内存，可手动加载。")
                    }
                    
                    successDialogBuilder.setMessage(message.toString())
                    successDialogBuilder.setPositiveButton("确定") { _, _ -> }
                    
                    if (errorRecords.isNotEmpty()) {
                        successDialogBuilder.setNeutralButton("查看错误日志") { _, _ ->
                            showBuildErrorLog(errorRecords)
                        }
                    }
                    
                    successDialogBuilder.setNegativeButton("查看详细日志") { _, _ ->
                        showBuildDetailLog()
                    }
                    
                    successDialogBuilder.show()
                    
                    Toast.makeText(this@TrieBuildActivity, "基础词典Trie树构建成功", Toast.LENGTH_SHORT).show()
                    
                    // 隐藏进度条
                    baseProgress.visibility = View.INVISIBLE
                    baseProgressText.visibility = View.INVISIBLE
                    
                    // 启用按钮
                    buildBaseButton.isEnabled = true
                    exportBaseButton.isEnabled = true
                    
                    // 更新内存加载状态
                    if (loadSuccess) {
                        updateBaseTrieLoadStatus(true)
                        Toast.makeText(this@TrieBuildActivity, "基础词典Trie树已自动加载到内存", Toast.LENGTH_SHORT).show()
                    }
                    
                    // 更新内存信息
                    updateMemoryInfo()
                    
                    // 刷新整体状态
                    refreshTrieStatus()
                }
            } catch (e: Exception) {
                Timber.e(e, "构建基础词典Trie树失败")
                
                // 停止内存更新定时器
                memoryUpdateTimer.cancel()
                
                // 记录错误
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val errorRecord = "[$timestamp] 严重错误: ${e.message}"
                errorRecords.add(errorRecord)
                errorRecords.add("详细堆栈: ${e.stackTraceToString()}")
                
                // 更新UI
                withContext(Dispatchers.Main) {
                    baseStatusText.text = "状态: 构建失败"
                    baseProgressText.text = "错误: ${e.message}"
                    
                    // 关闭状态对话框
                    dialog.dismiss()
                    
                    // 显示错误对话框
                    val errorDialogBuilder = AlertDialog.Builder(this@TrieBuildActivity)
                    errorDialogBuilder.setTitle("构建失败")
                    errorDialogBuilder.setMessage("基础词典Trie树构建失败: ${e.message}\n\n查看详细日志以获取更多信息。")
                    errorDialogBuilder.setPositiveButton("确定") { _, _ -> }
                    errorDialogBuilder.setNegativeButton("查看错误日志") { _, _ ->
                        showBuildErrorLog(errorRecords)
                    }
                    errorDialogBuilder.setNeutralButton("查看详细日志") { _, _ ->
                        showBuildDetailLog()
                    }
                    errorDialogBuilder.show()
                    
                    Toast.makeText(this@TrieBuildActivity, "构建失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    
                    // 隐藏进度条
                    baseProgress.visibility = View.INVISIBLE
                    
                    // 启用按钮
                    buildBaseButton.isEnabled = true
                }
            }
        }
    }
    
    /**
     * 显示构建过程中的错误日志
     */
    private fun showBuildErrorLog(errorRecords: List<String>) {
        val errorLogBuilder = AlertDialog.Builder(this)
        errorLogBuilder.setTitle("构建错误日志")
        
        val scrollView = ScrollView(this)
        val textView = TextView(this)
        textView.setPadding(20, 20, 20, 20)
        textView.textSize = 14f
        textView.setTextIsSelectable(true)
        
        val errorLog = StringBuilder()
        errorLog.append("基础词典Trie树构建错误日志 - ${java.util.Date()}\n\n")
        
        errorRecords.forEach { errorRecord ->
            errorLog.append("$errorRecord\n\n")
        }
        
        textView.text = errorLog.toString()
        scrollView.addView(textView)
        
        errorLogBuilder.setView(scrollView)
        errorLogBuilder.setPositiveButton("关闭") { dialog, _ -> dialog.dismiss() }
        errorLogBuilder.setNeutralButton("复制到剪贴板") { _, _ ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Trie构建错误日志", errorLog.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
        
        errorLogBuilder.show()
    }
    
    /**
     * 显示详细构建日志
     */
    private fun showBuildDetailLog() {
        try {
            val logFile = File(filesDir, "logs/base_trie_build_log.txt")
            if (!logFile.exists()) {
                Toast.makeText(this, "找不到详细日志文件", Toast.LENGTH_SHORT).show()
                return
            }
            
            val logContent = logFile.readText()
            
            val detailLogBuilder = AlertDialog.Builder(this)
            detailLogBuilder.setTitle("详细构建日志")
            
            val scrollView = ScrollView(this)
            val textView = TextView(this)
            textView.setPadding(20, 20, 20, 20)
            textView.textSize = 14f
            textView.setTextIsSelectable(true)
            textView.text = logContent
            scrollView.addView(textView)
            
            detailLogBuilder.setView(scrollView)
            detailLogBuilder.setPositiveButton("关闭") { dialog, _ -> dialog.dismiss() }
            detailLogBuilder.setNeutralButton("复制到剪贴板") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Trie构建详细日志", logContent)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            
            detailLogBuilder.show()
        } catch (e: Exception) {
            Toast.makeText(this, "读取日志文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            Timber.e(e, "读取详细日志文件失败")
        }
    }
    
    /**
     * 导出Trie树到外部存储
     */
    private fun exportTrie(type: TrieBuilder.TrieType) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = when (type) {
                    TrieBuilder.TrieType.CHARS -> "chars_trie.dat"
                    TrieBuilder.TrieType.BASE -> "base_trie.dat"
                }
                
                // 使用TrieManager的方法检查文件是否存在
                if (!trieManager.isTrieFileExists(type)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TrieBuildActivity, "Trie树文件不存在", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                val srcFile = File(filesDir, "trie/$fileName")
                
                // 导出到下载目录
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val exportFileName = "${type.name.lowercase()}_trie_$timestamp.dat"
                val exportDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(exportDir, exportFileName)
                
                // 复制文件
                srcFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TrieBuildActivity, 
                        "已导出到下载目录: $exportFileName", 
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "导出Trie树失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TrieBuildActivity, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
    
    /**
     * 测试单字Trie树搜索功能
     */
    private fun testCharsTrie() {
        val inputText = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.test_input).text.toString()
        
        if (inputText.isBlank()) {
            Toast.makeText(this, "请输入要测试的拼音或首字母", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!trieManager.isTrieLoaded(TrieBuilder.TrieType.CHARS)) {
            Toast.makeText(this, "单字Trie树未加载，请先加载", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val results = trieManager.searchCharsByPrefix(inputText, 20)
            val endTime = System.currentTimeMillis()
            
            withContext(Dispatchers.Main) {
                val timeCost = endTime - startTime
                val resultBuilder = StringBuilder()
                resultBuilder.append("单字Trie查询: '$inputText'\n")
                resultBuilder.append("耗时: ${timeCost}ms\n")
                resultBuilder.append("找到${results.size}个结果:\n\n")
                
                results.forEachIndexed { index, wordFreq ->
                    resultBuilder.append("${index + 1}. ${wordFreq.word} (频率: ${wordFreq.frequency})\n")
                }
                
                testResultText.text = resultBuilder.toString()
            }
        }
    }
    
    /**
     * 测试基础词典Trie树搜索功能
     */
    private fun testBaseTrie() {
        val inputText = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.test_input).text.toString()
        
        if (inputText.isBlank()) {
            Toast.makeText(this, "请输入要测试的拼音或首字母", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!trieManager.isTrieLoaded(TrieBuilder.TrieType.BASE)) {
            Toast.makeText(this, "基础词典Trie树未加载，请先加载", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val results = trieManager.searchBaseByPrefix(inputText, 20)
            val endTime = System.currentTimeMillis()
            
            withContext(Dispatchers.Main) {
                val timeCost = endTime - startTime
                val resultBuilder = StringBuilder()
                resultBuilder.append("基础词典Trie查询: '$inputText'\n")
                resultBuilder.append("耗时: ${timeCost}ms\n")
                resultBuilder.append("找到${results.size}个结果:\n\n")
                
                results.forEachIndexed { index, wordFreq ->
                    resultBuilder.append("${index + 1}. ${wordFreq.word} (频率: ${wordFreq.frequency})\n")
                }
                
                testResultText.text = resultBuilder.toString()
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
} 