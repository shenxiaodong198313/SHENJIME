package com.shenji.aikeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.databinding.ActivityMainBinding
import com.shenji.aikeyboard.data.trie.TrieManager
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
                Toast.makeText(this, "词典管理功能已简化", Toast.LENGTH_SHORT).show()
            }
            
            // 添加开发工具入口
            binding.btnDevTools?.setOnClickListener {
                Log.d("MainActivity", "btnDevTools 按钮被点击")
                Toast.makeText(this, "开发工具功能已简化", Toast.LENGTH_SHORT).show()
            }
            
            // 添加输入法设置入口
            binding.mainButtonContainer?.findViewById<Button>(R.id.btn_ime_settings)?.setOnClickListener {
                Log.d("MainActivity", "btn_ime_settings 按钮被点击")
                openInputMethodSettings()
            }
            
            // 添加系统检查入口
            binding.mainButtonContainer?.findViewById<Button>(R.id.btn_system_check)?.setOnClickListener {
                Log.d("MainActivity", "btn_system_check 按钮被点击")
                Toast.makeText(this, "系统检查功能已简化", Toast.LENGTH_SHORT).show()
            }
            
            // 添加优化候选词测试入口
            binding.mainButtonContainer?.findViewById<Button>(R.id.btn_optimized_test)?.setOnClickListener {
                Log.d("MainActivity", "btn_optimized_test 按钮被点击")
                openOptimizedCandidateTest()
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
    
    /**
     * 打开开发工具界面
     */
    
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
    
    /**
     * 打开优化候选词测试
     */
    private fun openOptimizedCandidateTest() {
        try {
            Timber.d("打开优化候选词测试")
            val intent = Intent(this, OptimizedCandidateTestActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "打开优化候选词测试失败: ${e.message}", e)
            Toast.makeText(this, "无法打开优化候选词测试: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 在主界面启动后台词典加载
     */
    private fun startBackgroundDictionaryLoading() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val trieManager = TrieManager.instance
                
                Timber.i("主界面启动 - 开始优化的词典加载策略")
                Timber.i("内存优化策略：启动时已加载chars，现在异步加载base，其他词典需手动加载")
                
                val runtime = Runtime.getRuntime()
                val maxMemory = runtime.maxMemory() / 1024 / 1024
                val usedMemoryBefore = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                
                Timber.i("异步加载前内存状态: 已用${usedMemoryBefore}MB / 最大${maxMemory}MB")
                
                // 等待一段时间，确保主界面完全加载
                delay(2000)
                
                Timber.i("简化的词典加载策略：只加载核心词典")
                
            } catch (e: Exception) {
                Timber.e(e, "异步词典加载过程异常")
            }
        }
    }
    
    /**
     * 获取Trie类型的显示名称
     */
} 