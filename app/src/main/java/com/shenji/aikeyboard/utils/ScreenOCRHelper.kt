package com.shenji.aikeyboard.utils

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.ven.assists.AssistsCore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 屏幕OCR识别工具类
 * 使用Google ML Kit进行文本识别，专门用于识别微信顶部菜单栏的对话对象昵称
 */
object ScreenOCRHelper {
    
    private const val TAG = "ScreenOCRHelper"
    
    // 中文文本识别器
    private val textRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    
    /**
     * 获取屏幕顶部区域的文本
     * 专门用于识别微信顶部导航栏的对话对象昵称
     * 
     * @param topHeightRatio 顶部区域高度占屏幕高度的比例，默认0.15（15%）
     * @return 识别到的文本内容，如果识别失败返回null
     */
    suspend fun getTopAreaText(topHeightRatio: Float = 0.15f): String? {
        return try {
            // 获取屏幕截图
            val screenBitmap = AssistsCore.takeScreenshot()
            if (screenBitmap == null) {
                Log.e(TAG, "Failed to take screenshot")
                return null
            }
            
            // 裁剪顶部区域
            val topAreaBitmap = cropTopArea(screenBitmap, topHeightRatio)
            if (topAreaBitmap == null) {
                Log.e(TAG, "Failed to crop top area")
                return null
            }
            
            // 进行OCR识别
            val recognizedText = recognizeText(topAreaBitmap)
            Log.d(TAG, "OCR识别结果: $recognizedText")
            
            // 从识别结果中提取对话对象昵称
            extractContactName(recognizedText)
            
        } catch (e: Exception) {
            Log.e(TAG, "OCR识别异常", e)
            null
        }
    }
    
    /**
     * 裁剪屏幕顶部区域
     * 
     * @param bitmap 原始屏幕截图
     * @param topHeightRatio 顶部区域高度比例
     * @return 裁剪后的顶部区域图片
     */
    private fun cropTopArea(bitmap: Bitmap, topHeightRatio: Float): Bitmap? {
        return try {
            val screenWidth = bitmap.width
            val screenHeight = bitmap.height
            val topHeight = (screenHeight * topHeightRatio).toInt()
            
            // 裁剪顶部区域
            Bitmap.createBitmap(bitmap, 0, 0, screenWidth, topHeight)
        } catch (e: Exception) {
            Log.e(TAG, "裁剪顶部区域失败", e)
            null
        }
    }
    
