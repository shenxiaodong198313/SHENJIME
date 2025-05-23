package com.shenji.aikeyboard

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.shenji.aikeyboard.data.CandidateManager
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.logger.CrashReportingTree
import com.shenji.aikeyboard.pinyin.PinyinQueryEngine
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

class ShenjiApplication : Application() {
    
    companion object {
        lateinit var instance: ShenjiApplication
            private set
        
        lateinit var appContext: Context
            private set
            
        lateinit var realm: Realm
            
        // 候选词管理器单例
        val candidateManager by lazy {
            CandidateManager()
        }
        
        // 标准化的拼音查询引擎
        val pinyinQueryEngine by lazy {
            PinyinQueryEngine.getInstance()
        }
        
        // Trie树管理器
        val trieManager by lazy {
            TrieManager.instance
        }
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
            
            // 初始化数据库和词典 - 使用try-catch包装
            try {
                logStartupMessage("开始初始化Realm数据库")
                initRealm()
                logStartupMessage("Realm数据库初始化完成")
            } catch (e: Exception) {
                logStartupMessage("初始化Realm数据库失败: ${e.message}")
                Timber.e(e, "初始化Realm数据库失败")
            }
            
            // 初始化词典管理器，但延迟加载词典
            try {
                logStartupMessage("开始初始化词典管理器")
                DictionaryManager.init()
                logStartupMessage("词典管理器初始化完成")
            } catch (e: Exception) {
                logStartupMessage("初始化词典管理器失败: ${e.message}")
                Timber.e(e, "初始化词典管理器失败")
            }
            
            // 初始化Trie树管理器
            try {
                logStartupMessage("开始初始化Trie树管理器")
                trieManager.init()
                logStartupMessage("Trie树管理器初始化完成")
            } catch (e: Exception) {
                logStartupMessage("初始化Trie树管理器失败: ${e.message}")
                Timber.e(e, "初始化Trie树管理器失败")
            }
            
            // 延迟2秒后在后台线程启动词典加载和缓存预热，避免启动卡顿
            Handler(Looper.getMainLooper()).postDelayed({
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        // 让应用界面先完全显示，再开始加载词典
                        delay(2000)
                        logStartupMessage("开始延迟加载词典数据和缓存预热")
                        
                        // 预热数据库缓存
                        val repository = DictionaryRepository()
                        repository.warmupCache()
                        logStartupMessage("缓存预热完成")
                        
                    } catch (e: Exception) {
                        logStartupMessage("延迟加载词典失败: ${e.message}")
                        Timber.e(e, "延迟加载词典失败")
                    }
                }
            }, 3000)
            
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
    
    private fun initRealm() {
        try {
            // 确保词典目录存在
            val internalDir = File(filesDir, "dictionaries")
            if (!internalDir.exists()) {
                internalDir.mkdirs()
            }
            
            val dictFile = File(internalDir, "shenji_dict.realm")
            
            // 如果文件不存在或者内容有问题，从assets中复制预构建的数据库
            if (!dictFile.exists() || dictFile.length() < 1000) {
                logStartupMessage("数据库文件不存在或无效，从assets中复制预构建的数据库")
                
                try {
                    // 从assets复制预构建的数据库
                    val inputStream = assets.open("shenji_dict.realm")
                    val outputStream = FileOutputStream(dictFile)
                    
                    val buffer = ByteArray(8192) // 使用更大的缓冲区提高复制效率
                    var length: Int
                    
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                    
                    inputStream.close()
                    outputStream.close()
                    
                    logStartupMessage("预构建数据库复制成功")
                } catch (e: IOException) {
                    logStartupMessage("复制预构建数据库失败: ${e.message}")
                    Timber.e(e, "复制预构建数据库失败")
                }
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
            logStartupMessage("Realm初始化成功")
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
            } catch (e2: Exception) {
                // 如果还是失败，记录日志但不抛出异常
                logStartupMessage("创建空Realm数据库失败: ${e2.message}")
                Timber.e(e2, "Failed to create Realm database")
            }
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