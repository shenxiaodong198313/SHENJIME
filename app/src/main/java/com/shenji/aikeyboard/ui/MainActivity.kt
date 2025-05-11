package com.shenji.aikeyboard.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.DictionaryConverter
import com.shenji.aikeyboard.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dictionaryConverter: DictionaryConverter
    
    // Android 11+需要请求所有文件访问权限的启动器
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // 权限已授予
                startDictionaryConversion()
            } else {
                Toast.makeText(this, "需要文件访问权限以保存数据库文件", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // 传统权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // 所有权限都被授予
            startDictionaryConversion()
        } else {
            Toast.makeText(this, "需要存储权限以保存数据库文件", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化词典转换器
        dictionaryConverter = DictionaryConverter(this)
        
        setupUI()
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
        
        // 词典转换按钮
        binding.btnConvertDict.setOnClickListener {
            checkAndRequestStoragePermission()
        }
    }
    
    /**
     * 检查并请求存储权限
     */
    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+，请求MANAGE_EXTERNAL_STORAGE权限
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                storagePermissionLauncher.launch(intent)
            } else {
                startDictionaryConversion()
            }
        } else {
            // Android 10及以下，请求传统存储权限
            val hasStoragePermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasStoragePermission) {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                )
            } else {
                startDictionaryConversion()
            }
        }
    }
    
    /**
     * 开始词典转换
     */
    private fun startDictionaryConversion() {
        // 更新UI状态
        binding.btnConvertDict.isEnabled = false
        binding.progressDict.visibility = View.VISIBLE
        binding.tvDictStatus.visibility = View.VISIBLE
        binding.tvDictStatus.text = getString(R.string.dict_converting)
        
        lifecycleScope.launch {
            dictionaryConverter.convertDictionaries()
                .collect { progress ->
                    // 更新UI
                    updateConversionProgress(progress)
                    
                    // 如果完成或出错，重新启用按钮
                    if (progress.isCompleted || progress.error != null) {
                        binding.btnConvertDict.isEnabled = true
                    }
                }
        }
    }
    
    /**
     * 更新转换进度UI
     */
    private fun updateConversionProgress(progress: DictionaryConverter.ConversionProgress) {
        // 进度条最大值设置为总字典数
        binding.progressDict.max = progress.totalDicts
        
        if (progress.error != null) {
            // 出错情况
            binding.tvDictStatus.text = "${getString(R.string.dict_error)}: ${progress.error}"
            return
        }
        
        if (progress.isCompleted) {
            // 完成情况
            binding.progressDict.progress = progress.totalDicts
            binding.tvDictStatus.text = "${getString(R.string.dict_completed)}\n数据库文件已保存至:\n${progress.dbPath}"
            return
        }
        
        // 正在进行中
        binding.progressDict.progress = progress.totalDicts
        binding.tvDictStatus.text = "正在处理: ${progress.currentDict}\n" +
                "已处理词条: ${progress.currentCount}\n" +
                "进度: ${progress.currentDict} (${progress.totalDicts} 个词典中的第 ${binding.progressDict.progress})"
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
} 