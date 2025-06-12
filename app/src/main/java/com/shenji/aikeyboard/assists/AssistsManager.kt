package com.shenji.aikeyboard.assists

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.shenji.aikeyboard.assists.service.EnhancedAssistsService
import com.shenji.aikeyboard.assists.stepper.UIAnalysisStepImpl
import com.ven.assists.AssistsCore
import com.ven.assists.stepper.StepManager
import timber.log.Timber

/**
 * Assists框架管理器
 * 统一管理Assists框架的初始化和功能调用
 */
object AssistsManager {
    
    private const val TAG = "AssistsManager"
    
    /**
     * 初始化Assists框架
     */
    fun initialize(context: Context) {
        try {
            // 使用Application上下文初始化AssistsCore
            AssistsCore.init(context.applicationContext as android.app.Application)
            Timber.d("$TAG: Assists框架初始化完成")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Assists框架初始化失败")
        }
    }
    
    /**
     * 检查Assists无障碍服务是否启用
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return EnhancedAssistsService.isServiceEnabled()
    }
    
    /**
     * 打开无障碍服务设置页面
     */
    fun openAccessibilitySettings() {
        try {
            EnhancedAssistsService.openAccessibilitySettings()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 打开无障碍设置失败")
        }
    }
    
    /**
     * 获取当前应用包名
     */
    fun getCurrentPackageName(): String {
        return EnhancedAssistsService.getCurrentPackageName()
    }
    
    /**
     * 获取所有文本节点
     */
    fun getAllTextNodes(callback: (List<String>) -> Unit) {
        EnhancedAssistsService.getAllTextNodes(callback)
    }
    
    /**
     * 查找包含指定文本的节点
     */
    fun findNodesByText(text: String): List<AccessibilityNodeInfo> {
        return if (isAccessibilityServiceEnabled()) {
            AssistsCore.findByText(text)
        } else {
            emptyList()
        }
    }
    
    /**
     * 查找指定ID的节点
     */
    fun findNodesById(id: String): List<AccessibilityNodeInfo> {
        return if (isAccessibilityServiceEnabled()) {
            AssistsCore.findById(id)
        } else {
            emptyList()
        }
    }
    
    /**
     * 获取所有节点
     */
    fun getAllNodes(): List<AccessibilityNodeInfo> {
        return if (isAccessibilityServiceEnabled()) {
            AssistsCore.getAllNodes()
        } else {
            emptyList()
        }
    }
    
    /**
     * 执行UI分析步骤
     */
    fun executeUIAnalysis() {
        try {
            if (!isAccessibilityServiceEnabled()) {
                Timber.w("$TAG: 无障碍服务未启用，无法执行UI分析")
                return
            }
            
            // 执行UI分析步骤，使用默认的起始步骤标识
            com.ven.assists.stepper.StepManager.execute(
                UIAnalysisStepImpl::class.java, 
                UIAnalysisStepImpl.STEP_START, 
                begin = true
            )
            Timber.d("$TAG: UI分析步骤已启动")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 执行UI分析失败")
        }
    }
    
    /**
     * 点击指定节点
     */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        return try {
            if (isAccessibilityServiceEnabled()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 点击节点失败")
            false
        }
    }
    
    /**
     * 设置节点文本
     */
    fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            if (isAccessibilityServiceEnabled()) {
                val arguments = android.os.Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 设置节点文本失败")
            false
        }
    }
    
    /**
     * 滚动页面
     */
    fun scrollForward(): Boolean {
        return try {
            if (isAccessibilityServiceEnabled()) {
                val scrollableNodes = getAllNodes().filter { it.isScrollable }
                if (scrollableNodes.isNotEmpty()) {
                    scrollableNodes.first().performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 滚动页面失败")
            false
        }
    }
    
    /**
     * 返回操作
     */
    fun performGlobalBack(): Boolean {
        return try {
            if (isAccessibilityServiceEnabled()) {
                // 这里需要获取服务实例来执行全局返回
                // 目前AssistsCore没有直接提供此功能，可能需要扩展
                false
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 执行返回操作失败")
            false
        }
    }
    
    /**
     * 获取服务状态信息
     */
    fun getServiceStatus(): String {
        return if (isAccessibilityServiceEnabled()) {
            val packageName = getCurrentPackageName()
            val nodeCount = getAllNodes().size
            "Assists服务正常\n当前应用: $packageName\n节点数量: $nodeCount"
        } else {
            "Assists服务未启用\n请在设置中开启无障碍服务"
        }
    }
} 