package com.shenji.aikeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.VISIBLE
import android.view.View.GONE
import android.widget.TextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.data.DictionaryModule
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.databinding.ActivityDictionaryManagerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.NumberFormat
import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Handler
import android.os.Looper

/**
 * 词典管理Activity
 */
class DictionaryManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDictionaryManagerBinding
    private lateinit var moduleAdapter: DictionaryModuleAdapter
    private lateinit var shimmerAdapter: DictionaryShimmerAdapter
    private val dictionaryRepository = DictionaryRepository()
    
    // 加载进度监听器
    private val progressListener: (String, Float) -> Unit = { type, progress ->
        lifecycleScope.launch(Dispatchers.Main) {
            moduleAdapter.updateProgress(type, progress)
        }
    }
    
    // 词典加载状态监控任务
    private var dictionaryMonitoringJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupShimmerEffect()
        
        // 确保词典管理器初始化完毕
        DictionaryManager.init()
        
        // 加载数据
        loadDictionaryStats()
        
        // 启动高频词典状态监控
        startDictionaryMonitoring()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_dictionary_manager, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_db_info -> {
                showDatabaseInfo()
                true
            }
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun setupRecyclerView() {
        // 设置实际数据适配器
        moduleAdapter = DictionaryModuleAdapter { module ->
            openDictionaryDetail(module)
        }
        
        binding.rvDictionaryModules.apply {
            layoutManager = LinearLayoutManager(this@DictionaryManagerActivity)
            adapter = moduleAdapter
        }
    }
    
    /**
     * 设置骨架屏效果
     */
    private fun setupShimmerEffect() {
        // 设置骨架屏适配器
        shimmerAdapter = DictionaryShimmerAdapter(6) // 显示6个骨架条目
        
        binding.rvShimmerModules.apply {
            layoutManager = LinearLayoutManager(this@DictionaryManagerActivity)
            adapter = shimmerAdapter
        }
        
        // 禁用闪烁动画
        binding.shimmerStatCard.stopShimmer()
        binding.shimmerModuleList.stopShimmer()
    }
    
    
    
    /**
     * 加载词典统计信息和模块列表
     */
    private fun loadDictionaryStats() {
        // 显示骨架屏
        showShimmerEffect(true)
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 为了更好地展示骨架屏效果，添加轻微延迟
                delay(800)
                
                // 添加调试日志
                Timber.d("开始加载词典统计数据...")
                
                // 获取统计数据
                val totalEntries = dictionaryRepository.getTotalEntryCount()
                val dbSize = dictionaryRepository.getDictionaryFileSize()
                val formattedDbSize = dictionaryRepository.formatFileSize(dbSize)
                
                Timber.d("获取到总词条数: $totalEntries, 数据库大小: $formattedDbSize")
                
                // 获取词典模块列表
                val modules = dictionaryRepository.getDictionaryModules()
                
                Timber.d("获取词典模块列表: ${modules.size}个模块")
                for (module in modules) {
                    Timber.d("词典模块: ${module.type} - ${module.chineseName} (${module.entryCount}条)")
                }
                
                // 获取内存使用情况
                val memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                val memoryUsageFormatted = dictionaryRepository.formatFileSize(memoryUsage)
                Timber.d("内存占用: $memoryUsageFormatted")
                
                withContext(Dispatchers.Main) {
                    // 更新UI
                    binding.tvTotalEntries.text = getString(R.string.dict_total_entries, totalEntries)
                    binding.tvDatabaseSize.text = getString(R.string.dict_db_size, formattedDbSize)
                    binding.tvMemoryUsage.text = getString(R.string.dict_memory_usage, memoryUsageFormatted)
                    binding.tvModuleCount.text = getString(R.string.dict_module_count, modules.size)
                    
                    // 更新列表
                    moduleAdapter.submitList(modules)
                    Timber.d("更新UI完成，提交了${modules.size}个模块到适配器")
                    
                    // 隐藏骨架屏，显示实际内容
                    showShimmerEffect(false)
                }
            } catch (e: Exception) {
                Timber.e(e, "加载词典统计数据失败")
                withContext(Dispatchers.Main) {
                    // 隐藏骨架屏，显示错误提示
                    showShimmerEffect(false)
                    
                    // 在UI上显示错误信息
                    binding.tvTotalEntries.text = "加载失败"
                    binding.tvDatabaseSize.text = "数据库错误: ${e.message}"
                    binding.tvMemoryUsage.text = "请检查assets/shenji_dict.realm文件"
                    
                    // 添加至少一个显示错误的模块
                    val errorModule = DictionaryModule(
                        type = "error",
                        chineseName = "加载错误",
                        entryCount = 0,
                        isInMemory = false,
                        memoryUsage = 0,
                        isGroupHeader = true,
                        isMemoryLoaded = false
                    )
                    moduleAdapter.submitList(listOf(errorModule))
                }
            }
        }
    }
    
    /**
     * 启动高频词典加载状态监控
     */
    private fun startDictionaryMonitoring() {
        // 取消之前的监控任务（如果有）
        dictionaryMonitoringJob?.cancel()
        
        // 创建新的监控任务
        dictionaryMonitoringJob = lifecycleScope.launch(Dispatchers.Default) {
            var lastLoadedState = DictionaryManager.instance.isLoaded()
            
            // 立即执行一次更新（无需等待状态变化）
            updateDictionaryModulesUI()
            
            // 每秒检查一次高频词典加载状态
            while (true) {
                try {
                    val currentLoadedState = DictionaryManager.instance.isLoaded()
                    
                    // 如果加载状态发生了变化或每5秒强制更新一次
                    if (currentLoadedState != lastLoadedState) {
                        Timber.d("高频词典加载状态变化: $lastLoadedState -> $currentLoadedState")
                        
                        // 更新UI
                        updateDictionaryModulesUI()
                        
                        // 更新状态
                        lastLoadedState = currentLoadedState
                    }
                    
                    // 等待1秒
                    delay(1000)
                } catch (e: Exception) {
                    Timber.e(e, "监控高频词典状态时出错")
                    delay(5000) // 发生错误时，等待较长时间再重试
                }
            }
        }
    }
    
    /**
     * 更新词典模块UI
     */
    private suspend fun updateDictionaryModulesUI() {
        // 异步更新词典数据
        withContext(Dispatchers.IO) {
            // 获取所有模块
            val newModules = dictionaryRepository.getDictionaryModules()
            
            // 在主线程更新UI
            withContext(Dispatchers.Main) {
                // 更新内存使用情况
                val memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                val memoryUsageFormatted = dictionaryRepository.formatFileSize(memoryUsage)
                
                // 显示总体内存占用
                binding.tvMemoryUsage.text = getString(R.string.dict_memory_usage, memoryUsageFormatted)
                
                // 更新模块列表
                moduleAdapter.submitList(newModules)
            }
        }
    }
    
    /**
     * 显示或隐藏骨架屏
     */
    private fun showShimmerEffect(show: Boolean) {
        if (show) {
            // 显示骨架屏，隐藏真实内容
            binding.shimmerStatCard.visibility = View.VISIBLE
            binding.shimmerModuleList.visibility = View.VISIBLE
            // 不启动闪烁
            binding.shimmerStatCard.stopShimmer()
            binding.shimmerModuleList.stopShimmer()
            
            binding.cardStats.visibility = View.GONE
            binding.rvDictionaryModules.visibility = View.GONE
        } else {
            // 隐藏骨架屏，显示真实内容
            binding.shimmerStatCard.stopShimmer()
            binding.shimmerModuleList.stopShimmer()
            binding.shimmerStatCard.visibility = View.GONE
            binding.shimmerModuleList.visibility = View.GONE
            
            binding.cardStats.visibility = View.VISIBLE
            binding.rvDictionaryModules.visibility = View.VISIBLE
        }
    }
    
    /**
     * 打开词典详情页面
     */
    private fun openDictionaryDetail(module: DictionaryModule) {
        val intent = Intent(this, DictionaryDetailActivity::class.java).apply {
            putExtra(DictionaryDetailActivity.EXTRA_DICT_TYPE, module.type)
            putExtra(DictionaryDetailActivity.EXTRA_DICT_NAME, module.chineseName)
        }
        startActivity(intent)
    }
    
    /**
     * 显示设置对话框
     */
    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_dictionary_settings, null)
        
        // 创建对话框
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("词典设置")
            .setNegativeButton("关闭") { d, _ -> d.dismiss() }
            .create()
        
        // 设置按钮点击事件
        dialogView.findViewById<Button>(R.id.btnRebuildRealm).setOnClickListener {
            dialog.dismiss()
            confirmRebuildRealm()
        }
        
        dialogView.findViewById<Button>(R.id.btnExportRealm).setOnClickListener {
            dialog.dismiss()
            exportRealmToExternalStorage()
        }
        
        dialogView.findViewById<Button>(R.id.btnBuildTrie).setOnClickListener {
            dialog.dismiss()
            showTrieNotImplementedMessage()
        }
        
        // 显示对话框
        dialog.show()
    }
    
    /**
     * 确认重构Realm数据库
     */
    private fun confirmRebuildRealm() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("重构Realm数据库")
            .setMessage("这将清空现有数据库并从YAML词典重新导入所有数据，这个过程可能需要几分钟。确定要继续吗？")
            .setPositiveButton("确定") { _, _ ->
                rebuildRealmFromYaml()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 从YAML词典重新构建Realm数据库
     */
    private fun rebuildRealmFromYaml() {
        // 显示进度对话框
        val progressDialog = android.app.ProgressDialog(this).apply {
            setTitle("正在重构数据库")
            setMessage("正在从YAML词典重新导入数据，请稍候...")
            setCancelable(false)
            setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            show()
        }
        
        // 启动后台任务
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 获取词典类型列表
                val dictTypes = getDictionaryDefaultTypes()
                val totalDicts = dictTypes.size
                var currentDict = 0
                
                // 创建新的Realm配置
                val config = io.realm.kotlin.RealmConfiguration.Builder(schema = setOf(
                    com.shenji.aikeyboard.data.Entry::class
                ))
                    .directory(ShenjiApplication.instance.filesDir.path + "/dictionaries")
                    .name("shenji_dict.realm")
                    .deleteRealmIfMigrationNeeded()
                    .build()
                
                // 删除现有数据库
                withContext(Dispatchers.Main) {
                    progressDialog.setMessage("正在清除现有数据库...")
                }
                io.realm.kotlin.Realm.deleteRealm(config)
                
                // 创建新的数据库
                val newRealm = io.realm.kotlin.Realm.open(config)
                
                // 从YAML词典导入数据
                for (dictType in dictTypes) {
                    currentDict++
                    withContext(Dispatchers.Main) {
                        progressDialog.setMessage("正在导入 $dictType 词典 ($currentDict/$totalDicts)...")
                        progressDialog.progress = (currentDict - 1) * 100 / totalDicts
                    }
                    
                    try {
                        // 从assets加载YAML词典
                        val dictFile = "cn_dicts/${dictType}.dict.yaml"
                        val inputStream = assets.open(dictFile)
                        val reader = inputStream.bufferedReader()
                        var line: String?
                        var count = 0
                        val totalLines = reader.readLines().size
                        inputStream.reset()
                        
                        // 分批导入，每次1000条
                        val batchSize = 1000
                        var entries = mutableListOf<com.shenji.aikeyboard.data.Entry>()
                        
                        reader.useLines { lines ->
                            lines.forEach { line ->
                                if (line.isNotBlank() && !line.startsWith("#")) {
                                    val parts = line.trim().split("\t")
                                    if (parts.size >= 2) {
                                        val word = parts[0]
                                        val pinyin = parts[1]
                                        val frequency = if (parts.size >= 3) parts[2].toIntOrNull() ?: 100 else 100
                                        
                                        val entry = com.shenji.aikeyboard.data.Entry().apply {
                                            id = "${dictType}_${word}_${System.currentTimeMillis()}_${count}"
                                            this.word = word
                                            this.pinyin = pinyin
                                            this.frequency = frequency
                                            this.type = dictType
                                            this.initialLetters = generateInitialLetters(pinyin)
                                        }
                                        
                                        entries.add(entry)
                                        count++
                                        
                                        // 分批写入
                                        if (entries.size >= batchSize) {
                                            newRealm.writeBlocking {
                                                entries.forEach { copyToRealm(it) }
                                            }
                                            entries.clear()
                                            
                                            // 更新进度
                                            val dictProgress = count * 100 / totalLines
                                            val overallProgress = ((currentDict - 1) * 100 + dictProgress) / totalDicts
                                            withContext(Dispatchers.Main) {
                                                progressDialog.progress = overallProgress
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // 写入剩余条目
                        if (entries.isNotEmpty()) {
                            newRealm.writeBlocking {
                                entries.forEach { copyToRealm(it) }
                            }
                            entries.clear()
                        }
                        
                        inputStream.close()
                    } catch (e: Exception) {
                        Timber.e(e, "导入词典 $dictType 失败")
                        withContext(Dispatchers.Main) {
                            progressDialog.setMessage("导入 $dictType 词典失败: ${e.message}")
                            android.os.Handler(Looper.getMainLooper()).postDelayed({
                                progressDialog.setMessage("继续导入其他词典...")
                            }, 2000)
                        }
                    }
                }
                
                // 关闭新数据库
                newRealm.close()
                
                // 重新初始化应用中的Realm实例
                try {
                    // 先关闭当前实例
                    newRealm.close()
                    
                    // 使用新配置重新打开数据库
                    val realmInstance = io.realm.kotlin.Realm.open(config)
                    
                    // 使用反射重新初始化ShenjiApplication.realm
                    val companionClass = ShenjiApplication::class.java.getDeclaredField("Companion").get(null)
                    val realmField = companionClass.javaClass.getDeclaredField("realm")
                    realmField.isAccessible = true
                    realmField.set(companionClass, realmInstance)
                } catch (e: Exception) {
                    Timber.e(e, "重新初始化Realm实例失败")
                }
                
                // 重新初始化词典管理器
                com.shenji.aikeyboard.data.DictionaryManager.init()
                
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    // 显示成功消息
                    androidx.appcompat.app.AlertDialog.Builder(this@DictionaryManagerActivity)
                        .setTitle("操作成功")
                        .setMessage("Realm数据库重构完成，共导入${dictTypes.size}个词典。")
                        .setPositiveButton("确定") { _, _ ->
                            // 刷新UI
                            loadDictionaryStats()
                        }
                        .show()
                }
            } catch (e: Exception) {
                Timber.e(e, "重构Realm数据库失败")
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    // 显示错误消息
                    androidx.appcompat.app.AlertDialog.Builder(this@DictionaryManagerActivity)
                        .setTitle("操作失败")
                        .setMessage("重构Realm数据库失败: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }
    
    /**
     * 导出Realm实例到外部存储
     */
    private fun exportRealmToExternalStorage() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 显示进度对话框
                val progressDialog = withContext(Dispatchers.Main) {
                    android.app.ProgressDialog(this@DictionaryManagerActivity).apply {
                        setTitle("导出数据库")
                        setMessage("正在导出Realm数据库文件，请稍候...")
                        setCancelable(false)
                        setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER)
                        show()
                    }
                }
                
                // 源数据库文件
                val sourceFile = dictionaryRepository.getDictionaryFile()
                
                // 目标目录
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadDir, "ShenjiKeyboard")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                
                // 创建目标文件，添加时间戳
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val destFile = File(appDir, "shenji_dict_${timestamp}.realm")
                
                // 复制文件
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    // 显示成功消息
                    androidx.appcompat.app.AlertDialog.Builder(this@DictionaryManagerActivity)
                        .setTitle("导出成功")
                        .setMessage("数据库已导出到：${destFile.absolutePath}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            } catch (e: Exception) {
                Timber.e(e, "导出Realm数据库失败")
                withContext(Dispatchers.Main) {
                    // 显示错误消息
                    androidx.appcompat.app.AlertDialog.Builder(this@DictionaryManagerActivity)
                        .setTitle("导出失败")
                        .setMessage("导出Realm数据库失败: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }
    
    /**
     * 显示Trie构建功能未实现消息
     */
    private fun showTrieNotImplementedMessage() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("功能开发中")
            .setMessage("构建双Trie数据功能正在开发中，敬请期待。")
            .setPositiveButton("确定", null)
            .show()
    }
    
    /**
     * 获取默认词典类型列表
     */
    private fun getDictionaryDefaultTypes(): List<String> {
        return listOf(
            "chars", "base", "correlation", "associational", 
            "compatible", "corrections", "place", "people", "poetry"
        )
    }
    
    /**
     * 生成首字母缩写
     */
    private fun generateInitialLetters(pinyin: String): String {
        return pinyin.split(" ")
            .filter { it.isNotEmpty() }
            .joinToString("") { it.first().toString() }
    }
    
    /**
     * 格式化数字为带千位分隔符的字符串
     */
    private fun formatNumber(number: Int): String {
        return NumberFormat.getNumberInstance().format(number)
    }
    
    override fun onResume() {
        super.onResume()
        
        // 页面恢复时强制刷新一次词典状态
        lifecycleScope.launch {
            updateDictionaryModulesUI()
        }
    }
    
    override fun onPause() {
        super.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 取消监控任务
        dictionaryMonitoringJob?.cancel()
    }
    
    /**
     * 显示数据库详情对话框
     */
    private fun showDatabaseInfo() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_database_info, null)
        
        // 创建对话框
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("数据库详情")
            .setPositiveButton("关闭") { d, _ -> d.dismiss() }
            .create()
        
        // 在后台加载数据库信息
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 获取数据库文件信息
                val dbFile = dictionaryRepository.getDictionaryFile()
                val dbName = dbFile.name
                val dbSize = dbFile.length()
                val dbSizeFormatted = dictionaryRepository.formatFileSize(dbSize)
                val dbPath = dbFile.absolutePath
                
                // 获取词条总数
                val totalEntries = dictionaryRepository.getTotalEntryCount()
                
                // 获取所有模块
                val modules = dictionaryRepository.getDictionaryModules()
                
                // 创建Markdown格式文本
                val markdownInfo = buildMarkdownInfo(dbName, dbSizeFormatted, totalEntries, dbPath, modules)
                
                // 更新UI
                withContext(Dispatchers.Main) {
                    // 更新基本信息
                    dialogView.findViewById<TextView>(R.id.tvDbFileName).text = "文件名称: $dbName"
                    dialogView.findViewById<TextView>(R.id.tvDbFileSize).text = "文件大小: $dbSizeFormatted"
                    dialogView.findViewById<TextView>(R.id.tvDbEntryCount).text = "词条总数: $totalEntries"
                    dialogView.findViewById<TextView>(R.id.tvDbSchema).text = "数据模式: Entry(id: 唯一标识符, word: 汉字词语, pinyin: 拼音(索引), initialLetters: 首字母(索引), frequency: 词频, type: 词典类型)"
                    dialogView.findViewById<TextView>(R.id.tvDbIndexes).text = "索引字段: pinyin, initialLetters(首字母缩写)"
                    dialogView.findViewById<TextView>(R.id.tvDbPath).text = "路径: $dbPath"
                    
                    // 设置复制按钮点击事件
                    dialogView.findViewById<Button>(R.id.btnCopyInfo).setOnClickListener {
                        copyToClipboard(markdownInfo)
                    }
                    
                    // 动态添加模块信息
                    val tableLayout = dialogView.findViewById<android.widget.TableLayout>(R.id.tableModules)
                    
                    // 清除示例行
                    if (tableLayout.childCount > 1) {
                        tableLayout.removeViews(1, tableLayout.childCount - 1)
                    }
                    
                    // 添加实际模块数据
                    for (module in modules) {
                        if (!module.isGroupHeader) {
                            val row = android.widget.TableRow(this@DictionaryManagerActivity)
                            row.setPadding(4, 4, 4, 4)
                            
                            // 模块名称
                            val nameView = TextView(this@DictionaryManagerActivity)
                            nameView.text = module.chineseName
                            row.addView(nameView)
                            
                            // 词条数
                            val countView = TextView(this@DictionaryManagerActivity)
                            countView.text = module.entryCount.toString()
                            countView.gravity = android.view.Gravity.CENTER
                            row.addView(countView)
                            
                            // 内存状态
                            val memoryStatusView = TextView(this@DictionaryManagerActivity)
                            memoryStatusView.text = if (module.isInMemory) "已加载" else "未加载"
                            memoryStatusView.gravity = android.view.Gravity.CENTER
                            row.addView(memoryStatusView)
                            
                            // 获取并显示数据样例
                            val sampleView = TextView(this@DictionaryManagerActivity)
                            val samples = dictionaryRepository.getSampleEntries(module.type, 1)
                            if (samples.isNotEmpty()) {
                                val sample = samples.first()
                                sampleView.text = "${sample.word}[${sample.pinyin}](${sample.frequency})"
                            } else {
                                sampleView.text = "无样例"
                            }
                            sampleView.gravity = android.view.Gravity.CENTER
                            row.addView(sampleView)
                            
                            tableLayout.addView(row)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "获取数据库信息失败")
                withContext(Dispatchers.Main) {
                    dialogView.findViewById<TextView>(R.id.tvDbFileName).text = "获取数据库信息失败"
                    dialogView.findViewById<TextView>(R.id.tvDbFileSize).text = "错误: ${e.message}"
                }
            }
        }
        
        // 显示对话框
        dialog.show()
    }
    
    /**
     * 构建Markdown格式的数据库信息
     */
    private suspend fun buildMarkdownInfo(
        dbName: String,
        dbSizeFormatted: String,
        totalEntries: Int,
        dbPath: String,
        modules: List<DictionaryModule>
    ): String {
        val sb = StringBuilder()
        
        // 添加标题
        sb.appendLine("# 神迹输入法数据库详情")
        sb.appendLine()
        
        // 添加基本信息
        sb.appendLine("## 数据库概览")
        sb.appendLine()
        sb.appendLine("- **文件名称**: $dbName")
        sb.appendLine("- **文件大小**: $dbSizeFormatted")
        sb.appendLine("- **词条总数**: $totalEntries")
        sb.appendLine("- **存储路径**: $dbPath")
        sb.appendLine()
        
        // 添加数据模型信息
        sb.appendLine("## 数据模型")
        sb.appendLine()
        sb.appendLine("### Entry类")
        sb.appendLine()
        sb.appendLine("| 字段 | 类型 | 说明 | 索引 |")
        sb.appendLine("|------|------|------|------|")
        sb.appendLine("| id | String | 词条唯一标识符 | 主键 |")
        sb.appendLine("| word | String | 汉字词语/单字 | - |")
        sb.appendLine("| pinyin | String | 拼音，空格分隔 | 是 |")
        sb.appendLine("| initialLetters | String | 拼音首字母缩写 | 是 |")
        sb.appendLine("| frequency | Int | 词频，用于候选词排序 | - |")
        sb.appendLine("| type | String | 词典类型 | - |")
        sb.appendLine()
        
        // 添加词典模块信息
        sb.appendLine("## 词典模块")
        sb.appendLine()
        sb.appendLine("| 模块名称 | 词条数 | 内存状态 | 数据样例 |")
        sb.appendLine("|---------|--------|---------|---------|")
        
        for (module in modules) {
            if (!module.isGroupHeader) {
                // 获取样例数据
                val samples = dictionaryRepository.getSampleEntries(module.type, 1)
                val sampleText = if (samples.isNotEmpty()) {
                    val sample = samples.first()
                    "${sample.word}[${sample.pinyin}](${sample.frequency})"
                } else {
                    "无样例"
                }
                
                sb.appendLine("| ${module.chineseName} | ${module.entryCount} | ${if (module.isInMemory) "已加载" else "未加载"} | $sampleText |")
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 复制文本到剪贴板
     */
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("数据库详情", text)
        clipboard.setPrimaryClip(clip)
        
        // 提示用户
        android.widget.Toast.makeText(this, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
    }
} 