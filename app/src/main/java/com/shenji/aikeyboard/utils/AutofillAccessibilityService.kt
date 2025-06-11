package com.shenji.aikeyboard.utils

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import timber.log.Timber

/**
 * 增强无障碍服务，用于自动填写验证码和屏幕截图
 */
class AutofillAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutofillAccessibilityService"
        
        // 保存当前验证码
        private var currentVerificationCode: String? = null
        
        // 服务实例
        private var serviceInstance: AutofillAccessibilityService? = null
        
        /**
         * 设置当前验证码
         * @param code 验证码
         */
        fun setVerificationCode(code: String) {
            currentVerificationCode = code
        }
        
        /**
         * 检查服务是否启用
         * @param context 上下文
         * @return 服务是否启用
         */
        fun isServiceEnabled(context: Context): Boolean {
            try {
                val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
                val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityEvent.TYPES_ALL_MASK)
                
                val packageName = context.packageName
                Timber.d("$TAG: Checking for accessibility service in package: $packageName")
                
                // 打印所有已启用的服务，用于调试
                enabledServices.forEach { serviceInfo ->
                    Timber.d("$TAG: Found enabled service: ${serviceInfo.id}")
                }
                
                // 方法1：通过服务列表检查
                val foundInList = enabledServices.any { serviceInfo ->
                    val serviceId = serviceInfo.id
                    val isOurService = serviceId.contains(packageName) && 
                        serviceId.contains("AutofillAccessibilityService")
                    
                    if (isOurService) {
                        Timber.d("$TAG: Found our accessibility service: $serviceId")
                    }
                    
                    isOurService
                }
                
                // 方法2：通过设置检查（兼容性更好）
                val enabledSetting = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                
                val foundInSettings = enabledSetting?.contains(packageName) == true
                
                Timber.d("$TAG: Service check results - List: $foundInList, Settings: $foundInSettings")
                Timber.d("$TAG: Enabled services setting: $enabledSetting")
                
                // 只要其中一个方法检测到服务启用就返回true
                return foundInList || foundInSettings
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error checking accessibility service status")
                return false
            }
        }
        
        /**
         * 获取服务实例
         * @return 服务实例，如果服务未启用则返回null
         */
        fun getInstance(): AutofillAccessibilityService? {
            return serviceInstance
        }
        
        /**
         * 通过无障碍服务截取屏幕
         * @param callback 截图回调
         */
        @RequiresApi(Build.VERSION_CODES.R)
        fun takeScreenshotViaAccessibility(callback: (Bitmap?) -> Unit) {
            val instance = getInstance()
            if (instance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    instance.takeScreenshot(
                        android.view.Display.DEFAULT_DISPLAY,
                        instance.mainExecutor,
                        object : AccessibilityService.TakeScreenshotCallback {
                            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                                try {
                                    val originalBitmap = Bitmap.wrapHardwareBuffer(
                                        screenshot.hardwareBuffer,
                                        screenshot.colorSpace
                                    )
                                    
                                    // 确保Bitmap格式为ARGB_8888
                                    val convertedBitmap = if (originalBitmap?.config != Bitmap.Config.ARGB_8888) {
                                        Timber.d("$TAG: Converting bitmap from ${originalBitmap?.config} to ARGB_8888")
                                        originalBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                    } else {
                                        originalBitmap
                                    }
                                    
                                    Timber.d("$TAG: Screenshot taken successfully, size: ${convertedBitmap?.width}x${convertedBitmap?.height}, config: ${convertedBitmap?.config}")
                                    callback(convertedBitmap)
                                } catch (e: Exception) {
                                    Timber.e(e, "$TAG: Error converting screenshot to bitmap")
                                    callback(null)
                                }
                            }
                            
                            override fun onFailure(errorCode: Int) {
                                Timber.e("$TAG: Screenshot failed with error code: $errorCode")
                                callback(null)
                            }
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Error taking screenshot")
                    callback(null)
                }
            } else {
                Timber.w("$TAG: Accessibility service not available or Android version < R")
                callback(null)
            }
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInstance = this
        Timber.d("$TAG: Accessibility service connected")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
        Timber.d("$TAG: Accessibility service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 只处理窗口内容变化、窗口状态变化和视图焦点事件
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            return
        }
        
        // 检查是否有验证码需要填写
        val code = currentVerificationCode ?: return
        
        // 获取根节点
        val rootNode = rootInActiveWindow ?: return
        
        try {
            // 查找可编辑的文本框
            val editableNodes = findEditableNodes(rootNode)
            
            // 如果找到可编辑的文本框，尝试填写验证码
            if (editableNodes.isNotEmpty()) {
                Timber.d("找到${editableNodes.size}个可编辑文本框，尝试填写验证码")
                
                // 优先尝试找到空的或者提示输入验证码的文本框
                val targetNode = editableNodes.firstOrNull { node ->
                    val nodeText = node.text?.toString() ?: ""
                    nodeText.isEmpty() || 
                    nodeText.contains("验证码") || 
                    nodeText.contains("code") ||
                    nodeText.contains("请输入")
                } ?: editableNodes.first()
                
                // 尝试直接设置文本
                if (trySetText(targetNode, code)) {
                    Timber.d("成功填写验证码: $code")
                    // 清除当前验证码，避免重复填写
                    currentVerificationCode = null
                } else {
                    Timber.d("无法直接设置文本，尝试通过剪贴板填写")
                    // 尝试通过剪贴板填写
                    fillViaClipboard(targetNode, code)
                }
            } else {
                Timber.d("未找到可编辑的文本框")
            }
        } catch (e: Exception) {
            Timber.e(e, "自动填写验证码异常")
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 查找可编辑的文本框节点
     * @param rootNode 根节点
     * @return 可编辑的文本框节点列表
     */
    private fun findEditableNodes(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        
        // 遍历节点树
        findEditableNodesRecursive(rootNode, result)
        
        return result
    }
    
    /**
     * 递归查找可编辑的文本框节点
     * @param node 当前节点
     * @param result 结果列表
     */
    private fun findEditableNodesRecursive(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        // 检查节点是否可编辑
        if (node.isEditable) {
            result.add(node)
        }
        
        // 遍历子节点
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i) ?: continue
            findEditableNodesRecursive(childNode, result)
        }
    }
    
    /**
     * 尝试直接设置文本
     * @param node 节点
     * @param text 文本
     * @return 是否成功设置
     */
    private fun trySetText(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
    
    /**
     * 通过剪贴板填写文本
     * @param node 节点
     * @param text 文本
     */
    private fun fillViaClipboard(node: AccessibilityNodeInfo, text: String) {
        // 获取剪贴板管理器
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        // 创建剪贴板数据
        val clipData = ClipData.newPlainText("验证码", text)
        
        // 设置剪贴板数据
        clipboardManager.setPrimaryClip(clipData)
        
        // 先清空文本框
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        
        // 尝试粘贴
        node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    override fun onInterrupt() {
        Timber.d("无障碍服务中断")
    }
} 