package com.shenji.aikeyboard

import android.app.Application
import android.os.Environment
import com.shenji.aikeyboard.logger.CrashReportingTree
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import timber.log.Timber
import java.io.File

class ShenjiApplication : Application() {

    companion object {
        lateinit var instance: ShenjiApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化Timber日志框架
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // 添加崩溃日志记录树
        Timber.plant(CrashReportingTree(this))
        
        // 初始化Realm数据库
        initRealm()
        
        Timber.i("神迹输入法应用已启动")
    }
    
    private fun initRealm() {
        // 配置Realm数据库
        val config = RealmConfiguration.create(schema = setOf())
        Realm.open(config)
    }
    
    // 获取崩溃日志文件
    fun getCrashLogFile(): File {
        val externalFilesDir = getExternalFilesDir(null)
        val logsDir = File(externalFilesDir, "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        return File(logsDir, "crash_log.txt")
    }
} 