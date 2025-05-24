package com.shenji.aikeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.databinding.ActivityMainBinding
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.trie.TrieBuilder
import com.shenji.aikeyboard.ui.dictionary.DictionaryMenuActivity
import com.shenji.aikeyboard.ui.trie.TrieBuildActivity
import com.shenji.aikeyboard.settings.InputMethodSettingsActivity
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            
            // 记录到系统日志
            Log.i("MainActivity", "开始创建主界面")
            
            // 尝试初始化视图绑定
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // 设置UI组件
            setupToolbar()
            setupUI()
            
            // 启动后台词典加载
            startBackgroundDictionaryLoading()
            
            Log.i("MainActivity", "主界面创建完成")
        } catch (e: Exception) {
            // 记录错误
            Log.e("MainActivity", "创建主界面时发生错误: ${e.message}", e)
            
            // 尝试加载基础布局并显示错误
            try {
                setContentView(R.layout.activity_main)
                Toast.makeText(this, "界面初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Log.e("MainActivity", "无法加载备用布局: ${e2.message}", e2)
                finish() // 无法恢复，关闭活动
            }
        }
    }
    
    private fun setupToolbar() {
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.title = getString(R.string.app_name)
        } catch (e: Exception) {
            Log.e("MainActivity", "设置工具栏失败: ${e.message}", e)
        }
    }
    
    private fun setupUI() {
        try {
            // 设置按钮点击事件
            Log.d("MainActivity", "设置按钮点击事件监听器")
            
            binding.btnLogs?.setOnClickListener {
                Log.d("MainActivity", "btnLogs 按钮被点击")
                openLogDetail()
            }
            
            binding.btnDictManager?.setOnClickListener {
                Log.d("MainActivity", "btnDictManager 按钮被点击")
                openDictManager()
            }
            
            // 添加开发工具入口
            binding.btnDevTools?.setOnClickListener {
                Log.d("MainActivity", "btnDevTools 按钮被点击")
                openDevTools()
            }
            
            // 添加输入法设置入口
            binding.mainButtonContainer?.findViewById<Button>(R.id.btn_ime_settings)?.setOnClickListener {
                Log.d("MainActivity", "btn_ime_settings 按钮被点击")
                openInputMethodSettings()
            }
            
            // 添加系统检查入口
            binding.mainButtonContainer?.findViewById<Button>(R.id.btn_system_check)?.setOnClickListener {
                Log.d("MainActivity", "btn_system_check 按钮被点击")
                openSystemCheck()
            }
            
            Log.d("MainActivity", "所有按钮监听器设置完成")
        } catch (e: Exception) {
            Log.e("MainActivity", "设置UI元素失败: ${e.message}", e)
            Toast.makeText(this, "界面初始化异常，部分功能可能不可用", Toast.LENGTH_LONG).show()      
        }
    }
    
    /**
     * 打开日志详情
     */
    private fun openLogDetail() {
        try {
            Log.d("MainActivity", "开始打开日志详情")
            Timber.d("打开日志详情")
            val intent = Intent(this, LogDetailActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "打开日志详情失败: ${e.message}", e)
            Toast.makeText(this, "无法打开日志详情: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 打开词典管理界面
     */
    private fun openDictManager() {
        try {
            Log.d("MainActivity", "开始打开词典管理")
            Timber.d("打开词典管理")
            val intent = Intent(this, com.shenji.aikeyboard.ui.dictionary.DictionaryMenuActivity::class.java)
            Log.d("MainActivity", "创建Intent: ${intent}")
            startActivity(intent)
            Log.d("MainActivity", "词典管理菜单Activity启动完成")
        } catch (e: Exception) {
            Log.e("MainActivity", "打开词典管理菜单失败: ${e.message}", e)
            Toast.makeText(this, "无法打开词典管理: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 打开开发工具界面
     */
    private fun openDevTools() {
        try {
            Timber.d("打开开发工具")
            val intent = Intent(this, DevToolsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "打开开发工具失败: ${e.message}", e)
            Toast.makeText(this, "无法打开开发工具: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 打开输入法设置
     */
    private fun openInputMethodSettings() {
        try {
            Timber.d("打开输入法设置")
            val intent = Intent(this, InputMethodSettingsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "打开输入法设置失败: ${e.message}", e)
            Toast.makeText(this, "无法打开输入法设置: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 打开系统检查
     */
    private fun openSystemCheck() {
        try {
            Timber.d("打开系统检查")
            val intent = Intent(this, SystemCheckActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "打开系统检查失败: ${e.message}", e)
            Toast.makeText(this, "无法打开系统检查: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 在主界面启动后台词典加载
     */
    private fun startBackgroundDictionaryLoading() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val trieManager = TrieManager.instance
                
                // 🎯 优化：只处理chars和base词典，避免检查其他词典
                Timber.i("主界面启动 - 开始优化的词典加载策略")
                Timber.i("内存优化策略：启动时已加载chars，现在异步加载base，其他词典需手动加载")
                
                val runtime = Runtime.getRuntime()
                val maxMemory = runtime.maxMemory() / 1024 / 1024
                val usedMemoryBefore = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                
                Timber.i("异步加载前内存状态: 已用${usedMemoryBefore}MB / 最大${maxMemory}MB")
                
                // 等待一段时间，确保主界面完全加载
                delay(2000)
                
                // 🔧 优化：只检查和加载必要的词典
                val coreTypes = listOf(
                    TrieBuilder.TrieType.CHARS to "单字词典",
                    TrieBuilder.TrieType.BASE to "基础词典"
                )
                
                var loadedCount = 0
                
                for ((trieType, displayName) in coreTypes) {
                    try {
                        val isLoaded = trieManager.isTrieLoaded(trieType)
                        val fileExists = trieManager.isTrieFileExists(trieType)
                        
                        if (isLoaded) {
                            Timber.i("✅ $displayName: 已在内存中")
                            loadedCount++
                        } else if (fileExists && trieType == TrieBuilder.TrieType.BASE) {
                            // 只异步加载base词典
                            Timber.i("开始异步加载$displayName...")
                            val startTime = System.currentTimeMillis()
                            val success = trieManager.loadTrieToMemory(trieType)
                            val loadTime = System.currentTimeMillis() - startTime
                            
                            if (success) {
                                val usedMemoryAfter = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                                val memoryIncrease = usedMemoryAfter - usedMemoryBefore
                                
                                Timber.i("$displayName 异步加载成功！")
                                Timber.i("加载耗时: ${loadTime}ms")
                                Timber.i("内存增加: ${memoryIncrease}MB (${usedMemoryBefore}MB -> ${usedMemoryAfter}MB)")
                                loadedCount++
                            } else {
                                Timber.w("$displayName 异步加载失败")
                            }
                        } else if (fileExists) {
                            Timber.i("📁 $displayName: 文件存在，手动加载")
                        } else {
                            Timber.d("❌ $displayName: 文件不存在")
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "处理$displayName 时出现异常")
                    }
                }
                
                val finalUsedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                val finalFreeMemory = maxMemory - finalUsedMemory
                
                Timber.i("📊 优化的异步加载完成总结:")
                Timber.i("  核心词典已加载: ${loadedCount}/2个")
                Timber.i("  最终内存使用: ${finalUsedMemory}MB / ${maxMemory}MB")
                Timber.i("  剩余可用内存: ${finalFreeMemory}MB")
                Timber.i("💡 提示: 如需加载其他词典，请使用词典管理界面手动加载")
                
            } catch (e: Exception) {
                Timber.e(e, "异步词典加载过程异常")
            }
        }
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
} 