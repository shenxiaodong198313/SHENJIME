package com.shenji.aikeyboard.data

import android.content.Context
import android.util.LruCache
import com.shenji.aikeyboard.ShenjiApplication
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.find
import timber.log.Timber
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.*

/**
 * 词典数据仓库类，用于提供词典相关的数据操作
 * 优化版本：添加缓存、异步处理和动态分层查询
 */
class DictionaryRepository {
    private val realm get() = ShenjiApplication.realm
    
    // 查询结果缓存 - LRU缓存，最多缓存1000个查询结果
    private val queryCache = LruCache<String, List<WordFrequency>>(1000)
    
    // 词频分层缓存 - 缓存每个词典类型的词频分层信息
    private val frequencyTierCache = LruCache<String, FrequencyTiers>(50)
    
    // 查询超时时间（毫秒）
    private val queryTimeoutMs = 500L
    
    /**
     * 词频分层数据类
     */
    data class FrequencyTiers(
        val high: Int,      // 高频阈值（前10-15%）
        val medium: Int,    // 中频阈值（前60-85%）
        val low: Int = 0    // 低频阈值
    )
    
    /**
     * 动态计算词频分层阈值
     */
    private suspend fun calculateFrequencyTiers(type: String): FrequencyTiers = withContext(Dispatchers.IO) {
        // 先检查缓存
        frequencyTierCache.get(type)?.let { return@withContext it }
        
        try {
            // 获取该类型的所有词频，按降序排列
            val frequencies = realm.query<Entry>("type == $0", type)
                .find()
                .map { it.frequency }
                .sortedDescending()
            
            if (frequencies.isEmpty()) {
                return@withContext FrequencyTiers(0, 0, 0)
            }
            
            val tiers = when(type) {
                "chars" -> FrequencyTiers(
                    high = frequencies.getPercentile(10),    // 前10%为高频
                    medium = frequencies.getPercentile(30)   // 前30%为中频
                )
                "base" -> FrequencyTiers(
                    high = frequencies.getPercentile(15),    // 前15%为高频
                    medium = frequencies.getPercentile(40)   // 前40%为中频
                )
                "place", "people" -> FrequencyTiers(
                    high = frequencies.getPercentile(5),     // 前5%为高频
                    medium = frequencies.getPercentile(20)   // 前20%为中频
                )
                else -> FrequencyTiers(
                    high = frequencies.getPercentile(10),
                    medium = frequencies.getPercentile(30)
                )
            }
            
            // 缓存结果
            frequencyTierCache.put(type, tiers)
            Timber.d("计算词典'$type'的词频分层: 高频>=${tiers.high}, 中频>=${tiers.medium}")
            
            return@withContext tiers
            
        } catch (e: Exception) {
            Timber.e(e, "计算词频分层失败: $type")
            return@withContext FrequencyTiers(0, 0, 0)
        }
    }
    
    /**
     * 扩展函数：获取列表的百分位数值
     */
    private fun List<Int>.getPercentile(percentile: Int): Int {
        if (isEmpty()) return 0
        val index = (size * percentile / 100.0).toInt().coerceIn(0, size - 1)
        return this[index]
    }
    
    /**
     * 获取所有词典类型
     */
    fun getAllDictionaryTypes(): List<String> {
        try {
            val dbTypes = realm.query<Entry>()
                .distinct("type")
                .find()
                .map { it.type }
                .filter { it.isNotEmpty() }  // 过滤掉空类型
            
            Timber.d("从数据库获取词典类型: ${dbTypes.joinToString()}")
            
            // 如果数据库查询结果为空，返回默认词典类型
            if (dbTypes.isEmpty()) {
                Timber.w("数据库中未找到词典类型，返回默认类型")
                return getDictionaryDefaultTypes()
            }
            
            return dbTypes
        } catch (e: Exception) {
            Timber.e(e, "获取词典类型失败，返回默认类型")
            return getDictionaryDefaultTypes()
        }
    }
    
    /**
     * 获取默认词典类型列表
     */
    private fun getDictionaryDefaultTypes(): List<String> {
        return listOf(
            "chars", "base", "correlation", "associational", 
            "compatible", "corrections", "place", "people", "poetry"
        )
    }
    
    /**
     * 获取总词条数量
     */
    fun getTotalEntryCount(): Int {
        return try {
            realm.query<Entry>().count().find().toInt()
        } catch (e: Exception) {
            Timber.e(e, "获取词条总数失败")
            0
        }
    }
    
