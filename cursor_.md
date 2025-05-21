# 继续未完成的内容开发
_Exported on 2025/5/22 at GMT+8 0:30:22 from Cursor (0.50.5)_

---

**User**

继续开发你没完成的内容

---

**Cursor**

根据上一次的对话内容，我需要继续实现模糊拼音功能。我已经开始创建了布局文件，现在需要继续实现模糊拼音设置界面和核心逻辑。

我将完成以下步骤：

1. 实现模糊拼音设置Activity
2. 创建模糊拼音配置管理类
3. 修改拼音查询引擎，支持模糊拼音匹配
4. 在输入法设置中添加模糊拼音入口

让我先检查一下项目结构，查看现有代码：

检查一下项目中有关设置的相关文件，了解如何添加新的设置页面：

检查是否有偏好设置相关的代码：

检查一下是否有设置相关的Manager类：

让我们创建实现模糊拼音功能所需的几个文件：

1. 首先，我们需要创建一个模糊拼音设置管理类：

```plaintext
package com.shenji.aikeyboard.settings

import android.content.Context
import android.content.SharedPreferences
import com.shenji.aikeyboard.ShenjiApplication
import timber.log.Timber

/**
 * 模糊拼音设置管理类
 * 
 * 管理模糊拼音设置的存储和读取，提供默认配置
 */
class FuzzyPinyinManager private constructor() {
    
    companion object {
        // 单例实例
        private var instance: FuzzyPinyinManager? = null
        
        // 获取单例实例
        @JvmStatic
        fun getInstance(): FuzzyPinyinManager {
            if (instance == null) {
                instance = FuzzyPinyinManager()
            }
            return instance!!
        }
        
        // SharedPreferences文件名和键值常量
        private const val PREFS_NAME = "fuzzy_pinyin_prefs"
        
        // 声母模糊匹配键
        private const val KEY_Z_ZH = "fuzzy_z_zh"   // z = zh
        private const val KEY_C_CH = "fuzzy_c_ch"   // c = ch
        private const val KEY_S_SH = "fuzzy_s_sh"   // s = sh
        
        // 韵母模糊匹配键
        private const val KEY_AN_ANG = "fuzzy_an_ang"  // an = ang
        private const val KEY_EN_ENG = "fuzzy_en_eng"  // en = eng
        private const val KEY_IN_ING = "fuzzy_in_ing"  // in = ing
        
        // 其他模糊匹配键
        private const val KEY_L_N = "fuzzy_l_n"     // l = n
    }
    
    // 模糊拼音配置状态
    private var fuzzyEnabled = false
    
    // 声母模糊匹配
    private var zEqualsZh = false    // z = zh
    private var cEqualsCh = false    // c = ch
    private var sEqualsSh = false    // s = sh
    
    // 韵母模糊匹配
    private var anEqualsAng = false  // an = ang
    private var enEqualsEng = false  // en = eng
    private var inEqualsIng = false  // in = ing
    
    // 其他模糊匹配
    private var lEqualsN = false     // l = n
    
    // SharedPreferences实例
    private val prefs: SharedPreferences by lazy {
        ShenjiApplication.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 初始化，加载保存的设置
     */
    init {
        loadSettings()
    }
    
    /**
     * 加载保存的设置
     */
    private fun loadSettings() {
        try {
            // 声母模糊匹配
            zEqualsZh = prefs.getBoolean(KEY_Z_ZH, false)
            cEqualsCh = prefs.getBoolean(KEY_C_CH, false)
            sEqualsSh = prefs.getBoolean(KEY_S_SH, false)
            
            // 韵母模糊匹配
            anEqualsAng = prefs.getBoolean(KEY_AN_ANG, false)
            enEqualsEng = prefs.getBoolean(KEY_EN_ENG, false)
            inEqualsIng = prefs.getBoolean(KEY_IN_ING, false)
            
            // 其他模糊匹配
            lEqualsN = prefs.getBoolean(KEY_L_N, false)
            
            // 检查是否有任何一个模糊拼音设置被启用
            fuzzyEnabled = zEqualsZh || cEqualsCh || sEqualsSh || 
                          anEqualsAng || enEqualsEng || inEqualsIng || 
                          lEqualsN
                          
            Timber.d("已加载模糊拼音设置")
        } catch (e: Exception) {
            Timber.e(e, "加载模糊拼音设置失败")
        }
    }
    
    /**
     * 判断是否有任何模糊拼音设置启用
     */
    fun isFuzzyEnabled(): Boolean {
        return fuzzyEnabled
    }
    
    /**
     * 应用模糊拼音规则到音节
     * 根据当前启用的规则，返回可能的模糊匹配音节列表
     * 
     * @param syllable 原始音节
     * @return 包含原始音节和所有可能的模糊匹配音节的列表
     */
    fun applyFuzzyRules(syllable: String): List<String> {
        // 如果没有启用任何模糊拼音规则，直接返回原始音节
        if (!fuzzyEnabled) {
            return listOf(syllable)
        }
        
        val result = mutableSetOf(syllable)
        
        // 声母模糊匹配
        if (zEqualsZh) {
            applyInitialFuzzy(result, syllable, "z", "zh")
        }
        
        if (cEqualsCh) {
            applyInitialFuzzy(result, syllable, "c", "ch")
        }
        
        if (sEqualsSh) {
            applyInitialFuzzy(result, syllable, "s", "sh")
        }
        
        // 韵母模糊匹配
        if (anEqualsAng) {
            applyFinalFuzzy(result, syllable, "an", "ang")
        }
        
        if (enEqualsEng) {
            applyFinalFuzzy(result, syllable, "en", "eng")
        }
        
        if (inEqualsIng) {
            applyFinalFuzzy(result, syllable, "in", "ing")
        }
        
        // 其他模糊匹配
        if (lEqualsN) {
            applyInitialFuzzy(result, syllable, "l", "n")
        }
        
        return result.toList()
    }
    
    /**
     * 应用声母模糊规则
     */
    private fun applyInitialFuzzy(result: MutableSet<String>, syllable: String, a: String, b: String) {
        if (syllable.startsWith(a)) {
            result.add(b + syllable.substring(a.length))
        } else if (syllable.startsWith(b)) {
            result.add(a + syllable.substring(b.length))
        }
    }
    
    /**
     * 应用韵母模糊规则
     */
    private fun applyFinalFuzzy(result: MutableSet<String>, syllable: String, a: String, b: String) {
        if (syllable.endsWith(a)) {
            result.add(syllable.substring(0, syllable.length - a.length) + b)
        } else if (syllable.endsWith(b)) {
            result.add(syllable.substring(0, syllable.length - b.length) + a)
        }
    }
    
    // Getters
    fun isZEqualsZh(): Boolean = zEqualsZh
    fun isCEqualsCh(): Boolean = cEqualsCh
    fun isSEqualsSh(): Boolean = sEqualsSh
    fun isAnEqualsAng(): Boolean = anEqualsAng
    fun isEnEqualsEng(): Boolean = enEqualsEng
    fun isInEqualsIng(): Boolean = inEqualsIng
    fun isLEqualsN(): Boolean = lEqualsN
    
    // Setters with automatic saving
    fun setZEqualsZh(enabled: Boolean) {
        zEqualsZh = enabled
        prefs.edit().putBoolean(KEY_Z_ZH, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    fun setCEqualsCh(enabled: Boolean) {
        cEqualsCh = enabled
        prefs.edit().putBoolean(KEY_C_CH, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    fun setSEqualsSh(enabled: Boolean) {
        sEqualsSh = enabled
        prefs.edit().putBoolean(KEY_S_SH, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    fun setAnEqualsAng(enabled: Boolean) {
        anEqualsAng = enabled
        prefs.edit().putBoolean(KEY_AN_ANG, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    fun setEnEqualsEng(enabled: Boolean) {
        enEqualsEng = enabled
        prefs.edit().putBoolean(KEY_EN_ENG, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    fun setInEqualsIng(enabled: Boolean) {
        inEqualsIng = enabled
        prefs.edit().putBoolean(KEY_IN_ING, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    fun setLEqualsN(enabled: Boolean) {
        lEqualsN = enabled
        prefs.edit().putBoolean(KEY_L_N, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    /**
     * 设置全部模糊拼音规则
     */
    fun setAll(enabled: Boolean) {
        zEqualsZh = enabled
        cEqualsCh = enabled
        sEqualsSh = enabled
        anEqualsAng = enabled
        enEqualsEng = enabled
        inEqualsIng = enabled
        lEqualsN = enabled
        
        prefs.edit()
            .putBoolean(KEY_Z_ZH, enabled)
            .putBoolean(KEY_C_CH, enabled)
            .putBoolean(KEY_S_SH, enabled)
            .putBoolean(KEY_AN_ANG, enabled)
            .putBoolean(KEY_EN_ENG, enabled)
            .putBoolean(KEY_IN_ING, enabled)
            .putBoolean(KEY_L_N, enabled)
            .apply()
            
        updateFuzzyEnabledStatus()
    }
    
    /**
     * 更新模糊拼音总开关状态
     */
    private fun updateFuzzyEnabledStatus() {
        fuzzyEnabled = zEqualsZh || cEqualsCh || sEqualsSh || 
                      anEqualsAng || enEqualsEng || inEqualsIng || 
                      lEqualsN
    }
}
```

