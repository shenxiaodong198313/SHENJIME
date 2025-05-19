package com.shenji.aikeyboard.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.databinding.ActivityDevToolsBinding
import com.shenji.aikeyboard.utils.PinyinUtils
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.min

class DevToolsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDevToolsBinding
    private var isBuilding = false
    
    // 用于存储和恢复状态的常量
    private val PREF_NAME = "realm_builder_prefs"
    private val KEY_DB_PATH = "db_path"
    private val KEY_BUILD_COMPLETED = "build_completed"
    private val KEY_BUILD_TIME = "build_time"

    // 权限请求码
    private val STORAGE_PERMISSION_CODE = 101
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDevToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupUI()
        
        // 恢复之前保存的状态
        restoreState()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "开发工具"
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupUI() {
        // 拼音分词测试
        binding.btnTestPinyin.setOnClickListener {
            testPinyinSplitter()
        }
        
        // 拼音分词优化测试工具
        binding.btnPinyinSegmenterTest.setOnClickListener {
            startActivity(Intent(this, PinyinTestActivity::class.java))
        }
        
        // 拼音输入法测试工具
        binding.btnPinyinTestTool.setOnClickListener {
            startActivity(android.content.Intent(this, PinyinTestToolActivity::class.java))
        }
        
        // 构建Realm数据库
        binding.btnBuildRealmDb.setOnClickListener {
            if (!isBuilding) {
                startBuildRealmDatabase()
            }
        }
        
        // 导出数据库
        binding.btnExportDb.setOnClickListener {
            exportRealmDatabase()
        }
        
        // 存储到外部目录
        binding.btnExportToPublic.setOnClickListener {
            if (checkStoragePermission()) {
                exportToPublicDirectory()
            } else {
                requestStoragePermission()
            }
        }
        
        // 查看数据库详情
        binding.btnShowDbDetails.setOnClickListener {
            showDatabaseDetails()
        }
        
        // 复制数据库信息按钮
        binding.btnCopyDbInfo.setOnClickListener {
            copyDatabaseInfo()
        }
        
        // 复制数据库路径按钮
        binding.btnCopyDbPath.setOnClickListener {
            copyDatabasePath()
        }
    }

    /**
     * 显示数据库详情
     */
    private fun showDatabaseDetails() {
        lifecycleScope.launch {
            try {
                binding.tvDictStatus.visibility = View.VISIBLE
                binding.tvDictStatus.text = "正在加载数据库详情..."
                
                // 找到最新的数据库文件
                val dbDir = File(filesDir, "dictionaries")
                val realmFiles = dbDir.listFiles { file -> file.name.endsWith(".realm") }
                val latestRealmFile = realmFiles?.maxByOrNull { it.lastModified() }
                
                if (latestRealmFile != null) {
                    val filePath = latestRealmFile.absolutePath
                    val fileSize = latestRealmFile.length() / (1024 * 1024) // MB
                    val lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(latestRealmFile.lastModified()))
                    
                    // 配置并打开Realm
                    val config = RealmConfiguration.Builder(schema = setOf(Entry::class))
                        .directory(dbDir.absolutePath)
                        .name(latestRealmFile.name)
                        .build()
                    
                    var totalEntries = 0L
                    var entryTypes = HashMap<String, Long>()
                    
                    withContext(Dispatchers.IO) {
                        val realm = Realm.open(config)
                        try {
                            // 查询总条目数
                            val entries = realm.query<Entry>().find()
                            totalEntries = entries.size.toLong()
                            
                            // 查询不同类型的条目数
                            val types = realm.query<Entry>().distinct("type").find()
                            for (type in types) {
                                val typeCount = realm.query<Entry>("type == $0", type.type).count().find()
                                entryTypes[type.type] = typeCount.toLong()
                            }
                        } finally {
                            realm.close()
                        }
                    }
                    
                    // 获取构建用时
                    val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    val buildTime = prefs.getLong(KEY_BUILD_TIME, 0)
                    val buildTimeStr = if (buildTime > 0) {
                        val minutes = buildTime / 60
                        val seconds = buildTime % 60
                        "${minutes}分${seconds}秒"
                    } else {
                        "未知"
                    }
                    
                    // 更新UI显示数据库详情
                    withContext(Dispatchers.Main) {
                        binding.cardDbInfo.visibility = View.VISIBLE
                        binding.tvDbPath.text = "路径: $filePath"
                        binding.tvDbSize.text = "大小: ${fileSize}MB"
                        binding.tvDbVersion.text = "Realm版本: 2.3.0"
                        binding.tvDbStructure.text = "结构: Entry(id, word, pinyin, frequency, type, initialLetters)"
                        
                        // 更新统计信息
                        val statsBuilder = StringBuilder().apply {
                            append("总条目数: $totalEntries\n")
                            append("最后修改时间: $lastModified\n")
                            append("词典类型统计:\n")
                            
                            // 按照条目数量从大到小排序
                            val sortedTypes = entryTypes.entries.sortedByDescending { it.value }
                            for ((type, count) in sortedTypes) {
                                append("  · $type: $count 条\n")
                            }
                        }
                        
                        binding.tvDbStats.text = statsBuilder.toString()
                        binding.tvDbBuildTime.text = "构建用时: $buildTimeStr"
                        binding.tvDictStatus.visibility = View.GONE
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.tvDictStatus.text = "未找到数据库文件"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvDictStatus.text = "加载数据库详情失败: ${e.message}"
                    Timber.e(e, "加载数据库详情失败")
                }
            }
        }
    }
    
    /**
     * 复制数据库信息
     */
    private fun copyDatabaseInfo() {
        val dbInfo = """
            数据库信息:
            ${binding.tvDbVersion.text}
            ${binding.tvDbPath.text}
            ${binding.tvDbSize.text}
            ${binding.tvDbStructure.text}
            ${binding.tvDbStats.text}
            ${binding.tvDbBuildTime.text}
        """.trimIndent()
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("数据库信息", dbInfo)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "数据库信息已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 复制数据库路径
     */
    private fun copyDatabasePath() {
        val dbPath = binding.tvDbPath.text.toString().replace("路径: ", "")
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("数据库路径", dbPath)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "数据库路径已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 测试拼音分词器效果
     */
    private fun testPinyinSplitter() {
        lifecycleScope.launch {
            // 显示加载提示
            Toast.makeText(this@DevToolsActivity, getString(R.string.pinyin_test_running), Toast.LENGTH_SHORT).show()
            
            // 在IO线程中执行测试
            val result = withContext(Dispatchers.IO) {
                PinyinUtils.testPinyinSplitter()
            }
            
            // 显示结果对话框
            AlertDialog.Builder(this@DevToolsActivity)
                .setTitle(getString(R.string.pinyin_test_title))
                .setMessage(result)
                .setPositiveButton("确定", null)
                .show()
                
            // 记录日志
            Timber.i(getString(R.string.pinyin_test_complete))
        }
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
                    binding.cardDbInfo.visibility = View.VISIBLE
                    binding.tvDbPath.text = "路径：${dbFile.absolutePath}"
                    binding.tvDbSize.text = "大小: ${dbFile.length() / (1024 * 1024)}MB"
                    
                    // 显示重置按钮
                    binding.btnResetBuild.visibility = View.VISIBLE
                    binding.btnResetBuild.setOnClickListener {
                        resetBuildState()
                    }
                    
                    // 保存数据库路径、构建完成状态和构建用时
                    saveState(dbFile.absolutePath, true, totalTime)
                    
                    // 记录日志
                    Timber.i("Realm数据库构建完成，路径: ${dbFile.absolutePath}")
                    
                    // 刷新显示数据库详情
                    showDatabaseDetails()
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
        totalEntries: Long
    ) {
        var processedEntries: Long = 0
        
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
                                            this.initialLetters = PinyinUtils.generateInitials(pinyin)
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
    private suspend fun countTotalEntries(dictFiles: List<String>): Long = withContext(Dispatchers.IO) {
        var count: Long = 0
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
                            binding.cardDbInfo.visibility = View.VISIBLE
                            binding.tvDbPath.text = "导出路径：$exportPath"
                            binding.tvDbSize.text = "大小: ${targetFile.length() / (1024 * 1024)}MB"
                            
                            // 设置复制功能
                            Toast.makeText(this@DevToolsActivity, "数据库已导出，可点击\"复制路径\"按钮复制路径", Toast.LENGTH_LONG).show()
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
    
    /**
     * 检查存储权限
     */
    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上使用Scoped Storage，不需要传统存储权限
            true
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 请求存储权限
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportToPublicDirectory()
            } else {
                Toast.makeText(this, "需要存储权限才能导出到外部目录", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * 导出数据库文件到外部公共目录
     */
    private fun exportToPublicDirectory() {
        lifecycleScope.launch {
            try {
                binding.tvDictStatus.visibility = View.VISIBLE
                binding.tvDictStatus.text = "正在存储数据库文件到外部目录..."
                
                withContext(Dispatchers.IO) {
                    // 找到最新的数据库文件
                    val dbDir = File(filesDir, "dictionaries")
                    val realmFiles = dbDir.listFiles { file -> file.name.endsWith(".realm") }
                    val latestRealmFile = realmFiles?.maxByOrNull { it.lastModified() }
                    
                    latestRealmFile?.let { sourceFile ->
                        val filename = sourceFile.name
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // Android 10及以上使用MediaStore API
                            val contentValues = ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                                put(MediaStore.Downloads.IS_PENDING, 1)
                            }
                            
                            val resolver = contentResolver
                            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            val itemUri = resolver.insert(collection, contentValues)
                            
                            itemUri?.let { uri ->
                                resolver.openOutputStream(uri)?.use { outputStream ->
                                    sourceFile.inputStream().use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                                
                                contentValues.clear()
                                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                                resolver.update(uri, contentValues, null, null)
                                
                                // 更新UI
                                withContext(Dispatchers.Main) {
                                    binding.tvDictStatus.text = "数据库已存储到外部下载目录"
                                    binding.cardDbInfo.visibility = View.VISIBLE
                                    binding.tvDbPath.text = "文件名: $filename\n位置: 下载目录"
                                    binding.tvDbSize.text = "大小: ${sourceFile.length() / (1024 * 1024)}MB"
                                    
                                    Toast.makeText(this@DevToolsActivity, "文件已保存到下载目录", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            // Android 9及以下使用直接文件访问
                            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val targetFile = File(downloadDir, filename)
                            
                            sourceFile.inputStream().use { input ->
                                targetFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            // 更新UI
                            withContext(Dispatchers.Main) {
                                val exportPath = targetFile.absolutePath
                                binding.tvDictStatus.text = "数据库已存储到外部下载目录"
                                binding.cardDbInfo.visibility = View.VISIBLE
                                binding.tvDbPath.text = "存储路径：$exportPath"
                                binding.tvDbSize.text = "大小: ${sourceFile.length() / (1024 * 1024)}MB"
                                
                                Toast.makeText(this@DevToolsActivity, "文件已保存到下载目录", Toast.LENGTH_LONG).show()
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
                    binding.tvDictStatus.text = "存储到外部目录失败: ${e.message}"
                    Timber.e(e, "导出数据库到外部目录失败")
                }
            }
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
            binding.cardDbInfo.visibility = View.VISIBLE
            binding.tvDbPath.text = "数据库存储路径：$dbPath"
            
            val dbFile = File(dbPath)
            if (dbFile.exists()) {
                binding.tvDbSize.text = "大小: ${dbFile.length() / (1024 * 1024)}MB"
            }
            
            // 显示重置按钮
            binding.btnResetBuild.visibility = View.VISIBLE
            binding.btnResetBuild.setOnClickListener {
                resetBuildState()
            }
            
            // 自动加载数据库详情
            showDatabaseDetails()
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
        binding.cardDbInfo.visibility = View.GONE
        binding.btnResetBuild.visibility = View.GONE
        
        // 隐藏进度和状态
        binding.pbDictExport.visibility = View.GONE
        binding.tvDictStatus.visibility = View.GONE
    }
    
    /**
     * 保存当前状态
     */
    private fun saveState(dbPath: String, buildCompleted: Boolean, buildTime: Long = 0) {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_DB_PATH, dbPath)
            putBoolean(KEY_BUILD_COMPLETED, buildCompleted)
            putLong(KEY_BUILD_TIME, buildTime)
            apply()
        }
    }
} 