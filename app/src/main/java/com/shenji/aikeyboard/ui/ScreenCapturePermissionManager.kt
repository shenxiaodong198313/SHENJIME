package com.shenji.aikeyboard.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import timber.log.Timber

/**
 * 屏幕录制权限持久化管理器
 * 实现权限状态记录，简化管理策略
 */
class ScreenCapturePermissionManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenCapturePermissionManager"
        private const val PREFS_NAME = "screen_capture_permissions"
        private const val KEY_HAS_PERMISSION = "has_permission"
        private const val KEY_PERMISSION_GRANTED_TIME = "permission_granted_time"
        
        @Volatile
        private var INSTANCE: ScreenCapturePermissionManager? = null
        
        fun getInstance(context: Context): ScreenCapturePermissionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenCapturePermissionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mediaProjectionManager: MediaProjectionManager = 
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    
    /**
     * 记录屏幕录制权限已授权
     */
    fun savePermission(resultCode: Int, data: Intent?) {
        try {
            if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                val editor = sharedPrefs.edit()
                editor.putBoolean(KEY_HAS_PERMISSION, true)
                editor.putLong(KEY_PERMISSION_GRANTED_TIME, System.currentTimeMillis())
                editor.apply()
                
                Timber.d("$TAG: Screen capture permission status saved successfully")
            } else {
                Timber.w("$TAG: Invalid permission data, clearing stored permission")
                clearPermission()
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to save screen capture permission status")
            clearPermission()
        }
    }
    
    /**
     * 检查是否有授权记录
     */
    fun hasStoredPermission(): Boolean {
        val hasPermission = sharedPrefs.getBoolean(KEY_HAS_PERMISSION, false)
        val grantedTime = sharedPrefs.getLong(KEY_PERMISSION_GRANTED_TIME, 0)
        
        // 检查权限是否在合理时间内授权（24小时内认为有效）
        val isRecentlyGranted = (System.currentTimeMillis() - grantedTime) < (24 * 60 * 60 * 1000)
        
        val result = hasPermission && grantedTime > 0 && isRecentlyGranted
        Timber.d("$TAG: hasStoredPermission - hasPermission=$hasPermission, isRecentlyGranted=$isRecentlyGranted, result=$result")
        
        return result
    }
    
    /**
     * 清除保存的权限状态
     */
    fun clearPermission() {
        try {
            sharedPrefs.edit().clear().apply()
            Timber.d("$TAG: Screen capture permission status cleared")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error clearing permission status")
        }
    }
    
    /**
     * 创建屏幕录制权限请求Intent
     */
    fun createScreenCaptureIntent(): Intent {
        return mediaProjectionManager.createScreenCaptureIntent()
    }
    
    /**
     * 检查权限是否有效（简化版本）
     */
    fun isPermissionValid(): Boolean {
        val result = hasStoredPermission()
        Timber.d("$TAG: isPermissionValid - result=$result")
        return result
    }
    
    /**
     * 获取MediaProjectionManager实例
     */
    fun getMediaProjectionManager(): MediaProjectionManager {
        return mediaProjectionManager
    }
    
    /**
     * 创建MediaProjection（需要实时权限数据）
     */
    fun createMediaProjection(resultCode: Int, data: Intent): MediaProjection? {
        return try {
            val projection = mediaProjectionManager.getMediaProjection(resultCode, data)
            Timber.d("$TAG: MediaProjection created successfully")
            projection
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to create MediaProjection")
            null
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        // 简化版本不需要特殊清理
        Timber.d("$TAG: ScreenCapturePermissionManager released")
    }
} 