package com.shenji.aikeyboard.data

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

// 调试信息类型别名，方便外部引用
typealias DebugInfo = StagedDictionaryRepository.DebugInfo

/**
 * 候选词管理器
 * 负责协调候选词生成和查询
 */
class CandidateManager(private val repository: DictionaryRepository) {
    
    // 分阶段查询仓库
    private val stagedRepository = StagedDictionaryRepository()
    
    // 调试信息
    val debugInfo: DebugInfo get() = stagedRepository.debugInfo
    
    // 回退缓存实现，存储最近的输入和候选词结果的映射
    private val candidateCache = object {
        // 缓存大小控制，最多保存20个最近查询
        private val cache = LruCache<String, List<WordFrequency>>(20)
        
        // 存入缓存
        fun put(input: String, candidates: List<WordFrequency>) {
            cache.put(input, candidates)
        }
        
        // 获取缓存，不存在返回null
        fun get(input: String): List<WordFrequency>? = cache.get(input)
        
        // 清空缓存
        fun clear() = cache.evictAll()
    }
    
    // 保存上一次输入内容，用于判断是否为回退操作
    private var previousInput: String = ""
    
    // 优化状态信息
    data class OptimizationStatus(
        val cacheUsed: Boolean = false,         // 是否使用了缓存
        val earlyTerminated: Boolean = false,   // 是否提前终止
        val queryTime: Long = 0,                // 查询耗时(ms)
        val backwardFilterUsed: Boolean = false,// 是否使用了回退过滤
        val stagesExecuted: Int = 0             // 执行的阶段数
    )
    
    // 当前查询优化状态
    private var currentOptimizationStatus = OptimizationStatus()
    
    // 获取当前优化状态
    val optimizationStatus: OptimizationStatus get() = currentOptimizationStatus
    
    /**
     * 根据输入生成候选词
     * @param input 用户输入
     * @param limit 候选词数量限制
     * @return 候选词列表
     */
    suspend fun generateCandidates(input: String, limit: Int): List<WordFrequency> = withContext(Dispatchers.IO) {
        // 规范化输入
        val normalizedInput = input.trim().lowercase()
        
        if (normalizedInput.isEmpty()) {
            Timber.d("输入为空，返回空候选词列表")
            currentOptimizationStatus = OptimizationStatus() // 重置优化状态
            return@withContext emptyList()
        }
        
        Timber.d("生成'$normalizedInput'的候选词")
        
        // 重置优化状态
        currentOptimizationStatus = OptimizationStatus()
        
        // 记录查询开始时间
        val startTime = System.currentTimeMillis()
        
        // 检查是否从缓存中获取结果
        val cachedResults = candidateCache.get(normalizedInput)
        if (cachedResults != null) {
            Timber.d("从缓存获取'$normalizedInput'的候选词")
            previousInput = normalizedInput
            
            // 更新优化状态
            currentOptimizationStatus = OptimizationStatus(
                cacheUsed = true,
                queryTime = System.currentTimeMillis() - startTime,
                stagesExecuted = 0 // 从缓存获取不执行查询阶段
            )
            
            return@withContext cachedResults
        }
        
        // 检查是否是回退操作（从长输入回退到短输入）
        val isBackspaceOperation = normalizedInput.length < previousInput.length && 
                                  previousInput.startsWith(normalizedInput)
        
        val results = if (isBackspaceOperation) {
            // 尝试从已有缓存中过滤得到回退结果
            val cachedPrevResults = candidateCache.get(previousInput)
            if (cachedPrevResults != null) {
                Timber.d("检测到回退操作，尝试从缓存过滤")
                // 过滤出仍然有效的候选词
                val filteredResults = filterCandidatesForBackward(cachedPrevResults, normalizedInput)
                
                // 如果过滤后结果太少，回退到正常查询
                if (filteredResults.size < limit / 2) {
                    Timber.d("过滤后结果太少(${filteredResults.size})，执行完整查询")
                    queryAndCacheResults(normalizedInput, limit)
                } else {
                    Timber.d("使用过滤后的缓存结果(${filteredResults.size}个)")
                    // 缓存过滤后的结果
                    candidateCache.put(normalizedInput, filteredResults)
                    
                    // 更新优化状态
                    currentOptimizationStatus = OptimizationStatus(
                        backwardFilterUsed = true,
                        queryTime = System.currentTimeMillis() - startTime,
                        stagesExecuted = 0 // 过滤不执行查询阶段
                    )
                    
                    filteredResults
                }
            } else {
                queryAndCacheResults(normalizedInput, limit)
            }
        } else {
            queryAndCacheResults(normalizedInput, limit)
        }
        
        // 更新上一次输入
        previousInput = normalizedInput
        
        return@withContext results
    }
    
    /**
     * 执行查询并缓存结果
     */
    private suspend fun queryAndCacheResults(input: String, limit: Int): List<WordFrequency> {
        val startTime = System.currentTimeMillis()
        val results = stagedRepository.queryCandidates(input, limit)
        val endTime = System.currentTimeMillis()
        
        // 记录查询时间
        val resultSize = results.size
        val queryTime = endTime - startTime
        Timber.d("使用分阶段查询，耗时${queryTime}ms，生成${resultSize}个候选词")
        
        // 缓存查询结果
        candidateCache.put(input, results)
        
        // 更新优化状态
        currentOptimizationStatus = OptimizationStatus(
            cacheUsed = false,
            earlyTerminated = debugInfo.earlyTerminated,
            queryTime = queryTime,
            backwardFilterUsed = false,
            stagesExecuted = debugInfo.stages.size
        )
        
        return results
    }
    
    /**
     * 根据回退后的输入过滤候选词
     * 保留拼音或首字母仍然匹配的候选词
     */
    private fun filterCandidatesForBackward(
        candidates: List<WordFrequency>, 
        currentInput: String
    ): List<WordFrequency> {
        // 获取拼音分词器实例
        val pinyinSplitter = PinyinSplitter.getInstance()
        
        return candidates.filter { candidate ->
            // 获取标准化的拼音（只取前缀部分）
            val normalizedQuery = currentInput.trim().lowercase()
            
            // 查询数据库检查当前词是否仍然匹配
            val entries = repository.queryByWord(candidate.word)
            if (entries.isNotEmpty()) {
                val entry = entries.first()
                
                // 检查拼音是否匹配（前缀匹配）
                val pinyin = entry.pinyin.replace(" ", "")
                if (pinyin.startsWith(normalizedQuery)) {
                    return@filter true
                }
                
                // 检查首字母是否匹配
                if (entry.initialLetters.startsWith(normalizedQuery)) {
                    return@filter true
                }
            }
            
            // 默认不保留
            false
        }
    }
} 