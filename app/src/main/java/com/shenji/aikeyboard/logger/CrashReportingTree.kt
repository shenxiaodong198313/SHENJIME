package com.shenji.aikeyboard.logger

import android.content.Context
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
        // 设置全局未捕获异常处理器
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
            // 调用默认处理器
            defaultHandler?.uncaughtException(thread, throwable)
        }
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
        
        // 写入崩溃日志
        writeLogToFile("CRASH", crashLogEntry, throwable)
    }

    private fun writeLogToFile(tag: String?, message: String, t: Throwable?) {
        try {
            val app = ShenjiApplication.instance
            val logFile = app.getCrashLogFile()
            
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
            Log.e("CrashReportingTree", "写入崩溃日志到: ${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("CrashReportingTree", "写入日志文件失败", e)
        }
    }

    private fun getStackTraceString(t: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
} 