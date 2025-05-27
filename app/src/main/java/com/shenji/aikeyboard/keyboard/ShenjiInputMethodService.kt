package com.shenji.aikeyboard.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.model.WordFrequency
import kotlinx.coroutines.*
import timber.log.Timber
import android.graphics.Color
import kotlinx.coroutines.Dispatchers

class ShenjiInputMethodService : InputMethodService() {
    
    private val TAG = "ShenjiIME"
    
    // 键盘视图
    private lateinit var keyboardView: View
    
    // 候选词视图（独立）
    private lateinit var candidatesViewLayout: View
    
    // 候选词容器
    private lateinit var candidatesContainer: LinearLayout
    
    // 默认候选词视图
    private lateinit var defaultCandidatesView: LinearLayout
    
    // 候选词线性布局（横向滚动）
    private lateinit var candidatesView: LinearLayout
    
    // 展开候选词按钮
    private lateinit var expandCandidatesButton: TextView
    
    // 工具栏视图
    private lateinit var toolbarView: LinearLayout
    
    // 拼音显示TextView
    private lateinit var pinyinDisplay: TextView
    
    // 应用名称显示TextView
    private lateinit var appNameDisplay: TextView
    
    // 应用图标ImageView
    private lateinit var appIcon: ImageView
    
    // 当前输入的拼音
    private var composingText = StringBuilder()
    
    // 当前候选词列表
    private var candidates = listOf<WordFrequency>()
    
    // 标记是否刚提交过候选词，用于处理连续输入
    private var justCommittedText = false
    
    // 中/英输入模式状态
    private var isChineseMode = true
    
    // 键盘模式状态
    private enum class KeyboardMode {
        QWERTY,    // 字母键盘
        NUMBER,    // 数字键盘
        SYMBOL     // 符号键盘
    }
    private var currentKeyboardMode = KeyboardMode.QWERTY
    
    // 符号键盘页面状态
    private enum class SymbolPage {
        CHINESE,        // 中文符号
        ENGLISH,        // 英文符号
        BRACKETS,       // 括号符号
        CURRENCY,       // 货币符号
        MATH,           // 数学符号
        CHINESE_NUM,    // 中文数字
        CIRCLE_NUM,     // 圆圈数字
        NORMAL_NUM      // 普通数字
    }
    private var currentSymbolPage = SymbolPage.CHINESE
    
    // 🔧 新增：智能防抖和双缓冲相关变量
    private var currentQueryJob: Job? = null
    private var debounceJob: Job? = null
    
    // 🎯 防抖配置：极低延迟，减少抖动感知
    private val DEBOUNCE_DELAY_CHINESE = 50L // 中文输入防抖时间（极低延迟）
    private val DEBOUNCE_DELAY_ENGLISH = 30L  // 英文输入防抖时间（极低延迟）
    private val DEBOUNCE_DELAY_SINGLE_CHAR = 20L // 单字符输入防抖时间（极低延迟）
    
    // 🎯 双缓冲：候选词缓存
    private var lastDisplayedCandidates = listOf<WordFrequency>()
    private var pendingCandidates = listOf<WordFrequency>()
    private var isUpdatingCandidates = false
    
    // 🎯 预测显示：快速响应缓存
    private val quickResponseCache = mutableMapOf<String, List<WordFrequency>>()
    private val maxCacheSize = 50
    
    // 长按删除键自动删除的处理器和任务
    private val deleteHandler = Handler(Looper.getMainLooper())
    private val deleteRunnable = object : Runnable {
        override fun run() {
            onDelete()
            // 持续触发删除操作，实现长按连续删除效果
            deleteHandler.postDelayed(this, DELETE_REPEAT_DELAY)
        }
    }
    
    // 长按删除键的延迟时间（毫秒）
    private val DELETE_INITIAL_DELAY = 400L  // 长按后首次触发的延迟
    private val DELETE_REPEAT_DELAY = 50L   // 连续触发的间隔
    
    // 符号键盘管理器
    private lateinit var symbolKeyboardManager: SymbolKeyboardManager
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("神迹输入法服务已创建")
        Timber.d("输入法服务生命周期: onCreate")
        
