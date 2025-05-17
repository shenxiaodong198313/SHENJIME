package com.shenji.aikeyboard.logger

import android.content.Context
import android.os.Environment
import android.util.Log
import com.shenji.aikeyboard.ShenjiApplication
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * 崩溃日志记录树，继承自Timber.Tree，用于捕获应用崩溃并记录日志
 */
class CrashReportingTree : Timber.Tree() {

    init {
        // 确保日志目录存在
        try {
            val logDir = getSafeLogDirectory()
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            Log.i("CrashReportingTree", "日志目录初始化: ${logDir.absolutePath}")
        } catch (e: Exception) {
            Log.e("CrashReportingTree", "创建日志目录失败", e)
        }
        
        // 设置全局未捕获异常处理器
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
            // 调用默认处理器
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        // 写入启动日志
        writeLogToFile("INIT", "应用启动，崩溃日志系统初始化完成", null)
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == Log.ASSERT || priority == Log.ERROR) {
            // 只记录ERROR和ASSERT级别的日志到文件
            writeLogToFile(tag, message, t)
        }
    }

    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val stackTrace = getStackTraceString(throwable)
        
        val crashLogEntry = """
            |====================== 崩溃日志 ======================
            |时间: $timestamp
            |线程: ${thread.name} (ID: ${thread.id})
            |异常: ${throwable.javaClass.name}
            |信息: ${throwable.message}
            |堆栈跟踪:
            |$stackTrace
            |====================================================
            |
        """.trimMargin()
        
        // 写入崩溃日志到多个可能的位置
        writeLogToFile("CRASH", crashLogEntry, throwable)
        writeLogToBackupLocation(crashLogEntry)
    }

    private fun writeLogToFile(tag: String?, message: String, t: Throwable?) {
        try {
            val logFile = getCrashLogFile()
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logTag = tag ?: "APP"
            val stackTrace = t?.let { getStackTraceString(it) } ?: ""
            
            val logEntry = """
                |[$timestamp] $logTag: $message
                |$stackTrace
                |
            """.trimMargin()
            
            FileOutputStream(logFile, true).use { fos ->
                fos.write(logEntry.toByteArray())
            }
            
            // 输出到Android日志，方便调试
            Log.i("CrashReportingTree", "写入日志到: ${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("CrashReportingTree", "写入日志文件失败", e)
            // 尝试写入到备用位置
            writeLogToBackupLocation("写入主日志失败: ${e.message}\n原日志内容: $message")
        }
    }
    
    // 备用日志写入位置
    private fun writeLogToBackupLocation(message: String) {
        try {
            // 尝试写入到外部存储的下载目录
            val backupDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupFile = File(backupDir, "shenji_crash.log")
            
            FileOutputStream(backupFile, true).use { fos ->
                fos.write(message.toByteArray())
            }
            
            Log.i("CrashReportingTree", "写入备用日志到: ${backupFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("CrashReportingTree", "写入备用日志失败", e)
        }
    }

    private fun getStackTraceString(t: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
    
    // 获取安全的日志目录
    private fun getSafeLogDirectory(): File {
        try {
            // 首选方案：使用应用的外部文件目录
            val context = ShenjiApplication.appContext
            val logDir = File(context.getExternalFilesDir(null), "logs")
            return logDir
        } catch (e: Exception) {
            Log.e("CrashReportingTree", "无法获取应用外部文件目录", e)
            
            // 备选方案：使用内部存储
            val context = ShenjiApplication.appContext
            return File(context.filesDir, "logs")
        }
    }
    
    // 获取崩溃日志文件
    private fun getCrashLogFile(): File {
        val logDir = getSafeLogDirectory()
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        
        // 使用当天日期作为文件名
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val today = dateFormat.format(Date())
        
        return File(logDir, "crash_log_${today}.txt")
    }
} 