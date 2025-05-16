package com.shenji.aikeyboard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.databinding.ActivityMainBinding
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.data.Entry
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import kotlin.math.min
import com.shenji.aikeyboard.utils.PinyinUtils
import com.shenji.aikeyboard.utils.PinyinInitialUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isBuilding = false
    
    // 用于存储和恢复状态的常量
    private val PREF_NAME = "realm_builder_prefs"
    private val KEY_DB_PATH = "db_path"
    private val KEY_BUILD_COMPLETED = "build_completed"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupUI()
        
        // 恢复之前保存的状态
        restoreState()
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
        
        binding.btnInputTest.setOnClickListener {
            openInputTest()
        }
        
        // 添加测试拼音分词按钮点击事件
        binding.btnTestPinyin.setOnClickListener {
            testPinyinSplitter()
        }
        
        // 添加构建Realm实例按钮点击事件
        binding.btnBuildRealmDb.setOnClickListener {
            if (!isBuilding) {
                startBuildRealmDatabase()
            }
        }

        // 添加导出数据库按钮点击事件
        binding.btnExportDb.setOnClickListener {
            exportRealmDatabase()
        }
        
        // 隐藏进度相关元素和词典处理部分
        binding.tvDictProcessTitle.visibility = View.GONE
        binding.dictProcessContainer.visibility = View.GONE
        binding.pbDictExport.visibility = View.GONE
        binding.tvDictStatus.visibility = View.GONE
    }
    
    /**
     * 开始构建Realm数据库
     */
    private fun startBuildRealmDatabase() {
        isBuilding = true
        
        // 显示进度条和状态
        binding.pbDictExport.progress = 0
        binding.pbDictExport.visibility = View.VISIBLE
        binding.tvDictStatus.visibility = View.VISIBLE
        binding.tvDictStatus.text = "准备构建..."
        
        // 禁用构建按钮
        binding.btnBuildRealmDb.isEnabled = false
        
        // 在协程中执行耗时操作
        lifecycleScope.launch {
            try {
                // 记录开始时间
                val startTime = System.currentTimeMillis()
                
                // 获取新数据库路径
                val dbDir = File(filesDir, "dictionaries")
                if (!dbDir.exists()) {
                    dbDir.mkdirs()
                }
                val dbFile = File(dbDir, "shenji_dict.realm")
                
                // 更新状态
                updateUI("创建数据库目录...", 5)
                
                // 配置Realm
                val config = RealmConfiguration.Builder(schema = setOf(Entry::class))
                    .directory(dbDir.absolutePath)
                    .name("shenji_dict.realm")
                    .deleteRealmIfMigrationNeeded() // 如果需要迁移就删除旧数据库
                    .build()
                    
                // 创建新的Realm实例
                withContext(Dispatchers.IO) {
                    // 更新状态
                    updateUI("计算总条目数...", 10)
                    
                    // 获取所有词典文件
                    val dictFiles = assets.list("cn_dicts")?.filter { it.endsWith(".dict.yaml") } ?: emptyList()
                    val totalFiles = dictFiles.size
                    
                    // 计算总条目数（预估）
                    val totalEntries = countTotalEntries(dictFiles)
                    updateUI("总计 ${totalFiles} 个词典文件，约 ${totalEntries} 个词条", 15)
                    
                    // 并行处理的核心数量
                    val availableProcessors = Runtime.getRuntime().availableProcessors()
                    val coreCount = minOf(8, availableProcessors) // 最多使用8个核心
                    
                    // 打开Realm数据库
                    val realm = Realm.open(config)
                    try {
                        // 分组处理词典文件（每个线程处理一部分文件）
                        val dictGroups = dictFiles.chunked((dictFiles.size + coreCount - 1) / coreCount)
                        
                        // 创建工作线程
                        val jobs = dictGroups.mapIndexed { groupIndex, group ->
                            lifecycleScope.async(Dispatchers.IO) {
                                processDictionaryGroup(realm, group, groupIndex, dictGroups.size, totalFiles, totalEntries)
                            }
                        }
                        
                        // 等待所有工作线程完成
                        jobs.awaitAll()
                        
                    } finally {
                        realm.close()
                    }
                }
                
                // 计算总用时
                val totalTime = (System.currentTimeMillis() - startTime) / 1000
                val minutes = totalTime / 60
                val seconds = totalTime % 60
                
                // 完成后显示数据库路径
                withContext(Dispatchers.Main) {
                    binding.pbDictExport.progress = 100
                    binding.tvDictStatus.text = "构建完成！用时: ${minutes}分${seconds}秒"
                    binding.btnBuildRealmDb.isEnabled = false
                    binding.tvDbPath.visibility = View.VISIBLE
                    binding.tvDbPath.text = "数据库存储路径：${dbFile.absolutePath}"
                    
                    // 显示重置按钮
                    binding.btnResetBuild.visibility = View.VISIBLE
                    binding.btnResetBuild.setOnClickListener {
                        resetBuildState()
                    }
                    
                    // 保存数据库路径和构建完成状态
                    saveState(dbFile.absolutePath, true)
                    
                    // 记录日志
                    Timber.i("Realm数据库构建完成，路径: ${dbFile.absolutePath}")
                }
            } catch (e: Exception) {
                Timber.e(e, "构建Realm数据库失败")
                withContext(Dispatchers.Main) {
                    binding.tvDictStatus.text = "构建失败: ${e.message}"
                    binding.btnBuildRealmDb.isEnabled = true
                }
            } finally {
                isBuilding = false
            }
        }
    }
    
    /**
     * 处理一组词典文件
     */
    private suspend fun processDictionaryGroup(
        realm: Realm,
        dictFiles: List<String>,
        groupIndex: Int,
        totalGroups: Int,
        totalFiles: Int,
        totalEntries: Int
    ) {
        var processedEntries = 0
        
        for (dictFile in dictFiles) {
            val dictType = dictFile.substringBefore(".dict.yaml")
            
            // 基础进度：15% + 组进度占比
            val baseProgress = 15 + ((groupIndex.toFloat() / totalGroups) * 85).toInt()
            updateUI("线程${groupIndex + 1}正在处理 $dictType 词典", baseProgress)
            
            // 读取并处理词典文件
            val inputStream = assets.open("cn_dicts/$dictFile")
            try {
                val reader = BufferedReader(InputStreamReader(inputStream))
                try {
                    var line: String? = null
                    var lineCount = 0
                    var needsUIUpdate = false
                    
                    while (true) {
                        // 每次处理一批数据，避免事务太大
                        val batchSize = 1000
                        var batchCount = 0
                        
                        // 开始事务
                        realm.writeBlocking {
                            while (batchCount < batchSize && reader.readLine().also { line = it } != null) {
                                line?.let { l ->
                                    // 使用制表符分割行
                                    val parts = l.split("\t")
                                    if (parts.size >= 3) {
                                        val word = parts[0]
                                        val pinyin = removeTones(parts[1])  // 去掉声调
                                        val frequency = parts[2].toIntOrNull() ?: 0
                                        
                                        // 创建并添加词条
                                        val entry = Entry().apply {
                                            id = UUID.randomUUID().toString()
                                            this.word = word
                                            this.pinyin = pinyin
                                            this.frequency = frequency
                                            this.type = dictType
                                            // 设置首字母
                                            this.initialLetters = PinyinInitialUtils.generateInitials(pinyin)
                                        }
                                        
                                        copyToRealm(entry)
                                        
                                        // 更新处理进度
                                        lineCount++
                                        processedEntries++
                                        batchCount++
                                        
                                        if (lineCount % 1000 == 0) {
                                            needsUIUpdate = true
                                        }
                                    }
                                }
                            }
                        }
                        
                        // 在事务块外更新UI
                        if (needsUIUpdate) {
                            val fileProgress = min(baseProgress + 3, 99) // 每个文件处理进度上限+3%
                            updateUI("线程${groupIndex + 1}处理 $dictType 词典: $lineCount 条", fileProgress)
                            needsUIUpdate = false
                        }
                        
                        // 如果没有读到数据，说明文件已处理完成
                        if (batchCount < batchSize) {
                            break
                        }
                    }
                    
                    // 文件处理完成
                    val fileEntries = lineCount
                    updateUI("线程${groupIndex + 1}完成处理 $dictType 词典: $fileEntries 条", baseProgress)
                    Timber.d("线程${groupIndex + 1}完成处理词典: $dictType, 条目数: $fileEntries")
                } finally {
                    reader.close()
                }
            } finally {
                inputStream.close()
            }
        }
    }
    
    /**
     * 更新UI
     */
    private suspend fun updateUI(message: String, progress: Int) {
        withContext(Dispatchers.Main) {
            binding.tvDictStatus.text = message
            binding.pbDictExport.progress = progress
        }
    }
    
    /**
     * 统计总条目数
     */
    private suspend fun countTotalEntries(dictFiles: List<String>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (dictFile in dictFiles) {
            val inputStream = assets.open("cn_dicts/$dictFile")
            try {
                val reader = BufferedReader(InputStreamReader(inputStream))
                try {
                    while (reader.readLine() != null) {
                        count++
                    }
                } finally {
                    reader.close()
                }
            } finally {
                inputStream.close()
            }
        }
        return@withContext count
    }
    
    /**
     * 去除拼音中的声调
     */
    private fun removeTones(pinyin: String): String {
        return pinyin.replace('ā', 'a')
            .replace('á', 'a')
            .replace('ǎ', 'a')
            .replace('à', 'a')
            .replace('ē', 'e')
            .replace('é', 'e')
            .replace('ě', 'e')
            .replace('è', 'e')
            .replace('ī', 'i')
            .replace('í', 'i')
            .replace('ǐ', 'i')
            .replace('ì', 'i')
            .replace('ō', 'o')
            .replace('ó', 'o')
            .replace('ǒ', 'o')
            .replace('ò', 'o')
            .replace('ū', 'u')
            .replace('ú', 'u')
            .replace('ǔ', 'u')
            .replace('ù', 'u')
            .replace('ǖ', 'v')
            .replace('ǘ', 'v')
            .replace('ǚ', 'v')
            .replace('ǜ', 'v')
            .replace('ü', 'v')  // 特殊处理ü
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
    
    /**
     * 打开输入测试界面
     */
    private fun openInputTest() {
        Timber.d("打开输入测试")
        val intent = Intent(this, InputTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * 测试拼音分词器效果
     */
    private fun testPinyinSplitter() {
        lifecycleScope.launch {
            // 显示加载提示
            Toast.makeText(this@MainActivity, getString(R.string.pinyin_test_running), Toast.LENGTH_SHORT).show()
            
            // 在IO线程中执行测试
            val result = withContext(Dispatchers.IO) {
                PinyinUtils.testPinyinSplitter()
            }
            
            // 显示结果对话框
            AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.pinyin_test_title))
                .setMessage(result)
                .setPositiveButton("确定", null)
                .show()
                
            // 记录日志
            Timber.i(getString(R.string.pinyin_test_complete))
        }
    }
    
    /**
     * 恢复之前保存的状态
     */
    private fun restoreState() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val buildCompleted = prefs.getBoolean(KEY_BUILD_COMPLETED, false)
        val dbPath = prefs.getString(KEY_DB_PATH, null)
        
        if (buildCompleted && dbPath != null) {
            // 显示之前构建的数据库路径
            binding.btnBuildRealmDb.isEnabled = false
            binding.tvDbPath.visibility = View.VISIBLE
            binding.tvDbPath.text = "数据库存储路径：$dbPath"
            
            // 显示重置按钮
            binding.btnResetBuild.visibility = View.VISIBLE
            binding.btnResetBuild.setOnClickListener {
                resetBuildState()
            }
        }
    }
    
    /**
     * 重置构建状态
     */
    private fun resetBuildState() {
        // 重置SharedPreferences
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        // 重置UI
        binding.btnBuildRealmDb.isEnabled = true
        binding.tvDbPath.visibility = View.GONE
        binding.btnResetBuild.visibility = View.GONE
        
        // 隐藏进度和状态
        binding.pbDictExport.visibility = View.GONE
        binding.tvDictStatus.visibility = View.GONE
    }
    
    /**
     * 保存当前状态
     */
    private fun saveState(dbPath: String, buildCompleted: Boolean) {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_DB_PATH, dbPath)
            putBoolean(KEY_BUILD_COMPLETED, buildCompleted)
            apply()
        }
    }

    /**
     * 导出Realm数据库文件到外部存储
     */
    private fun exportRealmDatabase() {
        lifecycleScope.launch {
            try {
                binding.tvDictStatus.visibility = View.VISIBLE
                binding.tvDictStatus.text = "正在导出数据库文件..."
                
                withContext(Dispatchers.IO) {
                    // 找到最新的数据库文件
                    val dbDir = File(filesDir, "dictionaries")
                    val realmFiles = dbDir.listFiles { file -> file.name.endsWith(".realm") }
                    val latestRealmFile = realmFiles?.maxByOrNull { it.lastModified() }
                    
                    latestRealmFile?.let { sourceFile ->
                        // 创建外部存储目录
                        val externalDir = getExternalFilesDir(null)
                        val targetFile = File(externalDir, sourceFile.name)
                        
                        // 复制文件
                        sourceFile.inputStream().use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        // 更新UI
                        withContext(Dispatchers.Main) {
                            val exportPath = targetFile.absolutePath
                            binding.tvDictStatus.text = "数据库导出成功"
                            binding.tvDbPath.visibility = View.VISIBLE
                            binding.tvDbPath.text = "导出路径：$exportPath"
                            
                            // 设置路径可复制
                            binding.tvDbPath.setOnClickListener {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("数据库路径", exportPath)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(this@MainActivity, "路径已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } ?: run {
                        withContext(Dispatchers.Main) {
                            binding.tvDictStatus.text = "未找到数据库文件"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvDictStatus.text = "导出失败: ${e.message}"
                    Timber.e(e, "导出数据库失败")
                }
            }
        }
    }
} 