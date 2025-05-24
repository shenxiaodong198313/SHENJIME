package com.shenji.aikeyboard.ui.dictionary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.DictionaryModule
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.ui.DictionaryDetailActivity
import com.shenji.aikeyboard.ui.DictionaryModuleAdapter
import com.shenji.aikeyboard.utils.DatabaseExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 词典列表Activity
 * 显示词典模块列表和统计信息，支持导出功能
 */
class DictionaryListActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: View
    private lateinit var emptyView: TextView
    private lateinit var totalCountText: TextView
    private lateinit var fileSizeText: TextView
    
    private lateinit var moduleAdapter: DictionaryModuleAdapter
    private val dictionaryRepository = DictionaryRepository()
    private lateinit var databaseExporter: DatabaseExporter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary_list)
        
        // 初始化导出工具
        databaseExporter = DatabaseExporter(this)
        
        // 设置标题栏
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "词典列表"
        
        // 初始化视图
        initViews()
        
        // 加载词典数据
        loadDictionaryData()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_dictionary_list, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_refresh_cache -> {
                refreshCacheAndReload()
                true
            }
            R.id.action_export -> {
                exportDatabase()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
     * 加载词典数据（优化版，使用缓存）
     */
    private fun loadDictionaryData() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                
                // 🔧 使用缓存的统计信息方法
                val statistics = dictionaryRepository.getDictionaryStatistics()
                
                // 🔧 使用缓存的模块列表方法
                val modules = dictionaryRepository.getDictionaryModules()
                
                val loadTime = System.currentTimeMillis() - startTime
                
                // 记录缓存状态
                val cacheInfo = dictionaryRepository.getCacheInfo()
                Timber.d("词典数据加载完成，耗时: ${loadTime}ms")
                Timber.d("缓存状态: $cacheInfo")
                
                withContext(Dispatchers.Main) {
                    // 更新统计信息
                    totalCountText.text = "${statistics.totalEntryCount} 个"
                    fileSizeText.text = statistics.formattedFileSize
                    
                    // 更新词典模块列表
                    if (modules.isNotEmpty()) {
                        moduleAdapter.submitList(modules)
                        recyclerView.visibility = View.VISIBLE
                        emptyView.visibility = View.GONE
                        
                        // 如果是从缓存加载的，显示提示
                        if (loadTime < 50) { // 加载时间很短，说明使用了缓存
                            Toast.makeText(this@DictionaryListActivity, 
                                "已从缓存加载 (${loadTime}ms)", Toast.LENGTH_SHORT).show()
                        }
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
     * 刷新缓存并重新加载数据
     */
    private fun refreshCacheAndReload() {
        Toast.makeText(this, "正在刷新缓存...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 强制刷新缓存
                dictionaryRepository.refreshCache()
                
                withContext(Dispatchers.Main) {
                    // 重新加载数据
                    loadDictionaryData()
                    Toast.makeText(this@DictionaryListActivity, "缓存已刷新", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "刷新缓存失败")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DictionaryListActivity, "刷新缓存失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    /**
     * 导出数据库
     */
    private fun exportDatabase() {
        // 显示导出进度对话框
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("导出数据库")
            .setMessage("正在导出数据库文件，请稍候...")
            .setCancelable(false)
            .create()
        
        progressDialog.show()
        
        lifecycleScope.launch {
            try {
                val result = databaseExporter.exportDatabase()
                
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    
                    if (result.success) {
                        showExportSuccessDialog(result.filePath!!, result.fileSize)
                    } else {
                        showExportErrorDialog(result.errorMessage ?: "未知错误")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    showExportErrorDialog("导出失败: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 显示导出成功对话框
     */
    private fun showExportSuccessDialog(filePath: String, fileSize: Long) {
        val formattedSize = databaseExporter.formatFileSize(fileSize)
        
        AlertDialog.Builder(this)
            .setTitle("导出成功")
            .setMessage("数据库已成功导出到：\n\n$filePath\n\n文件大小：$formattedSize\n\n注意：文件保存在应用私有目录中，可以通过文件管理器访问。")
            .setPositiveButton("复制路径") { _, _ ->
                copyToClipboard(filePath)
            }
            .setNegativeButton("确定", null)
            .show()
    }
    
    /**
     * 显示导出错误对话框
     */
    private fun showExportErrorDialog(errorMessage: String) {
        AlertDialog.Builder(this)
            .setTitle("导出失败")
            .setMessage(errorMessage)
            .setPositiveButton("确定", null)
            .show()
    }
    
    /**
     * 复制文本到剪贴板
     */
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("导出路径", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "路径已复制到剪贴板", Toast.LENGTH_SHORT).show()
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
} 