2. 接下来，我们创建模糊拼音设置Activity：

```plaintext
package com.shenji.aikeyboard.settings

import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.shenji.aikeyboard.R
import timber.log.Timber

/**
 * 模糊拼音设置Activity
 * 用于管理模糊拼音设置
 */
class FuzzyPinyinSettingsActivity : AppCompatActivity() {

    // 模糊拼音管理器
    private val fuzzyPinyinManager = FuzzyPinyinManager.getInstance()
    
    // 全选开关
    private lateinit var switchSelectAll: SwitchMaterial
    
    // 声母模糊匹配开关
    private lateinit var switchZZh: SwitchMaterial
    private lateinit var switchCCh: SwitchMaterial
    private lateinit var switchSSh: SwitchMaterial
    
    // 韵母模糊匹配开关
    private lateinit var switchAnAng: SwitchMaterial
    private lateinit var switchEnEng: SwitchMaterial
    private lateinit var switchInIng: SwitchMaterial
    
    // 其他模糊匹配开关
    private lateinit var switchLN: SwitchMaterial
    
    // 是否正在批量更新（避免开关状态变化时的循环更新）
    private var isBatchUpdating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fuzzy_pinyin_settings)
        
        // 初始化UI
        initUI()
        
        // 加载当前设置
        loadCurrentSettings()
    }
    
    /**
     * 初始化UI元素
     */
    private fun initUI() {
        // 返回按钮
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }
        
        // 全选开关
        switchSelectAll = findViewById(R.id.switch_select_all)
        switchSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                setAllSwitches(isChecked)
                fuzzyPinyinManager.setAll(isChecked)
                showToast(if (isChecked) "已启用全部模糊拼音规则" else "已禁用全部模糊拼音规则")
            }
        }
        
        // 声母模糊匹配开关
        switchZZh = findViewById(R.id.switch_z_zh)
        switchZZh.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setZEqualsZh(isChecked)
                updateSelectAllSwitch()
            }
        }
        
        switchCCh = findViewById(R.id.switch_c_ch)
        switchCCh.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setCEqualsCh(isChecked)
                updateSelectAllSwitch()
            }
        }
        
        switchSSh = findViewById(R.id.switch_s_sh)
        switchSSh.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setSEqualsSh(isChecked)
                updateSelectAllSwitch()
            }
        }
        
        // 韵母模糊匹配开关
        switchAnAng = findViewById(R.id.switch_an_ang)
        switchAnAng.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setAnEqualsAng(isChecked)
                updateSelectAllSwitch()
            }
        }
        
        switchEnEng = findViewById(R.id.switch_en_eng)
        switchEnEng.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setEnEqualsEng(isChecked)
                updateSelectAllSwitch()
            }
        }
        
        switchInIng = findViewById(R.id.switch_in_ing)
        switchInIng.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setInEqualsIng(isChecked)
                updateSelectAllSwitch()
            }
        }
        
        // 其他模糊匹配开关
        switchLN = findViewById(R.id.switch_l_n)
        switchLN.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setLEqualsN(isChecked)
                updateSelectAllSwitch()
            }
        }
    }
    
    /**
     * 加载当前的模糊拼音设置
     */
    private fun loadCurrentSettings() {
        isBatchUpdating = true
        
        try {
            // 声母模糊匹配
            switchZZh.isChecked = fuzzyPinyinManager.isZEqualsZh()
            switchCCh.isChecked = fuzzyPinyinManager.isCEqualsCh()
            switchSSh.isChecked = fuzzyPinyinManager.isSEqualsSh()
            
            // 韵母模糊匹配
            switchAnAng.isChecked = fuzzyPinyinManager.isAnEqualsAng()
            switchEnEng.isChecked = fuzzyPinyinManager.isEnEqualsEng()
            switchInIng.isChecked = fuzzyPinyinManager.isInEqualsIng()
            
            // 其他模糊匹配
            switchLN.isChecked = fuzzyPinyinManager.isLEqualsN()
            
            // 更新全选开关状态
            updateSelectAllSwitch()
        } catch (e: Exception) {
            Timber.e(e, "加载模糊拼音设置失败")
            showToast("加载设置失败")
        } finally {
            isBatchUpdating = false
        }
    }
    
    /**
     * 设置所有开关的状态
     */
    private fun setAllSwitches(checked: Boolean) {
        isBatchUpdating = true
        
        try {
            // 声母模糊匹配
            switchZZh.isChecked = checked
            switchCCh.isChecked = checked
            switchSSh.isChecked = checked
            
            // 韵母模糊匹配
            switchAnAng.isChecked = checked
            switchEnEng.isChecked = checked
            switchInIng.isChecked = checked
            
            // 其他模糊匹配
            switchLN.isChecked = checked
        } finally {
            isBatchUpdating = false
        }
    }
    
    /**
     * 更新全选开关状态
     * 如果所有规则都被选中，则全选开关也应该被选中
     */
    private fun updateSelectAllSwitch() {
        val allChecked = switchZZh.isChecked && switchCCh.isChecked && switchSSh.isChecked &&
                        switchAnAng.isChecked && switchEnEng.isChecked && switchInIng.isChecked &&
                        switchLN.isChecked
        
        // 避免循环触发
        isBatchUpdating = true
        switchSelectAll.isChecked = allChecked
        isBatchUpdating = false
    }
    
    /**
     * 显示Toast消息
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
```

