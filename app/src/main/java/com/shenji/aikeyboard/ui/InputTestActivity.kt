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
                
                // 规范化拼音（使用DictionaryManager的规范化方法，如果它是私有的，我们需要自己实现）
                val normalizedInput = normalizePinyin(input)
                logMessage("规范化后的拼音: '$normalizedInput'")
                
                // 记录开始时间
                val startTime = System.currentTimeMillis()
                
                // 从Realm词库中查找，使用规范化后的拼音
                val realmResults = repository.searchEntries(normalizedInput, 5, emptyList())
                
                // 计算查询耗时
                val searchTime = System.currentTimeMillis() - startTime
                
                if (realmResults.isNotEmpty()) {
                    // 获取结果所属的词典类型
                    val dictTypes = getDictionaryTypesForEntries(realmResults)
                    
                    // 创建Realm结果的简洁表示
                    val realmResultsText = realmResults.joinToString(", ") { 
                        "${it.word}(${it.frequency})" 
                    }
                    
                    logMessage("从Realm数据库中查询到${realmResults.size}个候选词，耗时: ${searchTime}ms：分别是 $realmResultsText")
                } else {
                    logMessage("Realm词库中未找到匹配'$normalizedInput'的候选词，耗时: ${searchTime}ms")
                }
                
                // 使用DictionaryManager的searchWords方法
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
     * 通过word字段查询词条
     */
    private fun searchByWord(word: String, limit: Int): List<WordFrequency> {
        return try {
            // 这里假设repository有一个通过word字段查询的方法
            // 如果没有，您可能需要添加实现
            repository.searchByWord(word, limit)
        } catch (e: Exception) {
            Timber.e(e, "通过词查询失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 判断是否可能是中文字符
     */
    private fun isPossibleChineseChar(text: String): Boolean {
        val regex = Regex("[\u4e00-\u9fa5]")
        return regex.containsMatchIn(text)
    }
    
    /**
     * 规范化拼音
     * 将连续拼音转换为带空格带声调的分词拼音（例如：beijing -> běi jīng）
     */
    private fun normalizePinyin(pinyin: String): String {
        return try {
            // 使用repository的规范化方法，包含分词和声调恢复
            val normalizedPinyin = repository.normalizeWithTones(pinyin)
            logMessage("拼音转换: '$pinyin' -> '$normalizedPinyin'")
            normalizedPinyin
        } catch (e: Exception) {
            Timber.e(e, "规范化拼音失败: ${e.message}")
            pinyin // 出错时返回原始拼音
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
                    return@withContext isInitialized
                } catch (e: Exception) {
                    logMessage("检查Realm状态出错: ${e.message}")
                    e.printStackTrace()
                    return@withContext false
                }
            }
            
            // 更新UI显示
            withContext(Dispatchers.Main) {
                binding.tvRealmStatus.text = "Realm词典状态: " + if (isRealmConnected) "已连接" else "未连接"
            }
        }
    }
    
    private fun setupLoggers() {
        // 设置展示日志的文本视图
        binding.tvLogs.setOnClickListener {
            // 允许日志滚动查看
            binding.tvLogs.isVerticalScrollBarEnabled = true
        }
    }
    
    /**
     * 记录日志消息
     */
    private fun logMessage(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(System.currentTimeMillis())
        val logEntry = "[$timestamp] $message\n"
        
        Timber.d(message)
        
        // 添加到日志构建器
        imeLogBuilder.append(logEntry)
        
        // 在UI线程更新日志显示
        runOnUiThread {
            binding.tvLogs.append(logEntry)
            
            // 滚动到底部
            val scrollAmount = binding.tvLogs.layout.getLineTop(binding.tvLogs.lineCount) - binding.tvLogs.height
            if (scrollAmount > 0) {
                binding.tvLogs.scrollTo(0, scrollAmount)
            } else {
                binding.tvLogs.scrollTo(0, 0)
            }
        }
    }
    
    /**
     * 显示键盘
     */
    private fun showKeyboard(view: View) {
        view.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
    
    /**
     * 复制日志到剪贴板
     */
    private fun copyLogsToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("输入法日志", imeLogBuilder.toString())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 清除日志
     */
    private fun clearLogs() {
        imeLogBuilder.clear()
        binding.tvLogs.text = ""
        Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
    }
} 