package com.shenji.aikeyboard.utils

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

/**
 * 无障碍服务，用于自动填写验证码
 */
class AutofillAccessibilityService : AccessibilityService() {

    companion object {
        // 保存当前验证码
        private var currentVerificationCode: String? = null
        
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
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityEvent.TYPES_ALL_MASK)
            
            return enabledServices.any {
                it.id.contains(AutofillAccessibilityService::class.java.name)
            }
        }
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