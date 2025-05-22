package com.shenji.aikeyboard.keyboard

import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.model.WordFrequency
import com.shenji.aikeyboard.pinyin.PinyinQueryEngine
import com.shenji.aikeyboard.pinyin.PinyinSplitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 拼音输入法适配器 - 连接输入法服务与标准化拼音查询模块
 */
class PinyinIMEAdapter {
    
    // 标准化的拼音查询引擎
    private val pinyinQueryEngine = ShenjiApplication.pinyinQueryEngine
    
    // 拼音分词器
    private val pinyinSplitter = PinyinSplitter.getInstance()
    
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
            // 使用标准拼音查询引擎
            val result = pinyinQueryEngine.query(input, limit, false)
            
            // 将标准模块的PinyinCandidate转换为输入法使用的WordFrequency
            return@withContext result.candidates.map { candidate ->
                WordFrequency(
                    word = candidate.word,
                    frequency = candidate.frequency
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "获取候选词异常: ${e.message}")
            emptyList()
        }
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
            return trieManager.searchCharsByPrefix(input, limit)
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
        return pinyinSplitter.splitPinyin(input)
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