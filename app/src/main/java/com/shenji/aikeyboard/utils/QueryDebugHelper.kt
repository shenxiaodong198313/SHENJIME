package com.shenji.aikeyboard.utils

import android.content.Context
import android.widget.TextView
import com.shenji.aikeyboard.data.CandidateManager
import com.shenji.aikeyboard.data.CandidateWeight
import com.shenji.aikeyboard.data.DebugInfo
import com.shenji.aikeyboard.model.Candidate
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
        callback: (List<Candidate>) -> Unit
    ) {
        scope.launch {
            try {
                // 执行查询
                val results = candidateManager.generateCandidates(input, limit)
                
                // 在主线程更新调试信息和回调结果
                withContext(Dispatchers.Main) {
                    // 获取调试信息
                    if (debugMode) {
                        val debugInfo = candidateManager.getDebugInfo()
                        updateDebugInfo(input, results)
                        logDebugInfo(input, results)
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
    private fun updateDebugInfo(input: String, results: List<Candidate>) {
        debugView?.updateDebugInfo(
            input = input,
            stageResults = mapOf(1 to results.map { it.word }),
            duplicates = emptyList(),
            weights = results.associate { it.word to createLegacyWeight(it) }
        )
    }
    
    /**
     * 创建兼容的权重对象
     */
    private fun createLegacyWeight(candidate: Candidate): CandidateWeight {
        return CandidateWeight(
            stage = candidate.source.layer,
            frequency = candidate.frequency,
            matchType = when (candidate.source.matchType.name) {
                "EXACT" -> 0
                "PREFIX" -> 1
                "ACRONYM" -> 2
                "FUZZY" -> 3
                else -> 0
            },
            lengthBonus = if (candidate.word.length > 3) 10 else 0
        )
    }
    
    /**
     * 输出详细调试日志
     */
    private fun logDebugInfo(input: String, results: List<Candidate>) {
        Timber.d("===== 候选词查询调试信息 =====")
        Timber.d("输入: $input")
        
        // 记录候选词结果
        Timber.d("候选词数量: ${results.size}")
        results.forEachIndexed { index, candidate ->
            Timber.d("  ${index + 1}. ${candidate.word} (权重: ${candidate.finalWeight}, 来源: ${candidate.source.generator})")
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
        
        // 简化的标记逻辑
        badgeView.dictionaryType = "new"
        badgeView.stage = 1
    }
} 