3. 现在，修改拼音分词器，支持模糊拼音匹配：

让我们修改拼音查询引擎，添加模糊拼音匹配：

```plaintext
package com.shenji.aikeyboard.pinyin

import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.settings.FuzzyPinyinManager
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 拼音查询引擎 - 标准化模块的核心类
 * 
 * 负责：
 * 1. 判断输入类型（单字符、拼音音节、拼音分词、首字母缩写等）
 * 2. 分词处理（使用PinyinSplitter）
 * 3. 构建查询条件
 * 4. 执行查询并返回标准化结果
 * 5. 提供详细的解释信息（用于测试工具）
 */
class PinyinQueryEngine {
    
    // 单例模式
    companion object {
        private var instance: PinyinQueryEngine? = null
        
        @JvmStatic
        fun getInstance(): PinyinQueryEngine {
            if (instance == null) {
                instance = PinyinQueryEngine()
            }
            return instance!!
        }
    }
    
    // 拼音分词器
    private val pinyinSplitter = PinyinSplitter.getInstance()
    
    // 模糊拼音管理器
    private val fuzzyPinyinManager = FuzzyPinyinManager.getInstance()
    
    // ... existing code ...

    /**
     * 查询单音节候选词
     */
    private suspend fun queryPinyinSyllable(
        input: String, 
        limit: Int,
        needExplain: Boolean
    ): PinyinQueryResult = withContext(Dispatchers.IO) {
        val explanation = StringBuilder()
        val candidates = mutableListOf<PinyinCandidate>()
        
        // 从输入中提取纯英文拼音部分
        val pinyinPart = extractPinyinPart(input)
        
        // 应用模糊拼音规则，获取可能的音节变体
        val fuzzySyllables = if (fuzzyPinyinManager.isFuzzyEnabled()) {
            fuzzyPinyinManager.applyFuzzyRules(pinyinPart)
        } else {
            listOf(pinyinPart)
        }
        
        if (needExplain) {
            explanation.append("查询过程:\n")
            explanation.append("1. 使用精确音节匹配规则查询单字词典\n")
            explanation.append("- 原始输入: '$input'\n")
            explanation.append("- 提取拼音部分: '$pinyinPart'\n")
            
            if (fuzzySyllables.size > 1) {
                explanation.append("- 应用模糊拼音规则，可能的音节: ${fuzzySyllables.joinToString(", ")}\n")
            }
            
            explanation.append("- 查询条件: type='chars' AND pinyin IN (${fuzzySyllables.joinToString(", ") { "'$it'" }})\n")
        }
        
        try {
            val realm = ShenjiApplication.realm
            
            // 对每个可能的音节变体执行查询
            for (syllable in fuzzySyllables) {
                // 查询单字词典中精确匹配的单字
                val query = realm.query<Entry>("type == $0 AND pinyin == $1", 
                    "chars", syllable)
                
                val entries = query.find()
                    .sortedByDescending { it.frequency }
                
                if (needExplain) {
                    explanation.append("- 音节'$syllable'匹配结果: ${entries.size}个\n")
                }
                
                // 转换为候选词，添加去重逻辑
                val seenWords = mutableSetOf<String>()
                entries.forEach { entry ->
                    // 只添加未见过的词
                    if (seenWords.add(entry.word)) {
                        val matchType = if (syllable == pinyinPart) 
                            MatchType.PINYIN_SYLLABLE 
                        else 
                            MatchType.FUZZY_SYLLABLE
                            
                        candidates.add(
                            PinyinCandidate(
                                word = entry.word,
                                pinyin = entry.pinyin,
                                frequency = entry.frequency,
                                type = entry.type,
                                matchType = matchType
                            )
                        )
                    }
                }
            }
            
            // 如果是开启了模糊拼音但没有精确匹配结果，查询词组
            if (candidates.isEmpty() && fuzzySyllables.size > 1) {
                if (needExplain) {
                    explanation.append("2. 单字无匹配，尝试查询以模糊音节开头的词组\n")
                }
                
                for (syllable in fuzzySyllables) {
                    // 查询以该音节开头的词组
                    val query = realm.query<Entry>("type != $0 AND pinyin BEGINSWITH $1", 
                        "chars", syllable)
                    
                    val entries = query.find()
                        .sortedByDescending { it.frequency }
                        .take(limit / fuzzySyllables.size)
                    
                    if (needExplain) {
                        explanation.append("- 以'$syllable'开头的词组: ${entries.size}个\n")
                    }
                    
                    // 转换为候选词
                    val seenWords = mutableSetOf<String>()
                    entries.forEach { entry ->
                        if (seenWords.add(entry.word)) {
                            val matchType = if (syllable == pinyinPart) 
                                MatchType.PINYIN_SYLLABLE 
                            else 
                                MatchType.FUZZY_SYLLABLE
                                
                            candidates.add(
                                PinyinCandidate(
                                    word = entry.word,
                                    pinyin = entry.pinyin,
                                    frequency = entry.frequency,
                                    type = entry.type,
                                    matchType = matchType
                                )
                            )
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "查询单音节候选词异常")
            if (needExplain) {
                explanation.append("查询异常: ${e.message}\n")
            }
        }
        
        // 返回结果对象，应用limit限制
        PinyinQueryResult(
            inputType = InputType.PINYIN_SYLLABLE,
            candidates = candidates.take(limit),
            syllables = fuzzySyllables,
            explanation = explanation.toString()
        )
    }
    
    /**
     * 查询音节拆分候选词
     */
    private suspend fun querySyllableSplit(
        input: String, 
        limit: Int,
        needExplain: Boolean
    ): PinyinQueryResult = withContext(Dispatchers.IO) {
        val explanation = StringBuilder()
        val candidates = mutableListOf<PinyinCandidate>()
        
        // 从输入中提取纯英文拼音部分
        val pinyinPart = extractPinyinPart(input)
        
        // 获取所有可能的拆分结果
        val allSplitResults = pinyinSplitter.getMultipleSplitResults(pinyinPart)
        
        if (allSplitResults.isEmpty()) {
            if (needExplain) {
                explanation.append("音节拆分失败，无法获得有效音节\n")
                explanation.append("- 原始输入: '$input'\n")
                explanation.append("- 提取拼音部分: '$pinyinPart'\n")
            }
            return@withContext PinyinQueryResult(
                inputType = InputType.SYLLABLE_SPLIT,
                candidates = emptyList(),
                syllables = emptyList(),
                allSyllableSplits = emptyList(),
                explanation = explanation.toString()
            )
        }
        
        if (needExplain) {
            explanation.append("查询过程:\n")
            explanation.append("1. 获取到${allSplitResults.size}种可能的音节拆分结果:\n")
            allSplitResults.forEachIndexed { index, syllables ->
                explanation.append("   - 拆分方案${index + 1}: ${syllables.joinToString("+")}\n")
            }
            explanation.append("- 原始输入: '$input'\n")
            explanation.append("- 提取拼音部分: '$pinyinPart'\n")
        }
        
        // 检查是否开启了模糊拼音
        val useFuzzy = fuzzyPinyinManager.isFuzzyEnabled()
        
        if (useFuzzy && needExplain) {
            explanation.append("- 模糊拼音已开启，将对每个音节应用模糊规则\n")
        }
        
        // 尝试每一种拆分方案
        var successIndex = -1
        var currentCandidates = mutableListOf<PinyinCandidate>()
        
        for ((index, syllables) in allSplitResults.withIndex()) {
            // 为每个音节应用模糊拼音规则
            val syllablesWithFuzzy = if (useFuzzy) {
                // 对每个音节应用模糊规则，生成所有可能的组合
                applyFuzzyToSyllables(syllables)
            } else {
                // 不使用模糊拼音，只有一种可能的组合
                listOf(syllables)
            }
            
            if (needExplain) {
                explanation.append("2. 尝试拆分方案${index + 1}: ${syllables.joinToString("+")}\n")
                
                if (useFuzzy && syllablesWithFuzzy.size > 1) {
                    explanation.append("   - 应用模糊拼音规则，生成${syllablesWithFuzzy.size}种变体\n")
                    syllablesWithFuzzy.forEachIndexed { fuzzyIndex, fuzzySyllables ->
                        if (fuzzyIndex > 0) { // 跳过原始音节组合
                            explanation.append("     * 变体${fuzzyIndex}: ${fuzzySyllables.joinToString("+")}\n")
                        }
                    }
                }
            }
            
            var foundCandidates = false
            
            // 尝试每种可能的音节组合
            for (fuzzySyllables in syllablesWithFuzzy) {
                // 将音节连接为完整的拼音字符串（带空格）
                val fullPinyin = fuzzySyllables.joinToString(" ")
                
                if (needExplain && (syllablesWithFuzzy.size > 1 || syllablesWithFuzzy.first() != syllables)) {
                    explanation.append("   - 尝试音节组合: ${fuzzySyllables.joinToString("+")}\n")
                    explanation.append("   - 构建完整拼音查询: '$fullPinyin'\n")
                    explanation.append("   - 查询条件: pinyin == '$fullPinyin'\n")
                }
                
                try {
                    val realm = ShenjiApplication.realm
                    
                    // 查询精确匹配的词条
                    val query = realm.query<Entry>("pinyin == $0", fullPinyin)
                    
                    var entries = query.find()
                        .sortedByDescending { it.frequency }
                    
                    if (needExplain) {
                        explanation.append("   - 精确匹配结果: ${entries.size}个\n")
                    }
                    
                    // 如果精确匹配没有结果，尝试前缀匹配
                    if (entries.isEmpty() && fuzzySyllables.size >= 2) {
                        if (needExplain) {
                            explanation.append("   - 精确匹配无结果，尝试前缀匹配\n")
                            explanation.append("   - 查询条件: pinyin BEGINSWITH '$fullPinyin'\n")
                        }
                        
                        val prefixQuery = realm.query<Entry>("pinyin BEGINSWITH $0", fullPinyin)
                        entries = prefixQuery.find()
                            .sortedByDescending { it.frequency }
                            .take(limit)
                        
                        if (needExplain) {
                            explanation.append("   - 前缀匹配结果: ${entries.size}个\n")
                        }
                    } else {
                        entries = entries.take(limit)
                    }
                    
                    // 如果找到了候选词，则使用这个音节组合
                    if (entries.isNotEmpty()) {
                        foundCandidates = true
                        
                        // 确定是否是模糊匹配
                        val isFuzzyMatch = fuzzySyllables != syllables
                        
                        // 转换为候选词，添加去重逻辑
                        val seenWords = mutableSetOf<String>()
                        entries.forEach { entry ->
                            // 只添加未见过的词
                            if (seenWords.add(entry.word)) {
                                currentCandidates.add(
                                    PinyinCandidate(
                                        word = entry.word,
                                        pinyin = entry.pinyin,
                                        frequency = entry.frequency,
                                        type = entry.type,
                                        matchType = if (isFuzzyMatch) MatchType.FUZZY_SYLLABLE else MatchType.SYLLABLE_SPLIT
                                    )
                                )
                            }
                        }
                        
                        if (needExplain) {
                            explanation.append("   - 成功找到候选词，使用${if (isFuzzyMatch) "模糊" else "精确"}音节组合\n")
                        }
                        
                        // 找到候选词后，不再尝试此拆分方案的其他模糊变体
                        break
                    } else if (needExplain) {
                        explanation.append("   - 未找到候选词，尝试下一个变体\n")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "查询音节拆分候选词异常")
                    if (needExplain) {
                        explanation.append("   - 查询异常: ${e.message}\n")
                    }
                }
            }
            
            // 如果当前拆分方案找到了候选词
            if (foundCandidates) {
                successIndex = index
                // 找到候选词后，不再尝试其他拆分方案
                break
            } else if (needExplain) {
                explanation.append("   - 此拆分方案未找到候选词，尝试下一个拆分方案\n")
            }
        }
        
        // 如果所有拆分方案都没有找到候选词，使用第一个拆分方案作为结果
        val usedIndex = if (successIndex >= 0) successIndex else 0
        val usedSyllables = if (allSplitResults.isNotEmpty()) allSplitResults[usedIndex] else emptyList()
        
        if (successIndex < 0 && needExplain) {
            explanation.append("3. 所有拆分方案都未找到候选词，使用默认拆分方案\n")
        }
        
        // 返回结果对象
        PinyinQueryResult(
            inputType = InputType.SYLLABLE_SPLIT,
            candidates = currentCandidates,
            syllables = usedSyllables,
            allSyllableSplits = allSplitResults,
            usedSplitIndex = usedIndex,
            explanation = explanation.toString()
        )
    }
    
    /**
     * 对一组音节应用模糊拼音规则，生成所有可能的组合
     * 
     * @param syllables 原始音节列表
     * @return 包含原始音节列表和所有可能的模糊变体的列表
     */
    private fun applyFuzzyToSyllables(syllables: List<String>): List<List<String>> {
        // 如果没有开启模糊拼音，或者音节列表为空，直接返回原始列表
        if (!fuzzyPinyinManager.isFuzzyEnabled() || syllables.isEmpty()) {
            return listOf(syllables)
        }
        
        val result = mutableListOf<List<String>>()
        
        // 添加原始音节列表作为第一个结果
        result.add(syllables)
        
        // 为每个音节获取其可能的模糊变体
        val syllableVariants = syllables.map { syllable ->
            fuzzyPinyinManager.applyFuzzyRules(syllable)
        }
        
        // 检查是否有任何一个音节有模糊变体
        val hasFuzzyVariants = syllableVariants.any { it.size > 1 }
        
        // 如果没有模糊变体，直接返回原始列表
        if (!hasFuzzyVariants) {
            return result
        }
        
        // 为了限制组合爆炸，只生成每个位置最多一个变体的组合
        // 首先，找出所有有模糊变体的位置
        val positionsWithVariants = syllableVariants.mapIndexed { index, variants ->
            if (variants.size > 1) index else -1
        }.filter { it >= 0 }
        
        // 对于每个有变体的位置，生成一个替换了该位置的变体列表
        for (position in positionsWithVariants) {
            // 对于该位置的每个变体（除了原始音节）
            for (variant in syllableVariants[position].drop(1)) {
                // 创建一个新的音节列表，替换该位置的音节
                val newSyllables = syllables.toMutableList()
                newSyllables[position] = variant
                
                // 将新的音节列表添加到结果中
                result.add(newSyllables)
            }
        }
        
        return result
    }
    
    // ... existing code ...
}
```

