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
import com.shenji.aikeyboard.databinding.ActivityInputTestBinding
import com.shenji.aikeyboard.utils.PinyinSplitter
import com.shenji.aikeyboard.utils.PinyinSyllableManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.random.Random

class InputTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInputTestBinding
    private val imeLogBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            binding = ActivityInputTestBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            setupToolbar()
            setupUI()
            checkRealmStatus()
            setupLoggers()
            
            // 记录活动创建成功
            logMessage("输入测试界面已创建")
        } catch (e: Exception) {
            // 如果布局加载失败，使用简单视图并报告错误
            Timber.e(e, "创建输入测试界面失败: ${e.message}")
            setContentView(R.layout.activity_main)
            Toast.makeText(this, "初始化界面失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish() // 关闭活动
        }
    }
    
    private fun setupToolbar() {
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.apply {
                title = getString(R.string.input_test_title)
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
            }
        } catch (e: Exception) {
            Timber.e(e, "设置工具栏失败: ${e.message}")
        }
    }
    
    private fun setupUI() {
        try {
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
            
            // 设置套件测试按钮
            binding.btnTestSuite.setOnClickListener {
                runPinyinTestSuite()
            }
            
            // 初始化日志区域
            binding.tvLogs.text = ""
            logMessage("UI初始化完成")
        } catch (e: Exception) {
            Timber.e(e, "设置UI元素失败: ${e.message}")
        }
    }
    
    private fun checkRealmStatus() {
        lifecycleScope.launch {
            try {
                // 初始化状态文本
                binding.tvRealmStatus.text = getString(R.string.realm_status, "检查中...")
                
                val isRealmConnected = withContext(Dispatchers.IO) {
                    try {
                        // 安全获取DictionaryManager实例
                        val dictManager = try {
                            DictionaryManager.instance
                        } catch (e: Exception) {
                            logMessage("DictionaryManager未初始化或获取失败")
                            return@withContext false
                        }
                        
                        val isInitialized = dictManager.isLoaded()
                        logMessage("Realm词典初始化状态: $isInitialized")
                        return@withContext isInitialized
                    } catch (e: Exception) {
                        logMessage("检查Realm状态出错: ${e.message}")
                        Timber.e(e, "检查Realm状态出错")
                        return@withContext false
                    }
                }
                
                // 更新UI显示
                withContext(Dispatchers.Main) {
                    binding.tvRealmStatus.text = getString(R.string.realm_status, if (isRealmConnected) "已连接" else "未连接")
                }
            } catch (e: Exception) {
                Timber.e(e, "检查Realm状态过程中发生异常")
                binding.tvRealmStatus.text = getString(R.string.realm_status, "检查失败")
            }
        }
    }
    
    private fun setupLoggers() {
        try {
            // 设置展示日志的文本视图
            binding.tvLogs.setOnClickListener {
                // 允许日志滚动查看
                binding.tvLogs.isVerticalScrollBarEnabled = true
            }
            logMessage("日志系统初始化完成")
        } catch (e: Exception) {
            Timber.e(e, "设置日志系统失败: ${e.message}")
        }
    }
    
    /**
     * 记录日志消息
     */
    private fun logMessage(message: String) {
        try {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(System.currentTimeMillis())
            val logEntry = "[$timestamp] $message\n"
            
            Timber.d(message)
            
            // 添加到日志构建器
            imeLogBuilder.append(logEntry)
            
            // 在UI线程更新日志显示
            runOnUiThread {
                try {
                    binding.tvLogs.append(logEntry)
                    
                    // 滚动到底部
                    val scrollAmount = binding.tvLogs.layout?.getLineTop(binding.tvLogs.lineCount) ?: 0
                    if (scrollAmount > binding.tvLogs.height) {
                        binding.tvLogs.scrollTo(0, scrollAmount - binding.tvLogs.height)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "更新日志UI失败")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "记录日志失败: ${e.message}")
        }
    }
    
    /**
     * 显示键盘
     */
    private fun showKeyboard(view: View) {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            view.requestFocus()
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            logMessage("请求显示键盘")
        } catch (e: Exception) {
            Timber.e(e, "显示键盘失败: ${e.message}")
            Toast.makeText(this, "无法显示键盘", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 复制日志到剪贴板
     */
    private fun copyLogsToClipboard() {
        try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("IME Logs", binding.tvLogs.text)
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
            logMessage("日志已复制到剪贴板")
        } catch (e: Exception) {
            Timber.e(e, "复制日志到剪贴板失败: ${e.message}")
            Toast.makeText(this, "复制日志失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 清除日志
     */
    private fun clearLogs() {
        try {
            imeLogBuilder.clear()
            binding.tvLogs.text = ""
            Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show()
            logMessage("日志已清除")
        } catch (e: Exception) {
            Timber.e(e, "清除日志失败: ${e.message}")
        }
    }
    
    /**
     * 运行拼音测试套件
     */
    private fun runPinyinTestSuite() {
        lifecycleScope.launch {
            try {
                val testCase = generateRandomPinyinTestCase()
                val pinyin = testCase.pinyin
                
                logMessage(getString(R.string.test_suite_pinyin, pinyin))
                
                val dictManager = DictionaryManager.instance
                // 先完成查询操作
                val candidateWords = withContext(Dispatchers.IO) {
                    dictManager.searchWords(pinyin, 5)
                }
                
                // 查询完成后再记录结果
                val candidates = if (candidateWords.isNotEmpty()) {
                    candidateWords.joinToString("、") { it.word }
                } else {
                    "无候选词"
                }
                
                // 记录候选词结果
                logMessage(getString(R.string.test_suite_candidates, candidates))
                // 记录查询逻辑
                logMessage(getString(R.string.test_suite_logic, generateLogicExplanation(testCase, pinyin)))
                
            } catch (e: Exception) {
                Timber.e(e, "运行拼音测试套件失败: ${e.message}")
                logMessage("测试套件执行错误: ${e.message}")
            }
        }
    }
    
    /**
     * 生成随机拼音测试案例
     */
    private data class PinyinTestCase(
        val pinyin: String,
        val type: TestCaseType
    )
    
    enum class TestCaseType {
        SINGLE_INITIAL,           // 单个声母，如 'a', 'b', 'w'
        INITIAL_ABBREVIATION,     // 声母缩写，如 'bj', 'wx'
        COMPLETE_SYLLABLE,        // 完整音节，如 'wei', 'tai', 'ma'
        SYLLABLE_WITH_EXTRA,      // 完整音节+额外输入，如 'weix', 'taiw'
        MULTI_SYLLABLES           // 多个完整音节，如 'weixin', 'taiwan', 'beijing'
    }
    
    private fun generateRandomPinyinTestCase(): PinyinTestCase {
        val random = Random.Default
        
        // 随机选择测试类型
        val testType = TestCaseType.values()[random.nextInt(TestCaseType.values().size)]
        
        // 根据测试类型生成拼音
        val pinyin = when (testType) {
            TestCaseType.SINGLE_INITIAL -> {
                // 随机选择一个声母
                val initials = listOf("a", "b", "c", "d", "f", "g", "h", "j", "k", "l", "m", 
                                      "n", "p", "q", "r", "s", "t", "w", "x", "y", "z")
                initials[random.nextInt(initials.size)]
            }
            
            TestCaseType.INITIAL_ABBREVIATION -> {
                // 随机生成两个字母的缩写
                val firstInitials = listOf("b", "c", "d", "f", "g", "h", "j", "k", "l", "m", 
                                          "n", "p", "q", "r", "s", "t", "w", "x", "y", "z")
                val secondInitials = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", 
                                           "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v",
                                           "w", "x", "y", "z")
                
                firstInitials[random.nextInt(firstInitials.size)] + 
                secondInitials[random.nextInt(secondInitials.size)]
            }
            
            TestCaseType.COMPLETE_SYLLABLE -> {
                // 随机选择一个完整音节
                val maxSyllableLength = PinyinSyllableManager.MAX_SYLLABLE_LENGTH
                val validLengths = (1..maxSyllableLength).filter { 
                    PinyinSyllableManager.getSyllablesByLength(it).isNotEmpty() 
                }
                val randomLength = validLengths[random.nextInt(validLengths.size)]
                val syllables = PinyinSyllableManager.getSyllablesByLength(randomLength).toList()
                syllables[random.nextInt(syllables.size)]
            }
            
            TestCaseType.SYLLABLE_WITH_EXTRA -> {
                // 随机选择一个完整音节 + 额外字母
                val maxSyllableLength = PinyinSyllableManager.MAX_SYLLABLE_LENGTH
                val validLengths = (1..maxSyllableLength).filter { 
                    PinyinSyllableManager.getSyllablesByLength(it).isNotEmpty() 
                }
                val randomLength = validLengths[random.nextInt(validLengths.size)]
                val syllables = PinyinSyllableManager.getSyllablesByLength(randomLength).toList()
                val syllable = syllables[random.nextInt(syllables.size)]
                val extraLetters = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k",
                                        "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v",
                                        "w", "x", "y", "z")
                
                syllable + extraLetters[random.nextInt(extraLetters.size)]
            }
            
            TestCaseType.MULTI_SYLLABLES -> {
                // 随机选择2-3个完整音节
                val maxSyllableLength = PinyinSyllableManager.MAX_SYLLABLE_LENGTH
                val validLengths = (1..maxSyllableLength).filter { 
                    PinyinSyllableManager.getSyllablesByLength(it).isNotEmpty() 
                }
                val syllableCount = random.nextInt(1, 3) // 1到2个音节
                
                val selectedSyllables = List(syllableCount) { 
                    val randomLength = validLengths[random.nextInt(validLengths.size)]
                    val syllables = PinyinSyllableManager.getSyllablesByLength(randomLength).toList()
                    syllables[random.nextInt(syllables.size)]
                }
                
                selectedSyllables.joinToString("")
            }
        }
        
        return PinyinTestCase(pinyin, testType)
    }
    
    /**
     * 生成详细的候选词生成逻辑说明
     */
    private fun generateLogicExplanation(testCase: PinyinTestCase, pinyin: String): String {
        val explanation = StringBuilder()
        
        // 添加拼音类型分析
        explanation.append("1. 拼音输入类型: ")
        when (testCase.type) {
            TestCaseType.SINGLE_INITIAL -> {
                explanation.append("单个声母或韵母 '${pinyin}'")
                explanation.append("\n   处理方式: 直接使用该字母作为音节首字母查询词库")
            }
            
            TestCaseType.INITIAL_ABBREVIATION -> {
                explanation.append("声母缩写 '${pinyin}'")
                explanation.append("\n   处理方式: 使用拼音首字母索引查询，查找initialLetters字段匹配的词条")
                explanation.append("\n   例如：'${pinyin}' 可能匹配 '${pinyin.first()}x${pinyin.last()}x...' 的词条")
            }
            
            TestCaseType.COMPLETE_SYLLABLE -> {
                explanation.append("完整音节 '${pinyin}'")
                explanation.append("\n   处理方式: 直接使用完整音节查询词库中pinyin字段完全匹配或前缀匹配的词条")
            }
            
            TestCaseType.SYLLABLE_WITH_EXTRA -> {
                // 分析可能的完整音节部分
                val parts = PinyinSplitter.smartSplit(pinyin)
                val validSyllable = parts.firstOrNull { PinyinSyllableManager.isValidSyllable(it) } ?: ""
                val extra = pinyin.substring(validSyllable.length)
                
                explanation.append("音节+额外输入 '${validSyllable}+${extra}'")
                explanation.append("\n   处理方式: 使用音节拆分策略，识别出有效音节'${validSyllable}'和额外字母'${extra}'")
                explanation.append("\n   然后查询包含该音节开头的词条，并根据额外字母进行进一步筛选")
            }
            
            TestCaseType.MULTI_SYLLABLES -> {
                // 尝试拆分为多个音节
                val syllables = PinyinSplitter.split(pinyin)
                val syllablesStr = if (syllables.isNotEmpty()) {
                    syllables.joinToString("+")
                } else {
                    "无法完整分词"
                }
                
                explanation.append("多个完整音节组合 '$syllablesStr'")
                if (syllables.isNotEmpty()) {
                    explanation.append("\n   处理方式: 使用音节组合查询词库")
                    explanation.append("\n   尝试格式1: '${syllables.joinToString(" ")}'（有空格）")
                    explanation.append("\n   尝试格式2: '${syllables.joinToString("")}'（无空格）")
                } else {
                    // 智能拆分结果
                    val smartParts = PinyinSplitter.smartSplit(pinyin)
                    explanation.append("\n   无法完整分词，智能拆分为: ${smartParts.joinToString("+")}")
                    explanation.append("\n   处理方式: 使用部分音节匹配策略")
                }
            }
        }
        
        // 添加具体查询逻辑
        explanation.append("\n\n2. 查询过程:")
        explanation.append("\n   a. 规范化拼音: '${pinyin}' → '${PinyinSplitter.normalize(pinyin)}'")
        
        // 根据不同情况说明查询表和索引
        when (testCase.type) {
            TestCaseType.SINGLE_INITIAL -> {
                explanation.append("\n   b. 查询方式: 使用BEGINSWITH查询Entry表中的pinyin字段")
                explanation.append("\n   c. 查询条件: pinyin BEGINSWITH '${pinyin}'")
                explanation.append("\n   d. 排序方式: 按frequency降序排列")
            }
            
            TestCaseType.INITIAL_ABBREVIATION -> {
                explanation.append("\n   b. 查询方式: 使用initialLetters索引字段")
                explanation.append("\n   c. 查询条件: initialLetters == '${pinyin}'")
                explanation.append("\n   d. 排序方式: 按frequency降序排列")
            }
            
            TestCaseType.COMPLETE_SYLLABLE -> {
                explanation.append("\n   b. 查询方式: 先精确查询，再前缀查询")
                explanation.append("\n   c. 精确查询: pinyin == '${pinyin}'")
                explanation.append("\n   d. 前缀查询: pinyin BEGINSWITH '${pinyin}'")
                explanation.append("\n   e. 合并结果: 优先展示精确匹配的结果")
                explanation.append("\n   f. 排序方式: 按frequency降序排列")
            }
            
            TestCaseType.SYLLABLE_WITH_EXTRA, TestCaseType.MULTI_SYLLABLES -> {
                explanation.append("\n   b. 查询方式: 使用音节拆分策略，多音节组合查询")
                explanation.append("\n   c. 先尝试标准拼音分词: ${PinyinSplitter.split(pinyin).joinToString("+")}")
                explanation.append("\n   d. 再尝试智能拆分: ${PinyinSplitter.smartSplit(pinyin).joinToString("+")}")
                explanation.append("\n   e. 分别查询有空格和无空格版本")
                explanation.append("\n   f. 合并查询结果并去重")
                explanation.append("\n   g. 排序方式: 按frequency降序排列")
            }
        }
        
        // 优先查询的词典类型
        explanation.append("\n\n3. 优先查询的词典类型:")
        if (testCase.type == TestCaseType.SINGLE_INITIAL) {
            explanation.append("\n   - chars (单字词典): 优先展示单字")
        } else {
            explanation.append("\n   - chars (单字词典): 基础单字")
            explanation.append("\n   - base (基础词典): 常用词组")
        }
        
        // 过滤掉的词典类型
        explanation.append("\n   排除的词典类型:")
        explanation.append("\n   - poetry (诗词词典): 避免生僻诗词干扰常用词")
        explanation.append("\n   - associational (联想词典): 只在输入确定字词后才使用联想")
        
        return explanation.toString()
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
    
    override fun onDestroy() {
        logMessage("输入测试界面关闭")
        super.onDestroy()
    }
} 