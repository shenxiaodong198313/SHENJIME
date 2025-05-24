package com.shenji.aikeyboard.ui.dictionary

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ui.trie.TrieBuildActivity
import com.shenji.aikeyboard.data.DatabaseReinitializer
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * 词典管理菜单Activity
 * 提供三个入口：Realm词典管理、双Trie树管理和高性能数据库重新初始化
 */
class DictionaryMenuActivity : AppCompatActivity() {
    
    private lateinit var realmDictButton: Button
    private lateinit var trieButton: Button
    private lateinit var reinitDbButton: Button
    private var isInitializing = false
    private var initializationJob: Job? = null
    
    // 进度显示相关
    private var progressDialog: AlertDialog? = null
    private val progressViews = mutableMapOf<String, ProgressView>()
    
    data class ProgressView(
        val container: LinearLayout,
        val nameText: TextView,
        val progressBar: ProgressBar,
        val statusText: TextView,
        val detailText: TextView
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary_menu)
        
        // 设置返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "词典管理中心"
        
        // 初始化按钮
        initButtons()
    }
    
    /**
     * 初始化按钮
     */
    private fun initButtons() {
        realmDictButton = findViewById(R.id.realm_dict_button)
        trieButton = findViewById(R.id.trie_button)
        reinitDbButton = findViewById(R.id.reinit_db_button)
        
        // 设置Realm词典按钮点击事件
        realmDictButton.setOnClickListener {
            openRealmDictManager()
        }
        
        // 设置Trie树按钮点击事件
        trieButton.setOnClickListener {
            openTrieManager()
        }
        
        // 设置重新初始化数据库按钮点击事件
        reinitDbButton.setOnClickListener {
            if (isInitializing) {
                // 如果正在初始化，点击按钮取消操作
                cancelInitialization()
            } else {
                showDictionarySelectionDialog()
            }
        }
    }
    
    /**
     * 打开Realm词典管理
     */
    private fun openRealmDictManager() {
        try {
            Timber.d("打开Realm词典管理")
            val intent = Intent(this, DictionaryListActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "打开Realm词典管理失败")
        }
    }
    
    /**
     * 打开Trie树管理
     */
    private fun openTrieManager() {
        try {
            Timber.d("打开Trie树管理")
            val intent = Intent(this, TrieBuildActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "打开Trie树管理失败")
        }
    }
    
    /**
     * 显示词典选择对话框
     */
    private fun showDictionarySelectionDialog() {
        val reinitializer = DatabaseReinitializer(this)
        val availableDictionaries = reinitializer.getAvailableDictionaries()
        val completedDictionaries = reinitializer.getCompletedDictionaries()
        
        // 创建滚动视图
        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        scrollView.addView(container)
        
        // 添加说明文本
        container.addView(TextView(this).apply {
            text = "选择要导入的词典类型："
            textSize = 16f
            setPadding(0, 0, 0, 16)
        })
        
        // 创建复选框
        val checkBoxes = mutableMapOf<String, CheckBox>()
        for (dict in availableDictionaries) {
            val checkBox = CheckBox(this).apply {
                text = "${dict.displayName} - ${dict.description} (约${dict.estimatedSize}条)"
                isChecked = dict.key !in completedDictionaries // 未完成的默认选中
                
                // 如果已完成，显示特殊标记
                if (dict.key in completedDictionaries) {
                    text = "$text ✓已完成"
                    setTextColor(getColor(android.R.color.holo_green_dark))
                }
            }
            checkBoxes[dict.key] = checkBox
            container.addView(checkBox)
        }
        
        // 断点续传选项
        container.addView(TextView(this).apply {
            text = "\n选项："
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        
        val resumeCheckBox = CheckBox(this).apply {
            text = "使用断点续传（跳过已完成的词典）"
            isChecked = completedDictionaries.isNotEmpty()
        }
        container.addView(resumeCheckBox)
        
        // 显示对话框
        AlertDialog.Builder(this)
            .setTitle("高性能数据库重新初始化")
            .setView(scrollView)
            .setPositiveButton("开始导入") { _, _ ->
                val selectedDictionaries = checkBoxes.filter { it.value.isChecked }.keys.toList()
                val resumeFromBreakpoint = resumeCheckBox.isChecked
                
                if (selectedDictionaries.isEmpty()) {
                    Toast.makeText(this, "请至少选择一个词典", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                reinitializeDatabase(selectedDictionaries, resumeFromBreakpoint)
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("全选/全不选") { _, _ ->
                val allChecked = checkBoxes.values.all { it.isChecked }
                checkBoxes.values.forEach { it.isChecked = !allChecked }
            }
            .show()
    }
    
    /**
     * 重新初始化数据库
     */
    private fun reinitializeDatabase(selectedDictionaries: List<String>, resumeFromBreakpoint: Boolean) {
        // 设置初始化状态
        isInitializing = true
        reinitDbButton.text = "正在初始化... (点击取消)"
        
        // 创建进度显示对话框
        createProgressDialog(selectedDictionaries)
        
        initializationJob = lifecycleScope.launch {
            try {
                val reinitializer = DatabaseReinitializer(this@DictionaryMenuActivity)
                val success = reinitializer.reinitializeDatabase(
                    selectedDictionaries = selectedDictionaries,
                    resumeFromBreakpoint = resumeFromBreakpoint
                ) { overallProgress, currentDict, dictProgress, message ->
                    runOnUiThread {
                        updateProgress(overallProgress, currentDict, dictProgress, message)
                    }
                }
                
                runOnUiThread {
                    resetInitializationState()
                    progressDialog?.dismiss()
                    
                    if (success) {
                        // 显示成功对话框，提示用户刷新
                        AlertDialog.Builder(this@DictionaryMenuActivity)
                            .setTitle("数据库重新初始化成功！")
                            .setMessage("数据库已成功重新构建。\n\n" +
                                    "为了确保Realm词典管理界面显示最新数据，建议您：\n" +
                                    "1. 重启应用，或\n" +
                                    "2. 进入Realm词典管理界面查看数据\n\n" +
                                    "是否现在打开Realm词典管理？")
                            .setPositiveButton("打开词典管理") { _, _ ->
                                openRealmDictManager()
                            }
                            .setNegativeButton("稍后查看", null)
                            .show()
                        reinitDbButton.text = "重新初始化数据库"
                    } else {
                        Toast.makeText(this@DictionaryMenuActivity, "数据库重新初始化失败，请查看日志", Toast.LENGTH_LONG).show()
                        reinitDbButton.text = "重新初始化数据库 (失败)"
                    }
                }
                
            } catch (e: CancellationException) {
                // 协程被取消，不需要特殊处理，已经在cancelInitialization中处理了
                Timber.d("数据库初始化协程被取消")
            } catch (e: Exception) {
                Timber.e(e, "重新初始化数据库失败")
                runOnUiThread {
                    resetInitializationState()
                    progressDialog?.dismiss()
                    Toast.makeText(this@DictionaryMenuActivity, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
                    reinitDbButton.text = "重新初始化数据库 (异常)"
                }
            }
        }
    }
    
    /**
     * 创建进度显示对话框
     */
    private fun createProgressDialog(selectedDictionaries: List<String>) {
        val reinitializer = DatabaseReinitializer(this)
        val availableDictionaries = reinitializer.getAvailableDictionaries()
        
        // 创建滚动视图
        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        scrollView.addView(container)
        
        // 总体进度
        val overallProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        val overallStatusText = TextView(this).apply {
            text = "准备开始..."
            textSize = 16f
            setPadding(0, 8, 0, 16)
        }
        
        container.addView(TextView(this).apply {
            text = "总体进度："
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        container.addView(overallProgressBar)
        container.addView(overallStatusText)
        
        // 分隔线
        container.addView(TextView(this).apply {
            text = "\n各词典详细进度："
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        
        // 为每个选中的词典创建进度视图
        for (dictKey in selectedDictionaries) {
            val dictInfo = availableDictionaries.find { it.key == dictKey } ?: continue
            
            val dictContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            
            val nameText = TextView(this).apply {
                text = dictInfo.displayName
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            
            val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 0
            }
            
            val statusText = TextView(this).apply {
                text = "等待开始..."
                textSize = 12f
            }
            
            val detailText = TextView(this).apply {
                text = "进度: 0%"
                textSize = 12f
                setTextColor(getColor(android.R.color.darker_gray))
            }
            
            dictContainer.addView(nameText)
            dictContainer.addView(progressBar)
            dictContainer.addView(statusText)
            dictContainer.addView(detailText)
            container.addView(dictContainer)
            
            // 保存进度视图引用
            progressViews[dictKey] = ProgressView(
                container = dictContainer,
                nameText = nameText,
                progressBar = progressBar,
                statusText = statusText,
                detailText = detailText
            )
        }
        
        progressDialog = AlertDialog.Builder(this)
            .setTitle("数据库重新初始化进度")
            .setView(scrollView)
            .setNegativeButton("取消") { _, _ ->
                cancelInitialization()
            }
            .setCancelable(false)
            .create()
        
        progressDialog?.show()
        
        // 保存总体进度视图的引用
        progressViews["_overall"] = ProgressView(
            container = container,
            nameText = TextView(this),
            progressBar = overallProgressBar,
            statusText = overallStatusText,
            detailText = TextView(this)
        )
    }
    
    /**
     * 更新进度显示
     */
    private fun updateProgress(overallProgress: Float, currentDict: String, dictProgress: Float, message: String) {
        // 更新总体进度
        progressViews["_overall"]?.let { view ->
            view.progressBar.progress = (overallProgress * 100).toInt()
            view.statusText.text = message
        }
        
        // 更新当前词典进度
        if (currentDict.isNotEmpty()) {
            progressViews[currentDict]?.let { view ->
                view.progressBar.progress = (dictProgress * 100).toInt()
                view.statusText.text = if (dictProgress >= 1.0f) "已完成 (断点续传)" else message
                view.detailText.text = "进度: ${(dictProgress * 100).toInt()}%"
            }
        }
        
        // 更新按钮文本和状态
        val progressPercent = (overallProgress * 100).toInt()
        if (overallProgress >= 1.0f) {
            // 完成状态
            reinitDbButton.text = "初始化完成"
            // 更新对话框按钮
            progressDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.text = "完成"
        } else {
            // 进行中状态
            reinitDbButton.text = "[$progressPercent%] 正在初始化... (点击取消)"
        }
    }
    
    /**
     * 取消初始化操作
     */
    private fun cancelInitialization() {
        // 检查是否已完成
        val overallProgress = progressViews["_overall"]?.progressBar?.progress ?: 0
        if (overallProgress >= 100) {
            // 已完成，直接关闭对话框
            resetInitializationState()
            progressDialog?.dismiss()
            Toast.makeText(this, "数据库初始化已完成", Toast.LENGTH_SHORT).show()
            reinitDbButton.text = "重新初始化数据库"
            return
        }
        
        // 未完成，显示取消确认对话框
        AlertDialog.Builder(this)
            .setTitle("取消初始化")
            .setMessage("确定要取消数据库初始化吗？\n\n⚠️ 注意：取消后可以使用断点续传功能继续未完成的导入。")
            .setPositiveButton("确定取消") { _, _ ->
                initializationJob?.cancel()
                resetInitializationState()
                progressDialog?.dismiss()
                Toast.makeText(this, "初始化已取消", Toast.LENGTH_SHORT).show()
                reinitDbButton.text = "重新初始化数据库 (已取消)"
            }
            .setNegativeButton("继续初始化", null)
            .show()
    }
    
    /**
     * 重置初始化状态
     */
    private fun resetInitializationState() {
        isInitializing = false
        initializationJob = null
        progressViews.clear()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 清理协程和对话框
        initializationJob?.cancel()
        progressDialog?.dismiss()
    }
} 