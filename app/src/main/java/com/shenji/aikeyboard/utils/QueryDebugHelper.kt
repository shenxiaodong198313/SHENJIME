package com.shenji.aikeyboard.utils

import android.content.Context
import android.widget.TextView
import com.shenji.aikeyboard.data.CandidateManager
import com.shenji.aikeyboard.data.CandidateWeight
import com.shenji.aikeyboard.data.DebugInfo
import com.shenji.aikeyboard.model.WordFrequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 分阶段查询调试助手
 * 帮助开发者理解候选词生成过程和优化查询效果
 */
class QueryDebugHelper(
    private val candidateManager: CandidateManager,
    private val debugView: CandidateDebugView?
) {
    // 是否启用调试模式
    var debugMode = false
        set(value) {
            field = value
            debugView?.showDebugInfo = value
        }
    
    /**
     * 查询候选词并展示调试信息
     * @param input 用户输入
     * @param limit 候选词数量限制
     * @param callback 查询结果回调
     */
    fun queryCandidatesWithDebug(
        input: String,
        limit: Int,
        scope: CoroutineScope,
        callback: (List<WordFrequency>) -> Unit
    ) {
        scope.launch {
            try {
                // 执行查询
                val results = candidateManager.generateCandidates(input, limit)
                
                // 在主线程更新调试信息和回调结果
                withContext(Dispatchers.Main) {
                    // 获取调试信息
                    if (debugMode) {
                        val debugInfo = candidateManager.debugInfo
                        updateDebugInfo(debugInfo)
                        logDebugInfo(debugInfo)
                    }
                    
                    // 回调结果
                    callback(results)
                }
            } catch (e: Exception) {
                Timber.e(e, "查询候选词出错: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }
    
    /**
     * 更新调试视图
     */
    private fun updateDebugInfo(debugInfo: DebugInfo) {
        debugView?.updateDebugInfo(
            input = debugInfo.input,
            stageResults = debugInfo.stages,
            duplicates = debugInfo.duplicates,
            weights = debugInfo.weights
        )
    }
    
    /**
     * 输出详细调试日志
     */
    private fun logDebugInfo(debugInfo: DebugInfo) {
        Timber.d("===== 候选词查询调试信息 =====")
        Timber.d("输入: ${debugInfo.input}")
        
        // 记录各阶段结果
        for ((stage, words) in debugInfo.stages) {
            Timber.d("阶段 $stage: ${words.joinToString()}")
        }
        
        // 记录冲突信息
        if (debugInfo.duplicates.isNotEmpty()) {
            Timber.d("词典冲突: ${debugInfo.duplicates.joinToString { "${it.first}: ${it.second}" }}")
        }
        
        // 记录权重信息
        if (debugInfo.weights.isNotEmpty()) {
            Timber.d("权重信息:")
            for ((word, weight) in debugInfo.weights) {
                Timber.d("  $word: 阶段=${weight.stage}, 词频=${weight.frequency}, " +
                        "匹配=${weight.matchType}, 长度奖励=${weight.lengthBonus}")
            }
        }
        
        Timber.d("================================")
    }
    
    /**
     * 添加调试标记到候选词按钮上
     * @param word 候选词文本
     * @param badgeView 标记视图
     */
    fun addDebugBadge(word: String, badgeView: CandidateBadgeView?) {
        if (!debugMode || badgeView == null) return
        
        val debugInfo = candidateManager.debugInfo
        val weight = debugInfo.weights[word]
        
        if (weight != null) {
            // 查找候选词的词典类型
            val type = findCandidateType(word, debugInfo)
            
            // 设置标记内容
            badgeView.stage = weight.stage
            badgeView.dictionaryType = type ?: "unknown"
        } else {
            badgeView.dictionaryType = ""
            badgeView.stage = 0
        }
    }
    
    /**
     * 查找候选词的词典类型
     */
    private fun findCandidateType(word: String, debugInfo: DebugInfo): String? {
        // 遍历各阶段结果，查找词所在的词典类型
        for ((stage, words) in debugInfo.stages) {
            if (word in words) {
                // 从冲突信息中找到词典类型
                val conflict = debugInfo.duplicates.find { it.first == word }
                if (conflict != null) {
                    // 从冲突信息中提取第一个词典类型
                    val match = Regex("(\\w+) vs").find(conflict.second)
                    return match?.groupValues?.get(1)
                }
                
                // 根据阶段猜测词典类型
                return when (stage) {
                    1 -> if (word.length == 1) "chars" else "base"
                    2 -> "assoc"
                    3 -> if (word.contains("市") || word.contains("省")) "place" else "spec"
                    4 -> "corr"
                    else -> "stage$stage"
                }
            }
        }
        
        return null
    }
} 