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
     * 将连续拼音转换为分词拼音（例如：beijing -> bei jing）
     */
    private fun normalizePinyin(pinyin: String): String {
        // 首先进行基本规范化：转小写并移除多余空格
        val normalized = pinyin.lowercase().trim().replace("\\s+".toRegex(), "")
        
        // 如果已经有空格，说明已经是分词拼音，直接返回
        if (normalized.contains(" ")) return normalized
        
        // 尝试将连续拼音转换为分词拼音
        return try {
            // 中文拼音音节列表（按长度排序，优先匹配较长的）
            val pinyinSyllables = listOf(
                // 常见四字母音节
                "bing", "ping", "ding", "ting", "ning", "ling", "jing", "qing", "xing", "ying", "zeng", "ceng", "seng", 
                "zheng", "cheng", "sheng", "zhing", "ching", "shing", "zhuang", "chuang", "shuang", "zhang", "chang", "shang",
                "zhong", "chong", "zhen", "chen", "shen", "zhan", "chan", "shan", "zhou", "chou", "shou", "zhua", "chua", "shua",
                // 常见三字母音节
                "zhi", "chi", "shi", "ang", "eng", "ing", "ong", "bai", "dai", "tai", "nai", "lai", "gai", "kai", "hai", "zai", "cai", "sai",
                "ban", "dan", "tan", "nan", "lan", "gan", "kan", "han", "zan", "can", "san", "bao", "dao", "tao", "nao", "lao", "gao", "kao", "hao", "zao", "cao", "sao",
                "bie", "die", "tie", "nie", "lie", "jie", "qie", "xie", "yan", "jin", "qin", "xin", "bin", "pin", "min", "nin", "lin", "jiu", "qiu", "xiu",
                "bei", "pei", "mei", "fei", "dei", "tei", "nei", "lei", "gei", "kei", "hei", "zei", "cei", "sei",
                "ben", "pen", "men", "fen", "den", "ten", "nen", "len", "gen", "ken", "hen", "zen", "cen", "sen",
                "zhu", "chu", "shu", "zhe", "che", "she", "zou", "cou", "sou", "zui", "cui", "sui", "zun", "cun", "sun",
                "zhuo", "chuo", "shuo", "zhan", "chan", "shan", "zhen", "chen", "shen",
                // 常见双字母音节
                "ba", "pa", "ma", "fa", "da", "ta", "na", "la", "ga", "ka", "ha", "za", "ca", "sa", 
                "bo", "po", "mo", "fo", "lo", "wo", "yo", "zo", "co", "so",
                "bi", "pi", "mi", "di", "ti", "ni", "li", "ji", "qi", "xi", "yi",
                "bu", "pu", "mu", "fu", "du", "tu", "nu", "lu", "gu", "ku", "hu", "zu", "cu", "su", "wu", "yu",
                "ai", "ei", "ui", "ao", "ou", "iu", "ie", "ve", "er", "an", "en", "in", "un", "vn", "ue",
                "wu", "yu", "ju", "qu", "xu", "zi", "ci", "si", "ge", "he", "ne", "le", "me", "de", "te", 
                "re", "ze", "ce", "se", "ye", "zh", "ch", "sh",
                // 单字母音节
                "a", "o", "e", "i", "u", "v"
            )
            
            // 贪婪匹配：从输入的开始位置尝试匹配最长的拼音音节
            var result = ""
            var position = 0
            
            while (position < normalized.length) {
                var matched = false
                
                // 尝试匹配最长的音节
                for (syllable in pinyinSyllables) {
                    if (position + syllable.length <= normalized.length &&
                        normalized.substring(position, position + syllable.length) == syllable) {
                        // 匹配到音节，添加到结果中
                        result += if (result.isEmpty()) syllable else " $syllable"
                        position += syllable.length
                        matched = true
                        break
                    }
                }
                
                // 如果没有匹配到任何音节，继续尝试下一个字符
                if (!matched) {
                    // 对于beijing这种特殊情况，尝试常见的拼音组合
                    if (position + 3 <= normalized.length && normalized.substring(position, position + 3) == "bei") {
                        result += if (result.isEmpty()) "bei" else " bei"
                        position += 3
                    } else if (position + 4 <= normalized.length && normalized.substring(position, position + 4) == "jing") {
                        result += if (result.isEmpty()) "jing" else " jing"
                        position += 4
                    } else if (position + 5 <= normalized.length && normalized.substring(position, position + 5) == "shang") {
                        result += if (result.isEmpty()) "shang" else " shang"
                        position += 5
                    } else if (position + 3 <= normalized.length && normalized.substring(position, position + 3) == "hai") {
                        result += if (result.isEmpty()) "hai" else " hai"
                        position += 3
                    } else {
                        // 如果还是没匹配到，则只好按单字符处理
                        val char = normalized[position]
                        result += if (result.isEmpty()) char.toString() else " $char"
                        position++
                    }
                }
            }
            
            logMessage("拼音转换: '$normalized' -> '$result'")
            result
        } catch (e: Exception) {
            Timber.e(e, "规范化拼音失败: ${e.message}")
            normalized // 出错时返回原始结果
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