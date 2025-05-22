package com.shenji.aikeyboard.ui.trie

import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.trie.TrieBuilder
import kotlinx.coroutines.Dispatchers
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
    
    // Trie树构建器
    private lateinit var trieBuilder: TrieBuilder
    
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
            Toast.makeText(this, "基础词典Trie树构建功能尚未实现", Toast.LENGTH_SHORT).show()
        }
        
        // 导出基础词典Trie树
        exportBaseButton.setOnClickListener {
            exportTrie(TrieBuilder.TrieType.BASE)
        }
    }
    
    /**
     * 刷新Trie树状态
     */
    private fun refreshTrieStatus() {
        try {
            // 检查单字Trie树
            val charsFile = File(filesDir, "trie/chars_trie.dat")
            if (charsFile.exists()) {
                val lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(charsFile.lastModified()))
                val fileSize = formatFileSize(charsFile.length())
                
                charsStatusText.text = "状态: 已构建"
                charsFileInfo.text = "文件信息: $fileSize, 更新于 $lastModified"
                exportCharsButton.isEnabled = true
            } else {
                charsStatusText.text = "状态: 未构建"
                charsFileInfo.text = "文件信息: 未构建"
                exportCharsButton.isEnabled = false
            }
            
            // 检查基础词典Trie树
            val baseFile = File(filesDir, "trie/base_trie.dat")
            if (baseFile.exists()) {
                val lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(baseFile.lastModified()))
                val fileSize = formatFileSize(baseFile.length())
                
                baseStatusText.text = "状态: 已构建"
                baseFileInfo.text = "文件信息: $fileSize, 更新于 $lastModified"
                exportBaseButton.isEnabled = true
            } else {
                baseStatusText.text = "状态: 未构建"
                baseFileInfo.text = "文件信息: 未构建"
                exportBaseButton.isEnabled = false
            }
            
            // 更新内存使用情况
            updateMemoryInfo()
        } catch (e: Exception) {
            Timber.e(e, "刷新Trie树状态失败")
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
                    
                    // 更新内存信息
                    updateMemoryInfo()
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
     * 导出Trie树到外部存储
     */
    private fun exportTrie(type: TrieBuilder.TrieType) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = when (type) {
                    TrieBuilder.TrieType.CHARS -> "chars_trie.dat"
                    TrieBuilder.TrieType.BASE -> "base_trie.dat"
                }
                
                val srcFile = File(filesDir, "trie/$fileName")
                if (!srcFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TrieBuildActivity, "Trie树文件不存在", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
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
                Timber.e(e, "导出Trie树失败")
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
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 