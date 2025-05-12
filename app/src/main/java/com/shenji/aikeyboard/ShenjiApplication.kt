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
        
        // 初始化词典管理器
        DictionaryManager.init()
        
        // 从Realm加载chars词库到Trie树
        loadCharsFromRealm()
        
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
        // 首先确保词典文件存在
        ensureDictionaryFileExists()
        
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
    
    /**
     * 从Realm数据库加载chars词库到Trie树
     */
    private fun loadCharsFromRealm() {
        // 在后台线程中加载chars词库
        Thread {
            try {
                Timber.d("开始从Realm加载chars词库到Trie树")
                DictionaryManager.instance.loadCharsFromRealm()
                Timber.d("从Realm加载chars词库到Trie树完成")
            } catch (e: Exception) {
                Timber.e(e, "从Realm加载chars词库失败: ${e.message}")
            }
        }.start()
    }
}