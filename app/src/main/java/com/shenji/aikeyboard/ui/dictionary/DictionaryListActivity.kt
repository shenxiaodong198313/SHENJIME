package com.shenji.aikeyboard.ui.dictionary

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.DictionaryModule
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.ui.DictionaryDetailActivity
import com.shenji.aikeyboard.ui.DictionaryModuleAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 词典列表Activity
 * 显示词典模块列表和统计信息
 */
class DictionaryListActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: View
    private lateinit var emptyView: TextView
    private lateinit var totalCountText: TextView
    private lateinit var fileSizeText: TextView
    
    private lateinit var moduleAdapter: DictionaryModuleAdapter
    private val dictionaryRepository = DictionaryRepository()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary_list)
        
        // 设置标题栏
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "词典列表"
        
        // 初始化视图
        initViews()
        
        // 加载词典数据
        loadDictionaryData()
    }
    
    /**
     * 初始化视图
     */
    private fun initViews() {
        recyclerView = findViewById(R.id.rv_dictionary_modules)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.tv_empty)
        totalCountText = findViewById(R.id.tv_total_count)
        fileSizeText = findViewById(R.id.tv_file_size)
        
        // 设置RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        moduleAdapter = DictionaryModuleAdapter { module ->
            openDictionaryDetail(module)
        }
        recyclerView.adapter = moduleAdapter
    }
    
    /**
     * 加载词典数据
     */
    private fun loadDictionaryData() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 获取词典统计信息
                val totalEntryCount = dictionaryRepository.getTotalEntryCount()
                val fileSize = dictionaryRepository.getDictionaryFileSize()
                val formattedFileSize = dictionaryRepository.formatFileSize(fileSize)
                
                // 获取词典模块列表
                val modules = dictionaryRepository.getDictionaryModules()
                
                withContext(Dispatchers.Main) {
                    // 更新统计信息
                    totalCountText.text = "$totalEntryCount 个"
                    fileSizeText.text = formattedFileSize
                    
                    // 更新词典模块列表
                    if (modules.isNotEmpty()) {
                        moduleAdapter.submitList(modules)
                        recyclerView.visibility = View.VISIBLE
                        emptyView.visibility = View.GONE
                    } else {
                        recyclerView.visibility = View.GONE
                        emptyView.visibility = View.VISIBLE
                    }
                    
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Timber.e(e, "加载词典数据失败")
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "加载失败: ${e.message}"
                }
            }
        }
    }
    
    /**
     * 打开词典详情
     */
    private fun openDictionaryDetail(module: DictionaryModule) {
        try {
            val intent = Intent(this, DictionaryDetailActivity::class.java).apply {
                putExtra(DictionaryDetailActivity.EXTRA_DICT_TYPE, module.type)
                putExtra(DictionaryDetailActivity.EXTRA_DICT_NAME, module.chineseName)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "打开词典详情失败")
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