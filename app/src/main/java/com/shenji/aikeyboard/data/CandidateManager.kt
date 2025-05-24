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
import com.shenji.aikeyboard.engine.CombinationCandidateGenerator
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.trie.TrieBuilder

// 调试信息类型别名，方便外部引用
// typealias DebugInfo = StagedDictionaryRepository.DebugInfo

/**
 * 候选词管理器 - 临时修复版本
 * 
 * 使用混合策略：优先使用新引擎，失败时回退到旧逻辑
 */
class CandidateManager {
    
    // Trie管理器（优先使用）
    private val trieManager = TrieManager.instance
    
    // 新的输入法引擎
    private val inputMethodEngine = InputMethodEngine()
    
    // 旧的查询逻辑组件
    private val dictionaryRepository = DictionaryRepository()
    // private val stagedDictionaryRepository = StagedDictionaryRepository()
    
    // 组合候选词生成器
    private val combinationGenerator = CombinationCandidateGenerator(dictionaryRepository)
    
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
                
                // 首先尝试从Trie获取候选词
                val trieCandidates = try {
                    generateCandidatesFromTrie(input, limit)
                } catch (e: Exception) {
                    Timber.w(e, "Trie查询失败，尝试新引擎")
                    emptyList()
                }
                
                // 如果Trie返回了结果，直接使用
                if (trieCandidates.isNotEmpty()) {
                    Timber.d("Trie返回 ${trieCandidates.size} 个候选词")
                    return@withContext trieCandidates
                }
                
                // 然后尝试新引擎
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
     * 从Trie生成候选词（优先策略）
     */
    private suspend fun generateCandidatesFromTrie(input: String, limit: Int): List<Candidate> {
        val normalizedInput = input.trim().lowercase()
        if (normalizedInput.isEmpty()) return emptyList()
        
        val candidates = mutableListOf<Candidate>()
        
        try {
            // 根据输入长度和特征选择合适的Trie类型
            val trieTypes = selectTrieTypes(normalizedInput)
            
            for (trieType in trieTypes) {
                if (trieManager.isTrieLoaded(trieType)) {
                    val wordFreqs = trieManager.searchByPrefix(trieType, normalizedInput, limit)
                    
                    val trieCandidates = wordFreqs.map { wordFreq ->
                        CandidateBuilder()
                            .word(wordFreq.word)
                            .pinyin(normalizedInput)
                            .initialLetters("")
                            .frequency(wordFreq.frequency)
                            .type(getTrieTypeName(trieType))
                            .weight(CandidateWeight.default(wordFreq.frequency))
                            .source(CandidateSource(
                                generator = GeneratorType.EXACT_MATCH,
                                matchType = MatchType.EXACT,
                                layer = 1,
                                confidence = 1.0f
                            ))
                            .build()
                    }
                    
                    candidates.addAll(trieCandidates)
                    Timber.d("从${getTrieTypeName(trieType)}Trie获取 ${trieCandidates.size} 个候选词")
                }
            }
            
            // 去重并排序
            val uniqueCandidates = candidates
                .distinctBy { it.word }
                .sortedByDescending { it.finalWeight }
                .take(limit)
            
            Timber.d("Trie总共返回 ${uniqueCandidates.size} 个候选词")
            return uniqueCandidates
            
        } catch (e: Exception) {
            Timber.e(e, "Trie查询失败: $normalizedInput")
            return emptyList()
        }
    }
    
    /**
     * 根据输入选择合适的Trie类型
     */
    private fun selectTrieTypes(input: String): List<TrieBuilder.TrieType> {
        return when {
            input.length == 1 -> listOf(TrieBuilder.TrieType.CHARS)
            input.length <= 4 -> listOf(
                TrieBuilder.TrieType.CHARS,
                TrieBuilder.TrieType.BASE,
                TrieBuilder.TrieType.PEOPLE,
                TrieBuilder.TrieType.PLACE
            )
            else -> listOf(
                TrieBuilder.TrieType.BASE,
                TrieBuilder.TrieType.PEOPLE,
                TrieBuilder.TrieType.PLACE,
                TrieBuilder.TrieType.POETRY,
                TrieBuilder.TrieType.CHARS
            )
        }
    }
    
