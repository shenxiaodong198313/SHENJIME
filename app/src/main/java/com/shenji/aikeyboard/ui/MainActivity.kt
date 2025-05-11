package com.shenji.aikeyboard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.databinding.ActivityMainBinding
import com.shenji.aikeyboard.data.DictionaryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

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
    
    override fun onResume() {
        super.onResume()
        // 更新输入法状态
        updateInputMethodStatus()
    }
    
    private fun setupUI() {
        // 设置按钮点击事件
        binding.btnSettings.setOnClickListener {
            openInputMethodSettings()
        }
        
        binding.btnLogs.setOnClickListener {
            openLogDetail()
        }
        
        binding.btnDictManager.setOnClickListener {
            openDictManager()
        }
        
        // 添加预编译词典按钮的点击处理
        binding.btnPrecompileDict.setOnClickListener {
            startPrecompileDictionary()
        }
        
        // 隐藏词典转换相关UI只保留预编译按钮
        binding.progressDict.visibility = android.view.View.GONE
        binding.tvDictStatus.visibility = android.view.View.GONE
    }
    
    /**
     * 开始预编译词典过程
     */
    private fun startPrecompileDictionary() {
        // 显示进度UI
        binding.progressDict.visibility = android.view.View.VISIBLE
        binding.tvDictStatus.visibility = android.view.View.VISIBLE
        binding.progressDict.isIndeterminate = true
        binding.tvDictStatus.text = "正在准备导出词典信息..."
        
        // 在后台线程执行导出
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 获取DictionaryManager实例
                val dictionaryManager = DictionaryManager.instance
                
                // 创建导出目录
                withContext(Dispatchers.Main) {
                    binding.tvDictStatus.text = "正在创建导出目录..."
                }
                
                val exportDir = File(getExternalFilesDir(null), "export")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                
                // 开始导出词典信息
                withContext(Dispatchers.Main) {
                    binding.tvDictStatus.text = "正在导出词典信息..."
                }
                
                // 调用DictionaryManager中的导出方法
                dictionaryManager.exportPrecompiledTrieForBuilding()
                
                // 获取导出的文件路径
                val versionFile = File(exportDir, "dictionary_versions.bin")
                val infoFile = File(exportDir, "dict_info.json")
                
                // 检查文件是否生成成功
                if (!versionFile.exists() || !infoFile.exists()) {
                    withContext(Dispatchers.Main) {
                        binding.tvDictStatus.text = "词典信息导出失败，请查看日志"
                        binding.progressDict.isIndeterminate = false
                        binding.progressDict.progress = 0
                    }
                    return@launch
                }
                
                // 显示成功信息
                withContext(Dispatchers.Main) {
                    binding.progressDict.isIndeterminate = false
                    binding.progressDict.progress = 100
                    binding.tvDictStatus.text = "词典信息导出成功！\n" +
                            "存放路径: ${exportDir.absolutePath}\n\n" +
                            "已导出以下文件:\n" +
                            "- dictionary_versions.bin\n" +
                            "- dict_info.json"
                    
                    Toast.makeText(this@MainActivity, "词典信息导出成功", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "导出词典信息失败")
                withContext(Dispatchers.Main) {
                    binding.progressDict.isIndeterminate = false
                    binding.progressDict.progress = 0
                    binding.tvDictStatus.text = "导出词典信息失败: ${e.message}"
                }
            }
        }
    }
    
    /**
     * 更新输入法状态
     */
    private fun updateInputMethodStatus() {
        val isEnabled = isInputMethodEnabled()
        val isSelected = isInputMethodSelected()
        
        binding.tvStatus.text = when {
            isSelected -> getString(R.string.ime_status).replace("未启用", "已启用并设为默认")
            isEnabled -> getString(R.string.ime_status).replace("未启用", "已启用但非默认")
            else -> getString(R.string.ime_status)
        }
    }
    
    /**
     * 检查输入法是否已启用
     */
    private fun isInputMethodEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val imeId = "com.shenji.aikeyboard/.ime.ShenjiInputMethodService"
        
        for (info in imm.enabledInputMethodList) {
            if (info.id == imeId) {
                return true
            }
        }
        return false
    }
    
    /**
     * 检查输入法是否被选为默认
     */
    private fun isInputMethodSelected(): Boolean {
        val currentImeId = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val imeId = "com.shenji.aikeyboard/.ime.ShenjiInputMethodService"
        return currentImeId == imeId
    }
    
    /**
     * 打开输入法设置
     */
    private fun openInputMethodSettings() {
        try {
            Timber.d("打开输入法设置")
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: Exception) {
            Timber.e(e, "打开输入法设置失败")
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
} 