    /**
     * 获取特定类型的词条数量
     */
    fun getEntryCountByType(type: String): Int {
        return try {
            realm.query<Entry>("type == $0", type).count().find().toInt()
        } catch (e: Exception) {
            Timber.e(e, "获取${type}词条数量失败")
            0
        }
    }
    
    /**
     * 分页获取特定类型的词条
     */
    fun getEntriesByType(type: String, offset: Int, limit: Int): List<Entry> {
        return try {
            realm.query<Entry>("type == $0", type)
                .find()
                .asSequence()
                .drop(offset)
                .take(limit)
                .toList()
        } catch (e: Exception) {
            Timber.e(e, "获取${type}词条列表失败")
            emptyList()
        }
    }
    
    /**
     * 分页获取特定类型的词条（支持词频过滤）
     * @param type 词典类型
     * @param offset 偏移量
     * @param limit 限制数量
     * @param frequencyFilter 词频过滤器
     */
    fun getEntriesByTypeWithFrequencyFilter(
        type: String, 
        offset: Int, 
        limit: Int, 
        frequencyFilter: com.shenji.aikeyboard.data.trie.TrieBuilder.FrequencyFilter
    ): List<Entry> {
        return try {
            if (frequencyFilter == com.shenji.aikeyboard.data.trie.TrieBuilder.FrequencyFilter.ALL) {
                // 如果是全部构建，直接使用原方法
                return getEntriesByType(type, offset, limit)
            }
            
            // 获取该类型的词频阈值
            val allEntries = realm.query<Entry>("type == $0", type)
                .find()
                .sortedByDescending { it.frequency }
            
            if (allEntries.isEmpty()) {
                return emptyList()
            }
            
            // 计算过滤后的数量
            val totalCount = allEntries.size
            val filteredCount = (totalCount * frequencyFilter.percentage).toInt()
            
            // 获取过滤后的词条
            val filteredEntries = allEntries.take(filteredCount)
            
            // 应用分页
            return filteredEntries.drop(offset).take(limit)
            
        } catch (e: Exception) {
            Timber.e(e, "获取${type}词条列表失败（词频过滤）")
            emptyList()
        }
    }
    
    /**
     * 获取过滤后的词条数量
     */
    fun getFilteredEntryCount(type: String, frequencyFilter: com.shenji.aikeyboard.data.trie.TrieBuilder.FrequencyFilter): Int {
        return try {
            if (frequencyFilter == com.shenji.aikeyboard.data.trie.TrieBuilder.FrequencyFilter.ALL) {
                return getEntryCountByType(type)
            }
            
            val totalCount = getEntryCountByType(type)
            (totalCount * frequencyFilter.percentage).toInt()
        } catch (e: Exception) {
            Timber.e(e, "获取${type}过滤后词条数量失败")
            0
        }
    }
    
    /**
     * 获取词典文件大小
     */
    fun getDictionaryFileSize(): Long {
        val context = ShenjiApplication.appContext
        val dictFile = File(context.filesDir, "dictionaries/shenji_dict.realm")
        return if (dictFile.exists()) dictFile.length() else 0
    }
    
    /**
     * 将字节大小转换为可读性好的字符串形式
     */
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
    
