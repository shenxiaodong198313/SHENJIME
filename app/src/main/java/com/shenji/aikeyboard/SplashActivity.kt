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
import kotlinx.coroutines.GlobalScope
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
                updateProgress(10, "检查运行环境", "正在检查系统兼容性...")
                delay(300) // 减少延迟
                
                // 步骤2: 检查内存状态
                updateProgress(20, "检查内存状态", "正在检查可用内存...")
                checkMemoryStatus()
                delay(300) // 减少延迟
                
                // 步骤3: 初始化数据库
                updateProgress(40, "初始化数据库", "正在连接Realm数据库...")
                initializeDatabase()
                delay(300) // 减少延迟
                
                // 步骤4: 检查Trie文件状态
                updateProgress(60, "检查词典文件", "正在扫描预编译词典文件...")
                checkTrieFiles()
                delay(300) // 减少延迟
                
                // 步骤5: 快速加载核心词典
                updateProgress(80, "加载核心词典", "正在加载单字词典...")
                loadCriticalTries()
                delay(300) // 减少延迟
                
                // 步骤6: 完成初始化
                updateProgress(100, "启动完成", "欢迎使用神机AI键盘！")
                delay(500) // 减少延迟
                
                // 快速跳转到主Activity
                navigateToMainActivity()
                
                // 启动后台异步加载其他词典
                // 注意：后台加载已移至MainActivity，避免重复加载
                Timber.i("启动页完成，后台词典加载将在主界面启动")
                
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
                // 检查是否需要从assets复制数据库
                val app = application as ShenjiApplication
                val dictFile = java.io.File(app.filesDir, "dictionaries/shenji_dict.realm")
                
                // 检查assets中是否有预构建的数据库文件
                val hasAssetsDb = try {
                    app.assets.open("shenji_dict.realm").use { true }
                } catch (e: Exception) {
                    false
                }
                
                if (hasAssetsDb && (!dictFile.exists() || dictFile.length() < 1000)) {
                    withContext(Dispatchers.Main) {
                        detailText.text = "发现预构建数据库，正在复制..."
                    }
                    
                    // 获取文件大小信息
                    val totalSize = app.assets.open("shenji_dict.realm").use { it.available().toLong() }
                    val totalSizeMB = totalSize / (1024 * 1024)
                    
                    withContext(Dispatchers.Main) {
                        detailText.text = "正在复制数据库文件 (${totalSizeMB}MB)..."
                    }
                    
                    // 初始化Realm数据库（这会触发复制）
                    app.initRealm()
                    
                    withContext(Dispatchers.Main) {
                        detailText.text = "数据库复制完成 ✓"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        detailText.text = "正在连接数据库..."
                    }
                    
                    // 初始化Realm数据库
                    app.initRealm()
                    
                    withContext(Dispatchers.Main) {
                        detailText.text = "数据库连接成功 ✓"
                    }
                }
                
                // 初始化词典管理器
                DictionaryManager.init()
                
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
     * 加载关键词典（启动时只加载chars）
     */
    private suspend fun loadCriticalTries() {
        withContext(Dispatchers.IO) {
            val trieManager = TrieManager.instance
            
            // 初始化TrieManager
            trieManager.init()
            
            // 只加载单字词典，确保基本输入功能
            try {
                if (trieManager.isTrieFileExists(TrieBuilder.TrieType.CHARS) && 
                    !trieManager.isTrieLoaded(TrieBuilder.TrieType.CHARS)) {
                    val success = trieManager.loadTrieToMemory(TrieBuilder.TrieType.CHARS)
                    if (success) {
                        withContext(Dispatchers.Main) {
                            detailText.text = "单字词典加载完成 ✓"
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            detailText.text = "单字词典加载失败，将使用数据库查询"
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        detailText.text = "单字词典已就绪 ✓"
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "加载单字词典失败")
                withContext(Dispatchers.Main) {
                    detailText.text = "单字词典加载异常，将使用数据库查询"
                }
            }
            
            // 启动后台异步加载其他词典
            // 注意：后台加载已移至MainActivity，避免重复加载
            Timber.i("启动页完成，后台词典加载将在主界面启动")
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