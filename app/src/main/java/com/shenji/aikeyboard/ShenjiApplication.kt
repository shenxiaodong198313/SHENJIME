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
        
        // 加载预编译的高频词典
        loadPrecompiledDictionary()
        
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
     * 加载预编译的高频词典
     * 检查并加载从assets目录中的预编译Trie树文件
     */
    private fun loadPrecompiledDictionary() {
        try {
            // 检查内部存储目录中是否已存在预编译Trie树文件
            val internalDir = File(filesDir, "precompiled_dict")
            if (!internalDir.exists()) {
                internalDir.mkdirs()
            }
            
            val trieFile = File(internalDir, "shenji_dict_trie.bin")
            
            // 如果文件不存在或需要更新，从assets目录复制
            if (!trieFile.exists()) {
                Timber.d("预编译高频词典不存在，开始从assets目录复制...")
                
                // 从assets目录复制文件
                trieFile.parentFile?.mkdirs()
                val inputStream = assets.open("shenji_dict_trie.bin")
                val outputStream = FileOutputStream(trieFile)
                
                try {
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    Timber.d("预编译高频词典已成功复制到内部存储")
                } finally {
                    inputStream.close()
                    outputStream.close()
                }
            }
            
            // 在后台线程中加载预编译词典
            Thread {
                try {
                    DictionaryManager.instance.loadPrecompiledDictionary(trieFile)
                    Timber.d("预编译高频词典成功加载到内存")
                } catch (e: Exception) {
                    Timber.e(e, "加载预编译高频词典失败: ${e.message}")
                }
            }.start()
            
        } catch (e: Exception) {
            Timber.e(e, "准备预编译高频词典失败: ${e.message}")
        }
    }
}