    /**
     * 构建词典模块列表
     */
    fun getDictionaryModules(): List<DictionaryModule> {
        val modules = mutableListOf<DictionaryModule>()
        
        try {
            // 获取所有词典类型
            val types = getAllDictionaryTypes()
            Timber.d("获取到词典类型列表: ${types.joinToString()}")
            
            // 添加各个词典类型
            for (type in types) {
                val count = getEntryCountByType(type)
                val chineseName = getChineseNameForType(type)
                
                modules.add(
                    DictionaryModule(
                        type = type,
                        chineseName = chineseName,
                        entryCount = count,
                        isInMemory = false,
                        memoryUsage = 0,
                        isPrecompiled = false,
                        isMemoryLoaded = false
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "构建词典模块列表失败")
        }
        
        return modules
    }
    
    /**
     * 获取词典类型的中文名称
     */
    private fun getChineseNameForType(type: String): String {
        return when (type) {
            "chars" -> "单字词典"
            "base" -> "基础词典"
            "correlation" -> "关联词典"
            "associational" -> "联想词典"
            "compatible" -> "兼容词典"
            "corrections" -> "纠错词典"
            "place" -> "地名词典"
            "people" -> "人名词典"
            "poetry" -> "诗词词典"
            else -> type
        }
    }
    
    /**
     * 根据汉字查询词条
     */
    fun searchByWord(word: String, limit: Int): List<WordFrequency> {
        if (word.isBlank()) return emptyList()
        
        return try {
            Timber.d("通过汉字在Realm数据库中搜索: '$word'")
            
            val entries = realm.query<Entry>("word BEGINSWITH $0", word)
                .find()
                .sortedByDescending { it.frequency }
                .take(limit)
            
            Timber.d("通过汉字查询找到${entries.size}个匹配的词条")
            
            entries.map { WordFrequency(it.word, it.frequency) }
        } catch (e: Exception) {
            Timber.e(e, "通过汉字搜索词条失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 优化版的基本词条查询，支持缓存和分层查询
     */
    suspend fun searchBasicEntries(pinyin: String, limit: Int, excludeTypes: List<String> = emptyList()): List<WordFrequency> {
        val cacheKey = "basic:$pinyin:$limit:${excludeTypes.joinToString(",")}"
        
        // 先检查缓存
        queryCache.get(cacheKey)?.let { 
            Timber.d("缓存命中: $cacheKey")
            return it 
        }
        
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(queryTimeoutMs) {
                    val startTime = System.currentTimeMillis()
                    val results = mutableListOf<WordFrequency>()
                    
                    // 按词典类型优先级分层查询
                    val priorityTypes = listOf(
                        listOf("chars", "base"),
                        listOf("correlation", "associational"),
                        listOf("place", "people"),
                        listOf("poetry", "corrections", "compatible")
                    )
                    
                    for ((priority, types) in priorityTypes.withIndex()) {
                        if (results.size >= limit) break
                        
                        for (type in types) {
                            if (type in excludeTypes) continue
                            if (results.size >= limit) break
                            
                            val quota = calculateQuota(priority, results.size, limit)
                            if (quota <= 0) continue
                            
                            val typeResults = searchByTypeWithTiers(pinyin, type, quota)
                            results.addAll(typeResults)
                        }
                    }
                    
                    val finalResults = results
                        .distinctBy { it.word }
                        .sortedByDescending { it.frequency }
                        .take(limit)
                    
                    val searchTime = System.currentTimeMillis() - startTime
                    Timber.d("优化搜索'$pinyin'耗时: ${searchTime}ms, 找到${finalResults.size}个结果")
                    
                    queryCache.put(cacheKey, finalResults)
                    
                    finalResults
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w("查询超时: $pinyin")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "基本词条搜索失败: ${e.message}")
                emptyList()
            }
        }
    }
    
    /**
     * 计算查询配额
     */
    private fun calculateQuota(priority: Int, currentSize: Int, totalLimit: Int): Int {
        val remaining = totalLimit - currentSize
        return when(priority) {
            0 -> minOf(remaining / 2, 8)
            1 -> minOf(remaining / 3, 5)
            2 -> minOf(remaining / 2, 4)
            3 -> minOf(remaining, 3)
            else -> 0
        }
    }
    
    /**
     * 按类型和词频分层查询
     */
    private suspend fun searchByTypeWithTiers(pinyin: String, type: String, limit: Int): List<WordFrequency> {
        val tiers = calculateFrequencyTiers(type)
        val results = mutableListOf<WordFrequency>()
        
        try {
            // 查询高频词
            val highFreqResults = realm.query<Entry>(
                "type == $0 AND (pinyin == $1 OR pinyin BEGINSWITH $1) AND frequency >= $2", 
                type, pinyin, tiers.high
            ).find()
                .sortedByDescending { it.frequency }
                .take(limit / 2)
                .map { WordFrequency(it.word, it.frequency) }
            
            results.addAll(highFreqResults)
            
            // 如果需要，查询中频词
            if (results.size < limit) {
                val mediumFreqResults = realm.query<Entry>(
                    "type == $0 AND (pinyin == $1 OR pinyin BEGINSWITH $1) AND frequency >= $2 AND frequency < $3", 
                    type, pinyin, tiers.medium, tiers.high
                ).find()
                    .sortedByDescending { it.frequency }
                    .take(limit - results.size)
                    .map { WordFrequency(it.word, it.frequency) }
                
                results.addAll(mediumFreqResults)
            }
            
            // 如果还需要，查询低频词
            if (results.size < limit) {
                val lowFreqResults = realm.query<Entry>(
                    "type == $0 AND (pinyin == $1 OR pinyin BEGINSWITH $1) AND frequency < $2", 
                    type, pinyin, tiers.medium
                ).find()
                    .sortedByDescending { it.frequency }
                    .take(limit - results.size)
                    .map { WordFrequency(it.word, it.frequency) }
                
                results.addAll(lowFreqResults)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "分层查询失败: type=$type, pinyin=$pinyin")
        }
        
        return results
    }
    
    /**
     * 优化版的首字母缩写查询
     */
    suspend fun searchByInitialLetters(initials: String, limit: Int): List<WordFrequency> {
        val cacheKey = "initials:$initials:$limit"
        
        queryCache.get(cacheKey)?.let { 
            Timber.d("首字母缓存命中: $cacheKey")
            return it 
        }
        
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(queryTimeoutMs) {
                    Timber.d("通过首字母缩写'$initials'查询")
                    val startTime = System.currentTimeMillis()
                    
                    val results = mutableListOf<WordFrequency>()
                    val seenWords = mutableSetOf<String>()
                    
                    val typeGroups = listOf(
                        listOf("chars", "base") to 0.6,
                        listOf("place", "people") to 0.25,
                        listOf("correlation", "associational", "poetry") to 0.15
                    )
                    
                    for ((types, ratio) in typeGroups) {
                        if (results.size >= limit) break
                        
                        val groupLimit = (limit * ratio).toInt().coerceAtLeast(1)
                        val groupResults = searchInitialsByTypes(initials, types, groupLimit, seenWords)
                        
                        results.addAll(groupResults)
                        groupResults.forEach { seenWords.add(it.word) }
                    }
                    
                    val searchTime = System.currentTimeMillis() - startTime
                    Timber.d("首字母查询'$initials'耗时: ${searchTime}ms, 找到${results.size}个结果")
                    
                    queryCache.put(cacheKey, results)
                    
                    results
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w("首字母查询超时: $initials")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "首字母缩写查询失败: ${e.message}")
                emptyList()
            }
        }
    }
    
    /**
     * 按指定词典类型查询首字母
     */
    private suspend fun searchInitialsByTypes(
        initials: String, 
        types: List<String>, 
        limit: Int,
        excludeWords: Set<String>
    ): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        for (type in types) {
            if (results.size >= limit) break
            
            val tiers = calculateFrequencyTiers(type)
            val typeLimit = (limit - results.size).coerceAtLeast(1)
            
            val exactHighFreq = realm.query<Entry>(
                "type == $0 AND initialLetters == $1 AND frequency >= $2", 
                type, initials, tiers.high
            ).find()
                .filter { it.word !in excludeWords }
                .sortedByDescending { it.frequency }
                .take(typeLimit / 2)
                .map { WordFrequency(it.word, it.frequency * 2) }
            
            results.addAll(exactHighFreq)
            
            if (results.size < limit) {
                val prefixMatches = realm.query<Entry>(
                    "type == $0 AND initialLetters BEGINSWITH $1 AND initialLetters != $1", 
                    type, initials
                ).find()
                    .filter { it.word !in excludeWords }
                    .sortedByDescending { it.frequency }
                    .take(limit - results.size)
                    .map { WordFrequency(it.word, it.frequency) }
                
                results.addAll(prefixMatches)
            }
        }
        
        return results
    }
    
