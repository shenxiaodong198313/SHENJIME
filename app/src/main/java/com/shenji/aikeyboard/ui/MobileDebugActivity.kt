package com.shenji.aikeyboard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.data.WordFrequency
import com.shenji.aikeyboard.databinding.ActivityMobileDebugBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView

/**
 * 移动端调试界面
 * 提供在手机上进行拼音输入法调试的功能
 */
class MobileDebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMobileDebugBinding
    private val logBuffer = StringBuilder()
    private val repository = DictionaryRepository()
    
    // 预设的测试用例
    private val testCases = listOf(
        "wj" to "声母缩写查询测试",
        "nb" to "声母缩写查询测试",
        "wei" to "单音节查询测试",
        "weix" to "音节+额外字母测试",
        "beijing" to "多音节测试",
        "w" to "单字母测试"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMobileDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupUI()
        checkRealmStatus()
        initTestCases()
        
        logMessage("移动调试界面已初始化")
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "拼音输入法调试工具"
            setDisplayHomeAsUpEnabled(true)
        }
    }
    
    private fun setupUI() {
        // 设置查询按钮
        binding.btnQuery.setOnClickListener {
            val input = binding.etPinyinInput.text.toString().trim()
            if (input.isNotEmpty()) {
                performPinyinQuery(input)
            } else {
                Toast.makeText(this, "请输入拼音", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 设置索引检查按钮
        binding.btnCheckIndex.setOnClickListener {
            val input = binding.etPinyinInput.text.toString().trim()
            if (input.isNotEmpty() && input.length >= 2) {
                checkInitialLettersIndex(input)
            } else {
                Toast.makeText(this, "请输入至少2个字母的首字母缩写", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 设置清除日志按钮
        binding.btnClearLog.setOnClickListener {
            clearLog()
        }
        
        // 设置导出日志按钮
        binding.btnExportLog.setOnClickListener {
            exportLog()
        }
        
        // 设置功能按钮
        binding.btnToInputTest.setOnClickListener {
            startActivity(Intent(this, InputTestActivity::class.java))
        }
        
        binding.btnToSyllableTest.setOnClickListener {
            startActivity(Intent(this, SyllableTestActivity::class.java))
        }
        
        binding.btnToDictManager.setOnClickListener {
            startActivity(Intent(this, DictionaryManagerActivity::class.java))
        }
    }
    
    private fun initTestCases() {
        // 设置测试用例下拉列表
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, 
                                  testCases.map { "${it.first} - ${it.second}" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTestCases.adapter = adapter
        
        binding.spinnerTestCases.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) { // 跳过第一个"选择测试用例"选项
                    val selectedCase = testCases[position - 1]
                    binding.etPinyinInput.setText(selectedCase.first)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 不做操作
            }
        }
    }
    
    private fun checkRealmStatus() {
        lifecycleScope.launch {
            try {
                binding.tvRealmStatus.text = "Realm状态: 检查中..."
                
                val isRealmConnected = withContext(Dispatchers.IO) {
                    try {
                        val dictManager = DictionaryManager.instance
                        val isInitialized = dictManager.isLoaded()
                        return@withContext isInitialized
                    } catch (e: Exception) {
                        logMessage("检查Realm状态出错: ${e.message}")
                        return@withContext false
                    }
                }
                
                binding.tvRealmStatus.text = "Realm状态: ${if (isRealmConnected) "已连接" else "未连接"}"
                
                // 如果已连接，获取词典统计信息
                if (isRealmConnected) {
                    withContext(Dispatchers.IO) {
                        val totalEntries = repository.getTotalEntryCount()
                        val fileSize = repository.getDictionaryFileSize() / (1024 * 1024) // MB
                        
                        withContext(Dispatchers.Main) {
                            binding.tvDictInfo.text = "词典信息: ${totalEntries}个词条, ${fileSize}MB"
                            binding.tvDictInfo.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "检查Realm状态过程中发生异常")
                binding.tvRealmStatus.text = "Realm状态: 检查失败"
            }
        }
    }
    
    /**
     * 执行拼音查询
     */
    private fun performPinyinQuery(input: String) {
        lifecycleScope.launch {
            try {
                logMessage("开始查询: $input")
                binding.progressBar.visibility = View.VISIBLE
                
                // 查询候选词
                val dictManager = DictionaryManager.instance
                val startTime = System.currentTimeMillis()
                
                val candidateWords = withContext(Dispatchers.IO) {
                    dictManager.searchWords(input, 10)
                }
                
                val endTime = System.currentTimeMillis()
                val queryTime = endTime - startTime
                
                // 记录结果
                if (candidateWords.isNotEmpty()) {
                    logMessage("查询结果 (耗时: ${queryTime}ms): ")
                    candidateWords.forEachIndexed { index, word ->
                        logMessage("  ${index + 1}. ${word.word} (频率: ${word.frequency})")
                    }
                } else {
                    logMessage("查询无结果 (耗时: ${queryTime}ms)")
                    logMessage("尝试分析原因...")
                    
                    // 首字母缩写分析
                    if (input.length >= 2 && input.all { it.isLowerCase() }) {
                        analyzeInitialAbbreviation(input)
                    }
                }
                
                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                Timber.e(e, "执行拼音查询失败: ${e.message}")
                logMessage("查询出错: ${e.message}")
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    /**
     * 分析首字母缩写查询失败的可能原因
     */
    private fun analyzeInitialAbbreviation(input: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    logMessage("分析首字母缩写 '$input'...")
                    
                    // 检查索引字段状态
                    val indexStatus = repository.checkInitialLettersIndex(input)
                    logMessage("索引状态: $indexStatus")
                    
                    // 手动尝试匹配可能的词汇
                    val potentialMatches = withContext(Dispatchers.IO) {
                        repository.searchByInitialLetters(input, 5)
                    }
                    
                    if (potentialMatches.isNotEmpty()) {
                        logMessage("找到潜在匹配词: ${potentialMatches.joinToString { it.word }}")
                    } else {
                        logMessage("未找到潜在匹配词，可能需要扩充词库或修复索引")
                    }
                    
                } catch (e: Exception) {
                    logMessage("分析首字母缩写失败: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 检查首字母索引字段
     */
    private fun checkInitialLettersIndex(input: String) {
        lifecycleScope.launch {
            try {
                logMessage("检查首字母缩写索引: $input")
                binding.progressBar.visibility = View.VISIBLE
                
                val indexInfo = withContext(Dispatchers.IO) {
                    repository.checkInitialLettersIndex(input)
                }
                
                logMessage("索引检查结果:")
                indexInfo.split("\n").forEach { line ->
                    logMessage("  $line")
                }
                
                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                Timber.e(e, "检查索引失败: ${e.message}")
                logMessage("检查索引出错: ${e.message}")
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    /**
     * 记录日志消息
     */
    private fun logMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        val logEntry = "[$timestamp] $message\n"
        
        Timber.d(message)
        logBuffer.append(logEntry)
        
        runOnUiThread {
            binding.tvLogs.append(logEntry)
            
            // 滚动到底部
            val scrollAmount = binding.tvLogs.layout?.getLineTop(binding.tvLogs.lineCount) ?: 0
            if (scrollAmount > binding.tvLogs.height) {
                binding.tvLogs.scrollTo(0, scrollAmount - binding.tvLogs.height)
            }
        }
    }
    
    /**
     * 清除日志
     */
    private fun clearLog() {
        logBuffer.clear()
        binding.tvLogs.text = ""
        Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 导出日志
     */
    private fun exportLog() {
        try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("Debug Logs", binding.tvLogs.text)
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.e(e, "导出日志失败: ${e.message}")
            Toast.makeText(this, "导出日志失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    companion object {
        fun start(context: Context) {
            val intent = Intent(context, MobileDebugActivity::class.java)
            context.startActivity(intent)
        }
    }
} 