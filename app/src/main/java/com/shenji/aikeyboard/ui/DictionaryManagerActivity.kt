package com.shenji.aikeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.VISIBLE
import android.view.View.GONE
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shenji.aikeyboard.R
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
            R.id.action_logs -> {
                openDictionaryLogs()
                true
            }
            R.id.action_db_info -> {
                showDatabaseInfo()
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
     * 打开词典日志页面
     */
    private fun openDictionaryLogs() {
        val intent = Intent(this, DictionaryLogsActivity::class.java)
        startActivity(intent)
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
                
                // 更新UI
                withContext(Dispatchers.Main) {
                    // 更新基本信息
                    dialogView.findViewById<TextView>(R.id.tvDbFileName).text = "文件名称: $dbName"
                    dialogView.findViewById<TextView>(R.id.tvDbFileSize).text = "文件大小: $dbSizeFormatted"
                    dialogView.findViewById<TextView>(R.id.tvDbEntryCount).text = "词条总数: $totalEntries"
                    dialogView.findViewById<TextView>(R.id.tvDbSchema).text = "数据模式: Entry(id, word, pinyin(索引), initialLetters(索引), frequency, type)"
                    dialogView.findViewById<TextView>(R.id.tvDbIndexes).text = "索引字段: pinyin, initialLetters(首字母缩写)"
                    dialogView.findViewById<TextView>(R.id.tvDbPath).text = "路径: $dbPath"
                    
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
} 