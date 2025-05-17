package com.shenji.aikeyboard.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.databinding.ActivityMainBinding
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupUI()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }
    
    private fun setupUI() {
        // 设置按钮点击事件
        binding.btnLogs.setOnClickListener {
            openLogDetail()
        }
        
        binding.btnDictManager.setOnClickListener {
            openDictManager()
        }
        
        binding.btnInputTest.setOnClickListener {
            openInputTest()
        }
        
        // 添加开发工具入口
        binding.btnDevTools.setOnClickListener {
            openDevTools()
        }
    }
    
    /**
     * 打开日志详情
     */
    private fun openLogDetail() {
        Timber.d("打开日志详情")
        val intent = Intent(this, LogDetailActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * 打开词典管理界面
     */
    private fun openDictManager() {
        Timber.d("打开词典管理")
        val intent = Intent(this, DictionaryManagerActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * 打开输入测试界面
     */
    private fun openInputTest() {
        Timber.d("打开输入测试")
        val intent = Intent(this, InputTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * 打开开发工具界面
     */
    private fun openDevTools() {
        Timber.d("打开开发工具")
        val intent = Intent(this, DevToolsActivity::class.java)
        startActivity(intent)
    }
} 