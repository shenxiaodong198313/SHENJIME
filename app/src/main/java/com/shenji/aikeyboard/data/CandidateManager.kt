package com.shenji.aikeyboard.data

import android.util.LruCache
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.model.Candidate
import com.shenji.aikeyboard.model.WordFrequency
import com.shenji.aikeyboard.model.CandidateBuilder
import com.shenji.aikeyboard.model.CandidateWeight
import com.shenji.aikeyboard.model.CandidateSource
import com.shenji.aikeyboard.model.GeneratorType
import com.shenji.aikeyboard.model.MatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import io.realm.kotlin.ext.query
import com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter
import com.shenji.aikeyboard.pinyin.InputType
import com.shenji.aikeyboard.pinyin.PinyinSplitter
import io.realm.kotlin.Realm
import com.shenji.aikeyboard.engine.InputMethodEngine

// 调试信息类型别名，方便外部引用
typealias DebugInfo = StagedDictionaryRepository.DebugInfo

/**
 * 候选词管理器 - 临时修复版本
 * 
 * 使用混合策略：优先使用新引擎，失败时回退到旧逻辑
 */
class CandidateManager {
    
    // 新的输入法引擎
    private val inputMethodEngine = InputMethodEngine()
    
    // 旧的查询逻辑组件
    private val dictionaryRepository = DictionaryRepository()
    // UnifiedPinyinSplitter是object，不需要实例化
    
    /**
     * 生成候选词 - 混合策略版本
     * 
     * @param input 用户输入
     * @param limit 候选词数量限制
     * @return 候选词列表
     */
    suspend fun generateCandidates(input: String, limit: Int = 10): List<Candidate> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("开始生成候选词: '$input'")
                
                // 首先尝试新引擎
                val newEngineCandidates = try {
                    inputMethodEngine.generateCandidates(input, limit)
                } catch (e: Exception) {
                    Timber.w(e, "新引擎失败，回退到旧逻辑")
                    emptyList()
                }
                
                // 如果新引擎返回了结果，直接使用
                if (newEngineCandidates.isNotEmpty()) {
                    Timber.d("新引擎返回 ${newEngineCandidates.size} 个候选词")
                    return@withContext newEngineCandidates
                }
                
                // 否则使用旧逻辑作为回退
                Timber.d("使用旧逻辑生成候选词")
                val fallbackCandidates = generateCandidatesFallback(input, limit)
                
