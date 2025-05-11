package com.shenji.aikeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.DictionaryModule
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.databinding.ActivityDictionaryManagerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 词典管理Activity
 */
class DictionaryManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDictionaryManagerBinding
    private lateinit var moduleAdapter: DictionaryModuleAdapter
    private val dictionaryRepository = DictionaryRepository()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        loadDictionaryStats()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun setupRecyclerView() {
        moduleAdapter = DictionaryModuleAdapter { module ->
            openDictionaryDetail(module)
        }
        
        binding.rvDictionaryModules.apply {
            layoutManager = LinearLayoutManager(this@DictionaryManagerActivity)
            adapter = moduleAdapter
        }
    }
    
    /**
     * 加载词典统计信息和模块列表
     */
    private fun loadDictionaryStats() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 获取统计数据
                val totalEntries = dictionaryRepository.getTotalEntryCount()
                val dbSize = dictionaryRepository.getDictionaryFileSize()
                val formattedDbSize = dictionaryRepository.formatFileSize(dbSize)
                val memoryUsage = dictionaryRepository.getMemoryUsage()
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
                    
                    binding.progressBar.visibility = android.view.View.GONE
                }
            } catch (e: Exception) {
                Timber.e(e, "加载词典统计数据失败")
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    // 可以在这里显示错误提示
                }
            }
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
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 