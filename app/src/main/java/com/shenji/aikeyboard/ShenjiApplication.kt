package com.shenji.aikeyboard

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.logger.CrashReportingTree
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ShenjiApplication : Application() {
    
    companion object {
        lateinit var instance: ShenjiApplication
            private set
        
        lateinit var appContext: Context
            private set
            
        lateinit var realm: Realm
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        
        instance = this
        appContext = applicationContext
        
        // 设置Timber日志框架
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // 生产环境使用自定义日志树，记录崩溃信息
            Timber.plant(CrashReportingTree())
        }
        
        // 初始化数据库和词典
        initRealm()
        
        // 初始化词典管理器，但延迟加载词典
        DictionaryManager.init()
        
        // 延迟2秒后在后台线程启动词典加载，避免启动卡顿
        Handler(Looper.getMainLooper()).postDelayed({
            Thread {
                try {
                    // 让应用界面先完全显示，再开始加载词典
                    Thread.sleep(2000)
                    Timber.d("开始延迟加载词典数据")
                    // 不再需要显式调用loadCharsFromRealm，在init()中已处理词典加载
                } catch (e: Exception) {
                    Timber.e(e, "延迟加载词典失败: ${e.message}")
                }
            }.start()
        }, 3000)
        
        Timber.d("应用初始化完成")
    }
    
    private fun ensureDictionaryFileExists() {
        try {
            val internalDir = File(filesDir, "dictionaries")
            if (!internalDir.exists()) {
                internalDir.mkdirs()
            }
            
            val dictFile = File(internalDir, "shenji_dict.realm")
            if (!dictFile.exists()) {
                Timber.d("词典文件不存在，从assets复制...")
                
                val inputStream = assets.open("shenji_dict.realm")
                val outputStream = FileOutputStream(dictFile)
                
                val buffer = ByteArray(1024)
                var length: Int
                
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
                
                inputStream.close()
                outputStream.close()
                
                Timber.d("词典文件复制成功")
            } else {
                Timber.d("词典文件已存在")
            }
        } catch (e: IOException) {
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
                Timber.w("数据库文件不存在或无效，从assets中复制预构建的数据库")
                
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
                    
                    Timber.d("预构建数据库复制成功")
                } catch (e: IOException) {
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
            Timber.d("Realm initialized successfully")
        } catch (e: Exception) {
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
                Timber.d("Empty Realm database created")
            } catch (e2: Exception) {
                // 如果还是失败，抛出异常
                Timber.e(e2, "Failed to create Realm database")
                throw e2
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
    

}