                Timber.d("回退逻辑返回 ${fallbackCandidates.size} 个候选词")
                return@withContext fallbackCandidates
                
            } catch (e: Exception) {
                Timber.e(e, "候选词生成完全失败: $input")
                emptyList()
            }
        }
    }
    
    /**
     * 回退的候选词生成逻辑（基于旧的实现）
     */
    private suspend fun generateCandidatesFallback(input: String, limit: Int): List<Candidate> {
        val normalizedInput = input.trim().lowercase()
        if (normalizedInput.isEmpty()) return emptyList()
        
        val candidates = mutableListOf<Candidate>()
        
        try {
            // 1. 单字母查询
            if (normalizedInput.length == 1) {
                candidates.addAll(querySingleLetter(normalizedInput, limit))
            }
            
            // 2. 缩写查询
            if (normalizedInput.length >= 2 && normalizedInput.all { it.isLetter() }) {
                candidates.addAll(queryAcronym(normalizedInput, limit))
            }
            
            // 3. 拼音查询
            if (normalizedInput.length >= 2) {
                candidates.addAll(queryPinyin(normalizedInput, limit))
            }
            
            // 4. 拼音拆分查询
            if (normalizedInput.length >= 3) {
                candidates.addAll(queryPinyinSplit(normalizedInput, limit))
            }
            
            // 去重并排序
            val uniqueCandidates = candidates
                .distinctBy { it.word }
                .sortedByDescending { it.finalWeight }
                .take(limit)
            
            return uniqueCandidates
            
        } catch (e: Exception) {
            Timber.e(e, "回退查询失败: $normalizedInput")
            return emptyList()
        }
    }
    
    /**
     * 单字母查询
     */
    private suspend fun querySingleLetter(input: String, limit: Int): List<Candidate> {
        val wordFreqs = dictionaryRepository.searchBasicEntries(input, limit, listOf("chars"))
        return wordFreqs.map { wordFreq ->
            CandidateBuilder()
                .word(wordFreq.word)
                .pinyin("")
                .initialLetters(input)
                .frequency(wordFreq.frequency)
                .type("chars")
                .weight(CandidateWeight.default(wordFreq.frequency))
                .source(CandidateSource(
                    generator = GeneratorType.EXACT_MATCH,
                    matchType = MatchType.EXACT,
                    layer = 1,
                    confidence = 1.0f
                ))
                .build()
        }
    }
    
    /**
     * 缩写查询
     */
    private suspend fun queryAcronym(input: String, limit: Int): List<Candidate> {
        val wordFreqs = dictionaryRepository.searchByInitialLetters(input, limit)
        return wordFreqs.map { wordFreq ->
            CandidateBuilder()
                .word(wordFreq.word)
                .pinyin("")
                .initialLetters(input)
                .frequency(wordFreq.frequency)
                .type("acronym")
                .weight(CandidateWeight.default(wordFreq.frequency))
                .source(CandidateSource(
                    generator = GeneratorType.EXACT_MATCH,
                    matchType = MatchType.ACRONYM,
                    layer = 1,
                    confidence = 0.9f
                ))
                .build()
        }
    }
    
    /**
     * 拼音查询
     */
    private suspend fun queryPinyin(input: String, limit: Int): List<Candidate> {
        val wordFreqs = dictionaryRepository.searchBasicEntries(input, limit)
        return wordFreqs.map { wordFreq ->
            CandidateBuilder()
                .word(wordFreq.word)
                .pinyin(input)
                .initialLetters("")
                .frequency(wordFreq.frequency)
                .type("pinyin")
                .weight(CandidateWeight.default(wordFreq.frequency))
                .source(CandidateSource(
                    generator = GeneratorType.EXACT_MATCH,
                    matchType = MatchType.EXACT,
                    layer = 1,
                    confidence = 0.95f
                ))
                .build()
        }
    }
    
    /**
     * 拼音拆分查询
     */
    private suspend fun queryPinyinSplit(input: String, limit: Int): List<Candidate> {
        val syllables = UnifiedPinyinSplitter.split(input)
        if (syllables.isEmpty()) return emptyList()
        
        val spacedPinyin = syllables.joinToString(" ")
        val wordFreqs = dictionaryRepository.searchBasicEntries(spacedPinyin, limit)
        
        return wordFreqs.map { wordFreq ->
            CandidateBuilder()
                .word(wordFreq.word)
                .pinyin(spacedPinyin)
                .initialLetters("")
                .frequency(wordFreq.frequency)
                .type("split")
                .weight(CandidateWeight.default(wordFreq.frequency))
                .source(CandidateSource(
                    generator = GeneratorType.EXACT_MATCH,
                    matchType = MatchType.EXACT,
                    layer = 1,
                    confidence = 0.85f
                ))
                .build()
        }
    }
    
    /**
     * 记录用户选择
     */
    fun recordUserSelection(
        input: String,
        selected: Candidate,
        alternatives: List<Candidate>,
        position: Int
    ) {
        try {
            inputMethodEngine.recordUserSelection(input, selected, alternatives, position)
        } catch (e: Exception) {
            Timber.e(e, "记录用户选择失败")
        }
    }
    
    /**
     * 获取性能统计
     */
    fun getPerformanceStats(): String {
        return try {
            inputMethodEngine.getPerformanceStats()
        } catch (e: Exception) {
            "性能统计获取失败: ${e.message}"
        }
    }
    
    /**
     * 获取调试信息
     */
    fun getDebugInfo(): String {
        return try {
            inputMethodEngine.getDebugInfo()
        } catch (e: Exception) {
            "调试信息获取失败: ${e.message}"
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            inputMethodEngine.cleanup()
        } catch (e: Exception) {
            Timber.e(e, "清理资源失败")
        }
    }
}

/*
// ==================== 旧代码 (已注释) ====================
// 以下是旧的CandidateManager实现，保留作为参考

import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.utils.PinyinSegmenterOptimized
import com.shenji.aikeyboard.utils.UnifiedPinyinSplitter
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class CandidateManagerOld {
    
    private val pinyinSplitter = UnifiedPinyinSplitter()
    
    // 旧的查询方法实现...
    // (这里包含了所有旧的方法实现)
    
}
*/ 