    /**
     * 根据汉字查询词条
     */
    fun queryByWord(word: String): List<Entry> {
        return try {
            realm.query<Entry>("word == $0", word).find()
        } catch (e: Exception) {
            Timber.e(e, "查询词'$word'失败")
            emptyList()
        }
    }
    
    /**
     * 获取所有单字
     */
    fun getAllChars(): List<Entry> {
        return try {
            realm.query<Entry>("type == $0", "chars")
                .find()
                .toList()
        } catch (e: Exception) {
            Timber.e(e, "获取所有单字失败")
            emptyList()
        }
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        queryCache.evictAll()
        frequencyTierCache.evictAll()
        Timber.d("已清理所有缓存")
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): String {
        return "查询缓存: ${queryCache.size()}/${queryCache.maxSize()}, " +
               "词频分层缓存: ${frequencyTierCache.size()}/${frequencyTierCache.maxSize()}"
    }
    
    /**
     * 预热缓存
     */
    suspend fun warmupCache() = withContext(Dispatchers.IO) {
        try {
            Timber.d("开始预热缓存...")
            val commonTypes = listOf("chars", "base", "correlation", "place", "people")
            
            for (type in commonTypes) {
                calculateFrequencyTiers(type)
                delay(10)
            }
            
            Timber.d("缓存预热完成: ${getCacheStats()}")
        } catch (e: Exception) {
            Timber.e(e, "缓存预热失败")
        }
    }
}

/**
 * 词典模块数据类
 */
data class DictionaryModule(
    val type: String,
    val chineseName: String,
    val entryCount: Int,
    val isInMemory: Boolean = false,
    val memoryUsage: Long = 0L,
    val isGroupHeader: Boolean = false,
    val isPrecompiled: Boolean = false,
    val isMemoryLoaded: Boolean = false
) 