package com.shenji.aikeyboard.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * 权限管理工具类
 * 处理外部存储权限的请求和检查
 */
class PermissionManager {
    
    companion object {
        const val REQUEST_WRITE_EXTERNAL_STORAGE = 1001
        const val REQUEST_MANAGE_EXTERNAL_STORAGE = 1002
        
        /**
         * 检查是否有写入外部存储的权限
         */
        fun hasWriteExternalStoragePermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 使用 MANAGE_EXTERNAL_STORAGE
                Environment.isExternalStorageManager()
            } else {
                // Android 10 及以下使用 WRITE_EXTERNAL_STORAGE
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        
        /**
         * 请求写入外部存储权限
         */
        fun requestWriteExternalStoragePermission(activity: Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 需要特殊权限
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE)
                } catch (e: Exception) {
                    Timber.e(e, "无法打开存储权限设置页面")
                    // 如果无法打开特定应用的设置页面，打开通用设置页面
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activity.startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE)
                }
            } else {
                // Android 10 及以下
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_EXTERNAL_STORAGE
                )
            }
        }
        
        /**
         * 检查权限请求结果
         */
        fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
        ): Boolean {
            return when (requestCode) {
                REQUEST_WRITE_EXTERNAL_STORAGE -> {
                    grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                }
                else -> false
            }
        }
        
        /**
         * 检查Activity结果（用于Android 11+的特殊权限）
         */
        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
            return when (requestCode) {
                REQUEST_MANAGE_EXTERNAL_STORAGE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager()
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
        
        /**
         * 获取下载目录路径
         */
        fun getDownloadDirectory(): String {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        }
        
        /**
         * 检查下载目录是否可用
         */
        fun isDownloadDirectoryAvailable(): Boolean {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return downloadDir.exists() || downloadDir.mkdirs()
        }
    }
} 