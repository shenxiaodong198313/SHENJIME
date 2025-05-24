package com.shenji.aikeyboard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.trie.TrieBuilder
import com.shenji.aikeyboard.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * 重构的启动页 - 分阶段内存优化启动流程
 * 
 * 启动阶段：
 * 1. 数据库初始化阶段 (0-40%)
 * 2. 内存清理阶段 (40-50%) 
 * 3. 核心词典加载阶段 (50-90%)
 * 4. 启动完成阶段 (90-100%)
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private val handler = Handler(Looper.getMainLooper())
    
    // 启动阶段枚举
    private enum class StartupPhase {
        DATABASE_INIT,      // 数据库初始化
        MEMORY_CLEANUP,     // 内存清理
        CORE_DICT_LOADING,  // 核心词典加载
        STARTUP_COMPLETE    // 启动完成
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        // 隐藏状态栏，全屏显示
        supportActionBar?.hide()
        
        initViews()
        startOptimizedInitialization()
    }
    
    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        detailText = findViewById(R.id.detailText)
        
        progressBar.max = 100
        progressBar.progress = 0
        statusText.text = "正在启动神机输入法..."
        detailText.text = "初始化预计需要10-20秒，正在准备数据库和词典..."
    }
    
    /**
     * 优化的分阶段初始化流程
     */
    private fun startOptimizedInitialization() {
        lifecycleScope.launch {
            try {
                // 阶段1: 数据库初始化 (0-40%)
                executePhase(StartupPhase.DATABASE_INIT, 0, 40) {
                    initializeDatabase()
                }
                
                // 阶段2: 内存清理 (40-50%)
                executePhase(StartupPhase.MEMORY_CLEANUP, 40, 50) {
                    performMemoryCleanup()
                }
                
                // 阶段3: 核心词典加载 (50-90%)
                executePhase(StartupPhase.CORE_DICT_LOADING, 50, 90) {
                    loadCoreCharsDictionary()
                }
                
                // 阶段4: 启动完成 (90-100%)
                executePhase(StartupPhase.STARTUP_COMPLETE, 90, 100) {
                    finalizeStartup()
                }
                
                // 跳转到主界面
                navigateToMainActivity()
                
            } catch (e: Exception) {
                Timber.e(e, "启动初始化失败")
                handleStartupError(e)
            }
        }
    }
    
    /**
     * 执行启动阶段
     */
    private suspend fun executePhase(
        phase: StartupPhase,
        startProgress: Int,
        endProgress: Int,
        action: suspend () -> Unit
    ) {
        updateProgress(startProgress, getPhaseTitle(phase), getPhaseDetail(phase))
        
        val startTime = System.currentTimeMillis()
        action()
        val duration = System.currentTimeMillis() - startTime
        
        updateProgress(endProgress, getPhaseTitle(phase), "完成 (耗时${duration}ms)")
        Timber.i("${getPhaseTitle(phase)}完成，耗时: ${duration}ms")
        
        // 短暂延迟，让用户看到进度
        delay(200)
    }
    
    /**
     * 阶段1: 数据库初始化
     */
    private suspend fun initializeDatabase() = withContext(Dispatchers.IO) {
        updateDetail("检查Realm数据库状态...")
        
        try {
            // 🔧 优化：Realm已在Application中初始化，这里只需验证状态
            updateDetail("验证数据库连接...")
            
            // 检查Realm实例是否可用
            val realm = ShenjiApplication.realm
            val isRealmAvailable = try {
                realm.query(Entry::class).count().find()
                true
            } catch (e: Exception) {
                Timber.w(e, "Realm数据库不可用")
                false
            }
            
            if (isRealmAvailable) {
                updateDetail("获取数据库统计信息...")
                val repository = DictionaryRepository()
                
                val dbSize = repository.getDictionaryFileSize()
                val dbSizeMB = dbSize / (1024 * 1024)
                val entryCount = try {
                    repository.getTotalEntryCount()
                } catch (e: Exception) {
                    Timber.w(e, "获取词条数失败")
                    0
                }
                
                updateDetail("数据库状态: ${dbSizeMB}MB, ${entryCount}个词条")
                Timber.i("数据库验证完成 - 大小: ${dbSizeMB}MB, 词条数: $entryCount")
                
                // 预热数据库连接（可选，如果数据库为空则跳过）
                if (entryCount > 0) {
                    updateDetail("正在预热数据库连接，这可能需要10-15秒...")
                    repository.warmupCache()
                } else {
                    updateDetail("数据库为空，跳过预热")
                    Timber.w("数据库为空，可能需要重新初始化")
                }
            } else {
                updateDetail("数据库不可用，将使用降级模式")
                Timber.w("数据库初始化失败，应用将以降级模式运行")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "数据库状态检查失败")
            updateDetail("数据库检查异常: ${e.message}")
            // 不抛出异常，让应用继续启动
        }
    }
    
    /**
     * 阶段2: 内存清理
     */
    private suspend fun performMemoryCleanup() = withContext(Dispatchers.IO) {
        updateDetail("正在清理内存...")
        
        val runtime = Runtime.getRuntime()
        val beforeCleanup = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        
        // 强制垃圾回收
        updateDetail("执行垃圾回收...")
        System.gc()
        delay(500) // 等待GC完成
        
        val afterCleanup = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val freed = beforeCleanup - afterCleanup
        
        updateDetail("内存清理完成，释放了${freed}MB")
        Timber.i("内存清理: 清理前${beforeCleanup}MB -> 清理后${afterCleanup}MB，释放${freed}MB")
        
        // 记录当前内存状态
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val usedMemory = afterCleanup
        val freeMemory = maxMemory - usedMemory
        
        Timber.i("内存状态: 最大${maxMemory}MB，已用${usedMemory}MB，可用${freeMemory}MB")
        updateDetail("可用内存: ${freeMemory}MB")
    }
    
    /**
     * 阶段3: 加载核心chars词典
     */
    private suspend fun loadCoreCharsDictionary() = withContext(Dispatchers.IO) {
        updateDetail("正在加载核心单字词典...")
        
        try {
            val trieManager = TrieManager.instance
            trieManager.init()
            
            // 只加载chars词典
            if (trieManager.isTrieFileExists(TrieBuilder.TrieType.CHARS)) {
                updateDetail("检测到chars词典文件...")
                
                val startTime = System.currentTimeMillis()
                val success = trieManager.loadTrieToMemory(TrieBuilder.TrieType.CHARS)
                val loadTime = System.currentTimeMillis() - startTime
                
                if (success) {
                    updateDetail("chars词典加载成功 (${loadTime}ms)")
                    Timber.i("chars词典加载成功，耗时: ${loadTime}ms")
                    
                    // 检查内存使用
                    val runtime = Runtime.getRuntime()
                    val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                    updateDetail("当前内存使用: ${usedMemory}MB")
                    
                } else {
                    updateDetail("chars词典加载失败，将使用数据库查询")
                    Timber.w("chars词典加载失败")
                }
            } else {
                updateDetail("未找到chars词典文件")
                Timber.w("chars词典文件不存在")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "加载chars词典时出错")
            updateDetail("词典加载异常: ${e.message}")
            // 不抛出异常，允许应用继续启动
        }
    }
    
    /**
     * 阶段4: 启动完成
     */
    private suspend fun finalizeStartup() = withContext(Dispatchers.IO) {
        updateDetail("正在完成启动准备...")
        
        // 最终内存状态检查
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val freeMemory = maxMemory - usedMemory
        
        updateDetail("启动完成 - 内存: ${usedMemory}/${maxMemory}MB")
        Timber.i("启动完成 - 内存使用: ${usedMemory}MB/${maxMemory}MB，剩余: ${freeMemory}MB")
        
        // 记录启动成功
        Timber.i("神机输入法启动成功，准备进入主界面")
    }
    
    /**
     * 跳转到主Activity
     */
    private fun navigateToMainActivity() {
        handler.post {
            updateProgress(100, "启动完成", "正在进入主界面...")
            
            // 短暂延迟后跳转
            handler.postDelayed({
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
                
                // 添加淡入淡出动画
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }, 500)
        }
    }
    
    /**
     * 处理启动错误
     */
    private fun handleStartupError(error: Exception) {
        handler.post {
            progressBar.progress = 100
            statusText.text = "启动遇到问题"
            detailText.text = "错误: ${error.message ?: "未知错误"}"
            
            Timber.e(error, "启动失败")
        }
        
        // 延迟后仍然跳转，避免卡在启动页
        handler.postDelayed({
            navigateToMainActivity()
        }, 3000)
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
     * 更新详细信息
     */
    private suspend fun updateDetail(detail: String) {
        withContext(Dispatchers.Main) {
            detailText.text = detail
        }
    }
    
    /**
     * 获取阶段标题
     */
    private fun getPhaseTitle(phase: StartupPhase): String {
        return when (phase) {
            StartupPhase.DATABASE_INIT -> "初始化数据库"
            StartupPhase.MEMORY_CLEANUP -> "优化内存"
            StartupPhase.CORE_DICT_LOADING -> "加载核心词典"
            StartupPhase.STARTUP_COMPLETE -> "启动完成"
        }
    }
    
    /**
     * 获取阶段详细描述
     */
    private fun getPhaseDetail(phase: StartupPhase): String {
        return when (phase) {
            StartupPhase.DATABASE_INIT -> "正在初始化Realm数据库..."
            StartupPhase.MEMORY_CLEANUP -> "正在清理内存，优化性能..."
            StartupPhase.CORE_DICT_LOADING -> "正在加载核心单字词典..."
            StartupPhase.STARTUP_COMPLETE -> "正在完成启动准备..."
        }
    }
    
    override fun onBackPressed() {
        // 在启动页禁用返回键，防止用户意外退出
        // super.onBackPressed()
    }
} 