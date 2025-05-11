package com.shenji.aikeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
    
    // 静态缓存
    companion object {
        // 缓存词典模块数据
        private var cachedModules: List<DictionaryModule>? = null
        private var cachedTotalEntries: Int = 0
        private var cachedDbSize: Long = 0
        private var cachedMemoryUsage: Long = 0
        private var isDataLoaded = false
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupShimmerEffect()
        
        // 确保词典管理器初始化完毕
        DictionaryManager.init()
        
        // 判断是否已有缓存数据
        if (isDataLoaded && cachedModules != null) {
            // 使用缓存数据直接更新UI
            updateUIWithCachedData()
        } else {
            // 首次加载或缓存失效时加载数据
            loadDictionaryStats()
        }
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
            R.id.action_export_trie -> {
                exportPrecompiledTrie()
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
     * 使用缓存数据更新UI
     */
    private fun updateUIWithCachedData() {
        binding.tvTotalEntries.text = getString(R.string.dict_total_entries, cachedTotalEntries)
        binding.tvDatabaseSize.text = getString(R.string.dict_db_size, dictionaryRepository.formatFileSize(cachedDbSize))
        binding.tvMemoryUsage.text = getString(R.string.dict_memory_usage, dictionaryRepository.formatFileSize(cachedMemoryUsage))
        binding.tvModuleCount.text = getString(R.string.dict_module_count, cachedModules?.size ?: 0)
        
        // 更新列表
        moduleAdapter.submitList(cachedModules)
        
        // 隐藏骨架屏，显示实际内容
        showShimmerEffect(false)
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
                
                // 获取预编译词典内存使用情况和已加载词条数
                val dictManager = DictionaryManager.instance
                val isPrecompiledDictLoaded = dictManager.isLoaded()
                var memoryUsage = 0L
                var loadedWordCount = 0
                
                if (isPrecompiledDictLoaded) {
                    // 如果预编译词典已加载，获取其内存使用情况
                    memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                    loadedWordCount = dictManager.typeLoadedCountMap["chars"] ?: 0
                    loadedWordCount += dictManager.typeLoadedCountMap["base"] ?: 0
                    Timber.d("预编译词典已加载，内存占用: ${dictionaryRepository.formatFileSize(memoryUsage)}, 加载词条数: $loadedWordCount")
                } else {
                    Timber.d("预编译词典未加载")
                }
                
                // 更新缓存
                cachedTotalEntries = totalEntries
                cachedDbSize = dbSize
                cachedMemoryUsage = memoryUsage
                cachedModules = modules
                isDataLoaded = true
                
                withContext(Dispatchers.Main) {
                    // 更新UI
                    binding.tvTotalEntries.text = getString(R.string.dict_total_entries, totalEntries)
                    binding.tvDatabaseSize.text = getString(R.string.dict_db_size, formattedDbSize)
                    
                    // 根据预编译词典是否加载，显示对应内存使用情况
                    val memoryUsageFormatted = if (isPrecompiledDictLoaded) {
                        dictionaryRepository.formatFileSize(memoryUsage)
                    } else {
                        "0 B" // 未加载内存词典
                    }
                    binding.tvMemoryUsage.text = getString(R.string.dict_memory_usage, memoryUsageFormatted)
                    
                    // 添加高频词典加载状态提示
                    if (isPrecompiledDictLoaded && loadedWordCount > 0) {
                        binding.tvMemoryUsage.text = getString(R.string.dict_memory_usage, memoryUsageFormatted) + 
                                " (已加载${loadedWordCount}个高频词条)"
                    }
                    
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
                        isGroupHeader = true
                    )
                    moduleAdapter.submitList(listOf(errorModule))
                }
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
     * 导出词典信息
     */
    private fun exportPrecompiledTrie() {
        // 显示确认对话框
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("导出词典信息")
            .setMessage("确定要导出词典信息吗？\n\n导出的文件将保存到应用外部存储目录，仅用于分析和调试。")
            .setPositiveButton("确定") { _, _ ->
                // 在后台线程执行导出操作
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        // 执行导出
                        DictionaryManager.instance.exportPrecompiledTrieForBuilding()
                        
                        // 在主线程显示结果
                        withContext(Dispatchers.Main) {
                            showExportMessage("导出成功\n\n词典信息文件已保存到应用外部存储目录下的export文件夹中")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "导出词典信息失败")
                        withContext(Dispatchers.Main) {
                            showExportMessage("导出失败: ${e.message}")
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示导出操作的结果消息
     */
    private fun showExportMessage(message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("导出结果")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        // 不再需要更新加载进度
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 不再需要移除加载进度监听器
    }
} 