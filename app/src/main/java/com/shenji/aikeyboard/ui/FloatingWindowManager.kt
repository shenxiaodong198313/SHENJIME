package com.shenji.aikeyboard.ui

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber

/**
 * 悬浮窗管理器
 * 统一管理悬浮窗的启动、停止和设置
 */
class FloatingWindowManager private constructor(private val context: Context) {
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "FloatingWindowManager"
        private const val PREFS_NAME = "floating_window_prefs"
        private const val KEY_FLOATING_ENABLED = "floating_enabled"
        
        @Volatile
        private var INSTANCE: FloatingWindowManager? = null
        
        fun getInstance(context: Context): FloatingWindowManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FloatingWindowManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 检查悬浮窗是否已启用
     */
    fun isFloatingWindowEnabled(): Boolean {
        return preferences.getBoolean(KEY_FLOATING_ENABLED, false)
    }
    
    /**
     * 设置悬浮窗启用状态
     */
    fun setFloatingWindowEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FLOATING_ENABLED, enabled).apply()
        Timber.d("$TAG: Floating window enabled: $enabled")
        
        if (enabled) {
            startFloatingWindow()
        } else {
            stopFloatingWindow()
        }
    }
    
    /**
     * 启动悬浮窗
     */
    fun startFloatingWindow() {
        if (!FloatingWindowService.canDrawOverlays(context)) {
            Timber.w("$TAG: No overlay permission, requesting permission")
            FloatingWindowService.requestOverlayPermission(context)
            return
        }
        
        Timber.d("$TAG: Starting floating window service")
        FloatingWindowService.startService(context)
    }
    
    /**
     * 停止悬浮窗
     */
    fun stopFloatingWindow() {
        Timber.d("$TAG: Stopping floating window service")
        FloatingWindowService.stopService(context)
    }
    
    /**
     * 检查并请求悬浮窗权限
     */
    fun checkAndRequestPermission(): Boolean {
        return if (FloatingWindowService.canDrawOverlays(context)) {
            true
        } else {
            FloatingWindowService.requestOverlayPermission(context)
            false
        }
    }
    
    /**
     * 切换悬浮窗状态
     */
    fun toggleFloatingWindow() {
        val currentState = isFloatingWindowEnabled()
        setFloatingWindowEnabled(!currentState)
    }
} 