    /**
     * 获取Trie类型名称
     */
    private fun getTrieTypeName(trieType: TrieBuilder.TrieType): String {
        return when (trieType) {
            TrieBuilder.TrieType.CHARS -> "单字"
            TrieBuilder.TrieType.BASE -> "基础词典"
            TrieBuilder.TrieType.CORRELATION -> "关联词典"
            TrieBuilder.TrieType.ASSOCIATIONAL -> "联想词典"
            TrieBuilder.TrieType.PLACE -> "地名词典"
            TrieBuilder.TrieType.PEOPLE -> "人名词典"
            TrieBuilder.TrieType.POETRY -> "诗词词典"
            TrieBuilder.TrieType.CORRECTIONS -> "纠错词典"
            TrieBuilder.TrieType.COMPATIBLE -> "兼容词典"
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
            
            // 5. 组合候选词生成（针对长输入且候选词不足的情况）
            if (normalizedInput.length >= 6 && candidates.size < 3) {
                candidates.addAll(queryCombination(normalizedInput, limit))
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
     * 单字母查询 - 优化版
     */
    private suspend fun querySingleLetter(input: String, limit: Int): List<Candidate> {
        return try {
            val wordFreqs = dictionaryRepository.searchBasicEntries(input, limit, listOf("chars"))
            wordFreqs.map { wordFreq ->
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
        } catch (e: Exception) {
            Timber.e(e, "单字母查询失败: $input")
            emptyList()
        }
    }
    
    /**
     * 缩写查询 - 优化版
     */
    private suspend fun queryAcronym(input: String, limit: Int): List<Candidate> {
        return try {
            val wordFreqs = dictionaryRepository.searchByInitialLetters(input, limit)
            wordFreqs.map { wordFreq ->
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
        } catch (e: Exception) {
            Timber.e(e, "缩写查询失败: $input")
            emptyList()
        }
    }
    
    /**
     * 拼音查询 - 优化版
     */
    private suspend fun queryPinyin(input: String, limit: Int): List<Candidate> {
        return try {
            val wordFreqs = dictionaryRepository.searchBasicEntries(input, limit)
            wordFreqs.map { wordFreq ->
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
        } catch (e: Exception) {
            Timber.e(e, "拼音查询失败: $input")
            emptyList()
        }
    }
    
    /**
     * 拼音拆分查询 - 优化版
     */
    private suspend fun queryPinyinSplit(input: String, limit: Int): List<Candidate> {
        return try {
            val syllables = UnifiedPinyinSplitter.split(input)
            if (syllables.isEmpty()) return emptyList()
            
            val spacedPinyin = syllables.joinToString(" ")
            val wordFreqs = dictionaryRepository.searchBasicEntries(spacedPinyin, limit)
            
            wordFreqs.map { wordFreq ->
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
        } catch (e: Exception) {
            Timber.e(e, "拼音拆分查询失败: $input")
            emptyList()
        }
    }
    
    /**
     * 组合候选词查询
     */
    private suspend fun queryCombination(input: String, limit: Int): List<Candidate> {
        return try {
            Timber.d("开始组合候选词查询: '$input'")
            
            // 使用拼音拆分器获取音节
            val syllables = UnifiedPinyinSplitter.split(input)
            if (syllables.isEmpty()) {
                Timber.d("拼音拆分失败，无法进行组合查询")
                return emptyList()
            }
            
            Timber.d("拆分音节: ${syllables.joinToString(" ")}")
            
            // 使用组合候选词生成器
            val combinationCandidates = combinationGenerator.generateCombinationCandidates(syllables, limit)
            
            Timber.d("组合候选词生成完成: ${combinationCandidates.size} 个")
            
            return combinationCandidates
            
        } catch (e: Exception) {
            Timber.e(e, "组合候选词查询失败: $input")
            emptyList()
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