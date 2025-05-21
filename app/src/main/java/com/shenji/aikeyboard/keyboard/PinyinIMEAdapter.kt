package com.shenji.aikeyboard.keyboard

import com.shenji.aikeyboard.ShenjiApplication
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
    
    /**
     * 获取候选词列表（异步方法）
     * 
     * @param input 用户输入
     * @param limit 返回候选词数量限制
     * @return 候选词列表
     */
    suspend fun getCandidates(input: String, limit: Int = 20): List<WordFrequency> = withContext(Dispatchers.IO) {
        try {
            val result = pinyinQueryEngine.query(input, limit, false)
            
            // 将标准模块的PinyinCandidate转换为输入法使用的WordFrequency
            return@withContext result.candidates.map { candidate ->
                WordFrequency(
                    word = candidate.word,
                    frequency = candidate.frequency
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "获取候选词异常")
            emptyList()
        }
    }
    
    /**
     * 拼音分词（同步方法）
     * 
     * @param input 用户输入
     * @return 分词结果（音节列表）
     */
    fun splitPinyin(input: String): List<String> {
        return try {
            // 直接使用PinyinSplitter而不是查询引擎
            pinyinSplitter.splitPinyin(input)
        } catch (e: Exception) {
            Timber.e(e, "拼音分词异常")
            emptyList()
        }
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