4. 添加匹配类型，以便区分模糊匹配：

```plaintext
package com.shenji.aikeyboard.pinyin

/**
 * 拼音匹配类型枚举
 */
enum class MatchType {
    /**
     * 未知类型
     */
    UNKNOWN,
    
    /**
     * 单字符首字母匹配
     * 例如：输入z，匹配"找"（zhao）的首字母z
     */
    INITIAL_LETTER,
    
    /**
     * 拼音音节匹配
     * 例如：输入zao，匹配拼音为"zao"的字词
     */
    PINYIN_SYLLABLE,
    
    /**
     * 拼音音节拆分匹配
     * 例如：输入nihao，拆分为ni+hao并匹配
     */
    SYLLABLE_SPLIT,
    
    /**
     * 首字母缩写匹配
     * 例如：输入bjrm，匹配"北京人民"(beijing renmin)
     */
    ACRONYM,
    
    /**
     * 模糊拼音匹配
     * 例如：输入zan但匹配zang（模糊规则an=ang）
     */
    FUZZY_SYLLABLE
}
```

5. 在InputMethodSettingsActivity中添加模糊拼音设置入口：

```plaintext
package com.shenji.aikeyboard.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.R
import timber.log.Timber

class InputMethodSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_method_settings)
        
        // 设置标题
        title = getString(R.string.ime_settings)
        
        // 初始化UI元素
        initUI()
    }
    
    private fun initUI() {
        // 启用输入法按钮
        findViewById<Button>(R.id.btn_enable_ime)?.setOnClickListener {
            openInputMethodSettings()
        }
        
        // 设为默认输入法按钮
        findViewById<Button>(R.id.btn_set_default_ime)?.setOnClickListener {
            openInputMethodPicker()
        }
        
        // 模糊拼音设置按钮
        findViewById<Button>(R.id.btn_fuzzy_pinyin)?.setOnClickListener {
            openFuzzyPinyinSettings()
        }
    }
    
    // 打开输入法设置界面
    private fun openInputMethodSettings() {
        try {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            Toast.makeText(this, "请在列表中启用「神迹输入法」", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Timber.e(e, "打开输入法设置失败")
            Toast.makeText(this, "打开输入法设置失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 打开输入法选择器
    private fun openInputMethodPicker() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        } catch (e: Exception) {
            Timber.e(e, "打开输入法选择器失败")
            Toast.makeText(this, "打开输入法选择器失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 打开模糊拼音设置
    private fun openFuzzyPinyinSettings() {
        try {
            val intent = Intent(this, FuzzyPinyinSettingsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "打开模糊拼音设置失败: ${e.message}")
            Toast.makeText(this, "打开模糊拼音设置失败", Toast.LENGTH_SHORT).show()
        }
    }
}
```

