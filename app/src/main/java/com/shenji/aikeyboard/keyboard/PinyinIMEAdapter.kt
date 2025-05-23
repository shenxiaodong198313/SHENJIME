package com.shenji.aikeyboard.keyboard

import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.CandidateManager
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.model.WordFrequency
import com.shenji.aikeyboard.model.Candidate
import com.shenji.aikeyboard.pinyin.PinyinQueryEngine
import com.shenji.aikeyboard.pinyin.UnifiedPinyinSplitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 拼音输入法适配器 - 连接输入法服务与标准化拼音查询模块
 */
class PinyinIMEAdapter {
    
    // 候选词管理器（支持分段匹配）
    private val candidateManager = ShenjiApplication.candidateManager
    
    // 标准化的拼音查询引擎（备用）
    private val pinyinQueryEngine = ShenjiApplication.pinyinQueryEngine
    
    // Trie树管理器
    private val trieManager = ShenjiApplication.trieManager
    
    /**
     * 获取候选词列表（异步方法）
     * 
     * @param input 用户输入
     * @param limit 返回候选词数量限制
     * @return 候选词列表
     */
    suspend fun getCandidates(input: String, limit: Int = 20): List<WordFrequency> = withContext(Dispatchers.IO) {
        try {
            Timber.d("PinyinIMEAdapter: 开始查询候选词，输入='$input', 限制=$limit")
            
            // 使用CandidateManager的公共方法generateCandidates
            val candidates = candidateManager.generateCandidates(input, limit)
            
            // 将Candidate转换为WordFrequency
            val result = candidates.map { candidate ->
                WordFrequency(
                    word = candidate.word,
                    frequency = candidate.frequency,
                    source = "${candidate.source.generator.name}_L${candidate.source.layer}"
                )
            }
            
            Timber.d("PinyinIMEAdapter: 获取到${result.size}个候选词")
            
            // 返回结果
            return@withContext result
            
        } catch (e: Exception) {
            Timber.e(e, "PinyinIMEAdapter: 获取候选词异常: ${e.message}")
            
            // 如果分段匹配失败，回退到原有逻辑
            try {
                Timber.d("PinyinIMEAdapter: 回退到原有查询逻辑")
                return@withContext getFallbackCandidates(input, limit)
            } catch (fallbackException: Exception) {
                Timber.e(fallbackException, "PinyinIMEAdapter: 回退查询也失败")
                return@withContext emptyList()
            }
        }
    }
    
    /**
     * 回退候选词查询方法（原有逻辑）
     */
    private suspend fun getFallbackCandidates(input: String, limit: Int): List<WordFrequency> {
        val resultList = mutableListOf<WordFrequency>()
        
        // 1. 首先尝试使用Trie树查询单字（如果Trie树已加载）
        if (trieManager.isTrieLoaded(com.shenji.aikeyboard.data.trie.TrieBuilder.TrieType.CHARS)) {
            val trieResults = getTrieCandidates(input, limit)
            if (trieResults.isNotEmpty()) {
                Timber.d("从Trie树获取到${trieResults.size}个候选词")
                resultList.addAll(trieResults)
            }
        }
        
        // 2. 如果Trie树结果不足或未加载Trie树，使用标准化拼音查询引擎查询数据库
        if (resultList.size < limit) {
            // 设置剩余需要的候选词数量
            val remainingLimit = limit - resultList.size
            
            val dbResult = pinyinQueryEngine.query(input, remainingLimit, false)
            
            // 将标准模块的PinyinCandidate转换为输入法使用的WordFrequency
            val dbCandidates = dbResult.candidates.map { candidate ->
                WordFrequency(
                    word = candidate.word,
                    frequency = candidate.frequency,
                    source = "数据库"
                )
            }
            
            // 避免重复添加已经从Trie树获取的词
            val existingWords = resultList.map { it.word }.toSet()
            dbCandidates.forEach { candidate ->
                if (candidate.word !in existingWords) {
                    resultList.add(candidate)
                }
            }
            
            Timber.d("从数据库获取到${dbCandidates.size}个候选词，合并后总计${resultList.size}个")
        }
        
        // 如果resultList数量多于limit，只取前limit个
        if (resultList.size > limit) {
            return resultList.take(limit)
        }
        
        // 返回结果，按词频排序
        return resultList.sortedByDescending { it.frequency }
    }
    
    /**
     * 使用Trie树查询候选词（异步方法）
     * 
     * @param input 用户输入的拼音前缀
     * @param limit 返回候选词数量限制
     * @return 候选词列表
     */
    fun getTrieCandidates(input: String, limit: Int = 20): List<WordFrequency> {
        try {
            // 使用Trie树进行前缀查询
            val trieResults = trieManager.searchCharsByPrefix(input, limit)
            // 标记来源为Trie树
            return trieResults.map { 
                // 添加额外的来源信息
                WordFrequency(it.word, it.frequency, source = "Trie树") 
            }
        } catch (e: Exception) {
            Timber.e(e, "Trie树查询异常: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * 拼音分词（同步方法）
     * 
     * @param input 用户输入
     * @return 分词结果（音节列表）
     */
    fun splitPinyin(input: String): List<String> {
        return UnifiedPinyinSplitter.split(input)
    }
    
    companion object {
        // 单例实例
        private var instance: PinyinIMEAdapter? = null
        
        /**
         * 获取实例
         */
        @JvmStatic
        fun getInstance(): PinyinIMEAdapter {
            if (instance == null) {
                instance = PinyinIMEAdapter()
            }
            return instance!!
        }
    }
} 