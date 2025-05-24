package com.shenji.aikeyboard.ui.trie

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.trie.TrieBuilder
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.DictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.appcompat.app.AlertDialog
import android.widget.ScrollView
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Menu

/**
 * Trie树构建Activity - 支持所有9种词典类型
 * 用于构建和管理拼音Trie树索引
 */
class TrieBuildActivity : AppCompatActivity() {
    
    // 主要UI元素
    private lateinit var scrollView: ScrollView
    private lateinit var mainContainer: LinearLayout
    private lateinit var memoryCard: CardView
    private lateinit var memoryInfoText: TextView
    
    // 词典卡片映射
    private val dictCards = mutableMapOf<TrieBuilder.TrieType, DictCard>()
    
    // Trie树构建器
    private lateinit var trieBuilder: TrieBuilder
    
    // Trie树管理器
    private val trieManager = TrieManager.instance
    
    // 数据库仓库
    private lateinit var repository: DictionaryRepository
    
    // 日志记录
    private val operationLogs = mutableListOf<String>()
    
    // 词典卡片数据类 - 重新设计
    data class DictCard(
        val card: CardView,
        val nameText: TextView,
        val descriptionText: TextView,
        val dbCountText: TextView,
        val prebuiltStatusText: TextView,
        val memoryStatusText: TextView,
        val progress: ProgressBar,
        val progressText: TextView,
        val buildButton: Button,
        val exportButton: Button,
        val loadButton: Button,
        val testButton: Button
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trie_build)
        
        // 设置Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "双Trie数据构建中心"
        
        // 初始化组件
        trieBuilder = TrieBuilder(this)
        repository = DictionaryRepository()
        
        // 初始化UI组件
        initViews()
        
        // 创建所有词典卡片
        createDictionaryCards()
        
        // 刷新状态
        refreshAllStatus()
        