6. 修改输入法设置布局文件，添加模糊拼音设置按钮：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="神迹输入法设置"
        android:textSize="24sp"
        android:textStyle="bold"
        android:layout_marginBottom="24dp"/>

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="使用神迹输入法需要完成以下两个步骤："
        android:textSize="16sp"
        android:layout_marginBottom="16dp"/>
        
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginBottom="16dp">
        
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="1. "
            android:textSize="16sp"
            android:textStyle="bold"/>
            
        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="在系统设置中启用神迹输入法"
            android:textSize="16sp"/>
            
        <Button
            android:id="@+id/btn_enable_ime"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="启用输入法"/>
    </LinearLayout>
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginBottom="24dp">
        
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="2. "
            android:textSize="16sp"
            android:textStyle="bold"/>
            
        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="将神迹输入法设为默认输入法"
            android:textSize="16sp"/>
            
        <Button
            android:id="@+id/btn_set_default_ime"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="设为默认"/>
    </LinearLayout>
    
    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="#CCCCCC"
        android:layout_marginBottom="24dp"/>
        
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="高级设置"
        android:textSize="18sp"
        android:textStyle="bold"
        android:layout_marginBottom="16dp"/>
        
    <!-- 模糊拼音设置 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginBottom="16dp">
        
        <ImageView
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:src="@android:drawable/ic_menu_edit"
            android:contentDescription="模糊拼音"
            android:layout_marginEnd="16dp"/>
            
        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="模糊拼音设置"
            android:textSize="16sp"/>
            
        <Button
            android:id="@+id/btn_fuzzy_pinyin"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="设置"/>
    </LinearLayout>
    
    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="#CCCCCC"
        android:layout_marginBottom="24dp"/>
        
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="功能说明"
        android:textSize="18sp"
        android:textStyle="bold"
        android:layout_marginBottom="16dp"/>
        
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="1. 标准QWERTY键盘布局\n2. 拼音输入支持\n3. 候选词条智能推荐\n4. 点击键盘才显示候选词区域\n5. 模糊拼音支持(z=zh, c=ch等)"
        android:textSize="14sp"
        android:lineSpacingExtra="8dp"/>

