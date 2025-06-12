package com.shenji.aikeyboard.assists.service

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.ven.assists.AssistsCore
import com.ven.assists.service.AssistsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 基于Assists框架的无障碍服务
 * 提供界面分析、自动化操作等功能
 */
class EnhancedAssistsService : AssistsService() {
    
    companion object {
        private const val TAG = "EnhancedAssistsService"
        
        /**
         * 获取所有文本节点 - 使用Assists框架API
         */
        fun getAllTextNodes(callback: (List<String>) -> Unit) {
            try {
                // 检查服务是否可用
                if (!AssistsCore.isAccessibilityServiceEnabled()) {
                    Timber.w("$TAG: Assists无障碍服务未启用")
                    callback(listOf("Assists无障碍服务未启用，请在设置中开启"))
                    return
                }
                
                val allTextNodes = mutableListOf<String>()
                
                // 获取当前窗口包名
                val packageName = AssistsCore.getPackageName()
                if (packageName.isNotEmpty()) {
                    allTextNodes.add("当前应用: $packageName")
                }
                
                // 使用Assists框架获取所有节点
                val allNodes = AssistsCore.getAllNodes()
                Timber.d("$TAG: 使用Assists框架获取到 ${allNodes.size} 个节点")
                
                if (allNodes.isNotEmpty()) {
                    // 收集有文本内容的节点
                    val textNodes = allNodes.filter { node ->
                        val text = node.text?.toString()
                        val desc = node.contentDescription?.toString()
                        !text.isNullOrBlank() || !desc.isNullOrBlank()
                    }
                    
                    allTextNodes.add("=== 发现 ${textNodes.size} 个有文本的节点 ===")
                    
                    textNodes.forEachIndexed { index, node ->
                        val text = node.text?.toString() ?: ""
                        val desc = node.contentDescription?.toString() ?: ""
                        val className = node.className?.toString() ?: ""
                        val viewId = node.viewIdResourceName ?: ""
                        
                        val nodeInfo = StringBuilder()
                        if (text.isNotBlank()) {
                            nodeInfo.append("文本: $text")
                        }
                        if (desc.isNotBlank()) {
                            if (nodeInfo.isNotEmpty()) nodeInfo.append(" | ")
                            nodeInfo.append("描述: $desc")
                        }
                        if (className.isNotBlank()) {
                            if (nodeInfo.isNotEmpty()) nodeInfo.append(" | ")
                            nodeInfo.append("类型: ${className.substringAfterLast('.')}")
                        }
                        if (viewId.isNotBlank()) {
                            if (nodeInfo.isNotEmpty()) nodeInfo.append(" | ")
                            nodeInfo.append("ID: ${viewId.substringAfterLast('.')}")
                        }
                        
                        // 添加节点状态信息
                        val states = mutableListOf<String>()
                        if (node.isClickable) states.add("可点击")
                        if (node.isEditable) states.add("可编辑")
                        if (node.isScrollable) states.add("可滚动")
                        if (node.isCheckable) states.add("可选择")
                        if (states.isNotEmpty()) {
                            nodeInfo.append(" | 状态: ${states.joinToString(",")}")
                        }
                        
                        if (nodeInfo.isNotEmpty()) {
                            allTextNodes.add("${index + 1}. $nodeInfo")
                        }
                    }
                    
                    // 添加节点统计信息
                    val clickableCount = allNodes.count { it.isClickable }
                    val editableCount = allNodes.count { it.isEditable }
                    val scrollableCount = allNodes.count { it.isScrollable }
                    
                    allTextNodes.add("")
                    allTextNodes.add("=== 节点统计 ===")
                    allTextNodes.add("总节点数: ${allNodes.size}")
                    allTextNodes.add("可点击: $clickableCount")
                    allTextNodes.add("可编辑: $editableCount")
                    allTextNodes.add("可滚动: $scrollableCount")
                    
                } else {
                    allTextNodes.add("当前界面没有发现任何节点")
                }
                
                callback(allTextNodes)
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 获取文本节点失败")
                callback(listOf("获取文本节点时出错: ${e.message}"))
            }
        }
        
        /**
         * 检查Assists服务是否启用
         */
        fun isServiceEnabled(): Boolean {
            return AssistsCore.isAccessibilityServiceEnabled()
        }
        
        /**
         * 打开无障碍设置页面
         */
        fun openAccessibilitySettings() {
            try {
                AssistsCore.openAccessibilitySetting()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 打开无障碍设置失败")
            }
        }
        
        /**
         * 获取当前应用包名
         */
        fun getCurrentPackageName(): String {
            return AssistsCore.getPackageName()
        }
        
        /**
         * 查找指定文本的节点
         */
        fun findNodesByText(text: String): List<AccessibilityNodeInfo> {
            return if (AssistsCore.isAccessibilityServiceEnabled()) {
                AssistsCore.findByText(text)
            } else {
                emptyList()
            }
        }
        
        /**
         * 查找指定ID的节点
         */
        fun findNodesById(id: String): List<AccessibilityNodeInfo> {
            return if (AssistsCore.isAccessibilityServiceEnabled()) {
                AssistsCore.findById(id)
            } else {
                emptyList()
            }
        }
        
        /**
         * 截图功能（需要Android R+）
         */
        @RequiresApi(Build.VERSION_CODES.R)
        fun takeScreenshotViaAccessibility(callback: (Bitmap?) -> Unit) {
            try {
                if (!isServiceEnabled()) {
                    Timber.w("$TAG: 无障碍服务未启用，无法截图")
                    callback(null)
                    return
                }
                
                // 使用协程执行异步截图
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val bitmap = AssistsCore.takeScreenshot()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            callback(bitmap)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: 截图执行失败")
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            callback(null)
                        }
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 截图失败")
                callback(null)
            }
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("$TAG: Assists无障碍服务创建")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.d("$TAG: Assists无障碍服务已连接")
        
        // 服务连接成功，可以使用AssistsCore的所有功能
        serviceScope.launch {
            try {
                val packageName = AssistsCore.getPackageName()
                Timber.d("$TAG: 当前应用: $packageName")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 获取当前应用包名失败")
            }
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        super.onAccessibilityEvent(event)
        
        // 记录界面变化事件（用于调试）
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: "unknown"
            val className = event.className?.toString() ?: "unknown"
            Timber.d("$TAG: 界面变化 - 包名: $packageName, 类名: ${className.substringAfterLast('.')}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("$TAG: Assists无障碍服务销毁")
    }
    
    override fun onInterrupt() {
        Timber.w("$TAG: Assists无障碍服务被中断")
    }
} 