        // 记录初始化日志
        addLog("Activity初始化完成")
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.trie_build_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_view_logs -> {
                showOperationLogs()
                true
            }
            R.id.action_diagnose_db -> {
                showDatabaseDiagnosis()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * 初始化视图组件
     */
    private fun initViews() {
        scrollView = findViewById(R.id.scroll_view)
        mainContainer = findViewById(R.id.main_container)
        memoryCard = findViewById(R.id.memory_card)
        memoryInfoText = findViewById(R.id.memory_info_text)
    }
    
    /**
     * 创建所有词典类型的卡片
     */
    private fun createDictionaryCards() {
        for (trieType in TrieBuilder.TrieType.values()) {
            val card = createDictionaryCard(trieType)
            dictCards[trieType] = card
            
            // 添加到主容器
            mainContainer.addView(card.card)
        }
    }
    
    /**
     * 创建单个词典卡片 - 重新设计
     */
    private fun createDictionaryCard(trieType: TrieBuilder.TrieType): DictCard {
        val displayName = getDisplayName(trieType)
        val englishName = getTypeString(trieType)
        val description = getDescription(trieType)
        
        // 创建卡片布局
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
            radius = 16f
            cardElevation = 8f
        }
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        
        // 标题：中文名称（英文名称）
        val nameText = TextView(this).apply {
            text = "$displayName ($englishName)"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.teal_700))
        }
        
        // 描述
        val descriptionText = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(getColor(android.R.color.darker_gray))
            setPadding(0, 4, 0, 12)
        }
        
        // 数据库词条数量
        val dbCountText = TextView(this).apply {
            text = "数据库词条数量: 检查中..."
            textSize = 13f
            setTextColor(getColor(android.R.color.black))
        }
        
        // 预编译文件状态
        val prebuiltStatusText = TextView(this).apply {
            text = "预编译文件状态: 检查中..."
            textSize = 13f
            setTextColor(getColor(android.R.color.black))
            setPadding(0, 4, 0, 0)
        }
        
        // Trie内存加载状态
        val memoryStatusText = TextView(this).apply {
            text = "Trie内存加载状态: 检查中..."
            textSize = 13f
            setTextColor(getColor(android.R.color.black))
            setPadding(0, 4, 0, 16)
        }
        
        // 进度条
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }
        
        val progressText = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(getColor(android.R.color.holo_blue_dark))
            visibility = View.GONE
            setPadding(0, 4, 0, 16)
        }
        
        // 操作按钮容器
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        
        val buildButton = Button(this).apply {
            text = "从Realm构建"
            setOnClickListener { showBuildConfigDialog(trieType) }
        }
        
        val exportButton = Button(this).apply {
            text = "导出"
            isEnabled = false
            setOnClickListener { exportTrie(trieType) }
        }
        
        val loadButton = Button(this).apply {
            text = "加载到内存"
            isEnabled = false
            setOnClickListener { toggleMemoryLoad(trieType) }
        }
        
        val testButton = Button(this).apply {
            text = "测试查询"
            isEnabled = false
            setOnClickListener { testTrie(trieType) }
        }
        
        // 组装布局
        buttonContainer.addView(buildButton)
        buttonContainer.addView(exportButton)
        buttonContainer.addView(loadButton)
        buttonContainer.addView(testButton)
        
        container.addView(nameText)
        container.addView(descriptionText)
        container.addView(dbCountText)
        container.addView(prebuiltStatusText)
        container.addView(memoryStatusText)
        container.addView(progress)
        container.addView(progressText)
        container.addView(buttonContainer)
        
        card.addView(container)
        
        return DictCard(
            card = card,
            nameText = nameText,
            descriptionText = descriptionText,
            dbCountText = dbCountText,
            prebuiltStatusText = prebuiltStatusText,
            memoryStatusText = memoryStatusText,
            progress = progress,
            progressText = progressText,
            buildButton = buildButton,
            exportButton = exportButton,
            loadButton = loadButton,
            testButton = testButton
        )
    }
    
    /**
     * 刷新所有状态
     */
    private fun refreshAllStatus() {
        for (trieType in TrieBuilder.TrieType.values()) {
            refreshCardStatus(trieType)
        }
        updateMemoryInfo()
    }
    
    /**
     * 刷新单个词典卡片状态 - 重新设计
     */
    private fun refreshCardStatus(trieType: TrieBuilder.TrieType) {
        val card = dictCards[trieType] ?: return
        val displayName = getDisplayName(trieType)
        
        try {
            lifecycleScope.launch(Dispatchers.IO) {
                // 获取数据库词条数量
                val dbCount = repository.getEntryCountByType(getTypeString(trieType))
                
                // 检查预编译文件状态
                val hasPrebuiltFile = trieManager.hasPrebuiltTrie(trieType)
                val hasUserBuiltFile = trieManager.hasUserBuiltTrie(trieType)
                
                // 获取文件大小信息
                var prebuiltFileSize = ""
                if (hasPrebuiltFile) {
                    try {
                        val assetPath = "trie/${getTypeString(trieType)}_trie.dat"
                        val inputStream = assets.open(assetPath)
                        val fileSize = inputStream.available().toLong()
                        inputStream.close()
                        prebuiltFileSize = " (${formatFileSize(fileSize)})"
                    } catch (e: Exception) {
                        prebuiltFileSize = " (大小未知)"
                    }
                }
                
                // 检查内存加载状态
                val isLoadedInMemory = trieManager.isTrieLoaded(trieType)
                var memoryInfo = ""
                if (isLoadedInMemory) {
                    val stats = trieManager.getTrieMemoryStats(trieType)
                    if (stats != null) {
                        val memoryUsage = trieManager.getTrieMemoryUsage(trieType)
                        memoryInfo = " (${formatFileSize(memoryUsage)}, 节点:${stats.nodeCount}, 词条:${stats.wordCount})"
                    }
                }
                
                withContext(Dispatchers.Main) {
                    // 更新数据库词条数量
                    card.dbCountText.text = "数据库词条数量: ${if (dbCount > 0) "$dbCount 条" else "无数据"}"
                    
                    // 更新预编译文件状态
                    val prebuiltStatus = when {
                        hasPrebuiltFile -> "存在$prebuiltFileSize"
                        hasUserBuiltFile -> "用户构建文件存在"
                        else -> "不存在"
                    }
                    card.prebuiltStatusText.text = "预编译文件状态: $prebuiltStatus"
                    
                    // 更新内存加载状态
                    val memoryStatus = if (isLoadedInMemory) "已加载$memoryInfo" else "未加载"
                    card.memoryStatusText.text = "Trie内存加载状态: $memoryStatus"
                    
                    // 更新按钮状态
                    card.buildButton.isEnabled = dbCount > 0
                    card.exportButton.isEnabled = hasPrebuiltFile || hasUserBuiltFile
                    
                    if (isLoadedInMemory) {
                        card.loadButton.text = "释放内存"
                        card.loadButton.isEnabled = true
                        card.testButton.isEnabled = true
                    } else {
                        card.loadButton.text = "加载到内存"
                        card.loadButton.isEnabled = hasPrebuiltFile || hasUserBuiltFile
                        card.testButton.isEnabled = false
                    }
                    
                    // 设置颜色
                    if (isLoadedInMemory) {
                        card.memoryStatusText.setTextColor(getColor(R.color.teal_700))
                    } else {
                        card.memoryStatusText.setTextColor(getColor(android.R.color.black))
                    }
                    
                    if (hasPrebuiltFile || hasUserBuiltFile) {
                        card.prebuiltStatusText.setTextColor(getColor(R.color.teal_700))
                    } else {
                        card.prebuiltStatusText.setTextColor(getColor(android.R.color.holo_red_light))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "刷新${displayName}状态失败")
            card.dbCountText.text = "数据库词条数量: 检查失败"
            card.prebuiltStatusText.text = "预编译文件状态: 检查失败"
            card.memoryStatusText.text = "Trie内存加载状态: 检查失败"
        }
    }
    
    /**
     * 更新系统内存信息 - 重新设计
     */
    private fun updateMemoryInfo() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val runtime = Runtime.getRuntime()
                val maxMemory = runtime.maxMemory()
                val totalMemory = runtime.totalMemory()
                val freeMemory = runtime.freeMemory()
                val usedMemory = totalMemory - freeMemory
                val availableMemory = maxMemory - usedMemory
                
                // 统计已加载的Trie
                val loadedTries = trieManager.getLoadedTrieTypes()
                val loadedTrieNames = loadedTries.map { getDisplayName(it) }
                
                // 计算Trie总内存占用
                var totalTrieMemory = 0L
                for (trieType in loadedTries) {
                    totalTrieMemory += trieManager.getTrieMemoryUsage(trieType)
                }
                
                val memoryInfo = buildString {
                    append("系统内存: ${formatFileSize(usedMemory)} / ${formatFileSize(maxMemory)}\n")
                    append("已加载Trie: ${loadedTries.size} 个 (${loadedTrieNames.joinToString(", ")})\n")
                    append("Trie总内存占用: ${formatFileSize(totalTrieMemory)}\n")
                    append("可用内存: ${formatFileSize(availableMemory)}")
                }
                
                withContext(Dispatchers.Main) {
                    memoryInfoText.text = memoryInfo
                }
                
            } catch (e: Exception) {
                Timber.e(e, "更新内存信息失败")
                withContext(Dispatchers.Main) {
                    memoryInfoText.text = "内存信息获取失败: ${e.message}"
                }
            }
        }
    }
    
    /**
     * 显示构建配置对话框
     */
    private fun showBuildConfigDialog(trieType: TrieBuilder.TrieType) {
        val displayName = getDisplayName(trieType)
        
        // 检查数据库是否有数据
        lifecycleScope.launch(Dispatchers.IO) {
            val count = repository.getEntryCountByType(getTypeString(trieType))
            
            withContext(Dispatchers.Main) {
                if (count <= 0) {
                    Toast.makeText(this@TrieBuildActivity, "${displayName}数据库无数据，无法构建", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                
                // 显示构建配置对话框
                val dialogView = layoutInflater.inflate(R.layout.dialog_build_config, null)
                val frequencyThresholdInput = dialogView.findViewById<EditText>(R.id.frequency_threshold_input)
                val enableOptimizationCheckbox = dialogView.findViewById<CheckBox>(R.id.enable_optimization_checkbox)
                
                // 设置默认值
                frequencyThresholdInput.setText("1")
                enableOptimizationCheckbox.isChecked = true
                
                AlertDialog.Builder(this@TrieBuildActivity)
                    .setTitle("构建${displayName}")
                    .setMessage("数据库中有 $count 条记录\n请配置构建参数:")
                    .setView(dialogView)
                    .setPositiveButton("开始构建") { _, _ ->
                        val threshold = frequencyThresholdInput.text.toString().toIntOrNull() ?: 1
                        val enableOptimization = enableOptimizationCheckbox.isChecked
                        buildTrie(trieType, threshold, enableOptimization)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }
    
    /**
     * 构建Trie树
     */
    private fun buildTrie(trieType: TrieBuilder.TrieType, frequencyThreshold: Int, enableOptimization: Boolean) {
        val card = dictCards[trieType] ?: return
        val displayName = getDisplayName(trieType)
        
        // 记录开始构建日志
        addLog("开始构建${displayName} - 词频阈值: $frequencyThreshold, 优化: $enableOptimization")
        
        // 显示进度条
        card.progress.visibility = View.VISIBLE
        card.progressText.visibility = View.VISIBLE
        card.progress.progress = 0
        
        // 禁用构建按钮
        card.buildButton.isEnabled = false
        
        // 提示用户
        Toast.makeText(this, "开始构建${displayName}，词频阈值: $frequencyThreshold", Toast.LENGTH_LONG).show()
        
        // 保持屏幕常亮
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 开始构建
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 更新状态
                withContext(Dispatchers.Main) {
                    card.memoryStatusText.text = "Trie内存加载状态: 正在构建..."
                    card.progressText.text = "准备中..."
                }
                
                Timber.d("开始构建${displayName}Trie树，词频阈值: $frequencyThreshold")
                
                // 构建Trie树 - 使用简化的构建方法
                addLog("${displayName} - 开始构建Trie树")
                val trie = trieBuilder.buildTrie(trieType) { progress, message ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        card.progress.progress = progress
                        card.progressText.text = message
                    }
                    // 记录重要进度
                    if (progress % 20 == 0 || progress >= 95) {
                        addLog("${displayName} - 构建进度: ${progress}% - $message")
                    }
                }
                
                // 保存Trie树
                addLog("${displayName} - 开始保存Trie树到文件")
                val file = trieBuilder.saveTrie(trie, trieType)
                addLog("${displayName} - Trie树保存成功: ${file.absolutePath}, 大小: ${formatFileSize(file.length())}")
                
                // 自动尝试加载到内存
                addLog("${displayName} - 尝试自动加载到内存")
                val loadSuccess = trieManager.loadTrieToMemory(trieType)
                if (loadSuccess) {
                    addLog("${displayName} - 自动加载到内存成功")
                } else {
                    addLog("${displayName} - 自动加载到内存失败")
                }
                
                // 更新UI
                withContext(Dispatchers.Main) {
                    val fileSize = formatFileSize(file.length())
                    
                    // 记录构建成功日志
                    addLog("${displayName} - 构建完成! 文件大小: $fileSize")
                    
                    // 隐藏进度条
                    card.progress.visibility = View.GONE
                    card.progressText.visibility = View.GONE
                    
                    // 启用按钮
                    card.buildButton.isEnabled = true
                    
                    // 刷新状态
                    refreshCardStatus(trieType)
                    
                    // 更新内存信息
                    updateMemoryInfo()
                    
                    if (loadSuccess) {
                        Toast.makeText(this@TrieBuildActivity, "${displayName}构建并加载成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@TrieBuildActivity, "${displayName}构建成功", Toast.LENGTH_SHORT).show()
                    }
                    
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "构建${displayName}Trie失败")
                addLog("${displayName} - 构建失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    card.progress.visibility = View.GONE
                    card.progressText.visibility = View.GONE
                    card.buildButton.isEnabled = true
                    
                    Toast.makeText(this@TrieBuildActivity, "构建失败: ${e.message}", Toast.LENGTH_LONG).show()
                    
                    // 刷新状态
                    refreshCardStatus(trieType)
                    
                    // 取消屏幕常亮
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }
    
    /**
     * 切换内存加载状态
     */
    private fun toggleMemoryLoad(trieType: TrieBuilder.TrieType) {
        val displayName = getDisplayName(trieType)
        val card = dictCards[trieType] ?: return
        
        if (trieManager.isTrieLoaded(trieType)) {
            // 释放内存
            trieManager.unloadTrie(trieType)
            addLog("${displayName} - 已从内存释放")
            Toast.makeText(this, "${displayName}已从内存释放", Toast.LENGTH_SHORT).show()
        } else {
            // 加载到内存
            addLog("${displayName} - 开始加载到内存")
            
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val startTime = System.currentTimeMillis()
                    val success = trieManager.loadTrieToMemory(trieType)
                    val endTime = System.currentTimeMillis()
                    val loadTime = endTime - startTime
                    
                    withContext(Dispatchers.Main) {
                        if (success) {
                            addLog("${displayName} - 加载到内存成功，耗时${loadTime}ms")
                            Toast.makeText(this@TrieBuildActivity, "${displayName}加载成功", Toast.LENGTH_SHORT).show()
                        } else {
                            addLog("${displayName} - 加载到内存失败")
                            Toast.makeText(this@TrieBuildActivity, "${displayName}加载失败", Toast.LENGTH_SHORT).show()
                        }
                        
                        // 刷新状态
                        refreshCardStatus(trieType)
                        updateMemoryInfo()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "加载${displayName}到内存失败")
                    addLog("${displayName} - 加载到内存异常: ${e.message}")
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TrieBuildActivity, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                        refreshCardStatus(trieType)
                    }
                }
            }
        }
        
        // 立即刷新状态
        refreshCardStatus(trieType)
        updateMemoryInfo()
    }
    
    /**
     * 测试Trie查询功能
     */
    private fun testTrie(trieType: TrieBuilder.TrieType) {
        val displayName = getDisplayName(trieType)
        
        if (!trieManager.isTrieLoaded(trieType)) {
            Toast.makeText(this, "${displayName}未加载到内存", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 显示测试对话框
        val input = EditText(this).apply {
            hint = "输入拼音前缀进行测试，如: zh, zhong"
            setText("zh") // 默认测试值
        }
        
        AlertDialog.Builder(this)
            .setTitle("测试${displayName}查询")
            .setMessage("请输入拼音前缀进行查询测试:")
            .setView(input)
            .setPositiveButton("查询") { _, _ ->
                val prefix = input.text.toString().trim()
                if (prefix.isNotEmpty()) {
                    performTrieTest(trieType, prefix)
                } else {
                    Toast.makeText(this, "请输入查询前缀", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 执行Trie查询测试
     */
    private fun performTrieTest(trieType: TrieBuilder.TrieType, prefix: String) {
        val displayName = getDisplayName(trieType)
        addLog("${displayName} - 开始测试查询: '$prefix'")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val results = trieManager.searchByPrefix(trieType, prefix, 20)
                val endTime = System.currentTimeMillis()
                val searchTime = endTime - startTime
                
                withContext(Dispatchers.Main) {
                    val resultText = if (results.isNotEmpty()) {
                        val topResults = results.take(10).joinToString("\n") { 
                            "${it.word} (频率: ${it.frequency})" 
                        }
                        "查询结果 (${results.size}条，耗时${searchTime}ms):\n\n$topResults" +
                                if (results.size > 10) "\n\n... 还有${results.size - 10}条结果" else ""
                    } else {
                        "未找到匹配结果 (耗时${searchTime}ms)"
                    }
                    
                    AlertDialog.Builder(this@TrieBuildActivity)
                        .setTitle("${displayName}查询结果")
                        .setMessage(resultText)
                        .setPositiveButton("复制结果") { _, _ ->
                            copyToClipboard("${displayName}查询'$prefix'", resultText)
                        }
                        .setNegativeButton("关闭", null)
                        .show()
                    
                    addLog("${displayName} - 查询'$prefix'完成，找到${results.size}条结果，耗时${searchTime}ms")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "${displayName}查询测试失败")
                addLog("${displayName} - 查询'$prefix'失败: ${e.message}")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TrieBuildActivity, "查询失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    /**
     * 导出Trie文件
     */
    private fun exportTrie(trieType: TrieBuilder.TrieType) {
        val displayName = getDisplayName(trieType)
        
        if (!trieManager.isTrieFileExists(trieType)) {
            Toast.makeText(this, "${displayName}文件不存在", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 这里可以实现导出功能，比如复制到外部存储
        Toast.makeText(this, "${displayName}导出功能开发中", Toast.LENGTH_SHORT).show()
        addLog("${displayName} - 导出请求（功能开发中）")
    }
    
    /**
     * 显示操作日志
     */
    private fun showOperationLogs() {
        val logsText = if (operationLogs.isNotEmpty()) {
            operationLogs.takeLast(50).joinToString("\n")
        } else {
            "暂无操作日志"
        }
        
        AlertDialog.Builder(this)
            .setTitle("操作日志 (最近50条)")
            .setMessage(logsText)
            .setPositiveButton("复制日志") { _, _ ->
                copyToClipboard("Trie构建日志", logsText)
            }
            .setNegativeButton("清空日志") { _, _ ->
                operationLogs.clear()
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("关闭", null)
            .show()
    }
    
    /**
     * 显示数据库诊断信息
     */
    private fun showDatabaseDiagnosis() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val diagnosisInfo = buildString {
                    append("=== 数据库诊断信息 ===\n\n")
                    
                    for (trieType in TrieBuilder.TrieType.values()) {
                        val displayName = getDisplayName(trieType)
                        val count = repository.getEntryCountByType(getTypeString(trieType))
                        append("${displayName}: ${count} 条记录\n")
                    }
                    
                    append("\n=== 文件状态 ===\n")
                    for (trieType in TrieBuilder.TrieType.values()) {
                        val displayName = getDisplayName(trieType)
                        val hasPrebuilt = trieManager.hasPrebuiltTrie(trieType)
                        val hasUserBuilt = trieManager.hasUserBuiltTrie(trieType)
                        val isLoaded = trieManager.isTrieLoaded(trieType)
                        
                        append("${displayName}:\n")
                        append("  预编译: ${if (hasPrebuilt) "✓" else "✗"}\n")
                        append("  用户构建: ${if (hasUserBuilt) "✓" else "✗"}\n")
                        append("  内存加载: ${if (isLoaded) "✓" else "✗"}\n")
                    }
                }
                
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@TrieBuildActivity)
                        .setTitle("数据库诊断")
                        .setMessage(diagnosisInfo)
                        .setPositiveButton("复制信息") { _, _ ->
                            copyToClipboard("数据库诊断信息", diagnosisInfo)
                        }
                        .setNegativeButton("关闭", null)
                        .show()
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TrieBuildActivity, "诊断失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    /**
     * 获取词典描述
     */
    private fun getDescription(trieType: TrieBuilder.TrieType): String {
        return when (trieType) {
            TrieBuilder.TrieType.CHARS -> "单字拼音索引，支持汉字单字查询"
            TrieBuilder.TrieType.BASE -> "基础词汇库，包含常用词汇和短语"
            TrieBuilder.TrieType.CORRELATION -> "关联词典，提供词汇关联建议"
            TrieBuilder.TrieType.ASSOCIATIONAL -> "联想词典，支持智能联想输入"
            TrieBuilder.TrieType.PLACE -> "地名词典，包含全国地名信息"
            TrieBuilder.TrieType.PEOPLE -> "人名词典，常见姓名拼音索引"
            TrieBuilder.TrieType.POETRY -> "诗词词典，古诗词名句索引"
            TrieBuilder.TrieType.CORRECTIONS -> "纠错词典，输入纠错和建议"
            TrieBuilder.TrieType.COMPATIBLE -> "兼容词典，向后兼容支持"
        }
    }
    
    /**
     * 获取Trie类型对应的字符串
     */
    private fun getTypeString(trieType: TrieBuilder.TrieType): String {
        return when (trieType) {
            TrieBuilder.TrieType.CHARS -> "chars"
            TrieBuilder.TrieType.BASE -> "base"
            TrieBuilder.TrieType.CORRELATION -> "correlation"
            TrieBuilder.TrieType.ASSOCIATIONAL -> "associational"
            TrieBuilder.TrieType.PLACE -> "place"
            TrieBuilder.TrieType.PEOPLE -> "people"
            TrieBuilder.TrieType.POETRY -> "poetry"
            TrieBuilder.TrieType.CORRECTIONS -> "corrections"
            TrieBuilder.TrieType.COMPATIBLE -> "compatible"
        }
    }
    
    /**
     * 获取Trie类型的显示名称
     */
    private fun getDisplayName(trieType: TrieBuilder.TrieType): String {
        return when (trieType) {
            TrieBuilder.TrieType.CHARS -> "单字词典"
            TrieBuilder.TrieType.BASE -> "基础词典"
            TrieBuilder.TrieType.CORRELATION -> "关联词典"
            TrieBuilder.TrieType.ASSOCIATIONAL -> "联想词典"
            TrieBuilder.TrieType.PLACE -> "地名词典"
            TrieBuilder.TrieType.PEOPLE -> "人名词典"
            TrieBuilder.TrieType.POETRY -> "诗词词典"
            TrieBuilder.TrieType.CORRECTIONS -> "纠错词典"
            TrieBuilder.TrieType.COMPATIBLE -> "兼容词典"
        }
    }
    
    /**
     * 复制文本到剪贴板
     */
    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1fKB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1fMB", mb)
        val gb = mb / 1024.0
        return String.format("%.1fGB", gb)
    }
    
    /**
     * 添加操作日志
     */
    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"
        operationLogs.add(logEntry)
        Timber.d("TrieBuild: $logEntry")
        
        // 保持日志数量在合理范围内
        if (operationLogs.size > 100) {
            operationLogs.removeAt(0)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 清理资源
        operationLogs.clear()
    }
}