</LinearLayout>
```

7. 最后，注册模糊拼音设置Activity到AndroidManifest.xml：

```xml
<!-- 输入法设置活动 -->
        <activity
            android:name=".settings.InputMethodSettingsActivity"
            android:label="@string/ime_settings"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
            </intent-filter>
        </activity>
        
        <!-- 模糊拼音设置 -->
        <activity
            android:name=".settings.FuzzyPinyinSettingsActivity"
            android:label="模糊拼音设置"
            android:exported="false" />
```

现在，我们已经成功实现了模糊拼音功能。让我们编译并安装到手机：

我们需要修复activity_fuzzy_pinyin_settings.xml文件中的XML语法错误：

看起来XML文件并没有明显的错误，但是错误信息提示在284行有一个TextView标记有问题。让我们重新生成这个文件，确保所有XML标签都正确关闭：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#F5F5F5">

    <!-- 顶部标题栏 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:background="#FFFFFF"
        android:padding="16dp"
        android:elevation="4dp">

        <ImageButton
            android:id="@+id/btn_back"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_revert"
            android:contentDescription="返回" />

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="16dp"
            android:text="模糊拼音"
            android:textColor="#333333"
            android:textSize="18sp"
            android:textStyle="bold" />
    </LinearLayout>

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <!-- 全选/清空 操作区 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp"
                android:layout_marginTop="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="全选"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_select_all"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <!-- 分隔线 -->
            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="#E0E0E0" />

            <!-- 声母模糊匹配部分 -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="#EEEEEE"
                android:padding="12dp"
                android:text="声母模糊匹配"
                android:textColor="#666666"
                android:textSize="14sp" />

            <!-- z = zh -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="z = zh"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_z_zh"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="#E0E0E0" />

            <!-- c = ch -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="c = ch"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_c_ch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="#E0E0E0" />

            <!-- s = sh -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="s = sh"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_s_sh"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <!-- 韵母模糊匹配部分 -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="#EEEEEE"
                android:padding="12dp"
                android:text="韵母模糊匹配"
                android:textColor="#666666"
                android:textSize="14sp" />

            <!-- an = ang -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="an = ang"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_an_ang"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="#E0E0E0" />

            <!-- en = eng -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="en = eng"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_en_eng"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="#E0E0E0" />

            <!-- in = ing -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="in = ing"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_in_ing"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <!-- 其他模糊匹配部分 -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="#EEEEEE"
                android:padding="12dp"
                android:text="其他模糊匹配"
                android:textColor="#666666"
                android:textSize="14sp" />

            <!-- l = n -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="l = n"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_l_n"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <!-- 底部说明 -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:padding="16dp"
                android:text="开启模糊拼音功能后，在输入拼音时可以使用上述简化或混淆形式，输入法会自动进行匹配。例如，开启"z = zh"后，输入'zi'会同时匹配'zi'和'zhi'的字词。"
                android:textColor="#666666"
                android:textSize="14sp" />

        </LinearLayout>
    </ScrollView>
</LinearLayout>
```

