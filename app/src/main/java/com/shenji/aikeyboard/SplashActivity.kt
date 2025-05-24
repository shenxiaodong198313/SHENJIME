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
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.ui.MainActivity

class SplashActivity : AppCompatActivity() {
    
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        // 隐藏状态栏和导航栏，全屏显示
        supportActionBar?.hide()
        
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        
        // 开始异步初始化
        startAsyncInitialization()
    }
    
    private fun startAsyncInitialization() {
        lifecycleScope.launch {
            try {
                // 模拟初始化步骤
                updateProgress(10, "正在检查环境...")
                withContext(Dispatchers.IO) {
                    Thread.sleep(500) // 模拟检查时间
                }
                
                updateProgress(20, "正在准备词典...")
                withContext(Dispatchers.IO) {
                    Thread.sleep(300)
                }
                
                updateProgress(30, "正在加载基础词典...")
                // 异步加载Trie
                val trieManager = TrieManager.getInstance(this@SplashActivity)
                
                withContext(Dispatchers.IO) {
                    // 检查是否已经加载过
                    if (!trieManager.isTriesLoaded()) {
                        // 分步骤加载不同的Trie文件
                        loadTrieWithProgress(trieManager, "chars", 40, "正在加载单字词典...")
                        loadTrieWithProgress(trieManager, "place", 55, "正在加载地名词典...")
                        loadTrieWithProgress(trieManager, "people", 70, "正在加载人名词典...")
                        loadTrieWithProgress(trieManager, "corrections", 85, "正在加载纠错词典...")
                        loadTrieWithProgress(trieManager, "compatible", 95, "正在加载兼容词典...")
                        
                        // 完成加载
                        trieManager.loadTries()
                    } else {
                        // 已经加载过，快速跳过
                        updateProgress(95, "词典已就绪...")
                        Thread.sleep(500)
                    }
                }
                
                updateProgress(100, "初始化完成！")
                
                // 延迟一下让用户看到完成状态
                withContext(Dispatchers.IO) {
                    Thread.sleep(800)
                }
                
                // 跳转到主Activity
                navigateToMainActivity()
                
            } catch (e: Exception) {
                e.printStackTrace()
                updateProgress(100, "初始化出错，正在启动...")
                
                // 即使出错也要跳转，避免卡在启动页
                handler.postDelayed({
                    navigateToMainActivity()
                }, 1000)
            }
        }
    }
    
    private suspend fun loadTrieWithProgress(
        trieManager: TrieManager, 
        trieType: String, 
        progress: Int, 
        message: String
    ) {
        updateProgress(progress, message)
        withContext(Dispatchers.IO) {
            try {
                // 这里可以调用具体的Trie加载方法
                // 目前先模拟加载时间
                Thread.sleep(800)
            } catch (e: Exception) {
                e.printStackTrace()
                // 加载失败也继续，不阻塞启动流程
            }
        }
    }
    
    private fun updateProgress(progress: Int, message: String) {
        handler.post {
            progressBar.progress = progress
            statusText.text = message
        }
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // 关闭启动页，防止用户返回
        
        // 添加淡入淡出动画
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
    
    override fun onBackPressed() {
        // 在启动页禁用返回键，防止用户意外退出
        // super.onBackPressed()
    }
} 