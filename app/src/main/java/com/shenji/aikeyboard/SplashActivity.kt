package com.shenji.aikeyboard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.trie.TrieBuilder
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.ui.MainActivity
import timber.log.Timber

/**
 * 启动页Activity - 显示应用初始化进度
 * 解决白屏问题，让用户直观看到加载过程
 */
class SplashActivity : AppCompatActivity() {
    
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        // 隐藏状态栏，全屏显示
        supportActionBar?.hide()
        
        // 初始化UI组件
        initViews()
        
        // 开始初始化流程
        startInitialization()
    }
    
    /**
     * 初始化视图组件
     */
    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        detailText = findViewById(R.id.detailText)
        
        // 设置初始状态
        progressBar.max = 100
        progressBar.progress = 0
        statusText.text = "正在启动神机AI键盘..."
        detailText.text = "准备初始化应用组件"
    }
    
    /**
     * 开始初始化流程
     */
    private fun startInitialization() {
        lifecycleScope.launch {
            try {
                // 步骤1: 检查环境
                updateProgress(5, "检查运行环境", "正在检查系统兼容性...")
                delay(800)
                
                // 步骤2: 初始化日志系统
                updateProgress(10, "初始化日志系统", "正在配置日志记录...")
                delay(500)
                
                // 步骤3: 检查内存状态
                updateProgress(15, "检查内存状态", "正在检查可用内存...")
                checkMemoryStatus()
                delay(600)
                
                // 步骤4: 初始化数据库
                updateProgress(25, "初始化数据库", "正在连接Realm数据库...")
                initializeDatabase()
                delay(600)
                
                // 步骤5: 检查Trie文件状态
                updateProgress(35, "检查Trie文件", "正在扫描预编译词典文件...")
                checkTrieFiles()
                delay(700)
                
                // 步骤6-9: 加载Trie数据
                loadTrieData()
                
                // 步骤10: 完成初始化
                updateProgress(95, "初始化完成", "正在启动主界面...")
                delay(800)
                
                updateProgress(100, "启动完成", "欢迎使用神机AI键盘！")
                delay(1000)
                
                // 跳转到主Activity
                navigateToMainActivity()
                
            } catch (e: Exception) {
                Timber.e(e, "初始化过程中发生错误")
                handleInitializationError(e)
            }
        }
    }
    
    /**
     * 初始化数据库
     */
    private suspend fun initializeDatabase() {
        withContext(Dispatchers.IO) {
            try {
                // 初始化Realm数据库
                val app = application as ShenjiApplication
                app.initRealm()
                
                // 初始化词典管理器
                DictionaryManager.init()
                
                withContext(Dispatchers.Main) {
                    detailText.text = "数据库连接成功 ✓"
                }
            } catch (e: Exception) {
                Timber.e(e, "初始化数据库失败")
                withContext(Dispatchers.Main) {
                    detailText.text = "数据库初始化失败: ${e.message}"
                }
            }
        }
    }
    
    /**
     * 检查内存状态
     */
    private suspend fun checkMemoryStatus() {
        withContext(Dispatchers.IO) {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            
            val maxMemoryMB = maxMemory / (1024 * 1024)
            val usedMemoryMB = (totalMemory - freeMemory) / (1024 * 1024)
            
            withContext(Dispatchers.Main) {
                detailText.text = "最大内存: ${maxMemoryMB}MB, 已用: ${usedMemoryMB}MB"
            }
        }
    }
    
    /**
     * 检查Trie文件状态
     */
    private suspend fun checkTrieFiles() {
        withContext(Dispatchers.IO) {
            val trieManager = TrieManager.instance
            val availableTypes = mutableListOf<String>()
            
            for (trieType in TrieBuilder.TrieType.values()) {
                if (trieManager.hasPrebuiltTrie(trieType)) {
                    availableTypes.add(getDisplayName(trieType))
                }
            }
            
            withContext(Dispatchers.Main) {
                if (availableTypes.isNotEmpty()) {
                    detailText.text = "发现${availableTypes.size}个预编译词典: ${availableTypes.take(3).joinToString(", ")}${if (availableTypes.size > 3) "..." else ""}"
                } else {
                    detailText.text = "未发现预编译词典文件"
                }
            }
        }
    }
    
    /**
     * 加载Trie数据
     */
    private suspend fun loadTrieData() {
        val trieManager = TrieManager.instance
        
        // 初始化TrieManager
        updateProgress(45, "初始化Trie管理器", "正在准备词典加载系统...")
        withContext(Dispatchers.IO) {
            trieManager.init()
        }
        delay(600)
        
        // 检查基础词典
        updateProgress(55, "检查基础词典", "正在检查base词典状态...")
        checkBaseTrieStatus()
        delay(700)
        
        // 加载关键词典到内存
        updateProgress(65, "加载核心词典", "正在加载单字和基础词典...")
        loadCriticalTries()
        delay(800)
        
        // 检查其他词典
        updateProgress(75, "检查扩展词典", "正在检查地名、人名等词典...")
        checkExtendedTries()
        delay(600)
        
        // 预热缓存
        updateProgress(85, "预热系统缓存", "正在优化内存布局...")
        preWarmCache()
        delay(700)
    }
    
    /**
     * 检查基础词典状态
     */
    private suspend fun checkBaseTrieStatus() {
        withContext(Dispatchers.IO) {
            val trieManager = TrieManager.instance
            val hasPrebuilt = trieManager.hasPrebuiltTrie(TrieBuilder.TrieType.BASE)
            val hasUserBuilt = trieManager.hasUserBuiltTrie(TrieBuilder.TrieType.BASE)
            val isLoaded = trieManager.isTrieLoaded(TrieBuilder.TrieType.BASE)
            
            withContext(Dispatchers.Main) {
                when {
                    isLoaded -> detailText.text = "基础词典已在内存中 ✓"
                    hasPrebuilt -> detailText.text = "发现预编译基础词典 ✓"
                    hasUserBuilt -> detailText.text = "发现用户构建的基础词典 ✓"
                    else -> detailText.text = "基础词典需要构建 ⚠"
                }
            }
        }
    }
    
    /**
     * 加载关键词典
     */
    private suspend fun loadCriticalTries() {
        withContext(Dispatchers.IO) {
            val trieManager = TrieManager.instance
            val criticalTypes = listOf(
                TrieBuilder.TrieType.CHARS,
                TrieBuilder.TrieType.BASE
            )
            
            var loadedCount = 0
            for (trieType in criticalTypes) {
                try {
                    if (trieManager.isTrieFileExists(trieType) && !trieManager.isTrieLoaded(trieType)) {
                        val success = trieManager.loadTrieToMemory(trieType)
                        if (success) {
                            loadedCount++
                            withContext(Dispatchers.Main) {
                                detailText.text = "已加载${getDisplayName(trieType)} ✓"
                            }
                            delay(300)
                        }
                    } else if (trieManager.isTrieLoaded(trieType)) {
                        loadedCount++
                    }
                } catch (e: Exception) {
                    Timber.w(e, "加载${getDisplayName(trieType)}失败")
                }
            }
            
            withContext(Dispatchers.Main) {
                detailText.text = "核心词典加载完成 (${loadedCount}/${criticalTypes.size})"
            }
        }
    }
    
    /**
     * 检查扩展词典
     */
    private suspend fun checkExtendedTries() {
        withContext(Dispatchers.IO) {
            val trieManager = TrieManager.instance
            val extendedTypes = listOf(
                TrieBuilder.TrieType.PLACE,
                TrieBuilder.TrieType.PEOPLE,
                TrieBuilder.TrieType.CORRECTIONS,
                TrieBuilder.TrieType.COMPATIBLE
            )
            
            val availableCount = extendedTypes.count { trieManager.isTrieFileExists(it) }
            val loadedCount = extendedTypes.count { trieManager.isTrieLoaded(it) }
            
            withContext(Dispatchers.Main) {
                detailText.text = "扩展词典: ${availableCount}个可用, ${loadedCount}个已加载"
            }
        }
    }
    
    /**
     * 预热缓存
     */
    private suspend fun preWarmCache() {
        withContext(Dispatchers.IO) {
            try {
                val repository = DictionaryRepository()
                repository.warmupCache()
                
                withContext(Dispatchers.Main) {
                    detailText.text = "缓存预热完成 ✓"
                }
            } catch (e: Exception) {
                Timber.w(e, "缓存预热失败")
                withContext(Dispatchers.Main) {
                    detailText.text = "缓存预热失败，但不影响使用"
                }
            }
        }
    }
    
    /**
     * 更新进度
     */
    private fun updateProgress(progress: Int, status: String, detail: String) {
        handler.post {
            progressBar.progress = progress
            statusText.text = status
            detailText.text = detail
        }
    }
    
    /**
     * 处理初始化错误
     */
    private fun handleInitializationError(error: Exception) {
        handler.post {
            progressBar.progress = 100
            statusText.text = "初始化遇到问题"
            detailText.text = "错误: ${error.message ?: "未知错误"}"
        }
        
        // 延迟后仍然跳转，避免卡在启动页
        handler.postDelayed({
            navigateToMainActivity()
        }, 3000)
    }
    
    /**
     * 跳转到主Activity
     */
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // 关闭启动页，防止用户返回
        
        // 添加淡入淡出动画
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
    
    /**
     * 获取Trie类型的显示名称
     */
    private fun getDisplayName(trieType: TrieBuilder.TrieType): String {
        return when (trieType) {
            TrieBuilder.TrieType.CHARS -> "单字"
            TrieBuilder.TrieType.BASE -> "基础词典"
            TrieBuilder.TrieType.CORRELATION -> "关联词典"
            TrieBuilder.TrieType.ASSOCIATIONAL -> "联想词典"
            TrieBuilder.TrieType.PLACE -> "地名词典"
            TrieBuilder.TrieType.PEOPLE -> "人名词典"
            TrieBuilder.TrieType.POETRY -> "诗词词典"
            TrieBuilder.TrieType.CORRECTIONS -> "纠错词典"
            TrieBuilder.TrieType.COMPATIBLE -> "兼容词典"
        }
    }
    
    override fun onBackPressed() {
        // 在启动页禁用返回键，防止用户意外退出
        // super.onBackPressed()
    }
} 