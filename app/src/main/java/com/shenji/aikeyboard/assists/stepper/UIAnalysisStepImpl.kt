package com.shenji.aikeyboard.assists.stepper

import android.graphics.Bitmap
import android.os.Build
import com.ven.assists.AssistsCore
import com.ven.assists.stepper.Step
import com.ven.assists.stepper.StepCollector
import com.ven.assists.stepper.StepImpl
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * UI分析步骤实现
 * 演示如何使用Assists框架进行界面元素分析和自动化操作
 */
class UIAnalysisStepImpl : StepImpl() {
    
    companion object {
        private const val TAG = "UIAnalysisStepImpl"
        
        // 步骤标签定义
        const val STEP_START = 1
        const val STEP_COLLECT_ELEMENTS = 2
        const val STEP_ANALYZE_LAYOUT = 3
        const val STEP_TAKE_SCREENSHOT = 4
        const val STEP_FINISH = 5
    }
    
    override fun onImpl(collector: StepCollector) {
        
        collector.next(STEP_START) {
            Timber.d("$TAG: 开始UI分析流程")
            runMain {
                // 检查无障碍服务状态
                if (!AssistsCore.isAccessibilityServiceEnabled()) {
                    Timber.w("$TAG: 无障碍服务未启用")
                }
            }
            
            delay(500)
            Step.get(STEP_COLLECT_ELEMENTS)
            
        }.next(STEP_COLLECT_ELEMENTS) {
            Timber.d("$TAG: 收集界面元素")
            
            runIO {
                try {
                    // 获取当前窗口包名
                    val packageName = AssistsCore.getPackageName()
                    Timber.d("$TAG: 当前应用包名: $packageName")
                    
                    // 获取所有节点
                    val allNodes = AssistsCore.getAllNodes()
                    Timber.d("$TAG: 找到 ${allNodes.size} 个节点")
                    
                    // 分析可点击元素
                    val clickableNodes = allNodes.filter { it.isClickable }
                    Timber.d("$TAG: 可点击元素: ${clickableNodes.size} 个")
                    
                    // 分析文本元素
                    val textNodes = allNodes.filter { !it.text.isNullOrBlank() }
                    Timber.d("$TAG: 文本元素: ${textNodes.size} 个")
                    
                    // 输出前几个文本内容作为示例
                    textNodes.take(5).forEach { node ->
                        Timber.d("$TAG: 文本: ${node.text}")
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: 收集元素时出错")
                }
            }
            
            delay(1000)
            Step.get(STEP_ANALYZE_LAYOUT)
            
        }.next(STEP_ANALYZE_LAYOUT) {
            Timber.d("$TAG: 分析界面布局")
            
            runIO {
                try {
                    // 查找常见的UI元素
                    val allNodes = AssistsCore.getAllNodes()
                    val buttons = allNodes.filter { it.className?.contains("Button") == true }
                    val editTexts = allNodes.filter { it.className?.contains("EditText") == true }
                    val textViews = allNodes.filter { it.className?.contains("TextView") == true }
                    
                    Timber.d("$TAG: 布局分析结果:")
                    Timber.d("$TAG: - 按钮: ${buttons.size} 个")
                    Timber.d("$TAG: - 输入框: ${editTexts.size} 个") 
                    Timber.d("$TAG: - 文本视图: ${textViews.size} 个")
                    
                    // 分析输入框内容
                    editTexts.forEach { editText ->
                        val text = editText.text?.toString()
                        val hint = editText.contentDescription?.toString()
                        Timber.d("$TAG: 输入框 - 文本: '$text', 提示: '$hint'")
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: 布局分析时出错")
                }
            }
            
            delay(800)
            Step.get(STEP_TAKE_SCREENSHOT)
            
        }.next(STEP_TAKE_SCREENSHOT) {
            Timber.d("$TAG: 尝试截图")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runIO {
                    try {
                        // 使用Assists框架截图
                        val bitmap = AssistsCore.takeScreenshot()
                        if (bitmap != null) {
                            Timber.d("$TAG: 截图成功 - 尺寸: ${bitmap.width}x${bitmap.height}")
                            
                            // 保存截图（可选）
                            val file = AssistsCore.takeScreenshotSave()
                            if (file != null) {
                                Timber.d("$TAG: 截图已保存到: ${file.absolutePath}")
                            }
                            
                            bitmap.recycle()
                        } else {
                            Timber.w("$TAG: 截图失败")
                        }
                        
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: 截图时出错")
                    }
                }
            } else {
                Timber.w("$TAG: 当前Android版本不支持无障碍截图")
            }
            
            delay(1000)
            Step.get(STEP_FINISH)
            
        }.next(STEP_FINISH) {
            Timber.d("$TAG: UI分析流程完成")
            
            runMain {
                // 这里可以发送完成通知或者更新UI
                Timber.i("$TAG: ✅ UI分析步骤执行完毕")
            }
            
            Step.none
        }
    }
} 