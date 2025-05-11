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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupShimmerEffect()
        
        // 确保词典管理器已初始化并触发加载
        if (!DictionaryManager.instance.isLoaded()) {
            Timber.d("词典未加载，开始初始化")
            DictionaryManager.instance.initialize()
        }
        
        // 注册加载进度监听器
        DictionaryManager.instance.addProgressListener(progressListener)
        
        loadDictionaryStats()
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
                
                // 获取统计数据
                val totalEntries = dictionaryRepository.getTotalEntryCount()
                val dbSize = dictionaryRepository.getDictionaryFileSize()
                val formattedDbSize = dictionaryRepository.formatFileSize(dbSize)
                
                // 获取内存占用 - 使用DictionaryManager中的总内存占用
                val memoryUsage = DictionaryManager.instance.getTotalMemoryUsage()
                val formattedMemoryUsage = dictionaryRepository.formatFileSize(memoryUsage)
                
                // 获取词典模块列表
                val modules = dictionaryRepository.getDictionaryModules()
                
                withContext(Dispatchers.Main) {
                    // 更新UI
                    binding.tvTotalEntries.text = getString(R.string.dict_total_entries, totalEntries)
                    binding.tvDatabaseSize.text = getString(R.string.dict_db_size, formattedDbSize)
                    binding.tvMemoryUsage.text = getString(R.string.dict_memory_usage, formattedMemoryUsage)
                    binding.tvModuleCount.text = getString(R.string.dict_module_count, modules.size)
                    
                    // 更新列表
                    moduleAdapter.submitList(modules)
                    
                    // 隐藏骨架屏，显示实际内容
                    showShimmerEffect(false)
                    
                    // 初始化高频词库的加载进度
                    initializeProgress()
                }
            } catch (e: Exception) {
                Timber.e(e, "加载词典统计数据失败")
                withContext(Dispatchers.Main) {
                    // 隐藏骨架屏，显示错误提示
                    showShimmerEffect(false)
                    // 可以在这里显示错误提示
                }
            }
        }
    }
    
    /**
     * 初始化高频词库的加载进度
     */
    private fun initializeProgress() {
        // 获取高频词库的当前进度并更新UI
        for (type in DictionaryManager.HIGH_FREQUENCY_DICT_TYPES) {
            val progress = DictionaryManager.instance.getTypeLoadingProgress(type)
            moduleAdapter.updateProgress(type, progress)
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
    
    override fun onResume() {
        super.onResume()
        // 每次页面可见时刷新数据
        loadDictionaryStats()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 移除加载进度监听器
        DictionaryManager.instance.removeProgressListener(progressListener)
    }
} 