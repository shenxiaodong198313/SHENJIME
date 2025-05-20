package com.shenji.aikeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.databinding.ActivityMainBinding
import timber.log.Timber

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
            binding.btnLogs?.setOnClickListener {
                openLogDetail()
            }
            
            binding.btnDictManager?.setOnClickListener {
                openDictManager()
            }
            
            // 添加开发工具入口
            binding.btnDevTools?.setOnClickListener {
                openDevTools()
            }
            
            // 添加输入法设置入口
            binding.mainButtonContainer?.findViewById<Button>(R.id.btn_ime_settings)?.setOnClickListener {
                openInputMethodSettings()
            }
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
            Timber.d("打开词典管理")
            val intent = Intent(this, DictionaryManagerActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "打开词典管理失败: ${e.message}", e)
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
            val intent = Intent(this, com.shenji.aikeyboard.settings.InputMethodSettingsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "打开输入法设置失败: ${e.message}", e)
            Toast.makeText(this, "无法打开输入法设置: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
} 