package com.shenji.aikeyboard.logger

import com.shenji.aikeyboard.ShenjiApplication
import java.io.File
import java.io.IOException

/**
 * 日志管理工具，用于读取和管理日志文件
 */
object LogManager {
    
    /**
     * 读取崩溃日志内容
     * @return 日志内容字符串，如果没有日志则返回null
     */
    fun readCrashLog(): String? {
        val logFile = ShenjiApplication.instance.getCrashLogFile()
        return if (logFile.exists() && logFile.length() > 0) {
            try {
                logFile.readText()
            } catch (e: IOException) {
                null
            }
        } else {
            null
        }
    }
    
    /**
     * 清除崩溃日志
     * @return 是否成功清除
     */
    fun clearCrashLog(): Boolean {
        val logFile = ShenjiApplication.instance.getCrashLogFile()
        return if (logFile.exists()) {
            try {
                logFile.writeText("")
                true
            } catch (e: IOException) {
                false
            }
        } else {
            true
        }
    }
    
    /**
     * 获取日志文件
     * @return 日志文件
     */
    fun getCrashLogFile(): File {
        return ShenjiApplication.instance.getCrashLogFile()
    }
} 