看起来文件内容相同，但可能有些隐藏字符导致了XML解析错误。让我们完全删除该文件，然后重新创建：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#F5F5F5">

    <!-- 顶部标题栏 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:background="#FFFFFF"
        android:padding="16dp"
        android:elevation="4dp">

        <ImageButton
            android:id="@+id/btn_back"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_revert"
            android:contentDescription="返回" />

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="16dp"
            android:text="模糊拼音"
            android:textColor="#333333"
            android:textSize="18sp"
            android:textStyle="bold" />
    </LinearLayout>

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <!-- 全选/清空 操作区 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp"
                android:layout_marginTop="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="全选"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_select_all"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <!-- 分隔线 -->
            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="#E0E0E0" />

            <!-- 声母模糊匹配部分 -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="#EEEEEE"
                android:padding="12dp"
                android:text="声母模糊匹配"
                android:textColor="#666666"
                android:textSize="14sp" />

            <!-- z = zh -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="z = zh"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_z_zh"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="#E0E0E0" />

            <!-- c = ch -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="c = ch"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_c_ch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="#E0E0E0" />

            <!-- s = sh -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="s = sh"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_s_sh"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <!-- 韵母模糊匹配部分 -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="#EEEEEE"
                android:padding="12dp"
                android:text="韵母模糊匹配"
                android:textColor="#666666"
                android:textSize="14sp" />

            <!-- an = ang -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="an = ang"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_an_ang"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="#E0E0E0" />

            <!-- en = eng -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="en = eng"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_en_eng"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="#E0E0E0" />

            <!-- in = ing -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="in = ing"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_in_ing"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <!-- 其他模糊匹配部分 -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="#EEEEEE"
                android:padding="12dp"
                android:text="其他模糊匹配"
                android:textColor="#666666"
                android:textSize="14sp" />

            <!-- l = n -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:background="#FFFFFF"
                android:padding="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="l = n"
                    android:textColor="#333333"
                    android:textSize="16sp" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switch_l_n"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <!-- 底部说明 -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:padding="16dp"
                android:text="开启模糊拼音功能后，在输入拼音时可以使用上述简化或混淆形式，输入法会自动进行匹配。例如，开启'z = zh'后，输入'zi'会同时匹配'zi'和'zhi'的字词。"
                android:textColor="#666666"
                android:textSize="14sp" />

        </LinearLayout>
    </ScrollView>
</LinearLayout>
```

```plaintext
// ... existing code ...
    /**
     * 应用模糊拼音规则到音节
     * 根据当前启用的规则，返回可能的模糊匹配音节列表
     * 
     * @param syllable 原始音节
     * @return 包含原始音节和所有可能的模糊匹配音节的列表
     */
    fun applyFuzzyRules(syllable: String): List<String> {
        // 如果没有启用任何模糊拼音规则，直接返回原始音节
        if (!fuzzyEnabled) {
            return listOf(syllable)
        }
// ... existing code ...
```

