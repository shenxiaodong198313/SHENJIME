package com.shenji.aikeyboard.utils

import android.content.Context
import android.os.Environment
import com.shenji.aikeyboard.ShenjiApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据库导出工具类
 * 处理Realm数据库文件的导出功能
 */
class DatabaseExporter(private val context: Context) {
    
    /**
     * 导出结果数据类
     */
    data class ExportResult(
        val success: Boolean,
        val filePath: String? = null,
        val errorMessage: String? = null,
        val fileSize: Long = 0L
    )
    
    /**
     * 导出当前Realm数据库到外部存储
     */
    suspend fun exportDatabase(): ExportResult = withContext(Dispatchers.IO) {
        try {
            // 获取源数据库文件
            val sourceDbFile = getSourceDatabaseFile()
            if (!sourceDbFile.exists()) {
                return@withContext ExportResult(
                    success = false,
                    errorMessage = "源数据库文件不存在"
                )
            }
            
            // 生成目标文件路径
            val targetFile = generateTargetFile()
            
            // 复制数据库文件
            copyDatabaseFile(sourceDbFile, targetFile)
            
            Timber.i("数据库导出成功: ${targetFile.absolutePath}")
            
            ExportResult(
                success = true,
                filePath = targetFile.absolutePath,
                fileSize = targetFile.length()
            )
            
        } catch (e: Exception) {
            Timber.e(e, "导出数据库失败")
            ExportResult(
                success = false,
                errorMessage = "导出失败: ${e.message}"
            )
        }
    }
    
    /**
     * 获取源数据库文件
     */
    private fun getSourceDatabaseFile(): File {
        // 首先尝试从ShenjiApplication获取数据库文件路径
        try {
            val app = context.applicationContext as ShenjiApplication
            val appDbFile = app.getDictionaryFile()
            if (appDbFile.exists()) {
                return appDbFile
            }
        } catch (e: Exception) {
            Timber.w(e, "无法从ShenjiApplication获取数据库文件路径")
        }
        
        // 如果应用方法不可用，使用默认路径
        val dictDir = File(context.filesDir, "dictionaries")
        return File(dictDir, "shenji_dict.realm")
    }
    
    /**
     * 生成目标文件
     */
    private fun generateTargetFile(): File {
        // 使用应用外部文件目录，这个目录不需要特殊权限
        val externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = externalFilesDir ?: File(context.filesDir, "exports")
        
        // 确保目录存在
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        
        // 生成带时间戳的文件名
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "shenji_dict_export_$timestamp.realm"
        
        return File(targetDir, fileName)
    }
    
    /**
     * 复制数据库文件
     */
    private fun copyDatabaseFile(sourceFile: File, targetFile: File) {
        // 确保目标目录存在
        targetFile.parentFile?.mkdirs()
        
        // 使用缓冲流复制文件
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8192) // 8KB缓冲区
                var bytesRead: Int
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                
                output.flush()
            }
        }
        
        // 验证文件大小
        if (targetFile.length() != sourceFile.length()) {
            throw Exception("文件复制不完整，源文件大小: ${sourceFile.length()}, 目标文件大小: ${targetFile.length()}")
        }
    }
    
    /**
     * 格式化文件大小
     */
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
    
    /**
     * 获取数据库文件信息
     */
    fun getDatabaseInfo(): DatabaseInfo {
        val dbFile = getSourceDatabaseFile()
        return DatabaseInfo(
            exists = dbFile.exists(),
            size = if (dbFile.exists()) dbFile.length() else 0L,
            path = dbFile.absolutePath,
            lastModified = if (dbFile.exists()) Date(dbFile.lastModified()) else null
        )
    }
    
    /**
     * 数据库信息数据类
     */
    data class DatabaseInfo(
        val exists: Boolean,
        val size: Long,
        val path: String,
        val lastModified: Date?
    )
} 