        // 🔧 新增：智能Trie状态检测和自动重建机制
        initializeInputMethodServiceSmart()
    }
    
    /**
     * 🚀 智能输入法服务初始化机制
     * 自愈式设计：无论什么情况都让输入法立即可用
     */
    private fun initializeInputMethodServiceSmart() {
        try {
            Timber.i("🚀 启动智能输入法服务初始化...")
            
            // 🎯 关键修复：精确区分安装场景
            val dictFile = getDictionaryFile()
            val databaseExists = dictFile.exists()
            val databaseSize = if (databaseExists) dictFile.length() else 0L
            
            when {
                !databaseExists -> {
                    Timber.i("🆕 数据库文件不存在，启动自愈机制")
                    Timber.i("输入法立即可用，后台自动初始化数据库...")
                    startSelfHealingMode()
                }
                databaseSize > 1024 * 1024 -> { // 大于1MB，说明是完整数据库
                    Timber.i("✅ 发现完整数据库文件 (${databaseSize / (1024 * 1024)} MB)")
                    Timber.i("判定为覆盖安装，输入法立即可用")
                    // 覆盖安装：数据库完整，输入法立即可用
                    return
                }
                else -> {
                    Timber.i("⚠️ 数据库文件过小 (${databaseSize} bytes)，启动自愈机制")
                    Timber.i("输入法立即可用，后台重新初始化数据库...")
                    startSelfHealingMode()
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 输入法服务初始化异常，启动自愈机制: ${e.message}")
            startSelfHealingMode()
        }
    }
    
    /**
     * 🎯 获取数据库文件路径
     */
    private fun getDictionaryFile(): java.io.File {
        val internalDir = java.io.File(filesDir, "dictionaries")
        return java.io.File(internalDir, "shenji_dict.realm")
    }
    
    /**
     * 🛠️ 自愈机制：输入法立即可用，后台自动修复数据库
     * 核心思想：永远不阻塞用户使用，在后台静默修复
     */
    private fun startSelfHealingMode() {
        Timber.i("🛠️ 启动自愈机制：输入法立即可用，后台自动修复...")
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                Timber.i("🔧 开始后台自愈流程...")
                
                // 第一步：确保应用基础组件可用
                ensureApplicationComponents()
                
                // 第二步：自动初始化或修复数据库
                autoInitializeDatabase()
                
                // 第三步：重建Trie内存结构
                autoRebuildTrieMemory()
                
                // 第四步：验证修复结果
                validateSelfHealing()
                
                Timber.i("🎉 自愈流程完成，输入法已完全恢复")
                
            } catch (e: Exception) {
                Timber.e(e, "❌ 自愈流程失败，但输入法仍可基础使用: ${e.message}")
            }
        }
    }
    
    /**
     * 🔧 确保应用基础组件可用
     */
    private suspend fun ensureApplicationComponents() {
        try {
            Timber.d("🔧 检查应用基础组件...")
            
            // 等待应用初始化（最多等待10秒）
            var retryCount = 0
            val maxRetries = 10
            
            while (retryCount < maxRetries) {
                if (checkAppInitializationStatus()) {
                    Timber.i("✅ 应用基础组件已可用")
                    return
                }
                
                delay(1000)
                retryCount++
                Timber.d("⏳ 等待应用组件初始化... (${retryCount}/${maxRetries})")
            }
            
            Timber.w("⚠️ 应用组件初始化超时，尝试强制初始化...")
            
            // 如果等待超时，尝试强制触发初始化
            try {
                ShenjiApplication.instance.initRealm()
                Timber.i("✅ 强制初始化成功")
            } catch (e: Exception) {
                Timber.e(e, "强制初始化失败")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "确保应用组件失败: ${e.message}")
        }
    }
    
    /**
     * 🗄️ 自动初始化或修复数据库
     */
    private suspend fun autoInitializeDatabase() {
        try {
            Timber.d("🗄️ 开始自动数据库修复...")
            
            val dictFile = getDictionaryFile()
            val needsInitialization = !dictFile.exists() || dictFile.length() < 1024 * 1024
            
            if (needsInitialization) {
                Timber.i("🔧 数据库需要修复，开始自动初始化...")
                
                // 确保目录存在
                dictFile.parentFile?.mkdirs()
                
                // 检查Application是否有初始化方法可用
                try {
                    val app = ShenjiApplication.instance
                    
                    // 如果数据库文件不存在或损坏，触发重新初始化
                    if (!dictFile.exists()) {
                        Timber.i("🔧 数据库文件不存在，触发重新初始化...")
                        app.initRealm()
                    } else if (dictFile.length() < 1024 * 1024) {
                        Timber.i("🔧 数据库文件损坏，删除并重新初始化...")
                        dictFile.delete()
                        app.initRealm()
                    }
                    
                    // 等待初始化完成
                    var waitCount = 0
                    while (waitCount < 30) { // 最多等待30秒
                        delay(1000)
                        waitCount++
                        
                        if (dictFile.exists() && dictFile.length() > 1024 * 1024) {
                            Timber.i("✅ 数据库自动修复成功，大小: ${dictFile.length() / (1024 * 1024)} MB")
                            return
                        }
                        
                        if (waitCount % 5 == 0) {
                            Timber.d("⏳ 等待数据库修复... (${waitCount}/30秒)")
                        }
                    }
                    
                    Timber.w("⚠️ 数据库修复超时，但输入法仍可基础使用")
                    
                } catch (e: Exception) {
                    Timber.e(e, "自动数据库修复失败: ${e.message}")
                }
            } else {
                Timber.i("✅ 数据库状态正常，无需修复")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "自动数据库修复异常: ${e.message}")
        }
    }
    
    /**
     * 🧠 自动重建Trie内存结构
     */
    private suspend fun autoRebuildTrieMemory() {
        try {
            Timber.d("🧠 开始自动重建Trie内存...")
            
            val trieManager = ShenjiApplication.trieManager
            
            // 检查核心词典状态
            val charsLoaded = trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieType.CHARS)
            val baseLoaded = trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieType.BASE)
            
            if (!charsLoaded || !baseLoaded) {
                Timber.i("🔧 Trie内存需要重建...")
                
                // 优先重建CHARS词典
                if (!charsLoaded) {
                    Timber.d("🔧 重建CHARS词典...")
                    val charsSuccess = trieManager.loadTrieToMemory(com.shenji.aikeyboard.data.trie.TrieType.CHARS)
                    if (charsSuccess) {
                        Timber.i("✅ CHARS词典重建成功")
                    } else {
                        Timber.w("⚠️ CHARS词典重建失败，但不影响基础使用")
                    }
                }
                
                // 然后重建BASE词典
                if (!baseLoaded) {
                    Timber.d("🔧 重建BASE词典...")
                    val baseSuccess = trieManager.loadTrieToMemory(com.shenji.aikeyboard.data.trie.TrieType.BASE)
                    if (baseSuccess) {
                        Timber.i("✅ BASE词典重建成功")
                    } else {
                        Timber.w("⚠️ BASE词典重建失败，但不影响基础使用")
                    }
                }
                
                val loadedTypes = trieManager.getLoadedTrieTypes()
                Timber.i("📚 Trie重建完成，已加载: ${loadedTypes.map { getTrieDisplayName(it) }}")
                
            } else {
                Timber.i("✅ Trie内存状态正常，无需重建")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "自动Trie重建失败，但不影响基础使用: ${e.message}")
        }
    }
    
    /**
     * ✅ 验证自愈结果
     */
    private suspend fun validateSelfHealing() {
        try {
            Timber.d("✅ 开始验证自愈结果...")
            
            // 测试数据库查询
            val dbTestResult = try {
                val realm = ShenjiApplication.realm
                val entryCount = realm.query(com.shenji.aikeyboard.data.Entry::class).count().find()
                Timber.i("📊 数据库测试: ${entryCount}个词条")
                entryCount > 0
            } catch (e: Exception) {
                Timber.w("数据库测试失败: ${e.message}")
                false
            }
            
            // 测试候选词引擎
            val engineTestResult = try {
                val engineAdapter = InputMethodEngineAdapter.getInstance()
                val testResults = engineAdapter.getCandidates("ni", 3)
                Timber.i("🔍 引擎测试: ${testResults.size}个候选词")
                testResults.isNotEmpty()
            } catch (e: Exception) {
                Timber.w("引擎测试失败: ${e.message}")
                false
            }
            
            // 汇总结果
            when {
                dbTestResult && engineTestResult -> {
                    Timber.i("🎉 自愈验证成功：数据库✅ 引擎✅")
                }
                dbTestResult -> {
                    Timber.i("🎯 自愈部分成功：数据库✅ 引擎⚠️（可基础使用）")
                }
                engineTestResult -> {
                    Timber.i("🎯 自愈部分成功：数据库⚠️ 引擎✅")
                }
                else -> {
                    Timber.w("⚠️ 自愈验证失败，但输入法仍可尝试使用")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "自愈验证异常: ${e.message}")
        }
    }
    
    /**
     * 🎯 Trie内存状态数据类
     */
    private data class TrieMemoryStatus(
        val charsLoaded: Boolean,
        val baseLoaded: Boolean,
        val totalLoadedTypes: Int,
        val needsReload: Boolean,
        val priority: String // "HIGH", "MEDIUM", "LOW"
    ) {
        override fun toString(): String {
            return "TrieStatus(CHARS:${if(charsLoaded) "✓" else "✗"}, BASE:${if(baseLoaded) "✓" else "✗"}, " +
                   "总计:$totalLoadedTypes, 需重建:$needsReload, 优先级:$priority)"
        }
    }
    
    /**
     * 🎯 快速检查应用初始化状态
     */
    private fun checkAppInitializationStatus(): Boolean {
        return try {
            ShenjiApplication.instance
            ShenjiApplication.appContext
            ShenjiApplication.realm
            ShenjiApplication.trieManager
            true
        } catch (e: Exception) {
            Timber.w("应用组件未完全初始化: ${e.message}")
            false
        }
    }
    
    /**
     * 🎯 检测Trie内存状态
     */
    private fun checkTrieMemoryStatus(): TrieMemoryStatus {
        return try {
            val trieManager = ShenjiApplication.trieManager
            
            // 检查核心词典状态
            val charsLoaded = trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieType.CHARS)
            val baseLoaded = trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieType.BASE)
            
            // 检查所有已加载的词典类型
            val loadedTypes = trieManager.getLoadedTrieTypes()
            val totalLoaded = loadedTypes.size
            
            // 判断是否需要重建
            val needsReload = !charsLoaded || !baseLoaded
            
            // 确定优先级
            val priority = when {
                !charsLoaded && !baseLoaded -> "HIGH"    // 核心词典都没有，高优先级
                !charsLoaded || !baseLoaded -> "MEDIUM"  // 缺少一个核心词典，中优先级
                totalLoaded < 3 -> "LOW"                 // 核心词典有，但其他词典少，低优先级
                else -> "NONE"                           // 状态良好，无需重建
            }
            
            TrieMemoryStatus(
                charsLoaded = charsLoaded,
                baseLoaded = baseLoaded,
                totalLoadedTypes = totalLoaded,
                needsReload = needsReload,
                priority = priority
            )
            
        } catch (e: Exception) {
            Timber.e(e, "检测Trie状态失败: ${e.message}")
            // 返回需要重建的状态作为安全回退
            TrieMemoryStatus(
                charsLoaded = false,
                baseLoaded = false,
                totalLoadedTypes = 0,
                needsReload = true,
                priority = "HIGH"
            )
        }
    }
    
    /**
     * 🎯 延迟启动Trie优化（应用未完全初始化时）
     */
    private fun scheduleDelayedTrieOptimization() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                // 等待应用初始化完成
                var retryCount = 0
                val maxRetries = 10
                
                while (retryCount < maxRetries) {
                    delay(1000) // 等待1秒
                    retryCount++
                    
                    if (checkAppInitializationStatus()) {
                        Timber.i("✅ 应用初始化完成，开始Trie优化 (重试${retryCount}次)")
                        
                        val trieStatus = checkTrieMemoryStatus()
                        if (trieStatus.needsReload) {
                            startAsyncTrieOptimization(trieStatus)
                        } else {
                            performQuickPreheat()
                        }
                        return@launch
                    }
                    
                    Timber.d("⏳ 等待应用初始化... (${retryCount}/${maxRetries})")
                }
                
                Timber.w("⚠️ 应用初始化超时，Trie优化将跳过")
                // 不启动降级模式，输入法基于Realm仍可正常使用
                
            } catch (e: Exception) {
                Timber.e(e, "延迟Trie优化失败: ${e.message}")
                // 不影响输入法使用，只记录日志
            }
        }
    }
    
    /**
     * 🎯 启动异步Trie优化（不阻塞输入法使用）
     */
    private fun startAsyncTrieOptimization(status: TrieMemoryStatus) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                Timber.i("🔧 开始异步Trie优化，优先级: ${status.priority}")
                val startTime = System.currentTimeMillis()
                
                val trieManager = ShenjiApplication.trieManager
                
                // 🎯 优先级重建策略
                when (status.priority) {
                    "HIGH" -> {
                        // 高优先级：立即重建核心词典
                        rebuildCoreTrieDictionaries(trieManager)
                        // 然后异步加载其他词典
                        loadAdditionalTrieDictionaries(trieManager)
                    }
                    "MEDIUM" -> {
                        // 中优先级：重建缺失的核心词典
                        if (!status.charsLoaded) {
                            loadTrieWithLogging(trieManager, com.shenji.aikeyboard.data.trie.TrieType.CHARS)
                        }
                        if (!status.baseLoaded) {
                            loadTrieWithLogging(trieManager, com.shenji.aikeyboard.data.trie.TrieType.BASE)
                        }
                    }
                    "LOW" -> {
                        // 低优先级：加载额外词典
                        loadAdditionalTrieDictionaries(trieManager)
                    }
                }
                
                val endTime = System.currentTimeMillis()
                val finalStatus = checkTrieMemoryStatus()
                
                Timber.i("🎉 异步Trie优化完成！")
                Timber.i("⏱️ 耗时: ${endTime - startTime}ms")
                Timber.i("📊 最终状态: $finalStatus")
                
                // 优化完成后预热引擎
                performQuickPreheat()
                
            } catch (e: Exception) {
                Timber.e(e, "❌ 异步Trie优化失败: ${e.message}")
                // 不影响输入法使用，只记录日志
            }
        }
    }
    
    /**
     * 🎯 重建核心Trie词典
     */
    private suspend fun rebuildCoreTrieDictionaries(trieManager: com.shenji.aikeyboard.data.trie.TrieManager) {
        val coreTypes = listOf(
            com.shenji.aikeyboard.data.trie.TrieType.CHARS,
            com.shenji.aikeyboard.data.trie.TrieType.BASE
        )
        
        for (trieType in coreTypes) {
            loadTrieWithLogging(trieManager, trieType)
        }
    }
    
    /**
     * 🎯 加载额外Trie词典
     */
    private suspend fun loadAdditionalTrieDictionaries(trieManager: com.shenji.aikeyboard.data.trie.TrieManager) {
        val additionalTypes = listOf(
            com.shenji.aikeyboard.data.trie.TrieType.CORRELATION,
            com.shenji.aikeyboard.data.trie.TrieType.ASSOCIATIONAL,
            com.shenji.aikeyboard.data.trie.TrieType.PLACE
        )
        
        for (trieType in additionalTypes) {
            try {
                if (!trieManager.isTrieLoaded(trieType)) {
                    loadTrieWithLogging(trieManager, trieType)
                }
            } catch (e: Exception) {
                Timber.w(e, "加载额外词典${getTrieDisplayName(trieType)}失败，跳过")
            }
        }
    }
    
    /**
     * 🎯 带日志的Trie加载
     */
    private suspend fun loadTrieWithLogging(
        trieManager: com.shenji.aikeyboard.data.trie.TrieManager, 
        trieType: com.shenji.aikeyboard.data.trie.TrieType
    ) {
        try {
            val startTime = System.currentTimeMillis()
            val loaded = trieManager.loadTrieToMemory(trieType)
            val endTime = System.currentTimeMillis()
            
            if (loaded) {
                Timber.i("✅ ${getTrieDisplayName(trieType)}词典加载成功，耗时${endTime - startTime}ms")
            } else {
                Timber.e("❌ ${getTrieDisplayName(trieType)}词典加载失败")
            }
        } catch (e: Exception) {
            Timber.e(e, "${getTrieDisplayName(trieType)}词典加载异常: ${e.message}")
        }
    }
    
    /**
     * 🎯 快速预热（Trie状态正常时）
     */
    private fun performQuickPreheat() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                Timber.d("🔥 开始快速预热...")
                val startTime = System.currentTimeMillis()
                
                val engineAdapter = InputMethodEngineAdapter.getInstance()
                val testResults = engineAdapter.getCandidates("ni", 3)
                
                val endTime = System.currentTimeMillis()
                
                if (testResults.isNotEmpty()) {
                    Timber.i("🔥 快速预热成功，耗时${endTime - startTime}ms，测试结果: ${testResults.map { it.word }}")
                } else {
                    Timber.w("⚠️ 快速预热完成，但测试查询无结果")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "快速预热失败: ${e.message}")
            }
        }
    }
    
    /**
     * 🎯 启动降级模式（异常情况下的安全回退）
     */
    private fun startFallbackMode() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                Timber.w("🛡️ 启动降级模式，尝试基础初始化...")
                
                // 基础检查和初始化
                delay(2000) // 等待2秒
                
                if (checkAppInitializationStatus()) {
                    val trieManager = ShenjiApplication.trieManager
                    
                    // 尝试至少加载CHARS词典
                    if (!trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieType.CHARS)) {
                        loadTrieWithLogging(trieManager, com.shenji.aikeyboard.data.trie.TrieType.CHARS)
                    }
                    
                    Timber.i("🛡️ 降级模式初始化完成")
                } else {
                    Timber.e("🛡️ 降级模式也无法初始化，输入法将使用数据库查询")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "降级模式失败: ${e.message}")
            }
        }
    }
    
    /**
     * 确保Realm数据库已初始化
     */
    private suspend fun ensureRealmInitialized() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // 检查Realm是否已初始化
            val isRealmInitialized = try {
                ShenjiApplication.realm
                true
            } catch (e: Exception) {
                false
            }
            
            if (!isRealmInitialized) {
                Timber.d("🔧 Realm未初始化，开始初始化...")
                // 这里可以添加Realm初始化逻辑，但通常在Application中已经处理
                Timber.w("Realm需要在Application中初始化")
            } else {
                Timber.d("✅ Realm数据库已可用")
            }
        } catch (e: Exception) {
            Timber.e(e, "检查Realm状态失败: ${e.message}")
        }
    }
    
    /**
     * 确保TrieManager已初始化
     */
    private suspend fun ensureTrieManagerInitialized() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val trieManager = ShenjiApplication.trieManager
            
            if (!trieManager.isInitialized()) {
                Timber.d("🔧 TrieManager未初始化，开始初始化...")
                trieManager.init()
                Timber.d("✅ TrieManager初始化完成")
            } else {
                Timber.d("✅ TrieManager已初始化")
            }
        } catch (e: Exception) {
            Timber.e(e, "TrieManager初始化失败: ${e.message}")
        }
    }
    
    /**
     * 自动加载核心词典（chars和base）
     * 这是输入法正常工作的最低要求
     */
    private suspend fun autoLoadCoreTrieDictionaries() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val trieManager = ShenjiApplication.trieManager
            val coreTypes = listOf(
                com.shenji.aikeyboard.data.trie.TrieType.CHARS,
                com.shenji.aikeyboard.data.trie.TrieType.BASE
            )
            
            for (trieType in coreTypes) {
                try {
                    if (!trieManager.isTrieLoaded(trieType)) {
                        Timber.d("🔧 自动加载${getTrieDisplayName(trieType)}词典...")
                        val startTime = System.currentTimeMillis()
                        
                        val loaded = trieManager.loadTrieToMemory(trieType)
                        val endTime = System.currentTimeMillis()
                        
                        if (loaded) {
                            Timber.i("✅ ${getTrieDisplayName(trieType)}词典自动加载成功，耗时${endTime - startTime}ms")
                        } else {
                            Timber.e("❌ ${getTrieDisplayName(trieType)}词典自动加载失败")
                        }
                    } else {
                        Timber.d("✅ ${getTrieDisplayName(trieType)}词典已加载")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "${getTrieDisplayName(trieType)}词典加载异常: ${e.message}")
                }
            }
            
            // 记录加载状态
            val loadedTypes = trieManager.getLoadedTrieTypes()
            Timber.i("📚 当前已加载词典: ${loadedTypes.map { getTrieDisplayName(it) }}")
            
        } catch (e: Exception) {
            Timber.e(e, "核心词典自动加载失败: ${e.message}")
        }
    }
    
    /**
     * 预热候选词引擎
     * 通过执行一次简单查询来确保引擎正常工作
     */
    private suspend fun preheatCandidateEngine() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            Timber.d("🔥 开始预热候选词引擎...")
            val startTime = System.currentTimeMillis()
            
            // 执行一个简单的测试查询
            val engineAdapter = InputMethodEngineAdapter.getInstance()
            val testResults = engineAdapter.getCandidates("ni", 5)
            
            val endTime = System.currentTimeMillis()
            
            if (testResults.isNotEmpty()) {
                Timber.i("✅ 候选词引擎预热成功，耗时${endTime - startTime}ms，测试查询返回${testResults.size}个结果")
                Timber.d("🔥 测试结果: ${testResults.take(3).map { it.word }}")
            } else {
                Timber.w("⚠️ 候选词引擎预热完成，但测试查询无结果，可能词典未完全加载")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "候选词引擎预热失败: ${e.message}")
        }
    }
    
    /**
     * 获取Trie类型的显示名称
     */
    private fun getTrieDisplayName(trieType: com.shenji.aikeyboard.data.trie.TrieType): String {
        return when (trieType) {
            com.shenji.aikeyboard.data.trie.TrieType.CHARS -> "单字"
            com.shenji.aikeyboard.data.trie.TrieType.BASE -> "基础词典"
            com.shenji.aikeyboard.data.trie.TrieType.CORRELATION -> "关联词典"
            com.shenji.aikeyboard.data.trie.TrieType.ASSOCIATIONAL -> "联想词典"
            com.shenji.aikeyboard.data.trie.TrieType.PLACE -> "地名词典"
            com.shenji.aikeyboard.data.trie.TrieType.PEOPLE -> "人名词典"
            com.shenji.aikeyboard.data.trie.TrieType.POETRY -> "诗词词典"
            com.shenji.aikeyboard.data.trie.TrieType.CORRECTIONS -> "纠错词典"
            com.shenji.aikeyboard.data.trie.TrieType.COMPATIBLE -> "兼容词典"
        }
    }
    
    override fun onCreateInputView(): View {
        Timber.d("输入法服务生命周期: onCreateInputView - 开始创建键盘视图")
        
        try {
            // 创建主容器，包含候选词和键盘
            val mainContainer = LinearLayout(this)
            mainContainer.orientation = LinearLayout.VERTICAL
            mainContainer.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            mainContainer.setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0")) // 调试背景色
            
            // 加载候选词布局
            candidatesViewLayout = layoutInflater.inflate(R.layout.candidates_view, null)
            
            // 初始化候选词区域
            candidatesContainer = candidatesViewLayout.findViewById(R.id.candidates_container)
            defaultCandidatesView = candidatesViewLayout.findViewById(R.id.default_candidates_view)
            candidatesView = candidatesViewLayout.findViewById(R.id.candidates_view)
            expandCandidatesButton = candidatesViewLayout.findViewById(R.id.expand_candidates_button)
            
            // 初始化拼音显示区域
            pinyinDisplay = candidatesViewLayout.findViewById(R.id.pinyin_display)
            appNameDisplay = candidatesViewLayout.findViewById(R.id.app_name_display)
            appIcon = candidatesViewLayout.findViewById(R.id.app_icon)
            // 初始化工具栏
            toolbarView = candidatesViewLayout.findViewById(R.id.toolbar_view)
            
            // 设置展开按钮点击事件
            expandCandidatesButton.setOnClickListener {
                Toast.makeText(this, "展开候选词功能 - 正在开发中", Toast.LENGTH_SHORT).show()
                Timber.d("点击了展开候选词按钮")
            }
            
            // 设置工具栏图标点击事件
            setupToolbarIcons()
            
            // 加载键盘布局（默认字母键盘）
            keyboardView = layoutInflater.inflate(R.layout.keyboard_layout, null)
            
            // 设置字母按键监听器
            setupLetterKeys()
            
            // 设置功能按键监听器
            setupFunctionKeys()
            
            // 初始化中/英切换按钮状态
            updateLanguageSwitchButton()
            
            // 设置候选词视图布局参数
            val candidatesLayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            candidatesViewLayout.layoutParams = candidatesLayoutParams
            
            // 设置键盘视图布局参数
            val keyboardLayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            keyboardView.layoutParams = keyboardLayoutParams
            
            // 创建分隔线 - 修复：使用更细的分隔线，避免遮挡候选词
            val separator = View(this)
            separator.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1 // 改为1dp高度，减少遮挡
            )
            separator.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0")) // 改为浅灰色，不那么突兀
            
            // 将候选词视图和键盘视图添加到主容器（确保顺序正确）
            mainContainer.addView(candidatesViewLayout, 0) // 候选词在顶部
            mainContainer.addView(separator, 1)            // 分隔线
            mainContainer.addView(keyboardView, 2)         // 键盘在底部
            
            Timber.d("🎯 布局层级: 候选词(index=0) -> 分隔线(index=1) -> 键盘(index=2)")
            
            Timber.d("🎯 整合视图创建成功：候选词+键盘")
            return mainContainer
        } catch (e: Exception) {
            Timber.e(e, "键盘视图创建失败: ${e.message}")
            // 返回一个空的视图作为备选方案
            return LinearLayout(this)
        }
    }
    
    override fun onCreateCandidatesView(): View? {
        Timber.d("🎯 onCreateCandidatesView被调用，但我们已经在InputView中整合了候选词")
        // 返回null，表示不使用独立的候选词视图
        return null
    }
    
    // 设置字母按键监听器
    private fun setupLetterKeys() {
        val letterIds = listOf(
            R.id.key_a, R.id.key_b, R.id.key_c, R.id.key_d, R.id.key_e, 
            R.id.key_f, R.id.key_g, R.id.key_h, R.id.key_i, R.id.key_j,
            R.id.key_k, R.id.key_l, R.id.key_m, R.id.key_n, R.id.key_o,
            R.id.key_p, R.id.key_q, R.id.key_r, R.id.key_s, R.id.key_t,
            R.id.key_u, R.id.key_v, R.id.key_w, R.id.key_x, R.id.key_y,
            R.id.key_z
        )
        
        letterIds.forEach { id ->
            keyboardView.findViewById<Button>(id)?.setOnClickListener { v ->
                val letter = (v as Button).text.toString().lowercase()
                onInputLetter(letter)
            }
        }
    }
    
    // 设置功能按键监听器
    private fun setupFunctionKeys() {
        // 数字键
        val numberIds = listOf(
            R.id.key_0, R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4,
            R.id.key_5, R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9
        )
        
        numberIds.forEach { id ->
            keyboardView.findViewById<Button>(id)?.setOnClickListener { v ->
                val number = (v as Button).text.toString()
                if (composingText.isEmpty()) {
                    // 没有拼音输入，直接输入数字
                    commitText(number)
                } else {
                    // 有拼音输入，追加到拼音中
                    onInputLetter(number)
                }
            }
        }
        
        // 删除键
        keyboardView.findViewById<Button>(R.id.key_delete)?.setOnClickListener {
            onDelete()
        }
        
        // 添加删除键长按监听
        keyboardView.findViewById<Button>(R.id.key_delete)?.setOnLongClickListener { 
            // 启动长按删除定时器
            deleteHandler.postDelayed(deleteRunnable, DELETE_INITIAL_DELAY)
            true
        }
        
        // 删除键触摸监听，用于检测长按结束
        keyboardView.findViewById<Button>(R.id.key_delete)?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 停止自动删除
                    deleteHandler.removeCallbacks(deleteRunnable)
                }
            }
            false // 返回false以不干扰点击事件
        }
        
        // 空格键
        keyboardView.findViewById<Button>(R.id.key_space)?.setOnClickListener {
            onSpace()
        }
        
        // 空格键长按
        keyboardView.findViewById<Button>(R.id.key_space)?.setOnLongClickListener {
            Toast.makeText(this, "语音输入功能即将上线", Toast.LENGTH_SHORT).show()
            true
        }
        
        // 回车键
        keyboardView.findViewById<Button>(R.id.key_enter)?.setOnClickListener {
            onEnter()
        }
        
        // 符号键（原123键）
        keyboardView.findViewById<Button>(R.id.key_symbol)?.setOnClickListener {
            Timber.d("符号键被点击，准备切换到符号键盘")
            try {
                switchToSymbolKeyboard()
            } catch (e: Exception) {
                Toast.makeText(this, "切换符号键盘失败: ${e.message}", Toast.LENGTH_SHORT).show()
                Timber.e(e, "切换符号键盘异常")
            }
        }
        
        // 123键（原分词键）
        val splitButton = keyboardView.findViewById<Button>(R.id.key_split)
        if (splitButton != null) {
            splitButton.setOnClickListener {
                Timber.d("123键被点击，准备切换到数字键盘")
                Toast.makeText(this, "123键被点击了！", Toast.LENGTH_LONG).show()
                try {
                    switchToNumberKeyboard()
                } catch (e: Exception) {
                    Toast.makeText(this, "切换失败: ${e.message}", Toast.LENGTH_LONG).show()
                    Timber.e(e, "切换数字键盘异常")
                }
            }
            Timber.d("123键事件监听器设置成功，按钮文本: ${splitButton.text}")
            Toast.makeText(this, "123键监听器已设置", Toast.LENGTH_SHORT).show()
        } else {
            Timber.e("找不到123键(key_split)，无法设置点击事件")
            Toast.makeText(this, "找不到123键！", Toast.LENGTH_LONG).show()
        }
        
        // 逗号键
        keyboardView.findViewById<Button>(R.id.key_comma)?.setOnClickListener {
            commitText(",")
        }
        
        // 句号键
        keyboardView.findViewById<Button>(R.id.key_period)?.setOnClickListener {
            commitText(".")
        }
        
        // 中/英切换键
        keyboardView.findViewById<Button>(R.id.key_lang_switch)?.setOnClickListener {
            onLanguageSwitch()
        }
        
        // Shift键
        keyboardView.findViewById<Button>(R.id.key_shift)?.setOnClickListener {
            // 暂不实现大小写切换
            Toast.makeText(this, "大小写切换功能开发中", Toast.LENGTH_SHORT).show()
            Timber.d("大小写切换暂未实现")
        }
    }
    
    /**
     * 设置工具栏图标点击事件
     */
    private fun setupToolbarIcons() {
        // 订单图标
        candidatesViewLayout.findViewById<ImageView>(R.id.order_icon)?.setOnClickListener {
            Toast.makeText(this, "订单功能即将上线", Toast.LENGTH_SHORT).show()
            Timber.d("点击了订单图标")
        }
        
        // 计划图标
        candidatesViewLayout.findViewById<ImageView>(R.id.plan_icon)?.setOnClickListener {
            Toast.makeText(this, "计划功能即将上线", Toast.LENGTH_SHORT).show()
            Timber.d("点击了计划图标")
        }
        
        // 编辑图标
        candidatesViewLayout.findViewById<ImageView>(R.id.edit_icon)?.setOnClickListener {
            Toast.makeText(this, "编辑功能即将上线", Toast.LENGTH_SHORT).show()
            Timber.d("点击了编辑图标")
        }
        
        // 评论图标
        candidatesViewLayout.findViewById<ImageView>(R.id.comment_icon)?.setOnClickListener {
            Toast.makeText(this, "评论功能即将上线", Toast.LENGTH_SHORT).show()
            Timber.d("点击了评论图标")
        }
        
        // App图标
        candidatesViewLayout.findViewById<ImageView>(R.id.app_icon_toolbar)?.setOnClickListener {
            Toast.makeText(this, "应用功能即将上线", Toast.LENGTH_SHORT).show()
            Timber.d("点击了App图标")
        }
        
        // 收起键盘箭头
        candidatesViewLayout.findViewById<ImageView>(R.id.collapse_keyboard_icon)?.setOnClickListener {
            // 收起键盘
            requestHideSelf(0)
            Timber.d("点击了收起键盘箭头，键盘已收起")
        }
    }
    
    // 处理字母输入
    private fun onInputLetter(letter: String) {
        // 如果是英文模式，直接输入字母
        if (!isChineseMode) {
            commitText(letter)
            return
        }
        
        // 中文模式下的拼音输入处理
        // 检查是否刚刚提交了候选词，如果是则开始新的输入流程
        if (justCommittedText) {
            // 确保开始新的输入流程
            composingText.clear()
            justCommittedText = false
            // 再次确保输入连接上的组合文本被清除
            currentInputConnection?.finishComposingText()
            
            // 重置候选词滚动位置
            if (::candidatesViewLayout.isInitialized) {
                candidatesViewLayout.findViewById<HorizontalScrollView>(R.id.candidates_scroll_view)?.scrollTo(0, 0)
            }
        }
        
        // 添加字母到拼音组合中
        composingText.append(letter)
        
        Timber.d("🎯 输入字母: '$letter', 当前拼音: '${composingText}'")
        
        // 输入框显示原始拼音（不带空格）
        currentInputConnection?.setComposingText(composingText, 1)
        
        // 显示候选词区域并获取候选词（包含拼音分段显示）
        loadCandidatesUltraSimple(composingText.toString())
    }
    
    // 处理删除操作
    private fun onDelete() {
        val ic = currentInputConnection
        if (ic == null) {
            Timber.w("InputConnection为空，无法执行删除操作")
            return
        }
        
        try {
            // 首先检查是否有选中的文本
            val selectedText = ic.getSelectedText(0)
            if (!selectedText.isNullOrEmpty()) {
                // 如果有选中文本，直接删除选中的内容
                Timber.d("删除选中文本: '$selectedText'")
                ic.commitText("", 1)
                return
            }
            
            if (composingText.isNotEmpty()) {
                // 🎯 取消防抖任务，立即响应删除操作
                debounceJob?.cancel()
                // 🔧 修复：不取消当前查询任务，避免删除后候选词消失
                // currentQueryJob?.cancel()
                
                // 删除拼音中的最后一个字母
                composingText.deleteCharAt(composingText.length - 1)
                
                if (composingText.isEmpty()) {
                    // 如果拼音为空，清空拼音显示并隐藏候选词区域
                    updatePinyinDisplay("")
                    hideCandidates()
                    
                    // 🎯 清空双缓冲状态
                    lastDisplayedCandidates = emptyList()
                    pendingCandidates = emptyList()
                    isUpdatingCandidates = false
                    
                    // 结束组合文本状态
                    ic.finishComposingText()
                } else {
                    // 输入框显示原始拼音（不带空格）
                    ic.setComposingText(composingText, 1)
                    
                    // 🎯 修复：使用专门的删除后候选词加载方法
                    loadCandidatesAfterDelete(composingText.toString())
                }
            } else {
                // 如果没有拼音，执行标准删除操作
                ic.deleteSurroundingText(1, 0)
            }
        } catch (e: Exception) {
            Timber.e(e, "删除操作失败: ${e.message}")
            // 回退到基本删除操作
            ic.deleteSurroundingText(1, 0)
        }
    }
    
    // 处理空格操作
    private fun onSpace() {
        if (composingText.isNotEmpty() && candidates.isNotEmpty()) {
            // 如果有拼音输入和候选词，选择第一个候选词
            val firstCandidate = candidates.firstOrNull()?.word ?: composingText.toString()
            commitText(firstCandidate)
        } else if (composingText.isNotEmpty()) {
            // 如果只有拼音输入，直接提交拼音
            commitText(composingText.toString())
        } else {
            // 否则插入空格
            commitText(" ")
        }
    }
    
    // 处理回车操作
    private fun onEnter() {
        if (composingText.isNotEmpty()) {
            // 如果有拼音输入，直接提交拼音并清空
            commitText(composingText.toString())
        } else {
            // 发送回车键事件
            val ic = currentInputConnection
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }
    
    // 处理中/英切换
    private fun onLanguageSwitch() {
        isChineseMode = !isChineseMode
        updateLanguageSwitchButton()
        
        // 如果有正在输入的拼音，清空它
        if (composingText.isNotEmpty()) {
            composingText.clear()
            currentInputConnection?.finishComposingText()
            updatePinyinDisplay("")
            hideCandidates()
        }
        
        val modeText = if (isChineseMode) "中文" else "英文"
        Toast.makeText(this, "已切换到${modeText}输入", Toast.LENGTH_SHORT).show()
        Timber.d("切换输入模式: $modeText")
    }
    
    // 更新中/英切换按钮的显示
    private fun updateLanguageSwitchButton() {
        keyboardView.findViewById<Button>(R.id.key_lang_switch)?.let { button ->
            if (isChineseMode) {
                button.text = "中/英"
                button.setTextColor(getColor(R.color.keyboard_text)) // 黑色
            } else {
                button.text = "中/英"
                button.setTextColor(getColor(R.color.secondary_gray)) // 浅色
            }
        }
    }
    
    // 切换到数字键盘
    private fun switchToNumberKeyboard() {
        try {
            Timber.d("🔢 开始切换到数字键盘...")
            currentKeyboardMode = KeyboardMode.NUMBER
            
            // 加载数字键盘布局
            val numberKeyboardView = layoutInflater.inflate(R.layout.number_keyboard_layout, null)
            Timber.d("🔢 数字键盘布局加载成功")
            
            // 替换键盘视图
            val mainContainer = keyboardView.parent as LinearLayout
            val keyboardIndex = mainContainer.indexOfChild(keyboardView)
            Timber.d("🔢 准备替换键盘视图，索引: $keyboardIndex")
            
            mainContainer.removeView(keyboardView)
            keyboardView = numberKeyboardView
            mainContainer.addView(keyboardView, keyboardIndex)
            Timber.d("🔢 键盘视图替换完成")
            
            // 设置数字键盘事件监听器（必须在视图替换后设置）
            setupNumberKeyboardListeners(keyboardView)
            Timber.d("🔢 数字键盘事件监听器设置完成")
            
            Toast.makeText(this, "已切换到数字键盘", Toast.LENGTH_SHORT).show()
            Timber.d("🔢 数字键盘切换成功")
        } catch (e: Exception) {
            Timber.e(e, "🔢 切换到数字键盘失败: ${e.message}")
            Toast.makeText(this, "切换数字键盘失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 切换到字母键盘
    private fun switchToQwertyKeyboard() {
        try {
            currentKeyboardMode = KeyboardMode.QWERTY
            
            // 加载字母键盘布局
            val qwertyKeyboardView = layoutInflater.inflate(R.layout.keyboard_layout, null)
            
            // 替换键盘视图
            val mainContainer = keyboardView.parent as LinearLayout
            val keyboardIndex = mainContainer.indexOfChild(keyboardView)
            mainContainer.removeView(keyboardView)
            keyboardView = qwertyKeyboardView
            mainContainer.addView(keyboardView, keyboardIndex)
            
            // 设置字母键盘事件监听器（必须在视图替换后设置）
            setupLetterKeys()
            setupFunctionKeys()
            updateLanguageSwitchButton()
            
            Timber.d("已切换到字母键盘")
        } catch (e: Exception) {
            Timber.e(e, "切换到字母键盘失败: ${e.message}")
        }
    }
    
    // 切换到符号键盘
    private fun switchToSymbolKeyboard() {
        try {
            Timber.d("🔣 开始切换到符号键盘...")
            currentKeyboardMode = KeyboardMode.SYMBOL
            
            // 加载新的符号键盘布局
            val symbolKeyboardView = layoutInflater.inflate(R.layout.symbol_keyboard_layout_new, null)
            Timber.d("🔣 符号键盘布局加载成功")
            
            // 替换键盘视图
            val mainContainer = keyboardView.parent as LinearLayout
            val keyboardIndex = mainContainer.indexOfChild(keyboardView)
            Timber.d("🔣 准备替换键盘视图，索引: $keyboardIndex")
            
            mainContainer.removeView(keyboardView)
            keyboardView = symbolKeyboardView
            mainContainer.addView(keyboardView, keyboardIndex)
            Timber.d("🔣 键盘视图替换完成")
            
            // 初始化符号键盘管理器
            initializeNewSymbolKeyboard(keyboardView)
            Timber.d("🔣 符号键盘管理器初始化完成")
            
            Toast.makeText(this, "已切换到符号键盘", Toast.LENGTH_SHORT).show()
            Timber.d("🔣 符号键盘切换成功")
        } catch (e: Exception) {
            Timber.e(e, "🔣 切换到符号键盘失败: ${e.message}")
            Toast.makeText(this, "切换符号键盘失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 初始化新的符号键盘
    private fun initializeNewSymbolKeyboard(symbolKeyboardView: View) {
        try {
            // 初始化符号键盘管理器
            symbolKeyboardManager = SymbolKeyboardManager()
            
            // 获取符号内容区域
            val symbolContentArea = symbolKeyboardView.findViewById<LinearLayout>(R.id.symbol_content_area)
            if (symbolContentArea != null) {
                symbolKeyboardManager.initialize(symbolContentArea)
                Timber.d("🔣 符号键盘管理器初始化成功")
            } else {
                Timber.e("🔣 找不到符号内容区域")
                return
            }
            
            // 设置符号按钮监听器
            setupNewSymbolButtonListeners(symbolKeyboardView)
            
            // 设置导航按钮监听器
            setupNewSymbolNavigationListeners(symbolKeyboardView)
            
            // 设置特殊按钮监听器
            setupNewSymbolSpecialButtons(symbolKeyboardView)
            
            Timber.d("🔣 新符号键盘初始化完成")
        } catch (e: Exception) {
            Timber.e(e, "🔣 初始化新符号键盘失败: ${e.message}")
        }
    }
    
    // 设置新符号键盘的按钮监听器
    private fun setupNewSymbolButtonListeners(symbolKeyboardView: View) {
        val symbolContentArea = symbolKeyboardView.findViewById<LinearLayout>(R.id.symbol_content_area)
        if (symbolContentArea == null) return
        
        // 为三行符号按钮设置监听器
        for (rowIndex in 0..2) {
            val rowLayout = symbolContentArea.getChildAt(rowIndex) as? LinearLayout ?: continue
            
            for (buttonIndex in 0 until rowLayout.childCount) {
                val button = rowLayout.getChildAt(buttonIndex) as? Button ?: continue
                
                // 跳过特殊按钮（ABC按钮和删除按钮）
                if (button.id == R.id.symbol_abc_btn || button.id == R.id.symbol_delete) {
                    continue
                }
                
                // 为普通符号按钮设置点击监听器
                button.setOnClickListener { v ->
                    val symbol = (v as Button).text.toString()
                    if (symbol.isNotEmpty()) {
                        commitText(symbol)
                    }
                }
            }
        }
    }
    
    // 设置新符号键盘的导航按钮监听器
    private fun setupNewSymbolNavigationListeners(symbolKeyboardView: View) {
        val navigationButtons = mapOf(
            R.id.nav_chinese to "chinese",
            R.id.nav_english to "english", 
            R.id.nav_brackets to "brackets",
            R.id.nav_currency to "currency",
            R.id.nav_math to "math",
            R.id.nav_chinese_num to "chinese_num",
            R.id.nav_circle_num to "circle_num",
            R.id.nav_superscript to "superscript"
        )
        
        navigationButtons.forEach { (textViewId, symbolSetKey) ->
            symbolKeyboardView.findViewById<TextView>(textViewId)?.setOnClickListener {
                switchSymbolSet(symbolSetKey, symbolKeyboardView)
            }
        }
    }
    
    // 设置新符号键盘的特殊按钮监听器
    private fun setupNewSymbolSpecialButtons(symbolKeyboardView: View) {
        // ABC按钮 - 返回字母键盘
        symbolKeyboardView.findViewById<Button>(R.id.symbol_abc_btn)?.setOnClickListener {
            switchToQwertyKeyboard()
        }
        
        // 返回按钮 - 返回字母键盘
        symbolKeyboardView.findViewById<Button>(R.id.symbol_back_btn)?.setOnClickListener {
            switchToQwertyKeyboard()
        }
        
        // 删除按钮
        val deleteButton = symbolKeyboardView.findViewById<Button>(R.id.symbol_delete)
        deleteButton?.setOnClickListener {
            onDelete()
        }
        
        // 删除按钮长按
        deleteButton?.setOnLongClickListener { 
            deleteHandler.postDelayed(deleteRunnable, DELETE_INITIAL_DELAY)
            true
        }
        
        // 删除按钮触摸监听
        deleteButton?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    deleteHandler.removeCallbacks(deleteRunnable)
                }
            }
            false
        }
        

    }
    
    // 切换符号集合
    private fun switchSymbolSet(symbolSetKey: String, symbolKeyboardView: View) {
        try {
            symbolKeyboardManager.switchToSymbolSet(symbolSetKey)
            updateNavigationButtonSelection(symbolSetKey, symbolKeyboardView)
            Timber.d("🔣 切换到符号集合: $symbolSetKey")
        } catch (e: Exception) {
            Timber.e(e, "🔣 切换符号集合失败: ${e.message}")
        }
    }
    
    // 更新导航按钮选中状态
    private fun updateNavigationButtonSelection(selectedKey: String, symbolKeyboardView: View) {
        val navigationButtons = mapOf(
            "chinese" to R.id.nav_chinese,
            "english" to R.id.nav_english,
            "brackets" to R.id.nav_brackets,
            "currency" to R.id.nav_currency,
            "math" to R.id.nav_math,
            "chinese_num" to R.id.nav_chinese_num,
            "circle_num" to R.id.nav_circle_num,
            "superscript" to R.id.nav_superscript
        )
        
        navigationButtons.forEach { (key, textViewId) ->
            val textView = symbolKeyboardView.findViewById<TextView>(textViewId)
            if (key == selectedKey) {
                // 选中状态 - 蓝色文字
                textView?.setTextColor(android.graphics.Color.parseColor("#2196F3"))
            } else {
                // 未选中状态 - 灰色文字
                textView?.setTextColor(android.graphics.Color.parseColor("#666666"))
            }
        }
    }
    
    // 设置数字键盘事件监听器
    private fun setupNumberKeyboardListeners(numberKeyboardView: View) {
        // 数字键 0-9
        val numberIds = listOf(
            R.id.num_key_0, R.id.num_key_1, R.id.num_key_2, R.id.num_key_3, R.id.num_key_4,
            R.id.num_key_5, R.id.num_key_6, R.id.num_key_7, R.id.num_key_8, R.id.num_key_9
        )
        
        numberIds.forEach { id ->
            numberKeyboardView.findViewById<Button>(id)?.setOnClickListener { v ->
                val number = (v as Button).text.toString()
                commitText(number)
            }
        }
        
        // 运算符号键
        numberKeyboardView.findViewById<Button>(R.id.num_key_plus)?.setOnClickListener {
            commitText("+")
        }
        
        numberKeyboardView.findViewById<Button>(R.id.num_key_minus)?.setOnClickListener {
            commitText("-")
        }
        
        numberKeyboardView.findViewById<Button>(R.id.num_key_multiply)?.setOnClickListener {
            commitText("*")
        }
        
        // 新添加的第四行按钮
        numberKeyboardView.findViewById<Button>(R.id.num_key_divide)?.setOnClickListener {
            commitText("/")
        }
        
        numberKeyboardView.findViewById<Button>(R.id.num_key_lparen)?.setOnClickListener {
            commitText("(")
        }
        
        numberKeyboardView.findViewById<Button>(R.id.num_key_rparen)?.setOnClickListener {
            commitText(")")
        }
        
        numberKeyboardView.findViewById<Button>(R.id.num_key_equal)?.setOnClickListener {
            commitText("=")
        }
        
        numberKeyboardView.findViewById<Button>(R.id.num_key_percent)?.setOnClickListener {
            commitText("%")
        }
        
        // 其他符号键
        numberKeyboardView.findViewById<Button>(R.id.num_key_at)?.setOnClickListener {
            commitText("@")
        }
        
        numberKeyboardView.findViewById<Button>(R.id.num_key_dot)?.setOnClickListener {
            commitText(".")
        }
        
        numberKeyboardView.findViewById<Button>(R.id.num_key_space)?.setOnClickListener {
            commitText(" ")
        }
        
        // 删除键
        numberKeyboardView.findViewById<Button>(R.id.num_key_delete)?.setOnClickListener {
            onDelete()
        }
        
        // 删除键长按
        numberKeyboardView.findViewById<Button>(R.id.num_key_delete)?.setOnLongClickListener { 
            deleteHandler.postDelayed(deleteRunnable, DELETE_INITIAL_DELAY)
            true
        }
        
        // 删除键触摸监听
        numberKeyboardView.findViewById<Button>(R.id.num_key_delete)?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    deleteHandler.removeCallbacks(deleteRunnable)
                }
            }
            false
        }
        
        // 返回字母键盘
        numberKeyboardView.findViewById<Button>(R.id.num_key_back)?.setOnClickListener {
            switchToQwertyKeyboard()
        }
        
        // 符号键
        numberKeyboardView.findViewById<Button>(R.id.num_key_symbol)?.setOnClickListener {
            switchToSymbolKeyboard()
        }
        
        // 确定键
        numberKeyboardView.findViewById<Button>(R.id.num_key_enter)?.setOnClickListener {
            onEnter()
        }
    }
    
    // 设置符号键盘事件监听器
    private fun setupSymbolKeyboardListeners(symbolKeyboardView: View) {
        // 设置所有页面的符号按键监听器
        setupAllSymbolPageListeners(symbolKeyboardView)
        
        // 返回字母键盘
        symbolKeyboardView.findViewById<Button>(R.id.sym_back_btn)?.setOnClickListener {
            switchToQwertyKeyboard()
        }
    }
    
    // 设置所有符号页面的按键监听器
    private fun setupAllSymbolPageListeners(symbolKeyboardView: View) {
        // 为所有页面设置通用的符号按钮监听器
        setupUniversalSymbolListeners(symbolKeyboardView)
        
        // 为所有页面设置123键和删除键
        val allPrefixes = listOf("sym", "eng", "bracket", "currency", "math", "chinese", "circle", "normal")
        allPrefixes.forEach { prefix ->
            setupCommonSymbolButtons(symbolKeyboardView, prefix)
        }
    }
    
    // 通用符号按钮监听器设置
    private fun setupUniversalSymbolListeners(symbolKeyboardView: View) {
        // 为ViewFlipper中的所有页面设置按钮监听器
        val viewFlipper = symbolKeyboardView.findViewById<android.widget.ViewFlipper>(R.id.symbol_view_flipper)
        if (viewFlipper != null) {
            for (i in 0 until viewFlipper.childCount) {
                val pageView = viewFlipper.getChildAt(i)
                setupPageButtonListeners(pageView)
            }
        }
    }
    
    // 第一页：中文符号监听器
    private fun setupChineseSymbolListeners(symbolKeyboardView: View) {
        val chineseSymbols = mapOf(
            R.id.sym_minus to "-",
            R.id.sym_underscore to "_",
            R.id.sym_semicolon to ";",
            R.id.sym_pipe to "|",
            R.id.sym_percent to "%",
            R.id.sym_plus to "+",
            R.id.sym_minus2 to "-",
            R.id.sym_multiply to "×",
            R.id.sym_divide to "÷",
            R.id.sym_equal to "=",
            R.id.sym_lparen to "(",
            R.id.sym_rparen to ")",
            R.id.sym_lbrace to "{",
            R.id.sym_rbrace to "}",
            R.id.sym_langle to "《",
            R.id.sym_rangle to "》",
            R.id.sym_hash to "#",
            R.id.sym_dollar to "$",
            R.id.sym_ampersand to "&",
            R.id.sym_dot to ".",
            R.id.sym_gamma to "Γ",
            R.id.sym_lsquare to "[",
            R.id.sym_less to "<",
            R.id.sym_greater to ">",
            R.id.sym_rsquare to "]",
            R.id.sym_caret to "^",
            R.id.sym_asterisk to "*"
        )
        
        chineseSymbols.forEach { (id, symbol) ->
            symbolKeyboardView.findViewById<Button>(id)?.setOnClickListener {
                commitText(symbol)
            }
        }
        
        // 123键和删除键
        setupCommonSymbolButtons(symbolKeyboardView, "sym")
    }
    
    // 通用符号按钮设置（123键、删除键等）
    private fun setupCommonSymbolButtons(symbolKeyboardView: View, prefix: String) {
        // 为所有页面设置123键和删除键
        val buttonIds = when (prefix) {
            "sym" -> listOf("sym_123_btn" to R.id.sym_123_btn, "sym_delete" to R.id.sym_delete)
            "eng" -> listOf("eng_123_btn" to R.id.eng_123_btn, "eng_delete" to R.id.eng_delete)
            "bracket" -> listOf("bracket_123_btn" to R.id.bracket_123_btn, "bracket_delete" to R.id.bracket_delete)
            "currency" -> listOf("currency_123_btn" to R.id.currency_123_btn, "currency_delete" to R.id.currency_delete)
            "math" -> listOf("math_123_btn" to R.id.math_123_btn, "math_delete" to R.id.math_delete)
            "chinese" -> listOf("chinese_123_btn" to R.id.chinese_123_btn, "chinese_delete" to R.id.chinese_delete)
            "circle" -> listOf("circle_123_btn" to R.id.circle_123_btn, "circle_delete" to R.id.circle_delete)
            "normal" -> listOf("subscript_123_btn" to R.id.subscript_123_btn, "subscript_delete" to R.id.subscript_delete)
            else -> emptyList()
        }
        
        buttonIds.forEach { (buttonName, buttonId) ->
            val button = symbolKeyboardView.findViewById<Button>(buttonId)
            if (button != null) {
                if (buttonName.contains("123")) {
                    // 123键 - 切换到数字键盘
                    button.setOnClickListener {
                        switchToNumberKeyboard()
                    }
                } else if (buttonName.contains("delete")) {
                    // 删除键
                    button.setOnClickListener {
                        onDelete()
                    }
                    
                    // 删除键长按
                    button.setOnLongClickListener { 
                        deleteHandler.postDelayed(deleteRunnable, DELETE_INITIAL_DELAY)
                        true
                    }
                    
                    // 删除键触摸监听
                    button.setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                deleteHandler.removeCallbacks(deleteRunnable)
                            }
                        }
                        false
                    }
                }
            }
        }
    }
    

    
    // 为页面中的所有按钮设置监听器
    private fun setupPageButtonListeners(pageView: android.view.View) {
        if (pageView is android.view.ViewGroup) {
            for (i in 0 until pageView.childCount) {
                val child = pageView.getChildAt(i)
                if (child is android.view.ViewGroup) {
                    setupPageButtonListeners(child) // 递归处理子视图组
                } else if (child is Button) {
                    val buttonId = child.id
                    val text = child.text.toString()
                    
                    // 跳过123键和删除键，这些由setupCommonSymbolButtons处理
                    val isSpecialButton = text == "123" || 
                                        text.isEmpty() || // 删除键通常没有文字，只有图标
                                        buttonId.toString().contains("123") ||
                                        buttonId.toString().contains("delete")
                    
                    if (!isSpecialButton && text.isNotEmpty()) {
                        // 为普通符号按钮设置点击监听器
                        child.setOnClickListener {
                            commitText(text)
                        }
                    }
                }
            }
        }
    }
    
    // 设置符号键盘底部导航监听器
    private fun setupSymbolNavigationListeners(symbolKeyboardView: View) {
        // 8个符号页面切换按钮
        symbolKeyboardView.findViewById<Button>(R.id.nav_chinese)?.setOnClickListener {
            switchToSymbolPage(SymbolPage.CHINESE)
        }
        
        symbolKeyboardView.findViewById<Button>(R.id.nav_english)?.setOnClickListener {
            switchToSymbolPage(SymbolPage.ENGLISH)
        }
        
        symbolKeyboardView.findViewById<Button>(R.id.nav_brackets)?.setOnClickListener {
            switchToSymbolPage(SymbolPage.BRACKETS)
        }
        
        symbolKeyboardView.findViewById<Button>(R.id.nav_currency)?.setOnClickListener {
            switchToSymbolPage(SymbolPage.CURRENCY)
        }
        
        symbolKeyboardView.findViewById<Button>(R.id.nav_math)?.setOnClickListener {
            switchToSymbolPage(SymbolPage.MATH)
        }
        
        symbolKeyboardView.findViewById<Button>(R.id.nav_fraction)?.setOnClickListener {
            switchToSymbolPage(SymbolPage.CHINESE_NUM)
        }
        
        symbolKeyboardView.findViewById<Button>(R.id.nav_circle_numbers)?.setOnClickListener {
            switchToSymbolPage(SymbolPage.CIRCLE_NUM)
        }
        
        symbolKeyboardView.findViewById<Button>(R.id.nav_numbers)?.setOnClickListener {
            switchToSymbolPage(SymbolPage.NORMAL_NUM)
        }
    }
    
    // 切换符号页面
    private fun switchToSymbolPage(page: SymbolPage) {
        try {
            currentSymbolPage = page
            
            // 获取ViewFlipper并切换页面
            val viewFlipper = keyboardView.findViewById<android.widget.ViewFlipper>(R.id.symbol_view_flipper)
            if (viewFlipper != null) {
                val pageIndex = when (page) {
                    SymbolPage.CHINESE -> 0
                    SymbolPage.ENGLISH -> 1
                    SymbolPage.BRACKETS -> 2
                    SymbolPage.CURRENCY -> 3
                    SymbolPage.MATH -> 4
                    SymbolPage.CHINESE_NUM -> 5
                    SymbolPage.CIRCLE_NUM -> 6
                    SymbolPage.NORMAL_NUM -> 7
                }
                
                viewFlipper.displayedChild = pageIndex
                updateSymbolPageSelection()
                
                Timber.d("🔣 切换到符号页面: $page (索引: $pageIndex)")
            } else {
                Timber.e("🔣 ViewFlipper未找到，无法切换页面")
            }
        } catch (e: Exception) {
            Timber.e(e, "🔣 切换符号页面失败: ${e.message}")
        }
    }
    
    // 更新符号页面选中状态
    private fun updateSymbolPageSelection() {
        try {
            // 重置所有按钮为普通样式
            val allNavButtons = listOf(
                R.id.nav_chinese, R.id.nav_english, R.id.nav_brackets, R.id.nav_currency,
                R.id.nav_math, R.id.nav_fraction, R.id.nav_circle_numbers, R.id.nav_numbers
            )
            
            allNavButtons.forEach { buttonId ->
                keyboardView.findViewById<Button>(buttonId)?.let { button ->
                    button.setBackgroundResource(R.drawable.keyboard_key_bg)
                    button.setTextColor(resources.getColor(android.R.color.black, null))
                }
            }
            
            // 设置当前选中按钮为高亮样式
            val selectedButtonId = when (currentSymbolPage) {
                SymbolPage.CHINESE -> R.id.nav_chinese
                SymbolPage.ENGLISH -> R.id.nav_english
                SymbolPage.BRACKETS -> R.id.nav_brackets
                SymbolPage.CURRENCY -> R.id.nav_currency
                SymbolPage.MATH -> R.id.nav_math
                SymbolPage.CHINESE_NUM -> R.id.nav_fraction
                SymbolPage.CIRCLE_NUM -> R.id.nav_circle_numbers
                SymbolPage.NORMAL_NUM -> R.id.nav_numbers
            }
            
            keyboardView.findViewById<Button>(selectedButtonId)?.let { button ->
                button.setBackgroundResource(R.drawable.keyboard_special_key_bg)
                button.setTextColor(resources.getColor(android.R.color.white, null))
            }
            
            Timber.d("🔣 更新符号页面选中状态: $currentSymbolPage")
        } catch (e: Exception) {
            Timber.e(e, "🔣 更新符号页面选中状态失败: ${e.message}")
        }
    }

    
    // 提交文本到输入框
    private fun commitText(text: String) {
        // 记录之前的输入状态，用于日志
        val hadComposingText = composingText.isNotEmpty()
        
        try {
            // 🎯 取消所有查询任务，避免提交后还有查询结果覆盖
            currentQueryJob?.cancel()
            debounceJob?.cancel()
            
            // 提交文本到输入框
            currentInputConnection?.commitText(text, 1)
            
            // 重置组合文本
            composingText.clear()
            
            // 清空拼音显示区域
            if (areViewComponentsInitialized()) {
                pinyinDisplay.text = ""
                hideCandidates()
                
                // 重置候选词滚动位置
                if (::candidatesViewLayout.isInitialized) {
                    candidatesViewLayout.findViewById<HorizontalScrollView>(R.id.candidates_scroll_view)?.scrollTo(0, 0)
                }
            }
            
            // 清空候选词
            candidates = emptyList()
            
            // 🎯 清空双缓冲状态
            lastDisplayedCandidates = emptyList()
            pendingCandidates = emptyList()
            isUpdatingCandidates = false
            
            // 确保完全结束组合状态
            currentInputConnection?.finishComposingText()
            
            // 标记刚刚提交了候选词，下次输入时需要重置状态
            justCommittedText = true
            
            Timber.d("🎯 提交文本: '$text', 之前有输入: $hadComposingText，已清空所有状态")
        } catch (e: Exception) {
            Timber.e(e, "提交文本失败: ${e.message}")
        }
    }
    
    // 辅助方法：检查视图组件是否已初始化
    private fun areViewComponentsInitialized(): Boolean {
        val layoutInit = ::candidatesViewLayout.isInitialized
        val containerInit = ::candidatesContainer.isInitialized
        val defaultViewInit = ::defaultCandidatesView.isInitialized
        val candidatesViewInit = ::candidatesView.isInitialized
        
        val allInitialized = layoutInit && containerInit && defaultViewInit && candidatesViewInit
        
        if (!allInitialized) {
            Timber.e("🎯 视图组件初始化状态检查:")
            Timber.e("  - candidatesViewLayout: $layoutInit")
            Timber.e("  - candidatesContainer: $containerInit") 
            Timber.e("  - defaultCandidatesView: $defaultViewInit")
            Timber.e("  - candidatesView: $candidatesViewInit")
        }
        
        return allInitialized
    }
    
    // 添加调试方法：记录候选词视图状态
    private fun logCandidateViewState() {
        if (areViewComponentsInitialized()) {
            Timber.d("候选词视图状态:")
            Timber.d("- 候选词容器可见性: ${visibilityToString(candidatesContainer.visibility)}")
            Timber.d("- 默认候选词视图可见性: ${visibilityToString(defaultCandidatesView.visibility)}")
            Timber.d("- 候选词视图子项数量: ${candidatesView.childCount}")
            Timber.d("- 默认候选词视图高度: ${defaultCandidatesView.height}px")
            Timber.d("- 候选词视图宽度: ${candidatesView.width}px")
        }
    }
    
    // 辅助方法：将可见性值转换为字符串
    private fun visibilityToString(visibility: Int): String {
        return when (visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            View.GONE -> "GONE"
            else -> "UNKNOWN($visibility)"
        }
    }
    
    // 安全显示候选词区域
    private fun showCandidates() {
        if (areViewComponentsInitialized()) {
            // 显示候选词区域时隐藏工具栏
            defaultCandidatesView.visibility = View.VISIBLE
            toolbarView.visibility = View.GONE
            
            // 🔧 固定高度，防止界面跳动
            val fixedHeight = 46 // 固定46dp高度
            val candidatesParams = defaultCandidatesView.layoutParams
            candidatesParams.height = fixedHeight
            candidatesParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            defaultCandidatesView.layoutParams = candidatesParams
            
            // 设置候选词区域背景为透明
            defaultCandidatesView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            Timber.d("🎯 显示候选词区域，固定高度: ${fixedHeight}dp")
            logCandidateViewState()
        }
    }
    
    // 🎯 新增：显示候选词区域但不清空现有内容（防止闪烁）
    private fun showCandidatesWithoutClearing() {
        if (areViewComponentsInitialized()) {
            Timber.d("🎯 视图组件已初始化，开始显示候选词区域")
            
            // 显示候选词区域时隐藏工具栏
            defaultCandidatesView.visibility = View.VISIBLE
            toolbarView.visibility = View.GONE
            
            // 🔧 固定高度和背景色，防止界面跳动和闪烁
            val fixedHeight = 46 // 固定46dp高度
            val candidatesParams = defaultCandidatesView.layoutParams
            candidatesParams.height = fixedHeight
            candidatesParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            defaultCandidatesView.layoutParams = candidatesParams
            
            // 🎯 关键：设置固定的背景色，避免透明背景导致的闪烁
            defaultCandidatesView.setBackgroundColor(android.graphics.Color.parseColor("#F8F8F8")) // 浅灰色背景
            
            Timber.d("🎯 候选词区域已显示，固定高度: ${fixedHeight}dp，背景色: #F8F8F8")
            logCandidateViewState()
        } else {
            Timber.e("🎯 视图组件未初始化，无法显示候选词区域")
        }
    }
    
    // 隐藏候选词区域
    private fun hideCandidates() {
        if (areViewComponentsInitialized()) {
            // 只隐藏候选词部分，保留拼音栏
            defaultCandidatesView.visibility = View.GONE
            // 隐藏候选词区域时显示工具栏
            toolbarView.visibility = View.VISIBLE
            
            Timber.d("🎯 隐藏候选词区域，显示工具栏")
        }
    }
    
    // 更新拼音显示区域
    private fun updatePinyinDisplay(pinyin: String) {
        if (::pinyinDisplay.isInitialized) {
            pinyinDisplay.text = pinyin
        }
    }
    
    // 🎯 新增：智能防抖候选词加载
    private fun loadCandidates(input: String) {
        if (input.isEmpty()) {
            hideCandidates()
            clearQuickResponseCache()
            return
        }
        
        Timber.d("🎯 开始加载候选词: '$input'")
        
        // 🔧 修复：不阻塞输入法使用，异步检查并加载Trie
        ensureTrieLoadedAsync()
        
        // 🎯 关键修复：立即显示候选词区域，确保用户看到响应
        showCandidatesWithoutClearing()
        
        // 🎯 第一阶段：立即显示预测候选词（如果有缓存）
        val hasPreview = showPredictiveCandidates(input)
        
        // 🎯 如果没有预览内容，显示加载提示
        if (!hasPreview) {
            Timber.d("🎯 没有缓存，显示加载提示")
            showLoadingHint()
        }
        
        // 🎯 第二阶段：智能防抖查询
        startDebouncedQuery(input)
    }
    
    /**
     * 🎯 超简单的候选词加载方法（真实查询版本）
     */
    private fun loadCandidatesUltraSimple(input: String) {
        if (input.isEmpty()) {
            hideCandidates()
            return
        }
        
        Timber.d("🎯 超简单加载候选词: '$input'")
        
        // 🔧 修复：增强视图初始化检查，添加重试机制
        if (!areViewComponentsInitialized()) {
            Timber.e("🎯 视图未初始化，尝试延迟重试")
            // 延迟50ms后重试一次
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                delay(50)
                if (areViewComponentsInitialized() && composingText.toString() == input) {
                    Timber.d("🎯 重试成功，视图已初始化")
                    loadCandidatesUltraSimple(input)
                } else {
                    Timber.e("🎯 重试失败，视图仍未初始化或输入已变化")
                }
            }
            return
        }
        
        try {
            // 🔧 修复：强制确保候选词区域可见，添加多重保护
            Timber.d("🎯 设置候选词区域可见性")
            defaultCandidatesView.visibility = View.VISIBLE
            toolbarView.visibility = View.GONE
            defaultCandidatesView.setBackgroundColor(android.graphics.Color.parseColor("#F8F8F8"))
            
            // 🔧 修复：强制刷新布局，确保视图状态生效
            defaultCandidatesView.invalidate()
            defaultCandidatesView.requestLayout()
            
            // 更新拼音显示
            updatePinyinDisplayWithSegmentation(input)
            
            Timber.d("🎯 候选词区域设置完成，开始异步查询")
            
            // 🎯 关键修复：使用简单的协程，增强状态保护
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    Timber.d("🎯 开始查询候选词: '$input'")
                    
                    // 🔧 修复：在查询前再次确认视图状态
                    if (!areViewComponentsInitialized()) {
                        Timber.e("🎯 协程执行时视图状态异常，中止查询")
                        return@launch
                    }
                    
                    // 🔧 修复：确认输入没有变化
                    if (composingText.toString() != input) {
                        Timber.d("🎯 输入已变化，中止查询: '$input' -> '${composingText}'")
                        return@launch
                    }
                    
                    val engineAdapter = InputMethodEngineAdapter.getInstance()
                    val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        engineAdapter.getCandidates(input, 15)
                    }
                    
                    Timber.d("🎯 查询完成: '$input' -> ${result.size}个候选词")
                    
                    // 🔧 修复：查询完成后再次确认状态
                    if (!areViewComponentsInitialized()) {
                        Timber.e("🎯 查询完成后视图状态异常，无法显示结果")
                        return@launch
                    }
                    
                    if (composingText.toString() != input) {
                        Timber.d("🎯 查询完成后输入已变化，丢弃结果: '$input' -> '${composingText}'")
                        return@launch
                    }
                    
                    // 🔧 修复：确保候选词区域仍然可见
                    if (defaultCandidatesView.visibility != View.VISIBLE) {
                        Timber.w("🎯 候选词区域被隐藏，重新显示")
                        defaultCandidatesView.visibility = View.VISIBLE
                        toolbarView.visibility = View.GONE
                        defaultCandidatesView.setBackgroundColor(android.graphics.Color.parseColor("#F8F8F8"))
                    }
                    
                    if (result.isNotEmpty()) {
                        candidates = result
                        
                        // 🔧 修复：使用增强的显示方法，确保可靠显示
                        displayCandidatesDirectlyEnhanced(result)
                        
                        Timber.d("🎯 候选词显示成功: ${result.take(3).map { it.word }}")
                        
                        // 🔧 修复：显示后验证结果
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            delay(100) // 等待100ms
                            if (candidatesView.childCount == 0) {
                                Timber.e("🎯 显示验证失败，候选词视图为空，尝试重新显示")
                                displayCandidatesDirectlyEnhanced(result)
                            } else {
                                Timber.d("🎯 显示验证成功，候选词视图有${candidatesView.childCount}个子项")
                            }
                        }
                    } else {
                        displayNoResultsDirectly()
                        Timber.w("🎯 无候选词结果")
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "🎯 查询候选词失败: ${e.message}")
                    
                    // 🔧 修复：异常时也要确保视图状态正确
                    try {
                        if (areViewComponentsInitialized()) {
                            displayErrorDirectly("查询失败: ${e.message}")
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex, "显示错误信息也失败")
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "🎯 超简单加载失败: ${e.message}")
            
            // 🔧 修复：主线程异常时的处理
            try {
                if (areViewComponentsInitialized()) {
                    displayErrorDirectly("加载失败: ${e.message}")
                }
            } catch (ex: Exception) {
                Timber.e(ex, "异常处理也失败")
            }
        }
    }
    
    /**
     * 🎯 简单的候选词加载方法（用于调试）
     */
    private fun loadCandidatesSimple(input: String) {
        if (input.isEmpty()) {
            hideCandidates()
            return
        }
        
        Timber.d("🎯 简单加载候选词: '$input'")
        
        // 立即显示候选词区域
        showCandidatesWithoutClearing()
        
        // 立即查询候选词
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                val engineAdapter = InputMethodEngineAdapter.getInstance()
                val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    engineAdapter.getCandidates(input, 20)
                }
                
                // 更新拼音显示
                updatePinyinDisplayWithSegmentation(input)
                
                if (result.isNotEmpty()) {
                    candidates = result
                    Timber.d("🎯 简单查询成功: '$input' -> ${result.size}个候选词: ${result.take(3).map { it.word }}")
                    
                    // 🎯 直接更新候选词视图，确保显示
                    updateCandidatesViewDirect(result)
                } else {
                    candidates = emptyList()
                    showNoResultsHintSmooth()
                    Timber.w("🎯 简单查询无结果: '$input'")
                }
            } catch (e: Exception) {
                Timber.e(e, "🎯 简单查询失败: '$input'")
                candidates = emptyList()
                showNoResultsHintSmooth()
            }
        }
    }
    
    /**
     * 🎯 立即加载候选词（用于删除操作等需要即时响应的场景）
     */
    private fun loadCandidatesImmediate(input: String) {
        if (input.isEmpty()) {
            hideCandidates()
            clearQuickResponseCache()
            return
        }
        
        // 🎯 立即显示候选词区域
        showCandidatesWithoutClearing()
        
        // 🎯 检查缓存，如果有则立即显示
        val hasPreview = showPredictiveCandidates(input)
        
        // 🎯 立即执行查询，不使用防抖
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            executeActualQuery(input)
        }
    }
    
    /**
     * 🔧 删除操作后的候选词加载（修复删除后候选词消失问题）
     */
    private fun loadCandidatesAfterDelete(input: String) {
        if (input.isEmpty()) {
            hideCandidates()
            return
        }
        
        Timber.d("🔧 删除后加载候选词: '$input'")
        
        try {
            // 🎯 确保候选词区域可见
            if (areViewComponentsInitialized()) {
                defaultCandidatesView.visibility = View.VISIBLE
                toolbarView.visibility = View.GONE
                defaultCandidatesView.setBackgroundColor(android.graphics.Color.parseColor("#F8F8F8"))
            }
            
            // 🎯 立即更新拼音显示
            updatePinyinDisplayWithSegmentation(input)
            
            // 🎯 使用新的协程，不取消之前的任务
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    Timber.d("🔧 开始删除后查询: '$input'")
                    
                    val engineAdapter = InputMethodEngineAdapter.getInstance()
                    val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        engineAdapter.getCandidates(input, 15)
                    }
                    
                    Timber.d("🔧 删除后查询完成: '$input' -> ${result.size}个候选词")
                    
                    if (result.isNotEmpty()) {
                        candidates = result
                        
                        // 🎯 直接显示候选词，确保可见
                        displayCandidatesDirectly(result)
                        
                        // 🎯 更新缓存
                        updateQuickResponseCache(input, result)
                        
                        Timber.d("🔧 删除后候选词显示成功: ${result.take(3).map { it.word }}")
                    } else {
                        candidates = emptyList()
                        displayNoResultsDirectly()
                        Timber.w("🔧 删除后无候选词结果")
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "🔧 删除后查询失败: ${e.message}")
                    candidates = emptyList()
                    displayErrorDirectly("查询失败")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "🔧 删除后加载候选词失败: ${e.message}")
        }
    }
    
    /**
     * 🎯 第一阶段：显示预测候选词（立即响应）
     * @return 是否有预览内容显示
     */
    private fun showPredictiveCandidates(input: String): Boolean {
        // 检查快速响应缓存
        val cachedCandidates = quickResponseCache[input]
        if (cachedCandidates != null) {
            Timber.d("🚀 使用缓存候选词: '$input' -> ${cachedCandidates.size}个")
            updateCandidatesViewInstant(cachedCandidates, isPreview = true)
            return true
        }
        
        // 检查前缀匹配缓存（部分匹配）
        val prefixMatch = quickResponseCache.entries.find { (key, _) ->
            input.startsWith(key) && key.isNotEmpty()
        }
        
        if (prefixMatch != null) {
            Timber.d("🚀 使用前缀匹配缓存: '$input' 匹配 '${prefixMatch.key}' -> ${prefixMatch.value.size}个")
            updateCandidatesViewInstant(prefixMatch.value, isPreview = true)
            return true
        }
        
        // 没有缓存，返回false表示没有预览内容
        return false
    }
    
    /**
     * 🎯 第二阶段：智能防抖查询
     */
    private fun startDebouncedQuery(input: String) {
        // 取消之前的防抖任务
        debounceJob?.cancel()
        
        // 确定防抖延迟时间
        val debounceDelay = when {
            input.length == 1 -> DEBOUNCE_DELAY_SINGLE_CHAR
            isChineseInput() -> DEBOUNCE_DELAY_CHINESE
            else -> DEBOUNCE_DELAY_ENGLISH
        }
        
        Timber.d("🎯 启动防抖查询: '$input', 延迟${debounceDelay}ms")
        
        debounceJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            delay(debounceDelay)
            
            // 防抖结束，执行实际查询
            executeActualQuery(input)
        }
    }
    
    /**
     * 🎯 执行实际查询（防抖后）
     */
    private suspend fun executeActualQuery(input: String) {
        // 取消之前的查询任务
        currentQueryJob?.cancel()
        
        currentQueryJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                Timber.d("🔍 执行实际查询: '$input'")
                
                val engineAdapter = InputMethodEngineAdapter.getInstance()
                val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    engineAdapter.getCandidates(input, 20)
                }
                
                // 检查任务是否被取消
                if (!isActive) {
                    Timber.d("🔍 查询任务已取消: '$input'")
                    return@launch
                }
                
                // 更新拼音显示
                updatePinyinDisplayWithSegmentation(input)
                
                if (result.isNotEmpty()) {
                    candidates = result
                    
                    Timber.d("🎯 查询成功: '$input' -> ${result.size}个候选词: ${result.take(3).map { it.word }}")
                    
                    // 🎯 缓存结果到快速响应缓存
                    updateQuickResponseCache(input, result)
                    
                    // 🎯 双缓冲更新
                    updateCandidatesViewBuffered(result)
                    
                    Timber.d("🎯 候选词视图更新完成")
                } else {
                    candidates = emptyList()
                    showNoResultsHintSmooth()
                    Timber.w("🎯 未找到候选词: '$input'")
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Timber.d("🔍 查询任务被取消: '$input'")
                } else {
                    Timber.e(e, "🎯 查询失败: '$input'")
                    candidates = emptyList()
                    showNoResultsHintSmooth()
                }
            }
        }
    }
    
    /**
     * 🎯 判断是否为中文输入模式
     */
    private fun isChineseInput(): Boolean {
        // 简单判断：如果包含拼音字符，认为是中文输入
        val pinyinChars = setOf('a', 'e', 'i', 'o', 'u', 'v', 'n', 'g', 'h', 'r')
        return composingText.any { it.lowercaseChar() in pinyinChars }
    }
    
    /**
     * 更新拼音显示，包含分段拆分结果
     * 只更新键盘上的拼音显示区域，不影响输入框
     */
    private fun updatePinyinDisplayWithSegmentation(input: String) {
        try {
            Timber.d("开始更新键盘拼音显示，输入: '$input'")
            
            // 获取分段拆分结果
            val engineAdapter = InputMethodEngineAdapter.getInstance()
            val syllables = engineAdapter.getSegments(input)
            
            Timber.d("拼音拆分结果: ${syllables.joinToString("+")}")
            
            // 如果有分段结果，显示分段后的拼音（用上引号代替空格）
            val displayText = if (syllables.isNotEmpty() && syllables.size > 1) {
                val segmentedText = syllables.joinToString("'")
                Timber.d("键盘显示分段拼音: '$segmentedText'")
                segmentedText
            } else {
                // 如果没有分段结果或只有一个音节，显示原始输入
                Timber.d("键盘显示原始拼音: '$input'")
                input
            }
            
            // 更新键盘上的拼音显示区域
            if (::pinyinDisplay.isInitialized) {
                pinyinDisplay.text = displayText
                Timber.d("键盘拼音显示已更新: '$input' -> '$displayText'")
                
                // 强制刷新UI
                pinyinDisplay.invalidate()
                pinyinDisplay.requestLayout()
            } else {
                Timber.e("pinyinDisplay未初始化，无法更新显示")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "更新键盘拼音显示失败: ${e.message}")
            // 如果分段失败，回退到显示原始输入
            if (::pinyinDisplay.isInitialized) {
                pinyinDisplay.text = input
                Timber.d("回退到原始输入显示: '$input'")
            }
        }
    }
    
    // 🎯 新增：显示加载提示（可选）
    private fun showLoadingHint() {
        if (!areViewComponentsInitialized()) {
            return
        }
        
        try {
            // 清空现有内容
            candidatesView.removeAllViews()
            
            // 创建加载提示
            val loadingText = TextView(this)
            loadingText.text = "正在查询..."
            loadingText.setTextColor(android.graphics.Color.parseColor("#999999")) // 浅灰色文字
            loadingText.setTextSize(14f) // 适中字体
            loadingText.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            loadingText.typeface = android.graphics.Typeface.DEFAULT
            
            // 🔧 修复：使用像素值设置固定高度
            val density = resources.displayMetrics.density
            val heightInPx = (46 * density).toInt()
            
            val loadingParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                heightInPx
            )
            loadingParams.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            loadingParams.setMargins(0, 0, 0, 0)
            loadingText.layoutParams = loadingParams
            
            candidatesView.addView(loadingText)
            Timber.d("🎯 显示加载提示")
        } catch (e: Exception) {
            Timber.e(e, "显示加载提示失败: ${e.message}")
        }
    }
    
    /**
     * 🎯 快速响应缓存管理
     */
    private fun updateQuickResponseCache(input: String, candidates: List<WordFrequency>) {
        // 限制缓存大小
        if (quickResponseCache.size >= maxCacheSize) {
            // 移除最旧的条目（简单的LRU策略）
            val oldestKey = quickResponseCache.keys.first()
            quickResponseCache.remove(oldestKey)
        }
        
        quickResponseCache[input] = candidates
        Timber.d("🎯 更新缓存: '$input' -> ${candidates.size}个候选词，缓存大小: ${quickResponseCache.size}")
    }
    
    private fun clearQuickResponseCache() {
        quickResponseCache.clear()
        Timber.d("🎯 清空快速响应缓存")
    }
    
    /**
     * 🎯 立即更新候选词视图（用于预测显示）
     */
    private fun updateCandidatesViewInstant(wordList: List<WordFrequency>, isPreview: Boolean = false) {
        if (!areViewComponentsInitialized() || isUpdatingCandidates) {
            return
        }
        
        try {
            // 如果是预览模式且内容相同，不更新
            if (isPreview && wordList == lastDisplayedCandidates) {
                return
            }
            
            Timber.d("🚀 立即更新候选词: ${wordList.size}个${if (isPreview) "（预览）" else ""}")
            
            // 快速更新，不清空现有内容，直接替换
            updateCandidatesViewDirect(wordList)
            lastDisplayedCandidates = wordList
            
        } catch (e: Exception) {
            Timber.e(e, "立即更新候选词失败: ${e.message}")
        }
    }
    
    /**
     * 🎯 双缓冲更新候选词视图
     */
    private fun updateCandidatesViewBuffered(wordList: List<WordFrequency>) {
        if (!areViewComponentsInitialized()) {
            Timber.e("🎯 视图组件未初始化，无法更新候选词")
            return
        }
        
        // 如果内容相同，不更新
        if (wordList == lastDisplayedCandidates) {
            Timber.d("🎯 候选词内容相同，跳过更新")
            return
        }
        
        // 如果正在更新，缓存待更新的内容
        if (isUpdatingCandidates) {
            pendingCandidates = wordList
            Timber.d("🎯 正在更新中，缓存待更新内容: ${wordList.size}个")
            return
        }
        
        try {
            isUpdatingCandidates = true
            
            Timber.d("🎯 开始双缓冲更新候选词: ${wordList.size}个，前3个: ${wordList.take(3).map { it.word }}")
            
            // 使用平滑更新
            updateCandidatesViewDirect(wordList)
            lastDisplayedCandidates = wordList
            
            Timber.d("🎯 双缓冲更新完成")
            
            // 检查是否有待更新的内容
            if (pendingCandidates.isNotEmpty() && pendingCandidates != wordList) {
                Timber.d("🎯 处理待更新内容: ${pendingCandidates.size}个")
                val pending = pendingCandidates
                pendingCandidates = emptyList()
                
                // 延迟处理待更新内容，避免频繁更新
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    delay(50) // 短暂延迟
                    if (!isUpdatingCandidates) {
                        updateCandidatesViewBuffered(pending)
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "双缓冲更新候选词失败: ${e.message}")
        } finally {
            isUpdatingCandidates = false
        }
    }
    
    /**
     * 🎯 直接更新候选词视图（简化版，确保可靠显示）
     */
    private fun updateCandidatesViewDirect(wordList: List<WordFrequency>) {
        if (!areViewComponentsInitialized()) {
            Timber.e("🎯 视图组件未初始化")
            return
        }
        
        try {
            Timber.d("🎯 开始直接更新候选词: ${wordList.size}个")
            
            // 🎯 确保候选词区域可见
            defaultCandidatesView.visibility = View.VISIBLE
            toolbarView.visibility = View.GONE
            defaultCandidatesView.setBackgroundColor(android.graphics.Color.parseColor("#F8F8F8"))
            
            // 🎯 简化逻辑：直接重建，确保可靠性
            rebuildCandidateViews(wordList)
            
            Timber.d("🎯 直接更新完成")
            
        } catch (e: Exception) {
            Timber.e(e, "直接更新候选词视图失败: ${e.message}")
            // 最后的回退：显示错误提示
            try {
                candidatesView.removeAllViews()
                val errorText = TextView(this)
                errorText.text = "显示错误"
                errorText.setTextColor(android.graphics.Color.RED)
                candidatesView.addView(errorText)
            } catch (ex: Exception) {
                Timber.e(ex, "连错误提示都显示失败")
            }
        }
    }
    
    /**
     * 🎯 更新现有候选词视图（复用视图，减少闪烁）
     */
    private fun updateExistingCandidateViews(wordList: List<WordFrequency>) {
        val candidatesRow = candidatesView.getChildAt(0) as? LinearLayout
        if (candidatesRow == null) {
            rebuildCandidateViews(wordList)
            return
        }
        
        val existingCount = candidatesRow.childCount
        val newCount = wordList.size
        
        Timber.d("🔄 复用视图更新: 现有${existingCount}个，新增${newCount}个")
        
        // 更新现有的TextView
        for (i in 0 until minOf(existingCount, newCount)) {
            val textView = candidatesRow.getChildAt(i) as? TextView
            if (textView != null) {
                val word = wordList[i]
                textView.text = word.word
                
                // 更新颜色
                if (i == 0) {
                    textView.setTextColor(Color.parseColor("#2196F3")) // 第一个候选词蓝色
                } else {
                    textView.setTextColor(Color.parseColor("#333333")) // 其他候选词深灰色
                }
                
                // 更新点击事件
                textView.setOnClickListener {
                    commitText(word.word)
                }
            }
        }
        
        // 如果新候选词更多，添加新的TextView
        if (newCount > existingCount) {
            for (i in existingCount until newCount) {
                val word = wordList[i]
                val candidateText = createCandidateTextView(word, i)
                candidatesRow.addView(candidateText)
            }
        }
        
        // 如果新候选词更少，移除多余的TextView
        if (newCount < existingCount) {
            candidatesRow.removeViews(newCount, existingCount - newCount)
        }
    }
    
    /**
     * 🎯 重建候选词视图（简化版，确保可靠显示）
     */
    private fun rebuildCandidateViews(wordList: List<WordFrequency>) {
        try {
            Timber.d("🔧 开始重建候选词视图: ${wordList.size}个")
            
            // 清空现有内容
            candidatesView.removeAllViews()
            
            if (wordList.isEmpty()) {
                // 显示无结果提示
                val hintText = createNoResultsHintView()
                candidatesView.addView(hintText)
                Timber.d("🔧 显示无结果提示")
                return
            }
            
            // 创建候选词行
            val candidatesRow = LinearLayout(this)
            candidatesRow.orientation = LinearLayout.HORIZONTAL
            candidatesRow.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            candidatesRow.gravity = Gravity.CENTER_VERTICAL
            
            // 添加候选词
            wordList.forEachIndexed { index, word ->
                try {
                    val candidateText = createCandidateTextView(word, index)
                    candidatesRow.addView(candidateText)
                    Timber.d("🔧 添加候选词[$index]: ${word.word}")
                } catch (e: Exception) {
                    Timber.e(e, "创建候选词[$index]失败: ${word.word}")
                }
            }
            
            // 添加到容器
            candidatesView.addView(candidatesRow)
            
            // 重置滚动位置
            if (::candidatesViewLayout.isInitialized) {
                candidatesViewLayout.findViewById<HorizontalScrollView>(R.id.candidates_scroll_view)?.scrollTo(0, 0)
            }
            
            Timber.d("🔧 重建完成: ${wordList.size}个候选词")
            
        } catch (e: Exception) {
            Timber.e(e, "重建候选词视图失败: ${e.message}")
            // 最后的回退
            try {
                candidatesView.removeAllViews()
                val errorText = TextView(this)
                errorText.text = "重建失败"
                errorText.setTextColor(android.graphics.Color.RED)
                candidatesView.addView(errorText)
            } catch (ex: Exception) {
                Timber.e(ex, "连错误提示都显示失败")
            }
        }
    }
    
    /**
     * 🎯 创建候选词TextView
     */
    private fun createCandidateTextView(word: WordFrequency, index: Int): TextView {
        val candidateText = TextView(this)
        candidateText.text = word.word
        candidateText.gravity = Gravity.CENTER
        
        // 设置颜色
        if (index == 0) {
            candidateText.setTextColor(Color.parseColor("#2196F3")) // 第一个候选词蓝色
        } else {
            candidateText.setTextColor(Color.parseColor("#333333")) // 其他候选词深灰色
        }
        
        candidateText.setBackgroundColor(Color.TRANSPARENT)
        candidateText.setTextSize(16f)
        candidateText.typeface = android.graphics.Typeface.DEFAULT
        candidateText.setPadding(12, 8, 12, 8)
        
        val textParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        textParams.gravity = Gravity.CENTER_VERTICAL
        
        if (index == 0) {
            textParams.setMargins(0, 4, 4, 4)
        } else {
            textParams.setMargins(4, 4, 4, 4)
        }
        
        candidateText.layoutParams = textParams
        candidateText.setOnClickListener {
            commitText(word.word)
        }
        
        return candidateText
    }
    
    /**
     * 🎯 创建无结果提示视图
     */
    private fun createNoResultsHintView(): TextView {
        val hintText = TextView(this)
        hintText.text = "请输入正确拼音"
        hintText.setTextColor(android.graphics.Color.parseColor("#999999"))
        hintText.setTextSize(14f)
        hintText.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        hintText.typeface = android.graphics.Typeface.DEFAULT
        
        val hintParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        hintParams.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        hintParams.setMargins(0, 0, 0, 0)
        hintText.layoutParams = hintParams
        
        return hintText
    }
    
    /**
     * 🎯 直接显示候选词（最简单版本）
     */
    private fun displayCandidatesDirectly(wordList: List<WordFrequency>) {
        try {
            candidatesView.removeAllViews()
            
            val candidatesRow = LinearLayout(this)
            candidatesRow.orientation = LinearLayout.HORIZONTAL
            candidatesRow.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            candidatesRow.gravity = Gravity.CENTER_VERTICAL
            
            wordList.forEachIndexed { index, word ->
                val candidateText = TextView(this)
                candidateText.text = word.word
                candidateText.gravity = Gravity.CENTER
                
                if (index == 0) {
                    candidateText.setTextColor(Color.parseColor("#2196F3"))
                } else {
                    candidateText.setTextColor(Color.parseColor("#333333"))
                }
                
                candidateText.setBackgroundColor(Color.TRANSPARENT)
                candidateText.setTextSize(16f)
                candidateText.setPadding(12, 8, 12, 8)
                
                val textParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                textParams.gravity = Gravity.CENTER_VERTICAL
                textParams.setMargins(if (index == 0) 0 else 4, 4, 4, 4)
                candidateText.layoutParams = textParams
                
                candidateText.setOnClickListener {
                    commitText(word.word)
                }
                
                candidatesRow.addView(candidateText)
            }
            
            candidatesView.addView(candidatesRow)
            
            // 重置滚动位置
            if (::candidatesViewLayout.isInitialized) {
                candidatesViewLayout.findViewById<HorizontalScrollView>(R.id.candidates_scroll_view)?.scrollTo(0, 0)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "直接显示候选词失败: ${e.message}")
        }
    }
    
    /**
     * 🔧 增强版直接显示候选词（带重试和验证机制）
     */
    private fun displayCandidatesDirectlyEnhanced(wordList: List<WordFrequency>) {
        try {
            Timber.d("🔧 开始增强显示候选词: ${wordList.size}个")
            
            // 🔧 第一步：确保视图状态正确
            if (!areViewComponentsInitialized()) {
                Timber.e("🔧 视图未初始化，无法显示候选词")
                return
            }
            
            // 🔧 第二步：强制确保候选词区域可见
            defaultCandidatesView.visibility = View.VISIBLE
            toolbarView.visibility = View.GONE
            defaultCandidatesView.setBackgroundColor(android.graphics.Color.parseColor("#F8F8F8"))
            
            // 🔧 第三步：清空现有内容
            candidatesView.removeAllViews()
            
            // 🔧 第四步：创建候选词行
            val candidatesRow = LinearLayout(this)
            candidatesRow.orientation = LinearLayout.HORIZONTAL
            candidatesRow.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            candidatesRow.gravity = Gravity.CENTER_VERTICAL
            
            // 🔧 第五步：添加候选词，带异常保护
            var successCount = 0
            wordList.forEachIndexed { index, word ->
                try {
                    val candidateText = TextView(this)
                    candidateText.text = word.word
                    candidateText.gravity = Gravity.CENTER
                    
                    if (index == 0) {
                        candidateText.setTextColor(Color.parseColor("#2196F3"))
                    } else {
                        candidateText.setTextColor(Color.parseColor("#333333"))
                    }
                    
                    candidateText.setBackgroundColor(Color.TRANSPARENT)
                    candidateText.setTextSize(16f)
                    candidateText.setPadding(12, 8, 12, 8)
                    
                    val textParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                    textParams.gravity = Gravity.CENTER_VERTICAL
                    textParams.setMargins(if (index == 0) 0 else 4, 4, 4, 4)
                    candidateText.layoutParams = textParams
                    
                    candidateText.setOnClickListener {
                        commitText(word.word)
                    }
                    
                    candidatesRow.addView(candidateText)
                    successCount++
                    
                } catch (e: Exception) {
                    Timber.e(e, "创建候选词[$index]失败: ${word.word}")
                }
            }
            
            // 🔧 第六步：添加到容器
            candidatesView.addView(candidatesRow)
            
            // 🔧 第七步：强制刷新UI
            candidatesView.invalidate()
            candidatesView.requestLayout()
            defaultCandidatesView.invalidate()
            defaultCandidatesView.requestLayout()
            
            // 🔧 第八步：重置滚动位置
            if (::candidatesViewLayout.isInitialized) {
                candidatesViewLayout.findViewById<HorizontalScrollView>(R.id.candidates_scroll_view)?.scrollTo(0, 0)
            }
            
            Timber.d("🔧 增强显示完成: 成功显示${successCount}/${wordList.size}个候选词")
            
            // 🔧 第九步：验证显示结果
            if (successCount == 0) {
                Timber.e("🔧 所有候选词显示失败，显示错误提示")
                displayErrorDirectly("显示失败")
            } else if (successCount < wordList.size) {
                Timber.w("🔧 部分候选词显示失败: ${successCount}/${wordList.size}")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "🔧 增强显示候选词失败: ${e.message}")
            
            // 🔧 回退到基础显示方法
            try {
                Timber.d("🔧 尝试回退到基础显示方法")
                displayCandidatesDirectly(wordList)
            } catch (ex: Exception) {
                Timber.e(ex, "🔧 回退显示也失败")
                // 最后的回退：显示错误信息
                try {
                    candidatesView.removeAllViews()
                    val errorText = TextView(this)
                    errorText.text = "显示异常"
                    errorText.setTextColor(android.graphics.Color.RED)
                    errorText.setTextSize(14f)
                    errorText.setPadding(12, 8, 12, 8)
                    candidatesView.addView(errorText)
                } catch (exx: Exception) {
                    Timber.e(exx, "连错误提示都无法显示")
                }
            }
        }
    }
    
    /**
     * 🎯 直接显示无结果提示
     */
    private fun displayNoResultsDirectly() {
        try {
            candidatesView.removeAllViews()
            val hintText = createNoResultsHintView()
            candidatesView.addView(hintText)
        } catch (e: Exception) {
            Timber.e(e, "显示无结果提示失败: ${e.message}")
        }
    }
    
    /**
     * 🎯 直接显示错误信息
     */
    private fun displayErrorDirectly(message: String) {
        try {
            candidatesView.removeAllViews()
            val errorText = TextView(this)
            errorText.text = message
            errorText.setTextColor(android.graphics.Color.RED)
            errorText.setTextSize(14f)
            errorText.setPadding(12, 8, 12, 8)
            candidatesView.addView(errorText)
        } catch (e: Exception) {
            Timber.e(e, "显示错误信息失败: ${e.message}")
        }
    }

    // 🎯 新增：平滑更新候选词视图（避免闪烁）
    private fun updateCandidatesViewSmooth(wordList: List<WordFrequency>) {
        Timber.d("🎨 updateCandidatesViewSmooth 开始，候选词数量: ${wordList.size}")
        
        if (!areViewComponentsInitialized()) {
            Timber.e("🎨 视图组件未初始化，无法更新候选词")
            return
        }
        
        try {
            // 🎯 关键：先清空现有内容，然后立即添加新内容，减少闪烁时间
            candidatesView.removeAllViews()
            
            // 强制设置候选词容器可见性和固定背景
            defaultCandidatesView.visibility = View.VISIBLE
            toolbarView.visibility = View.GONE
            defaultCandidatesView.setBackgroundColor(android.graphics.Color.parseColor("#F8F8F8")) // 保持固定背景色
            
            // 🔧 修复：使用像素值设置固定高度，防止跳动
            val density = resources.displayMetrics.density
            val heightInPx = (46 * density).toInt()
            
            val params = defaultCandidatesView.layoutParams
            params.height = heightInPx
            params.width = LinearLayout.LayoutParams.MATCH_PARENT
            defaultCandidatesView.layoutParams = params
            
            // 🔧 确保candidatesView也有正确的高度
            val candidatesParams = candidatesView.layoutParams
            candidatesParams.height = heightInPx
            candidatesView.layoutParams = candidatesParams
            
            if (wordList.isEmpty()) {
                Timber.d("🎨 没有候选词，显示提示文本")
                showNoResultsHintSmooth()
                return
            }
            
            Timber.d("🎨 开始更新候选词视图，数量: ${wordList.size}")
            
            // 创建一个水平的LinearLayout来放置候选词
            val candidatesRow = LinearLayout(this)
            candidatesRow.orientation = LinearLayout.HORIZONTAL
            candidatesRow.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                heightInPx // 使用像素高度
            )
            candidatesRow.gravity = Gravity.CENTER_VERTICAL
            
            // 添加每个候选词按钮到水平布局
            wordList.forEachIndexed { index, word ->
                val candidateText = TextView(this)
                candidateText.text = word.word
                candidateText.gravity = Gravity.CENTER
                
                // 🎯 新样式：去掉背景色框，默认黑色文字，第一个候选词蓝色
                if (index == 0) {
                    candidateText.setTextColor(Color.parseColor("#2196F3")) // 第一个候选词蓝色
                } else {
                    candidateText.setTextColor(Color.parseColor("#333333")) // 其他候选词深灰色（接近黑色）
                }
                
                // 🎯 去掉背景色，设置透明背景
                candidateText.setBackgroundColor(Color.TRANSPARENT)
                
                candidateText.setTextSize(16f)
                candidateText.typeface = android.graphics.Typeface.DEFAULT // 去掉加粗
                candidateText.setPadding(12, 8, 12, 8) // 减少内边距
                
                val textParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    heightInPx // 使用像素高度
                )
                textParams.gravity = Gravity.CENTER_VERTICAL
                
                if (index == 0) {
                    // 🎯 修复：第一个候选词完全左对齐，不添加额外margin
                    textParams.setMargins(0, 4, 4, 4)
                } else {
                    textParams.setMargins(4, 4, 4, 4)
                }
                
                candidateText.layoutParams = textParams
                candidateText.setOnClickListener {
                    commitText(word.word)
                }
                
                candidatesRow.addView(candidateText)
            }
            
            // 将候选词行添加到候选词视图
            candidatesView.addView(candidatesRow)
            
            // 重置水平滚动位置到起始位置
            if (::candidatesViewLayout.isInitialized) {
                candidatesViewLayout.findViewById<HorizontalScrollView>(R.id.candidates_scroll_view)?.scrollTo(0, 0)
            }
            
            Timber.d("🎨 平滑更新候选词完成，使用像素高度: ${heightInPx}px，第一个候选词左对齐")
            
        } catch (e: Exception) {
            Timber.e(e, "平滑更新候选词视图失败: ${e.message}")
        }
    }
    
    // 🎯 新增：平滑显示无结果提示（避免闪烁）
    private fun showNoResultsHintSmooth() {
        if (!areViewComponentsInitialized()) {
            return
        }
        
        try {
            // 🎯 先创建提示内容，再替换，减少空白时间
            val hintText = createNoResultsHintView()
            
            // 🎯 原子操作：快速替换内容
            candidatesView.removeAllViews()
            candidatesView.addView(hintText)
            
            Timber.d("🎨 平滑显示无结果提示")
            
        } catch (e: Exception) {
            Timber.e(e, "平滑显示无结果提示失败: ${e.message}")
        }
    }
    
    // 更新候选词视图
    private fun updateCandidatesView(wordList: List<WordFrequency>) {
        Timber.d("🎨 updateCandidatesView 开始，候选词数量: ${wordList.size}")
        
        if (!areViewComponentsInitialized()) {
            Timber.e("🎨 视图组件未初始化，无法更新候选词")
            return
        }
        
        try {
            // 清空现有的候选词
            candidatesView.removeAllViews()
            Timber.d("🎨 已清空现有候选词")
            
            // 强制设置候选词容器可见性
            defaultCandidatesView.visibility = View.VISIBLE
            toolbarView.visibility = View.GONE
            
            // 设置候选词视图背景为透明
            defaultCandidatesView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // 🔧 确保候选词视图固定高度，防止跳动
            val params = defaultCandidatesView.layoutParams
            params.height = LinearLayout.LayoutParams.MATCH_PARENT // 使用MATCH_PARENT填满可用空间
            params.width = LinearLayout.LayoutParams.MATCH_PARENT // 固定宽度
            defaultCandidatesView.layoutParams = params
            
            // 如果没有候选词，显示提示信息
            if (wordList.isEmpty()) {
                Timber.d("🎨 没有候选词，显示提示文本")
                showNoResultsHint()
                return
            }
            
            Timber.d("🎨 开始更新候选词视图，数量: ${wordList.size}")
            Timber.d("🎨 候选词列表: ${wordList.take(5).map { "${it.word}(${it.frequency})" }}")
            
            Timber.d("🎨 设置容器可见性完成，背景色已设置为透明")
            
            // 获取拼音显示区域的左边距，确保候选词与拼音完全对齐
            val pinyinPaddingStart = pinyinDisplay.paddingStart
            
            // 设置候选词容器内边距与拼音区域一致
            candidatesView.setPadding(0, candidatesView.paddingTop, candidatesView.paddingRight, candidatesView.paddingBottom)
            
            // 创建一个水平的LinearLayout来放置候选词
            val candidatesRow = LinearLayout(this)
            candidatesRow.orientation = LinearLayout.HORIZONTAL
            candidatesRow.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT // 使用MATCH_PARENT填满可用空间
            )
            candidatesRow.gravity = Gravity.CENTER_VERTICAL // 垂直居中
            
            // 添加每个候选词按钮到水平布局
            Timber.d("🎨 开始创建${wordList.size}个候选词TextView")
            wordList.forEachIndexed { index, word ->
                Timber.d("🎨 创建候选词[$index]: ${word.word}")
                
                val candidateText = TextView(this)
                
                // 显示候选词文本
                candidateText.text = word.word
                
                // 使用TextView而不是Button以减少默认样式的影响
                candidateText.gravity = Gravity.CENTER // 水平和垂直居中
                
                // 🎯 新样式：去掉背景色框，默认黑色文字，第一个候选词蓝色
                if (index == 0) {
                    candidateText.setTextColor(Color.parseColor("#2196F3")) // 第一个候选词蓝色
                } else {
                    candidateText.setTextColor(Color.parseColor("#333333")) // 其他候选词深灰色（接近黑色）
                }
                
                // 🎯 去掉背景色，设置透明背景
                candidateText.setBackgroundColor(Color.TRANSPARENT)
                
                // 设置文字大小和样式
                candidateText.setTextSize(16f) // 适中字体
                candidateText.typeface = android.graphics.Typeface.DEFAULT // 去掉加粗
                
                // 减少内边距
                candidateText.setPadding(12, 8, 12, 8)
                
                // 🔧 设置固定布局参数，防止跳动
                val textParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT // 使用MATCH_PARENT填满容器高度
                )
                textParams.gravity = Gravity.CENTER_VERTICAL // 垂直居中
                
                if (index == 0) {
                    // 🎯 修复：第一个候选词完全左对齐，不添加额外margin
                    textParams.setMargins(0, 4, 4, 4)
                } else {
                    // 其他候选词间距
                    textParams.setMargins(4, 4, 4, 4)
                }
                
                candidateText.layoutParams = textParams
                
                // 设置点击事件
                candidateText.setOnClickListener {
                    commitText(word.word)
                }
                
                // 添加到候选词行
                candidatesRow.addView(candidateText)
                Timber.d("🎨 候选词[$index]已添加到行中: '${word.word}', 文字颜色: ${candidateText.currentTextColor}, 背景: ${candidateText.background}")
            }
            
            Timber.d("🎨 所有候选词TextView创建完成")
            
            // 将候选词行添加到候选词视图
            candidatesView.addView(candidatesRow)
            Timber.d("🎨 候选词行已添加到candidatesView")
            
            // 重置水平滚动位置到起始位置
            if (::candidatesViewLayout.isInitialized) {
            candidatesViewLayout.findViewById<HorizontalScrollView>(R.id.candidates_scroll_view)?.scrollTo(0, 0)
        }
            Timber.d("🎨 滚动位置已重置")
            
            // 强制刷新UI
            candidatesContainer.invalidate()
            candidatesContainer.requestLayout()
            defaultCandidatesView.invalidate()
            defaultCandidatesView.requestLayout()
            candidatesView.invalidate()
            candidatesView.requestLayout()
            Timber.d("🎨 UI刷新完成")
            
            // 记录日志，确认候选词视图状态
            Timber.d("🎨 最终状态检查:")
            Timber.d("🎨 候选词容器可见性: ${this.candidatesContainer.visibility == View.VISIBLE}")
            Timber.d("🎨 默认候选词视图可见性: ${defaultCandidatesView.visibility == View.VISIBLE}")
            Timber.d("🎨 候选词视图子项数量: ${candidatesView.childCount}")
            Timber.d("🎨 候选词行子项数量: ${candidatesRow.childCount}")
            
            // 记录详细的候选词视图状态
            logCandidateViewState()
            
        } catch (e: Exception) {
            Timber.e(e, "更新候选词视图失败: ${e.message}")
        }
    }
    
    // 清空候选词视图
    private fun clearCandidatesView() {
        if (areViewComponentsInitialized()) {
            candidatesView.removeAllViews()
        }
    }
    
    /**
     * 显示无结果提示
     * 当查询不到候选词时，显示"请输入正确拼音"提示
     */
    private fun showNoResultsHint() {
        if (!areViewComponentsInitialized()) {
            return
        }
        
        try {
            // 获取拼音显示区域的左边距，确保提示文本与拼音完全对齐
            val pinyinPaddingStart = pinyinDisplay.paddingStart
            
            // 创建提示文本
            val hintText = TextView(this)
            hintText.text = "请输入正确拼音"
            hintText.setTextColor(android.graphics.Color.parseColor("#999999")) // 灰色文字
            hintText.setTextSize(14f) // 稍小的字体
            hintText.gravity = Gravity.CENTER_VERTICAL or Gravity.START // 左对齐，垂直居中
            hintText.typeface = android.graphics.Typeface.DEFAULT // 普通字体
            
            // 设置布局参数，与拼音左对齐
            val hintParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            hintParams.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            hintParams.setMargins(pinyinPaddingStart, 0, 0, 0) // 与拼音左对齐
            hintText.layoutParams = hintParams
            
            // 添加到候选词视图
            candidatesView.addView(hintText)
            
            // 强制刷新UI
            candidatesView.invalidate()
            candidatesView.requestLayout()
            
            Timber.d("🎨 显示无结果提示: '请输入正确拼音'")
            
        } catch (e: Exception) {
            Timber.e(e, "显示无结果提示失败: ${e.message}")
        }
    }
    
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        
        // 🔧 关键修复：自愈式Trie检查，永不阻塞用户
        val dictFile = getDictionaryFile()
        val databaseExists = dictFile.exists()
        val databaseSize = if (databaseExists) dictFile.length() else 0L
        
        when {
            !databaseExists -> {
                Timber.d("🆕 数据库不存在，启动后台自愈")
                performAsyncSelfHealing()
            }
            databaseSize > 1024 * 1024 -> {
                Timber.d("✅ 覆盖安装场景，检查Trie状态")
                // 覆盖安装：数据库完整，但可能需要重建Trie
                performAsyncTrieCheck()
            }
            else -> {
                Timber.d("⚠️ 数据库文件异常，启动后台自愈")
                performAsyncSelfHealing()
            }
        }
        
        // 获取并显示当前应用名称
        if (::appNameDisplay.isInitialized) {
            val packageName = info?.packageName ?: ""
            appNameDisplay.text = getAppNameFromPackage(packageName)
            Timber.d("当前应用: ${appNameDisplay.text}")
        }
        
        // 清空初始化状态，确保没有硬编码的"w"等字符
        composingText.clear()
        updatePinyinDisplay("")
        clearCandidatesView()
        candidates = emptyList()
        justCommittedText = false
        
        // 🔧 重置键盘模式为默认拼音输入键盘
        if (currentKeyboardMode != KeyboardMode.QWERTY) {
            Timber.d("🔄 重置键盘模式为默认拼音输入键盘")
            try {
                switchToQwertyKeyboard()
            } catch (e: Exception) {
                Timber.e(e, "重置键盘模式失败: ${e.message}")
                // 如果切换失败，至少重置状态变量
                currentKeyboardMode = KeyboardMode.QWERTY
                currentSymbolPage = SymbolPage.CHINESE
            }
        }
        
        // 确保输入连接上的组合文本也被清除
        currentInputConnection?.finishComposingText()
        
        // 确保候选词视图正确初始化
        if (areViewComponentsInitialized()) {
            // 🔧 设置候选词视图布局参数，使用MATCH_PARENT确保有足够空间
            val params = defaultCandidatesView.layoutParams
            params.height = LinearLayout.LayoutParams.MATCH_PARENT // 使用MATCH_PARENT确保有足够空间
            params.width = LinearLayout.LayoutParams.MATCH_PARENT // 固定宽度
            defaultCandidatesView.layoutParams = params
            
            // 初始状态：显示工具栏，隐藏候选词
            toolbarView.visibility = View.VISIBLE
            defaultCandidatesView.visibility = View.GONE
            
            // 记录候选词视图状态
            logCandidateViewState()
        }
        
        Timber.d("🎯 初始化输入视图完成（整合模式），已清空所有状态")
    }
    
    override fun onInitializeInterface() {
        super.onInitializeInterface()
        // 初始化时确保状态清空
        composingText.clear()
        candidates = emptyList()
        justCommittedText = false
        Timber.d("输入法接口初始化，清空所有状态")
    }
    
    // 获取应用名称和图标
    private fun getAppNameFromPackage(packageName: String): String {
        if (packageName.isEmpty()) return ""
        
        val packageManager = packageManager
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            
            // 设置应用图标
            try {
                val appIcon = packageManager.getApplicationIcon(packageName)
                if (::appIcon.isInitialized) {
                    this.appIcon.setImageDrawable(appIcon)
                }
            } catch (e: Exception) {
                Timber.e(e, "获取应用图标失败")
                // 如果获取失败，使用默认图标
                if (::appIcon.isInitialized) {
                    this.appIcon.setImageResource(android.R.drawable.ic_menu_info_details)
                }
            }
            
            return "${appName}插件已加持"
        } catch (e: Exception) {
            Timber.e(e, "获取应用信息失败")
            // 设置默认图标
            if (::appIcon.isInitialized) {
                this.appIcon.setImageResource(android.R.drawable.ic_menu_info_details)
            }
            return "插件已加持"
        }
    }
    
    /**
     * 🛠️ 异步自愈（数据库问题时）
     */
    private fun performAsyncSelfHealing() {
        Timber.d("🛠️ 启动异步自愈流程...")
        startSelfHealingMode()
    }
    
    /**
     * 🔧 异步Trie检查（覆盖安装时）
     */
    private fun performAsyncTrieCheck() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                Timber.d("🔧 开始异步Trie检查...")
                
                if (!checkAppInitializationStatus()) {
                    Timber.d("🎯 应用未完全初始化，等待后再检查")
                    delay(2000) // 等待2秒
                    if (!checkAppInitializationStatus()) {
                        Timber.w("应用初始化超时，跳过Trie检查")
                        return@launch
                    }
                }
                
                val trieStatus = checkTrieMemoryStatus()
                
                if (trieStatus.needsReload) {
                    Timber.i("🔄 检测到Trie需要重建: $trieStatus")
                    startAsyncTrieOptimization(trieStatus)
                } else {
                    Timber.d("✅ Trie状态正常: $trieStatus")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "异步Trie检查失败: ${e.message}")
            }
        }
    }
    
    /**
     * 异步确保Trie词典可用（不阻塞输入法使用）
     * 覆盖安装后的优雅处理：
     * 1. 输入法立即可用（通过数据库查询）
     * 2. 后台异步重新加载Trie内存
     * 3. 加载完成后自动切换到高性能Trie查询
     */
    private fun ensureTrieLoadedAsync() {
        try {
            val trieManager = ShenjiApplication.trieManager
            
            // 检查核心词典状态
            val charsLoaded = trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieType.CHARS)
            val baseLoaded = trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieType.BASE)
            
            if (!charsLoaded || !baseLoaded) {
                Timber.i("🔄 检测到Trie内存未加载，启动后台重建...")
                Timber.i("📊 当前状态 - CHARS: ${if (charsLoaded) "✓" else "✗"}, BASE: ${if (baseLoaded) "✓" else "✗"}")
                
                // 🚀 关键：不阻塞输入法，在后台异步加载
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        Timber.i("🔧 开始后台重建Trie内存...")
                        val startTime = System.currentTimeMillis()
                        
                        // 优先加载CHARS词典（单字查询）
                        if (!charsLoaded) {
                            Timber.d("🔧 重建CHARS词典...")
                            val charsSuccess = trieManager.loadTrieToMemory(com.shenji.aikeyboard.data.trie.TrieType.CHARS)
                            if (charsSuccess) {
                                Timber.i("✅ CHARS词典重建成功")
                            } else {
                                Timber.e("❌ CHARS词典重建失败")
                            }
                        }
                        
                        // 然后加载BASE词典（词组查询）
                        if (!baseLoaded) {
                            Timber.d("🔧 重建BASE词典...")
                            val baseSuccess = trieManager.loadTrieToMemory(com.shenji.aikeyboard.data.trie.TrieType.BASE)
                            if (baseSuccess) {
                                Timber.i("✅ BASE词典重建成功")
                            } else {
                                Timber.e("❌ BASE词典重建失败")
                            }
                        }
                        
                        val endTime = System.currentTimeMillis()
                        val loadedTypes = trieManager.getLoadedTrieTypes()
                        Timber.i("🎉 Trie内存重建完成，耗时${endTime - startTime}ms")
                        Timber.i("📚 已加载词典: ${loadedTypes.map { getTrieDisplayName(it) }}")
                        
                        // 重建完成后，可以选择性地预热引擎
                        try {
                            val engineAdapter = InputMethodEngineAdapter.getInstance()
                            val testResults = engineAdapter.getCandidates("ni", 3)
                            if (testResults.isNotEmpty()) {
                                Timber.i("🔥 Trie重建后引擎测试成功: ${testResults.size}个结果")
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Trie重建后引擎测试失败")
                        }
                        
                    } catch (e: Exception) {
                        Timber.e(e, "❌ 后台Trie重建失败: ${e.message}")
                    }
                }
            } else {
                Timber.d("✅ Trie内存状态正常，无需重建")
            }
        } catch (e: Exception) {
            Timber.e(e, "检查Trie状态失败: ${e.message}")
        }
    }
    
}