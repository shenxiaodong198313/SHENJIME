package com.shenji.aikeyboard.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.shenji.aikeyboard.data.CandidateWeight
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.data.StagedDictionaryRepository
import com.shenji.aikeyboard.model.WordFrequency
import timber.log.Timber

/**
 * 候选词调试视图
 * 用于在测试界面显示候选词来源和权重信息
 */
class CandidateDebugView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // 画笔
    private val stagePaint = Paint().apply {
        color = Color.parseColor("#2196F3") // 蓝色
        textSize = 26f
        isAntiAlias = true
    }
    
    private val typePaint = Paint().apply {
        color = Color.parseColor("#757575") // 灰色
        textSize = 20f
        isAntiAlias = true
    }
    
    private val contentPaint = Paint().apply {
        color = Color.parseColor("#212121") // 深灰色
        textSize = 36f
        isAntiAlias = true
    }
    
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#F5F5F5") // 浅灰色背景
        style = Paint.Style.FILL
    }
    
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0") // 边框色
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    // 调试数据
    private var input: String = ""
    private var stageResults: Map<Int, List<String>> = emptyMap()
    private var duplicates: List<Pair<String, String>> = emptyList()
    private var weights: Map<String, CandidateWeight> = emptyMap()
    
    // 是否显示调试信息
    var showDebugInfo = false
        set(value) {
            field = value
            invalidate()
        }
    
    /**
     * 更新调试数据
     */
    fun updateDebugInfo(
        input: String,
        stageResults: Map<Int, List<String>>,
        duplicates: List<Pair<String, String>>,
        weights: Map<String, CandidateWeight>
    ) {
        this.input = input
        this.stageResults = stageResults
        this.duplicates = duplicates
        this.weights = weights
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!showDebugInfo || stageResults.isEmpty()) return
        
        // 绘制背景
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
        
        // 绘制输入信息
        val inputText = "输入: $input"
        canvas.drawText(inputText, 10f, 40f, contentPaint)
        
        // 绘制阶段结果
        var y = 80f
        stageResults.forEach { (stage, words) ->
            val stageText = "阶段 $stage:"
            canvas.drawText(stageText, 10f, y, stagePaint)
            
            // 绘制该阶段的词条
            y += 30f
            val wordsText = words.joinToString(", ")
            canvas.drawText(if (wordsText.length > 40) wordsText.substring(0, 40) + "..." else wordsText, 
                           30f, y, contentPaint)
            y += 40f
        }
        
        // 绘制词典冲突
        if (duplicates.isNotEmpty()) {
            y += 10f
            canvas.drawText("词典冲突:", 10f, y, stagePaint)
            y += 30f
            
            // 最多显示3个冲突
            duplicates.take(3).forEach { (word, conflict) ->
                val conflictText = "$word: $conflict"
                canvas.drawText(conflictText, 30f, y, typePaint)
                y += 30f
            }
        }
        
        // 绘制权重信息
        if (weights.isNotEmpty()) {
            y += 10f
            canvas.drawText("权重信息:", 10f, y, stagePaint)
            y += 30f
            
            // 最多显示5个权重
            weights.entries.take(5).forEach { (word, weight) ->
                val weightText = "$word: 阶段=${weight.stage}, 词频=${weight.frequency}" +
                                ", 匹配=${weight.matchType}, 长度奖励=${weight.lengthBonus}"
                canvas.drawText(weightText, 30f, y, typePaint)
                y += 30f
            }
        }
    }
}

/**
 * 候选词标签视图
 * 用于在候选词按钮上显示词典来源
 */
class CandidateBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val paint = Paint().apply {
        color = Color.parseColor("#757575") // 灰色
        textSize = 24f
        isAntiAlias = true
    }
    
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#F5F5F5") // 浅灰色背景
        style = Paint.Style.FILL
        alpha = 180
    }
    
    private val rect = Rect()
    
    var dictionaryType: String = ""
        set(value) {
            field = value
            invalidate()
        }
    
    var stage: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (dictionaryType.isBlank()) return
        
        val text = "${if (stage > 0) "S$stage-" else ""}$dictionaryType"
        paint.getTextBounds(text, 0, text.length, rect)
        
        // 绘制背景
        val padding = 4f
        canvas.drawRect(
            0f, 
            0f, 
            rect.width() + padding * 2, 
            rect.height() + padding * 2, 
            bgPaint
        )
        
        // 绘制文本
        canvas.drawText(
            text, 
            padding, 
            rect.height() + padding, 
            paint
        )
    }
} 