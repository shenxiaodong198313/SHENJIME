package com.shenji.aikeyboard

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.logger.CrashReportingTree
import com.shenji.aikeyboard.keyboard.OptimizedCandidateEngine
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShenjiApplication : MultiDexApplication() {
    
    companion object {
        lateinit var instance: ShenjiApplication
            private set
        
        lateinit var appContext: Context
            private set
            
        lateinit var realm: Realm
            
        // 优化的候选词引擎（新增）
        val optimizedCandidateEngine by lazy {
            OptimizedCandidateEngine.getInstance()
        }
        
        // Trie树管理器
        val trieManager by lazy {
            TrieManager.instance
        }
    }
    
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
    
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        
        // 初始化全局异常处理器 - 放在最前面确保崩溃能被记录
        setupUncaughtExceptionHandler()
        
        try {
            // 初始化基本变量
            instance = this
            appContext = applicationContext
            
            // 写入基本日志
            logStartupMessage("开始初始化应用")
            
            // 记录当前可用内存
            logMemoryInfo()
            
            // 设置Timber日志框架
            if (BuildConfig.DEBUG) {
                Timber.plant(Timber.DebugTree())
                logStartupMessage("DEBUG模式：使用DebugTree")
            } else {
                // 生产环境使用自定义日志树，记录崩溃信息
                Timber.plant(CrashReportingTree())
                logStartupMessage("RELEASE模式：使用CrashReportingTree")
            }
            
            // 🔧 修复：在Application启动时就初始化Realm
            logStartupMessage("开始初始化Realm数据库...")
            initRealm()
            logStartupMessage("Realm数据库初始化完成")
            
            // 只进行最基本的初始化，其他工作移到SplashActivity中进行
            // 这样可以避免Application启动时的长时间阻塞
            logStartupMessage("基础初始化完成，详细初始化将在启动页中进行")
            
            // 初始化Trie管理器（轻量级初始化）
            trieManager.init()
            
            // 异步初始化优化引擎（预加载核心Trie）
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    Timber.d("开始初始化优化候选词引擎")
                    // 触发优化引擎的初始化，这会在后台预加载核心Trie
                    optimizedCandidateEngine
                    Timber.d("优化候选词引擎初始化完成")
                } catch (e: Exception) {
                    Timber.e(e, "优化候选词引擎初始化失败")
                }
            }
            
            logStartupMessage("应用初始化完成")
            Timber.d("应用初始化完成")
        } catch (e: Exception) {
            // 捕获应用初始化过程中的任何异常
            logStartupMessage("应用初始化过程中发生致命错误: ${e.message}")
            Timber.e(e, "应用初始化过程中发生致命错误")
        }
    }
    
    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // 记录崩溃信息到独立的启动日志
                val stackTrace = getStackTraceAsString(throwable)
                logStartupMessage("应用崩溃: ${throwable.message}\n$stackTrace")
            } catch (e: Exception) {
                // 忽略日志记录失败
            }
            // 调用默认的处理器
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    private fun logStartupMessage(message: String) {
        try {
            // 获取时间戳
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            
            // 组合日志消息
            val logMessage = "[$timestamp] $message\n"
            
            // 记录到Android系统日志
            Log.i("ShenjiApp", message)
            
            // 写入到文件
            val logDir = File(getExternalFilesDir(null), "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            val startupLogFile = File(logDir, "startup_log.txt")
            FileOutputStream(startupLogFile, true).use { fos ->
                fos.write(logMessage.toByteArray())
            }
        } catch (e: Exception) {
            // 如果日志记录失败，至少输出到系统日志
            Log.e("ShenjiApp", "记录启动日志失败: ${e.message}")
        }
    }
    
    private fun getStackTraceAsString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
    
    private fun ensureDictionaryFileExists() {
        try {
            val internalDir = File(filesDir, "dictionaries")
            if (!internalDir.exists()) {
                internalDir.mkdirs()
            }
            
            val dictFile = File(internalDir, "shenji_dict.realm")
            if (!dictFile.exists()) {
                logStartupMessage("词典文件不存在，从assets复制...")
                
                val inputStream = assets.open("shenji_dict.realm")
                val outputStream = FileOutputStream(dictFile)
                
                val buffer = ByteArray(1024)
                var length: Int
                
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
                
                inputStream.close()
                outputStream.close()
                
                logStartupMessage("词典文件复制成功")
            } else {
                logStartupMessage("词典文件已存在")
            }
        } catch (e: IOException) {
            logStartupMessage("复制词典文件出错: ${e.message}")
            Timber.e(e, "复制词典文件出错")
        }
    }
    
    fun initRealm() {
        try {
            logStartupMessage("正在配置Realm数据库...")
            
            // 确保词典目录存在
            val internalDir = File(filesDir, "dictionaries")
            if (!internalDir.exists()) {
                internalDir.mkdirs()
                logStartupMessage("创建词典目录: ${internalDir.absolutePath}")
            }
            
            val dictFile = File(internalDir, "shenji_dict.realm")
            
            // 检查assets中是否有预构建的数据库文件
            val hasAssetsDb = try {
                assets.open("shenji_dict.realm").use { true }
            } catch (e: Exception) {
                logStartupMessage("assets中未找到预构建数据库")
                false
            }
            
            if (hasAssetsDb) {
                // 如果assets中有数据库文件，检查是否需要更新本地文件
                val shouldCopyFromAssets = !dictFile.exists() || 
                    dictFile.length() < 1000 || 
                    isAssetsDatabaseNewer(dictFile)
                
                if (shouldCopyFromAssets) {
                    logStartupMessage("从assets复制预构建数据库...")
                    copyDatabaseFromAssets(dictFile)
                } else {
                    logStartupMessage("使用现有的数据库文件")
                }
            } else {
                logStartupMessage("assets中未找到预构建数据库，使用现有文件或创建空数据库")
            }
            
            // 配置Realm
            val config = RealmConfiguration.Builder(schema = setOf(
                Entry::class
            ))
                .directory(filesDir.path + "/dictionaries")
                .name("shenji_dict.realm")
                .deleteRealmIfMigrationNeeded()
                .build()
                
            // 打开数据库
            realm = Realm.open(config)
            logStartupMessage("Realm数据库打开成功")
            
            // 验证数据库连接
            val entryCount = try {
                realm.query(Entry::class).count().find()
            } catch (e: Exception) {
                logStartupMessage("数据库查询测试失败: ${e.message}")
                0
            }
            
            logStartupMessage("Realm初始化成功，词条数: $entryCount")
        
        } catch (e: Exception) {
            logStartupMessage("初始化Realm数据库失败，尝试创建空数据库: ${e.message}")
            Timber.e(e, "Error initializing Realm database, creating empty one")
            
            try {
                // 创建一个新的空数据库
                val config = RealmConfiguration.Builder(schema = setOf(
                    Entry::class
                ))
                    .directory(filesDir.path + "/dictionaries")
                    .name("shenji_dict.realm")
                    .deleteRealmIfMigrationNeeded()
                    .build()
                    
                realm = Realm.open(config)
                logStartupMessage("创建空Realm数据库成功")
                
                // 验证空数据库
                val entryCount = try {
                    realm.query(Entry::class).count().find()
                } catch (e2: Exception) {
                    logStartupMessage("空数据库验证失败: ${e2.message}")
                    0
                }
                logStartupMessage("空数据库验证成功，词条数: $entryCount")
                
            } catch (e2: Exception) {
                // 如果还是失败，记录日志但不抛出异常
                logStartupMessage("创建空Realm数据库失败: ${e2.message}")
                Timber.e(e2, "Failed to create Realm database")
                
                // 🚨 最后的回退方案：创建一个最小配置的Realm
                try {
                    val fallbackConfig = RealmConfiguration.Builder(schema = setOf(Entry::class))
                        .name("fallback_dict.realm")
                        .deleteRealmIfMigrationNeeded()
                        .build()
                    realm = Realm.open(fallbackConfig)
                    logStartupMessage("回退方案：创建最小配置数据库成功")
                } catch (e3: Exception) {
                    logStartupMessage("致命错误：无法创建任何Realm数据库实例: ${e3.message}")
                    Timber.e(e3, "Fatal: Cannot create any Realm database instance")
                    // 这里不抛出异常，让应用继续运行，但功能会受限
                }
            }
        }
    }
    
    /**
     * 检查assets中的数据库是否比本地文件更新
     */
    private fun isAssetsDatabaseNewer(localFile: File): Boolean {
        return try {
            // 简单的检查：如果assets文件存在且本地文件较小，认为需要更新
            assets.open("shenji_dict.realm").use { inputStream ->
                val assetsSize = inputStream.available().toLong()
                assetsSize > localFile.length()
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 从assets复制数据库文件
     */
    private fun copyDatabaseFromAssets(targetFile: File) {
        try {
            assets.open("shenji_dict.realm").use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(8192) // 8KB缓冲区
                    var bytesRead: Int
                    var totalBytes = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        
                        // 每复制10MB记录一次进度
                        if (totalBytes % (10 * 1024 * 1024) == 0L) {
                            logStartupMessage("已复制 ${totalBytes / (1024 * 1024)} MB")
                        }
                    }
                    
                    outputStream.flush()
                    logStartupMessage("数据库复制完成，总大小: ${totalBytes / (1024 * 1024)} MB")
                }
            }
        } catch (e: IOException) {
            logStartupMessage("复制数据库失败: ${e.message}")
            Timber.e(e, "复制数据库失败")
            throw e
        }
    }
    
    // 获取词典文件
    fun getDictionaryFile(): File {
        return File(filesDir, "dictionaries/shenji_dict.realm")
    }
    
    // 获取崩溃日志文件
    fun getCrashLogFile(): File {
        val logDir = File(getExternalFilesDir(null), "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        return File(logDir, "crash_log.txt")
    }
    
    /**
     * 记录当前内存使用情况
     */
    private fun logMemoryInfo() {
        try {
            val runtime = Runtime.getRuntime()
            val maxMem = runtime.maxMemory() / (1024 * 1024)
            val totalMem = runtime.totalMemory() / (1024 * 1024)
            val freeMem = runtime.freeMemory() / (1024 * 1024)
            val usedMem = totalMem - freeMem
            
            logStartupMessage("内存状态: 最大=$maxMem MB, 已分配=$totalMem MB, 已使用=$usedMem MB, 空闲=$freeMem MB")
        } catch (e: Exception) {
            logStartupMessage("记录内存信息失败: ${e.message}")
        }
    }
}