    /**
     * 使用ML Kit进行文本识别
     * 
     * @param bitmap 要识别的图片
     * @return 识别到的文本内容
     */
    private suspend fun recognizeText(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val recognizedText = visionText.text
                    Log.d(TAG, "ML Kit识别成功: $recognizedText")
                    continuation.resume(recognizedText)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit识别失败", e)
                    continuation.resume(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "创建InputImage失败", e)
            continuation.resume(null)
        }
    }
    
    /**
     * 从OCR识别结果中提取联系人昵称
     * 过滤掉常见的系统UI文本，提取真正的对话对象昵称
     * 
     * @param recognizedText OCR识别的原始文本
     * @return 提取的联系人昵称，如果提取失败返回null
     */
    private fun extractContactName(recognizedText: String?): String? {
        if (recognizedText.isNullOrBlank()) {
            return null
        }
        
        // 按行分割文本
        val lines = recognizedText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        // 过滤掉常见的系统UI文本
        val filteredLines = lines.filter { line ->
            !isSystemUIText(line)
        }
        
        Log.d(TAG, "过滤后的文本行: $filteredLines")
        
        // 寻找最可能是联系人昵称的文本
        // 优先选择中文昵称，然后是其他可能的昵称
        
        // 第一优先级：包含中文字符的文本
        for (line in filteredLines) {
            if (isPossibleContactName(line) && containsChinese(line)) {
                Log.d(TAG, "提取到中文联系人昵称: $line")
                return line
            }
        }
        
        // 第二优先级：其他可能的联系人昵称（排除单个符号）
        for (line in filteredLines) {
            if (isPossibleContactName(line) && line.length > 1 && !isSingleSymbol(line)) {
                Log.d(TAG, "提取到联系人昵称: $line")
                return line
            }
        }
        
        // 第三优先级：任何非系统文本（排除单个符号）
        for (line in filteredLines) {
            if (line.length > 1 && !isSingleSymbol(line)) {
                Log.d(TAG, "使用非符号文本作为联系人昵称: $line")
                return line
            }
        }
        
        // 最后选择：第一个非系统文本
        return filteredLines.firstOrNull()?.also {
            Log.d(TAG, "使用第一个非系统文本作为联系人昵称: $it")
        }
    }
    
    /**
     * 判断是否为系统UI文本
     * 过滤掉微信界面中的系统按钮和状态文本
     */
    private fun isSystemUIText(text: String): Boolean {
        val systemTexts = setOf(
            "微信", "WeChat", "返回", "更多", "搜索", "添加", "设置",
            "消息", "通讯录", "发现", "我", "聊天信息", "语音通话", "视频通话",
            "时间", "电量", "信号", "WiFi", "蓝牙", "定位",
            "今天", "昨天", "星期", "上午", "下午", "晚上",
            "正在输入", "已读", "未读", "置顶", "免打扰",
            "群聊", "单聊", "文件传输助手"
        )
        
        // 检查是否包含系统文本
        val containsSystemText = systemTexts.any { systemText ->
            text.contains(systemText, ignoreCase = true)
        }
        
        // 过滤微信顶部菜单栏的固定UI元素
        val isWeChatTopBarElement = isWeChatTopBarFixedElement(text)
        
        return containsSystemText || isWeChatTopBarElement
    }
    
    /**
     * 判断是否为微信顶部菜单栏的固定UI元素
     * 包括：返回箭头、三个点菜单、时间、网络状态、电池等
     */
    private fun isWeChatTopBarFixedElement(text: String): Boolean {
        // 返回箭头符号
        if (text == "く" || text == "<" || text == "‹" || text == "←") {
            return true
        }
        
        // 三个点菜单
        if (text == "..." || text == "⋯" || text == "•••" || text.matches(Regex("^[.•⋯]{3,}$"))) {
            return true
        }
        
        // 时间格式 (HH:MM)
        if (text.matches(Regex("^\\d{1,2}:\\d{2}$"))) {
            return true
        }
        
        // 网络状态相关
        if (text.matches(Regex(".*KB/[Ss].*")) || 
            text.matches(Regex(".*MB/[Ss].*")) ||
            text.contains("5G") ||
            text.contains("4G") ||
            text.contains("WiFi") ||
            text.contains("WIFI")) {
            return true
        }
        
        // 电池电量
        if (text.matches(Regex("^\\d{1,3}[%+]?$")) || text.matches(Regex("^\\d{1,3}\\s*[%+]$"))) {
            return true
        }
        
        // 单个字符或符号
        if (text.length == 1 && !text.matches(Regex("[\\u4e00-\\u9fa5a-zA-Z0-9]"))) {
            return true
        }
        
        // 纯数字（可能是信号强度等）
        if (text.matches(Regex("^\\d+(\\.\\d+)?$"))) {
            return true
        }
        
        // 包含特殊符号的状态信息
        if (text.contains("X") && text.length <= 3) {
            return true
        }
        
        return false
    }
    
    /**
     * 判断文本是否可能是联系人昵称
     * 基于长度、字符类型等特征判断
     */
    private fun isPossibleContactName(text: String): Boolean {
        // 长度合理（1-20个字符）
        if (text.length < 1 || text.length > 20) {
            return false
        }
        
        // 不包含特殊符号（除了常见的昵称符号）
        val allowedSpecialChars = setOf('-', '_', '·', '•', '♂', '♀')
        val hasInvalidChars = text.any { char ->
            !char.isLetterOrDigit() && 
            !char.isCJKUnifiedIdeograph() && 
            char !in allowedSpecialChars &&
            !char.isWhitespace()
        }
        
        if (hasInvalidChars) {
            return false
        }
        
        // 不是纯数字或纯符号
        if (text.all { it.isDigit() } || text.all { !it.isLetterOrDigit() && !it.isCJKUnifiedIdeograph() }) {
            return false
        }
        
        return true
    }
    
    /**
     * 检查字符是否为中日韩统一表意文字
     */
    private fun Char.isCJKUnifiedIdeograph(): Boolean {
        val codePoint = this.code
        return (codePoint in 0x4E00..0x9FFF) || // CJK Unified Ideographs
               (codePoint in 0x3400..0x4DBF) || // CJK Extension A
               (codePoint in 0x20000..0x2A6DF) || // CJK Extension B
               (codePoint in 0x2A700..0x2B73F) || // CJK Extension C
               (codePoint in 0x2B740..0x2B81F) || // CJK Extension D
               (codePoint in 0x2B820..0x2CEAF) || // CJK Extension E
               (codePoint in 0x2CEB0..0x2EBEF)    // CJK Extension F
    }
    
    /**
     * 检查文本是否包含中文字符
     */
    private fun containsChinese(text: String): Boolean {
        return text.any { it.isCJKUnifiedIdeograph() }
    }
    
    /**
     * 检查是否为单个符号
     */
    private fun isSingleSymbol(text: String): Boolean {
        if (text.length != 1) return false
        val char = text[0]
        return !char.isLetterOrDigit() && !char.isCJKUnifiedIdeograph()
    }
} 