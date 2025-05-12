package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class InputTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInputTestBinding
    private val imeLogBuilder = StringBuilder()
    private val repository = DictionaryRepository()  // 添加仓库实例

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInputTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupUI()
        checkRealmStatus()
        setupLoggers()
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
    
    private fun checkRealmStatus() {
        lifecycleScope.launch {
            val isRealmConnected = withContext(Dispatchers.IO) {
                try {
                    val isInitialized = DictionaryManager.instance.isLoaded()
                    logMessage("Realm词典初始化状态: $isInitialized")
                    
                    // 检查高频词典Trie内存加载状态
                    val isTrieLoaded = DictionaryManager.instance.trieTree.isLoaded()
                    logMessage("高频词典Trie内存加载状态: $isTrieLoaded")
                    
                    // 如果高频词典已加载，显示前5个词条
                    if (isTrieLoaded) {
                        showHighFrequencyDictionaryEntries()
                    }
                    
                    // 如果词典初始化成功，显示每个词典的前3个词条
                    if (isInitialized) {
                        showTopDictionaryEntries()
                    }
                    
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
    
    /**
     * 显示高频词典的前5个词条（从内存中读取）
     */
    private suspend fun showHighFrequencyDictionaryEntries() {
        withContext(Dispatchers.IO) {
            try {
                // 从高频词典中获取几个常用汉字的词条
                val commonPrefixes = listOf("a", "b", "c", "d", "ni")
                
                logMessage("==== 高频词典内存中的词条示例 ====")
                
                // 对每个前缀搜索词条
                commonPrefixes.forEach { prefix ->
                    val entries = DictionaryManager.instance.trieTree.search(prefix, 1)
                    if (entries.isNotEmpty()) {
                        entries.forEach { entry ->
                            logMessage("前缀[$prefix]: ${entry.word} - 频率:${entry.frequency}")
                        }
                    }
                }
                
                // 显示高频词典词条总数
                val wordCount = DictionaryManager.instance.trieTree.getWordCount()
                logMessage("高频词典总词条数: $wordCount")
                
            } catch (e: Exception) {
                logMessage("获取高频词典数据出错: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 显示每个词典的前3个词条
     */
    private suspend fun showTopDictionaryEntries() {
        withContext(Dispatchers.IO) {
            try {
                // 获取所有词典类型
                val dictTypes = repository.getAllDictionaryTypes()
                logMessage("发现${dictTypes.size}个词典类型: ${dictTypes.joinToString()}")
                
                // 遍历每个词典类型
                for (type in dictTypes) {
                    // 获取每个类型前3个词条
                    val entries = repository.getEntriesByType(type, 0, 3)
                    if (entries.isNotEmpty()) {
                        logMessage("==== 词典[$type]前3个词条 ====")
                        entries.forEach { entry ->
                            // pinyin是String类型
                            logMessage("${entry.word} [${entry.pinyin}] - 频率:${entry.frequency}")
                        }
                    } else {
                        logMessage("==== 词典[$type]无词条数据 ====")
                    }
                }
                
                // 尝试获取内存中的词条
                val memoryEntries = withContext(Dispatchers.Default) {
                    val samplePinyin = "bei" // 尝试获取"北京"
                    DictionaryManager.instance.searchWords(samplePinyin, 3)
                }
                logMessage("==== 内存词典前3个匹配词条(bei) ====")
                if (memoryEntries.isNotEmpty()) {
                    memoryEntries.forEach { entry ->
                        logMessage("${entry.word} - 频率:${entry.frequency}")
                    }
                } else {
                    logMessage("内存词典无匹配词条数据")
                }
            } catch (e: Exception) {
                logMessage("获取词典数据出错: ${e.message}")
                e.printStackTrace()
            }
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
            imeLogBuilder.append("${message}\n")
            binding.tvLogs.text = imeLogBuilder.toString()
            
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