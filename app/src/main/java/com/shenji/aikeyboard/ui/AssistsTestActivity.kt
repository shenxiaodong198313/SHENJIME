package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.assists.AssistsManager
import com.shenji.aikeyboard.assists.service.EnhancedAssistsService
import com.shenji.aikeyboard.databinding.ActivityAssistsTestBinding
import com.ven.assists.AssistsCore
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Assists框架测试Activity
 * 提供完整的Assists框架功能测试和诊断界面
 */
class AssistsTestActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAssistsTestBinding
    private val TAG = "AssistsTestActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityAssistsTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkServiceStatus()
    }
    
    override fun onResume() {
        super.onResume()
        checkServiceStatus()
    }
    
    private fun setupUI() {
        // 标题栏
        supportActionBar?.title = "Assists框架测试"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 服务状态检查
        binding.btnCheckService.setOnClickListener {
            checkServiceStatus()
        }
        
        // 打开无障碍设置
        binding.btnOpenSettings.setOnClickListener {
            try {
                AssistsManager.openAccessibilitySettings()
                showToast("正在打开无障碍设置...")
            } catch (e: Exception) {
                showToast("打开设置失败: ${e.message}")
            }
        }
        
        // 获取当前应用信息
        binding.btnGetAppInfo.setOnClickListener {
            getCurrentAppInfo()
        }
        
        // 获取所有节点
        binding.btnGetAllNodes.setOnClickListener {
            getAllNodes()
        }
        
        // 获取文本节点
        binding.btnGetTextNodes.setOnClickListener {
            getTextNodes()
        }
        
        // 查找节点测试
        binding.btnFindNodes.setOnClickListener {
            findNodesTest()
        }
        
        // AssistsCore直接测试
        binding.btnAssistsCoreTest.setOnClickListener {
            assistsCoreDirectTest()
        }
        
        // 服务状态详情
        binding.btnServiceDetails.setOnClickListener {
            getServiceDetails()
        }
        
        // 清空日志
        binding.btnClearLog.setOnClickListener {
            binding.tvLogOutput.text = ""
        }
    }
    
    private fun checkServiceStatus() {
        lifecycleScope.launch {
            try {
                val isEnabled = AssistsManager.isAccessibilityServiceEnabled()
                val assistsCoreEnabled = AssistsCore.isAccessibilityServiceEnabled()
                val enhancedServiceEnabled = EnhancedAssistsService.isServiceEnabled()
                
                updateServiceStatusUI(isEnabled, assistsCoreEnabled, enhancedServiceEnabled)
                
                logMessage("=== 服务状态检查 ===")
                logMessage("AssistsManager检查: $isEnabled")
                logMessage("AssistsCore检查: $assistsCoreEnabled")
                logMessage("EnhancedService检查: $enhancedServiceEnabled")
                
                if (isEnabled) {
                    logMessage("✅ Assists无障碍服务已启用")
                } else {
                    logMessage("❌ Assists无障碍服务未启用")
                    logMessage("请点击'打开无障碍设置'按钮启用服务")
                }
                
            } catch (e: Exception) {
                logMessage("❌ 检查服务状态时出错: ${e.message}")
                Timber.e(e, "检查服务状态失败")
            }
        }
    }
    
    private fun updateServiceStatusUI(
        assistsManagerEnabled: Boolean,
        assistsCoreEnabled: Boolean, 
        enhancedServiceEnabled: Boolean
    ) {
        val allEnabled = assistsManagerEnabled && assistsCoreEnabled && enhancedServiceEnabled
        
        binding.tvServiceStatus.text = if (allEnabled) {
            "✅ 服务正常运行"
        } else {
            "❌ 服务未启用"
        }
        
        binding.tvServiceStatus.setTextColor(
            if (allEnabled) {
                resources.getColor(android.R.color.holo_green_dark, null)
            } else {
                resources.getColor(android.R.color.holo_red_dark, null)
            }
        )
        
        // 启用/禁用测试按钮
        val testEnabled = allEnabled
        binding.btnGetAppInfo.isEnabled = testEnabled
        binding.btnGetAllNodes.isEnabled = testEnabled
        binding.btnGetTextNodes.isEnabled = testEnabled
        binding.btnFindNodes.isEnabled = testEnabled
        binding.btnAssistsCoreTest.isEnabled = testEnabled
        binding.btnServiceDetails.isEnabled = testEnabled
    }
    
    private fun getCurrentAppInfo() {
        lifecycleScope.launch {
            try {
                logMessage("=== 当前应用信息 ===")
                
                val packageName = AssistsManager.getCurrentPackageName()
                logMessage("包名: $packageName")
                
                val allNodes = AssistsManager.getAllNodes()
                logMessage("节点总数: ${allNodes.size}")
                
                val clickableCount = allNodes.count { it.isClickable }
                val editableCount = allNodes.count { it.isEditable }
                val scrollableCount = allNodes.count { it.isScrollable }
                
                logMessage("可点击节点: $clickableCount")
                logMessage("可编辑节点: $editableCount")
                logMessage("可滚动节点: $scrollableCount")
                
            } catch (e: Exception) {
                logMessage("❌ 获取应用信息失败: ${e.message}")
            }
        }
    }
    
    private fun getAllNodes() {
        lifecycleScope.launch {
            try {
                logMessage("=== 获取所有节点 ===")
                
                val allNodes = AssistsManager.getAllNodes()
                logMessage("共找到 ${allNodes.size} 个节点")
                
                if (allNodes.isNotEmpty()) {
                    logMessage("前10个节点信息:")
                    allNodes.take(10).forEachIndexed { index, node ->
                        val text = node.text?.toString() ?: ""
                        val desc = node.contentDescription?.toString() ?: ""
                        val className = node.className?.toString()?.substringAfterLast('.') ?: ""
                        val viewId = node.viewIdResourceName?.substringAfterLast('.') ?: ""
                        
                        val nodeInfo = StringBuilder("${index + 1}. ")
                        if (text.isNotBlank()) nodeInfo.append("文本:$text ")
                        if (desc.isNotBlank()) nodeInfo.append("描述:$desc ")
                        if (className.isNotBlank()) nodeInfo.append("类型:$className ")
                        if (viewId.isNotBlank()) nodeInfo.append("ID:$viewId ")
                        
                        logMessage(nodeInfo.toString().trim())
                    }
                    
                    if (allNodes.size > 10) {
                        logMessage("... 还有 ${allNodes.size - 10} 个节点")
                    }
                } else {
                    logMessage("未找到任何节点")
                }
                
            } catch (e: Exception) {
                logMessage("❌ 获取节点失败: ${e.message}")
            }
        }
    }
    
    private fun getTextNodes() {
        lifecycleScope.launch {
            try {
                logMessage("=== 获取文本节点 ===")
                
                AssistsManager.getAllTextNodes { textNodes ->
                    lifecycleScope.launch {
                        logMessage("EnhancedAssistsService返回 ${textNodes.size} 个结果:")
                        textNodes.take(20).forEachIndexed { index, text ->
                            logMessage("${index + 1}. $text")
                        }
                        
                        if (textNodes.size > 20) {
                            logMessage("... 还有 ${textNodes.size - 20} 个结果")
                        }
                    }
                }
                
            } catch (e: Exception) {
                logMessage("❌ 获取文本节点失败: ${e.message}")
            }
        }
    }
    
    private fun findNodesTest() {
        lifecycleScope.launch {
            try {
                logMessage("=== 节点查找测试 ===")
                
                // 测试按文本查找
                val textNodes = AssistsManager.findNodesByText("测试")
                logMessage("包含'测试'的节点: ${textNodes.size} 个")
                
                // 测试按常见ID查找
                val commonIds = listOf("android:id/content", "android:id/text1", "android:id/button1")
                commonIds.forEach { id ->
                    val nodes = AssistsManager.findNodesById(id)
                    if (nodes.isNotEmpty()) {
                        logMessage("ID '$id' 找到 ${nodes.size} 个节点")
                    }
                }
                
                // 直接使用AssistsCore测试
                if (AssistsCore.isAccessibilityServiceEnabled()) {
                    val coreNodes = AssistsCore.getAllNodes()
                    logMessage("AssistsCore直接获取: ${coreNodes.size} 个节点")
                }
                
            } catch (e: Exception) {
                logMessage("❌ 节点查找测试失败: ${e.message}")
            }
        }
    }
    
    private fun assistsCoreDirectTest() {
        lifecycleScope.launch {
            try {
                logMessage("=== AssistsCore直接测试 ===")
                
                if (!AssistsCore.isAccessibilityServiceEnabled()) {
                    logMessage("❌ AssistsCore报告服务未启用")
                    return@launch
                }
                
                logMessage("✅ AssistsCore服务状态: 正常")
                
                val packageName = AssistsCore.getPackageName()
                logMessage("当前包名: $packageName")
                
                val allNodes = AssistsCore.getAllNodes()
                logMessage("总节点数: ${allNodes.size}")
                
                // 按类型统计节点
                val nodeTypes = allNodes.groupBy { it.className?.toString() ?: "unknown" }
                logMessage("节点类型统计:")
                nodeTypes.entries.take(10).forEach { (type, nodes) ->
                    logMessage("  ${type.substringAfterLast('.')}: ${nodes.size} 个")
                }
                
                // 查找可交互节点
                val clickableNodes = allNodes.filter { it.isClickable }
                val editableNodes = allNodes.filter { it.isEditable }
                val scrollableNodes = allNodes.filter { it.isScrollable }
                
                logMessage("可交互节点统计:")
                logMessage("  可点击: ${clickableNodes.size}")
                logMessage("  可编辑: ${editableNodes.size}")
                logMessage("  可滚动: ${scrollableNodes.size}")
                
            } catch (e: Exception) {
                logMessage("❌ AssistsCore测试失败: ${e.message}")
            }
        }
    }
    
    private fun getServiceDetails() {
        lifecycleScope.launch {
            try {
                logMessage("=== 服务详细信息 ===")
                
                val statusInfo = AssistsManager.getServiceStatus()
                logMessage(statusInfo)
                
                // 检查服务实例
                logMessage("\n服务实例检查:")
                
                val assistsServiceInstance = com.ven.assists.service.AssistsService.instance
                logMessage("AssistsService实例: ${if (assistsServiceInstance != null) "存在" else "null"}")
                
                if (assistsServiceInstance != null) {
                    try {
                        val rootNode = assistsServiceInstance.rootInActiveWindow
                        logMessage("根节点可用: ${rootNode != null}")
                        
                        if (rootNode != null) {
                            logMessage("根节点包名: ${rootNode.packageName}")
                            logMessage("根节点子节点数: ${rootNode.childCount}")
                        }
                    } catch (e: Exception) {
                        logMessage("获取根节点信息失败: ${e.message}")
                    }
                }
                
            } catch (e: Exception) {
                logMessage("❌ 获取服务详情失败: ${e.message}")
            }
        }
    }
    
    private fun logMessage(message: String) {
        val timestamp = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        val logEntry = "[$timestamp] $message\n"
        
        runOnUiThread {
            binding.tvLogOutput.append(logEntry)
            binding.scrollView.post {
                binding.scrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN)
            }
        }
        
        Timber.d("$TAG: $message")
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 