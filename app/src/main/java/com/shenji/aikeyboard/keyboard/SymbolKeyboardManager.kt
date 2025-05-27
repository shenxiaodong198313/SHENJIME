package com.shenji.aikeyboard.keyboard

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.shenji.aikeyboard.R

class SymbolKeyboardManager {
    
    // 定义8个符号集合
    private val symbolSets = mapOf(
        "chinese" to SymbolSet(
            "中文符号",
            arrayOf(
                arrayOf("，", "。", "？", "！", "；", "：", """, """, "'", "'"),
                arrayOf("（", "）", "【", "】", "《", "》", "〈", "〉", "「", "」"),
                arrayOf("、", "·", "…", "—", "～", "￥", "¥", "※", "§", "¶")
            )
        ),
        "english" to SymbolSet(
            "英文符号",
            arrayOf(
                arrayOf("-", "_", ";", "|", "%", "&", "^", "*", "+", "="),
                arrayOf("~", "/", ":", ";", "(", ")", "$", "@", "\"", "."),
                arrayOf(".", ",", "?", "!", "...", "|", "\\", "/", "#", "%")
            )
        ),
        "brackets" to SymbolSet(
            "括号符号",
            arrayOf(
                arrayOf("[", "]", "⌊", "⌋", "(", ")", "[", "]", "{", "}"),
                arrayOf("⟨", "⟩", "⟪", "⟫", "⟮", "⟯", "⦃", "⦄", "⦅", "⦆"),
                arrayOf("⦇", "⦈", "⦉", "⦊", "⦋", "⦌", "⦍", "⦎", "⦏", "⦐")
            )
        ),
        "currency" to SymbolSet(
            "货币符号",
            arrayOf(
                arrayOf("$", "¢", "£", "¤", "¥", "₦", "§", "¨", "©", "ª"),
                arrayOf("«", "¬", "®", "¯", "°", "±", "²", "³", "´", "µ"),
                arrayOf("¶", "·", "¸", "¹", "º", "»", "¼", "½", "¾", "¿")
            )
        ),
        "math" to SymbolSet(
            "数学符号",
            arrayOf(
                arrayOf("+", "-", "×", "÷", "=", "≠", "≈", "≡", "≤", "≥"),
                arrayOf("∞", "∝", "∠", "∥", "⊥", "∴", "∵", "∶", "∷", "∽"),
                arrayOf("√", "∛", "∜", "∫", "∮", "∑", "∏", "∆", "∇", "∂")
            )
        ),
        "chinese_num" to SymbolSet(
            "中文数字",
            arrayOf(
                arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十"),
                arrayOf("壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖", "拾"),
                arrayOf("百", "千", "万", "亿", "零", "〇", "○", "●", "◯", "◎")
            )
        ),
        "circle_num" to SymbolSet(
            "圆圈数字",
            arrayOf(
                arrayOf("①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩"),
                arrayOf("❶", "❷", "❸", "❹", "❺", "❻", "❼", "❽", "❾", "❿"),
                arrayOf("Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ", "Ⅴ", "Ⅵ", "Ⅶ", "Ⅷ", "Ⅸ", "Ⅹ")
            )
        ),
        "superscript" to SymbolSet(
            "角标数字",
            arrayOf(
                arrayOf("⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹"),
                arrayOf("₀", "₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉"),
                arrayOf("⁺", "⁻", "⁼", "₊", "₋", "₌", "₍", "₎", "ⁿ", "ⁱ")
            )
        )
    )
    
    private var currentSymbolSet = "chinese"
    private var symbolContentArea: LinearLayout? = null
    
    data class SymbolSet(
        val name: String,
        val symbols: Array<Array<String>>
    )
    
    fun initialize(contentArea: LinearLayout) {
        symbolContentArea = contentArea
        updateSymbolDisplay()
    }
    
    fun switchToSymbolSet(setKey: String) {
        if (symbolSets.containsKey(setKey)) {
            currentSymbolSet = setKey
            updateSymbolDisplay()
        }
    }
    
    private fun updateSymbolDisplay() {
        val contentArea = symbolContentArea ?: return
        val symbolSet = symbolSets[currentSymbolSet] ?: return
        
        // 更新三行按钮的文本
        for (rowIndex in 0..2) {
            val rowLayout = contentArea.getChildAt(rowIndex) as? LinearLayout ?: continue
            val symbols = symbolSet.symbols[rowIndex]
            
            for (buttonIndex in 0 until minOf(rowLayout.childCount, symbols.size)) {
                val button = rowLayout.getChildAt(buttonIndex) as? Button
                
                // 跳过特殊按钮（ABC按钮和删除按钮）
                if (button?.id == R.id.symbol_abc_btn || button?.id == R.id.symbol_delete) {
                    continue
                }
                
                // 调整按钮索引，考虑第三行的ABC按钮
                val symbolIndex = if (rowIndex == 2 && buttonIndex > 0) {
                    buttonIndex - 1  // 第三行第一个是ABC按钮，所以符号索引要减1
                } else {
                    buttonIndex
                }
                
                if (symbolIndex < symbols.size) {
                    button?.text = symbols[symbolIndex]
                }
            }
        }
    }
    
    fun getCurrentSymbolSet(): String = currentSymbolSet
    
    fun getSymbolSets(): Map<String, SymbolSet> = symbolSets
} 