package com.shenji.aikeyboard

import android.app.Application
import android.content.Context
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
        
        val appContext: Context
            get() = instance.applicationContext
            
        lateinit var realm: Realm
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 确保日志目录存在
        val logFile = getCrashLogFile()
        Log.d("ShenjiApplication", "日志文件路径: ${logFile.absolutePath}")
        
        // 初始化Timber日志
        try {
            if (BuildConfig.DEBUG) {
                Timber.plant(Timber.DebugTree())
                Log.d("ShenjiApplication", "初始化Timber DebugTree")
            } else {
                // 在生产环境使用自定义的CrashReportingTree
                Timber.plant(CrashReportingTree())
                Log.d("ShenjiApplication", "初始化Timber CrashReportingTree")
            }
            
            // 测试日志
            Timber.d("ShenjiApplication初始化开始")
            
            // 确保词典文件存在
            ensureDictionaryFileExists()
            
            // 初始化Realm
            initRealm()
            
            // 初始化词典管理器
            DictionaryManager.init()
            
            Timber.d("ShenjiApplication初始化完成")
        } catch (e: Exception) {
            Log.e("ShenjiApplication", "应用初始化失败", e)
            // 写入错误到崩溃日志
            try {
                FileOutputStream(logFile, true).use { fos ->
                    val errorMsg = "应用初始化失败: ${e.message}\n${e.stackTraceToString()}\n"
                    fos.write(errorMsg.toByteArray())
                }
            } catch (ioEx: IOException) {
                Log.e("ShenjiApplication", "写入初始化错误日志失败", ioEx)
            }
        }
    }
    
    private fun ensureDictionaryFileExists() {
        try {
            val internalDir = File(filesDir, "dictionaries")
            if (!internalDir.exists()) {
                internalDir.mkdirs()
            }
            
            val dictFile = File(internalDir, "shenji_dict.realm")
            if (!dictFile.exists()) {
                Timber.d("Dictionary file does not exist, copying from assets...")
                
                val inputStream = assets.open("shenji_dict.realm")
                val outputStream = FileOutputStream(dictFile)
                
                val buffer = ByteArray(1024)
                var length: Int
                
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
                
                inputStream.close()
                outputStream.close()
                
                Timber.d("Dictionary file copied successfully")
            } else {
                Timber.d("Dictionary file already exists")
            }
        } catch (e: IOException) {
            Timber.e(e, "Error copying dictionary file")
        }
    }
    
    private fun initRealm() {
        val dictFile = File(filesDir, "dictionaries/shenji_dict.realm")
        
        try {
            // 首先尝试正常打开数据库
            val config = RealmConfiguration.Builder(schema = setOf(
                Entry::class
            ))
                .directory(filesDir.path + "/dictionaries")
                .name("shenji_dict.realm")
                .build()
                
            realm = Realm.open(config)
            Timber.d("Realm initialized with dictionary file")
        } catch (e: Exception) {
            // 如果失败，则尝试删除现有数据库文件并重新创建
            Timber.e(e, "Error opening Realm database, trying to recreate it...")
            
            try {
                // 删除现有数据库文件
                if (dictFile.exists()) {
                    dictFile.delete()
                    Timber.d("Deleted existing Realm file due to schema mismatch")
                }
                
                // 从assets中重新复制词典文件
                val inputStream = assets.open("shenji_dict.realm")
                val outputStream = FileOutputStream(dictFile)
                
                val buffer = ByteArray(1024)
                var length: Int
                
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
                
                inputStream.close()
                outputStream.close()
                
                // 重新打开数据库
                val config = RealmConfiguration.Builder(schema = setOf(
                    Entry::class
                ))
                    .directory(filesDir.path + "/dictionaries")
                    .name("shenji_dict.realm")
                    .build()
                    
                realm = Realm.open(config)
                Timber.d("Realm recreated and initialized successfully")
            } catch (e2: Exception) {
                // 如果还是失败，则创建一个新的空数据库
                Timber.e(e2, "Failed to recreate Realm database, creating empty one")
                
                val config = RealmConfiguration.Builder(schema = setOf(
                    Entry::class
                ))
                    .directory(filesDir.path + "/dictionaries")
                    .name("shenji_dict.realm")
                    .deleteRealmIfMigrationNeeded()
                    .build()
                    
                realm = Realm.open(config)
                Timber.d("Empty Realm database created")
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