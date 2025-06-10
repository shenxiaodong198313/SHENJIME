package com.shenji.aikeyboard.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 屏幕截图管理器
 * 使用MediaProjection API实现屏幕截取功能
 * 支持持久化权限管理
 */
class ScreenCaptureManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenCaptureManager"
        const val REQUEST_CODE_SCREEN_CAPTURE = 1001
        
        @Volatile
        private var INSTANCE: ScreenCaptureManager? = null
        
        fun getInstance(context: Context): ScreenCaptureManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenCaptureManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private var mediaProjectionManager: MediaProjectionManager = 
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private val handler = Handler(Looper.getMainLooper())
    
    // 持久权限管理器
    private val permissionManager = ScreenCapturePermissionManager.getInstance(context)
    
    /**
     * 请求屏幕录制权限
     */
    fun createScreenCaptureIntent(): Intent {
        return mediaProjectionManager.createScreenCaptureIntent()
    }
    
    /**
     * 初始化MediaProjection
     * 同时保存权限以供后续使用
     */
    fun initializeMediaProjection(resultCode: Int, data: Intent) {
        try {
            Timber.d("$TAG: Initializing MediaProjection with resultCode: $resultCode")
            
            // 直接创建MediaProjection实例
            mediaProjection = permissionManager.createMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                throw IllegalStateException("Failed to create MediaProjection from permission data")
            }
            
            // 注册MediaProjection回调（Android 14+要求）
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Timber.d("$TAG: MediaProjection stopped")
                    cleanup()
                }
                
                override fun onCapturedContentResize(width: Int, height: Int) {
                    super.onCapturedContentResize(width, height)
                    Timber.d("$TAG: Captured content resized to ${width}x${height}")
                }
                
                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                    super.onCapturedContentVisibilityChanged(isVisible)
                    Timber.d("$TAG: Captured content visibility changed: $isVisible")
                }
            }, handler)
            
            Timber.d("$TAG: MediaProjection initialized successfully: ${mediaProjection != null}")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize MediaProjection")
            throw e
        }
    }
    
    /**
     * 尝试使用持久权限初始化MediaProjection
     */
    fun tryInitializeWithStoredPermission(): Boolean {
        return try {
            if (permissionManager.hasStoredPermission()) {
                // 新策略：不尝试恢复MediaProjection，只检查权限状态
                Timber.d("$TAG: Stored permission available, but MediaProjection needs fresh authorization")
                return false // 返回false，提示需要重新授权
            }
            
            Timber.d("$TAG: No stored permission available")
            false
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to check stored permission")
            false
        }
    }
    
    /**
     * 截取屏幕
     */
    suspend fun captureScreen(): Bitmap = suspendCancellableCoroutine { continuation ->
        var isCompleted = false
        
        try {
            // 检查MediaProjection是否可用
            if (mediaProjection == null) {
                val errorMessage = if (permissionManager.hasStoredPermission()) {
                    "屏幕录制权限需要重新授权"
                } else {
                    "请先授权屏幕录制权限"
                }
                
                continuation.resumeWithException(IllegalStateException(errorMessage))
                return@suspendCancellableCoroutine
            }
            
            val displayMetrics = context.resources.displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val density = displayMetrics.densityDpi
            
            Timber.d("$TAG: Starting screen capture - ${width}x${height}, density: $density")
            
            // 创建ImageReader
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            
            // 设置图像可用监听器
            imageReader?.setOnImageAvailableListener({ reader ->
                if (isCompleted) {
                    Timber.d("$TAG: Image available but already completed, skipping")
                    return@setOnImageAvailableListener
                }
                
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val bitmap = convertImageToBitmap(image, width, height)
                        image.close()
                        
                        // 标记为已完成
                        isCompleted = true
                        
                        // 清理资源
                        cleanup()
                        
                        // 返回结果
                        if (continuation.isActive) {
                            continuation.resume(bitmap)
                        }
                    } else {
                        if (!isCompleted && continuation.isActive) {
                            isCompleted = true
                            cleanup()
                            continuation.resumeWithException(RuntimeException("Failed to acquire image"))
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Error processing captured image")
                    if (!isCompleted && continuation.isActive) {
                        isCompleted = true
                        cleanup()
                        continuation.resumeWithException(e)
                    }
                }
            }, handler)
            
            // 创建VirtualDisplay的回调
            val virtualDisplayCallback = object : VirtualDisplay.Callback() {
                override fun onPaused() {
                    super.onPaused()
                    Timber.d("$TAG: VirtualDisplay paused")
                }
                
                override fun onResumed() {
                    super.onResumed()
                    Timber.d("$TAG: VirtualDisplay resumed")
                }
                
                override fun onStopped() {
                    super.onStopped()
                    Timber.d("$TAG: VirtualDisplay stopped")
                    if (!isCompleted) {
                        isCompleted = true
                        cleanup()
                        if (continuation.isActive) {
                            continuation.resumeWithException(RuntimeException("VirtualDisplay stopped unexpectedly"))
                        }
                    }
                }
            }
            
            // 创建虚拟显示
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                virtualDisplayCallback, handler
            )
            
            // 设置取消回调
            continuation.invokeOnCancellation {
                isCompleted = true
                cleanup()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error starting screen capture")
            if (!isCompleted) {
                isCompleted = true
                cleanup()
                continuation.resumeWithException(e)
            }
        }
    }
    
    /**
     * 将Image转换为Bitmap
     */
    private fun convertImageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width
        
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        
        bitmap.copyPixelsFromBuffer(buffer)
        
        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        }
    }
    
    /**
     * 清理资源
     */
    private fun cleanup() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            
            imageReader?.close()
            imageReader = null
            
            Timber.d("$TAG: Resources cleaned up")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error cleaning up resources")
        }
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean {
        return mediaProjection != null || permissionManager.isPermissionValid()
    }
    
    /**
     * 检查是否有活跃的MediaProjection实例
     */
    fun hasActiveMediaProjection(): Boolean {
        return mediaProjection != null
    }
    
    /**
     * 检查是否有存储的权限
     */
    fun hasStoredPermission(): Boolean {
        return permissionManager.hasStoredPermission()
    }
    
    /**
     * 清除存储的权限
     */
    fun clearStoredPermission() {
        permissionManager.clearPermission()
        mediaProjection = null
    }
    
    /**
     * 保存权限状态
     */
    fun savePermissionStatus(resultCode: Int, data: Intent?) {
        permissionManager.savePermission(resultCode, data)
    }
    
    /**
     * 释放MediaProjection
     */
    fun release() {
        try {
            cleanup()
            mediaProjection?.stop()
            mediaProjection = null
            // 注意：不要清除持久权限，只是释放当前实例
            Timber.d("$TAG: ScreenCaptureManager released")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error releasing ScreenCaptureManager")
        }
    }
} 