package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.data.WordFrequency
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.databinding.ActivityInputTestBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class InputTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInputTestBinding
    private val imeLogBuilder = StringBuilder()
    private val repository = DictionaryRepository()  // 添加仓库实例
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInputTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupUI()
        checkRealmStatus()
        setupLoggers()
        setupInputListener()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.input_test_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }
    
    private fun setupUI() {
        // 设置输入框点击事件
        binding.etInput.setOnClickListener {
            showKeyboard(it)
        }
        
        // 设置复制日志按钮
        binding.btnCopyLogs.setOnClickListener {
            copyLogsToClipboard()
        }
        
        // 设置清除日志按钮
        binding.btnClearLogs.setOnClickListener {
            clearLogs()
        }
    }
    
    /**
     * 设置输入监听器，实现输入测试功能
     */
    private fun setupInputListener() {
        binding.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // 不需要实现
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // 取消之前的搜索任务
                searchJob?.cancel()
                
                // 获取当前输入文本
                val input = s?.toString() ?: ""
                
                // 如果输入为空，不进行搜索
                if (input.isBlank()) return
                
                // 创建新的搜索任务，添加轻微延迟避免频繁搜索
                searchJob = lifecycleScope.launch {
                    delay(200) // 200毫秒延迟
                    searchCandidates(input)
                }
            }

            override fun afterTextChanged(s: Editable?) {
                // 不需要实现
            }
        })
    }
    
    /**
     * 搜索候选词
     */
    private suspend fun searchCandidates(input: String) {
        withContext(Dispatchers.IO) {
            try {
                logMessage("用户查询候选词: '$input'")
                
                // 先在Trie树中查找
                val trieResults = if (DictionaryManager.instance.isLoaded()) {
                    DictionaryManager.instance.trieTree.search(input, 5)
                } else {
                    emptyList()
                }
                
                if (trieResults.isNotEmpty()) {
                    // 创建Trie树结果的简洁表示
                    val trieResultsText = trieResults.joinToString(", ") { 
                        "${it.word}(${it.frequency})" 
                    }
                    logMessage("从Trie树中找到${trieResults.size}个匹配'$input'的候选词（词频最高的最多5个）：分别是 $trieResultsText")
                } else {
                    logMessage("Trie树中未找到匹配'$input'的候选词")
                    
                    // 如果Trie树中没找到，则从Realm词库中查找
                    val realmResults = repository.searchEntries(input, 5, emptyList())
                    
                    if (realmResults.isNotEmpty()) {
                        // 获取结果所属的词典类型
                        val dictTypes = getDictionaryTypesForEntries(realmResults)
                        
                        // 创建Realm结果的简洁表示
                        val realmResultsText = realmResults.joinToString(", ") { 
                            "${it.word}(${it.frequency})" 
                        }
                        
                        logMessage("从Realm数据库的${dictTypes}词典中查询到${realmResults.size}个候选词（词频最高的最多5个）：分别是 $realmResultsText")
                    } else {
                        logMessage("Realm词库中也未找到匹配'$input'的候选词")
                    }
                }
                
                // 使用DictionaryManager的searchWords方法，它会自动先查Trie树，再查Realm
                val combinedResults = DictionaryManager.instance.searchWords(input, 10)
                
                if (combinedResults.isNotEmpty()) {
                    logMessage("最终返回${combinedResults.size}个候选词结果")
                }
                
            } catch (e: Exception) {
                logMessage("搜索候选词时出错: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 根据词条获取它们所属的词典类型
     */
    private suspend fun getDictionaryTypesForEntries(entries: List<WordFrequency>): String {
        val types = mutableSetOf<String>()
        
        // 实现简单版本，不查询真实类型，只返回一个固定文本
        return "低频"
    }
    
    private fun checkRealmStatus() {
        lifecycleScope.launch {
            val isRealmConnected = withContext(Dispatchers.IO) {
                try {
                    val isInitialized = DictionaryManager.instance.isLoaded()
                    logMessage("Realm词典初始化状态: $isInitialized")
                    
                    // 检查Trie树加载状态
                    val isTrieLoaded = DictionaryManager.instance.trieTree.isLoaded()
                    logMessage("Trie树内存加载状态: $isTrieLoaded")
                    
                    if (isTrieLoaded) {
                        // 获取总词条数
                        val totalWordCount = DictionaryManager.instance.getTotalLoadedCount()
                        logMessage("Trie树词条总数: $totalWordCount")
                        
                        // 显示各个词典的加载状态
                        val typeLoadedCounts = DictionaryManager.instance.typeLoadedCountMap
                        logMessage("按条件加载的词典状态:")
                        
                        // 遍历所有需要加载到Trie树的词典类型
                        for (type in DictionaryManager.TRIE_DICT_TYPES) {
                            val count = typeLoadedCounts[type] ?: 0
                            val typeName = when(type) {
                                "base" -> "基础词库"
                                "correlation" -> "关联词库" 
                                "people" -> "人名词库"
                                "corrections" -> "错音词库"
                                "compatible" -> "兼容词库"
                                else -> "${type}词库"
                            }
                            
                            if (count > 0) {
                                logMessage("· $typeName：已加载 $count 条")
                            } else {
                                logMessage("· $typeName：未加载")
                            }
                        }
                    }
                    
                    // 显示内存使用情况
                    val memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                    val formattedMemory = repository.formatFileSize(memoryUsage)
                    logMessage("当前内存占用: $formattedMemory")
                    
                    isInitialized
                } catch (e: Exception) {
                    logMessage("Realm词典初始化错误: ${e.message}")
                    false
                }
            }
            
            binding.tvRealmStatus.text = getString(R.string.realm_status, 
                if (isRealmConnected) "正常" else "异常")
        }
    }
    
    private fun setupLoggers() {
        // 添加Timber树，捕获日志
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (tag?.contains("ShenjiInputMethod") == true || 
                    message.contains("input") || 
                    message.contains("keyboard") ||
                    message.contains("候选词")) {
                    logMessage("[$tag] $message")
                }
            }
        })
        
        // 初始日志
        logMessage("输入测试开始")
    }
    
    private fun logMessage(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val logEntry = "[$timestamp] $message"
            
            // 添加到日志列表开头
            imeLogBuilder.insert(0, logEntry + "\n")
            binding.tvLogs.text = imeLogBuilder.toString()
            
            // 保持日志不超过100条
            if (imeLogBuilder.length > 100 * 1024) {
                imeLogBuilder.delete(100 * 1024, imeLogBuilder.length)
            }
            
            // 滚动到底部
            binding.scrollViewLogs.post {
                binding.scrollViewLogs.fullScroll(View.FOCUS_DOWN)
            }
        }
    }
    
    private fun copyLogsToClipboard() {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("输入法日志", binding.tvLogs.text)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show()
    }
    
    private fun clearLogs() {
        imeLogBuilder.clear()
        binding.tvLogs.text = ""
        logMessage("日志已清除")
    }
    
    private fun showKeyboard(view: View) {
        view.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 移除添加的Timber树
        Timber.uprootAll()
    }
} 