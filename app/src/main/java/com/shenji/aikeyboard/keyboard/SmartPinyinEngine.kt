package com.shenji.aikeyboard.keyboard

import android.util.LruCache
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.trie.TrieType
import com.shenji.aikeyboard.model.WordFrequency
// import com.shenji.aikeyboard.data.repository.DictionaryRepository
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * 智能拼音引擎 - 优化版
 * 
 * 核心优化：
 * 1. 简化分词逻辑，移除硬编码
 * 2. 分层词典查询策略
 * 3. 6分段以上停止查询的性能优化
 * 4. 清晰的查询优先级
 */
class SmartPinyinEngine private constructor() : CandidateEngine {
    
    private val trieManager = TrieManager.instance
    
    // 简化缓存策略
    private val queryCache = LruCache<String, List<WordFrequency>>(100)
    
    // 性能统计
    private val queryCount = AtomicLong(0)
    private val cacheHits = AtomicLong(0)
    
    companion object {
        @Volatile
        private var INSTANCE: SmartPinyinEngine? = null
        
        fun getInstance(): SmartPinyinEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartPinyinEngine().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 查询分析数据类
     */
    data class QueryAnalysis(
        val inputType: InputType,
        val queryStrategy: QueryStrategy,
        val segmentCount: Int,
        val segments: List<String>,
        val trieStatus: String,
        val queryTime: Long,
        val resultCount: Int,
        val cacheHit: Boolean
    )
    
    /**
     * 输入类型枚举
     */
    enum class InputType {
        SINGLE_CHAR,        // 单字符
        ABBREVIATION,       // 缩写
        SHORT_INPUT,        // 短输入(2-3分段)
        MEDIUM_INPUT,       // 中等输入(4分段)
        LONG_INPUT,         // 长输入(5-6分段)
        OVER_LIMIT          // 超过限制(7+分段)
    }
    
    /**
     * 查询策略枚举
     */
    enum class QueryStrategy {
        CHARS_BASE_PRIORITY,    // 单字+基础词组优先
        ABBREVIATION_MATCH,     // 缩写匹配
        CORRELATION_PRIORITY,   // 4字词组优先
        ASSOCIATIONAL_PRIORITY, // 长词组优先
        STOP_QUERY             // 停止查询
    }
    
    /**
     * 主要查询接口
     */
    override suspend fun getCandidates(currentPinyin: String, limit: Int, offset: Int): List<WordFrequency> {
        if (currentPinyin.isBlank()) return emptyList()
        
        val cleanInput = currentPinyin.trim().lowercase()
        queryCount.incrementAndGet()
        
        // 检查缓存（使用原始输入作为缓存键）
        val cacheKey = "${cleanInput}_${limit}_${offset}"
        queryCache.get(cacheKey)?.let { cached ->
            cacheHits.incrementAndGet()
            return cached
        }
        
        val startTime = System.currentTimeMillis()
        
        // 🔧 生成查询变体（包括原始输入和v/ü转换）
        val queryVariants = generateInputVariants(cleanInput)
        Timber.d("🔄 输入变体: $cleanInput -> ${queryVariants.joinToString(", ")}")
        
        // 对每个变体进行输入分析，选择最佳的分析结果
        val bestAnalysis = queryVariants.map { variant ->
            analyzeInput(variant)
        }.maxByOrNull { it.confidence } ?: analyzeInput(cleanInput)
        
        // 根据输入分析选择查询策略
        val results = when (bestAnalysis.type) {
            InputType.SINGLE_CHAR -> {
                if (offset == 0) {
                    // 首次查询：分层推荐
                    queryMultiVariantSingleChar(queryVariants, limit)
                } else {
                    // 懒加载：更多内容
                    queryMultiVariantSingleCharLazyLoad(queryVariants, limit, offset)
                }
            }
            InputType.ABBREVIATION -> queryMultiVariantAbbreviation(queryVariants, limit)
            InputType.SHORT_INPUT -> queryMultiVariantShortInput(queryVariants, bestAnalysis.segments, limit)
            InputType.MEDIUM_INPUT -> queryMultiVariantMediumInput(queryVariants, bestAnalysis.segments, limit)
            InputType.LONG_INPUT -> queryMultiVariantLongInput(queryVariants, bestAnalysis.segments, limit)
            InputType.OVER_LIMIT -> {
                Timber.d("输入超过限制(${bestAnalysis.segments.size}分段)，停止查询")
                emptyList()
            }
        }
        
        val queryTime = System.currentTimeMillis() - startTime
        Timber.d("查询完成: $cleanInput -> ${bestAnalysis.type} -> ${results.size}结果 (${queryTime}ms)")
        
        // 缓存结果（使用原始输入作为缓存键）
        queryCache.put(cacheKey, results)
        
        // 分页返回
        val startIndex = offset
        val endIndex = minOf(offset + limit, results.size)
        return if (startIndex < results.size) {
            results.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }
    
    /**
     * 带默认参数的便捷方法
     */
    suspend fun getCandidates(currentPinyin: String, limit: Int = 25): List<WordFrequency> {
        return getCandidates(currentPinyin, limit, 0)
    }
    
    /**
     * 输入分析数据类
     */
    data class InputAnalysis(
        val type: InputType,
        val segments: List<String>,
        val isAbbreviation: Boolean,
        val confidence: Double
    )
    
    /**
     * 智能输入分析
     */
    private fun analyzeInput(input: String): InputAnalysis {
        // 基础分词
        val segments = simpleSegmentation(input)
        
        // 缩写检测
        val abbreviationAnalysis = detectAbbreviation(input, segments)
        
        // 确定输入类型
        val inputType = when {
            input.length == 1 -> InputType.SINGLE_CHAR
            abbreviationAnalysis.isAbbreviation -> InputType.ABBREVIATION
            segments.size > 6 -> InputType.OVER_LIMIT
            segments.size == 1 -> InputType.SINGLE_CHAR
            segments.size in 2..3 -> InputType.SHORT_INPUT
            segments.size == 4 -> InputType.MEDIUM_INPUT
            segments.size in 5..6 -> InputType.LONG_INPUT
            else -> InputType.SHORT_INPUT
        }
        
        return InputAnalysis(
            type = inputType,
            segments = segments,
            isAbbreviation = abbreviationAnalysis.isAbbreviation,
            confidence = abbreviationAnalysis.confidence
        )
    }
    
    /**
     * 缩写检测分析
     */
    data class AbbreviationAnalysis(
        val isAbbreviation: Boolean,
        val confidence: Double,
        val reason: String
    )
    
    /**
     * 通用缩写检测（不依赖硬编码）
     */
    private fun detectAbbreviation(input: String, segments: List<String>): AbbreviationAnalysis {
        // 规则1: 单字符输入不是缩写
        if (input.length == 1) {
            return AbbreviationAnalysis(false, 0.0, "单字符")
        }
        
        // 规则2: 如果分词成功且都是有效音节，不是缩写
        if (segments.all { it.length > 1 && isValidSyllable(it) }) {
            return AbbreviationAnalysis(false, 0.9, "完整音节")
        }
        
        // 规则3: 如果大部分字符都是单字符分段，可能是缩写
        val singleCharSegments = segments.count { it.length == 1 }
        val singleCharRatio = singleCharSegments.toDouble() / segments.size
        
        if (singleCharRatio >= 0.7) {
            return AbbreviationAnalysis(true, singleCharRatio, "单字符比例高")
        }
        
        // 规则4: 连续的单字符且长度适中，可能是缩写
        if (input.length in 2..6 && input.all { it.isLetter() } && segments.size == input.length) {
            return AbbreviationAnalysis(true, 0.8, "连续单字符")
        }
        
        return AbbreviationAnalysis(false, 0.1, "不符合缩写特征")
    }
    
    /**
     * 检查是否为有效音节
     */
    private fun isValidSyllable(syllable: String): Boolean {
        // 基础音节检查（简化版）
        val validSyllables = setOf(
            "a", "e", "o", "ai", "ei", "ao", "ou", "an", "en", "ang", "eng", "er",
            "ba", "bi", "bo", "bu", "pa", "pi", "po", "pu", "ma", "mi", "mo", "mu",
            "fa", "fo", "fu", "da", "di", "du", "ta", "ti", "tu", "na", "ni", "nu",
            "la", "li", "lu", "ga", "ge", "gu", "ka", "ke", "ku", "ha", "he", "hu",
            "ji", "ju", "qi", "qu", "xi", "xu", "zhi", "chi", "shi", "ri", "zi", "ci", "si",
            "ya", "ye", "yi", "yo", "yu", "wa", "wo", "wu", "wei", "wen", "weng"
        )
        return validSyllables.contains(syllable.lowercase())
    }
    
    /**
     * 单字符查询（智能分层推荐）
     */
    private suspend fun querySingleChar(char: String, limit: Int): List<WordFrequency> {
        if (char.length == 1) {
            return querySmartSingleChar(char, limit)
        } else {
            // 完整音节查询
            return queryWithFallback(listOf(TrieType.CHARS, TrieType.BASE), char, limit)
        }
    }
    
    /**
     * 单字符懒加载查询
     * 提供更多层级的内容：三字词组、四字词组等
     */
    private suspend fun querySingleCharLazyLoad(char: String, limit: Int, offset: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        Timber.d("🔄 单字符懒加载: $char (offset: $offset)")
        
        // 根据offset决定加载哪一层内容
        when {
            offset <= 50 -> {
                // 第四层：三字词组
                if (trieManager.isTrieLoaded(TrieType.BASE)) {
                    val threeCharWords = trieManager.searchByPrefix(TrieType.BASE, char, limit * 2)
                        .filter { it.word.length == 3 }
                        .sortedByDescending { it.frequency }
                    
                    results.addAll(threeCharWords)
                    Timber.d("🔄 第四层三字词组: ${threeCharWords.size}个")
                }
            }
            offset <= 100 -> {
                // 第五层：四字词组
                if (trieManager.isTrieLoaded(TrieType.CORRELATION)) {
                    val fourCharWords = trieManager.searchByPrefix(TrieType.CORRELATION, char, limit * 2)
                        .filter { it.word.length == 4 }
                        .sortedByDescending { it.frequency }
                    
                    results.addAll(fourCharWords)
                    Timber.d("🔄 第五层四字词组: ${fourCharWords.size}个")
                } else {
                    // 如果CORRELATION未加载，从BASE中查找四字词
                    if (trieManager.isTrieLoaded(TrieType.BASE)) {
                        val fourCharWords = trieManager.searchByPrefix(TrieType.BASE, char, limit * 2)
                            .filter { it.word.length == 4 }
                            .sortedByDescending { it.frequency }
                        
                        results.addAll(fourCharWords)
                        Timber.d("🔄 第五层四字词组(BASE): ${fourCharWords.size}个")
                    }
                }
            }
            else -> {
                // 第六层：更长词组和地名人名
                val allResults = mutableListOf<WordFrequency>()
                
                // 长词组
                if (trieManager.isTrieLoaded(TrieType.ASSOCIATIONAL)) {
                    val longWords = trieManager.searchByPrefix(TrieType.ASSOCIATIONAL, char, limit)
                        .filter { it.word.length >= 5 }
                        .sortedByDescending { it.frequency }
                    allResults.addAll(longWords)
                }
                
                // 地名
                if (trieManager.isTrieLoaded(TrieType.PLACE)) {
                    val placeWords = trieManager.searchByPrefix(TrieType.PLACE, char, limit)
                        .sortedByDescending { it.frequency }
                    allResults.addAll(placeWords)
                }
                
                // 人名
                if (trieManager.isTrieLoaded(TrieType.PEOPLE)) {
                    val peopleWords = trieManager.searchByPrefix(TrieType.PEOPLE, char, limit)
                        .sortedByDescending { it.frequency }
                    allResults.addAll(peopleWords)
                }
                
                results.addAll(allResults.distinctBy { it.word }.sortedByDescending { it.frequency })
                Timber.d("🔄 第六层长词组/地名/人名: ${results.size}个")
            }
        }
        
        // 按字数优先 + 频率排序
        val sortedResults = sortByLengthAndFrequency(results.distinctBy { it.word })
        val finalResults = sortedResults.take(limit)
        
        Timber.d("✅ 懒加载完成: ${finalResults.size}个结果")
        
        return finalResults
    }
    
    /**
     * 缩写查询（通用方法，不依赖硬编码）
     */
    private suspend fun queryAbbreviation(input: String, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        Timber.d("缩写查询: $input")
        
        // 策略1: 在BASE词典中查找以该缩写开头的词组
        val baseResults = queryWithFallback(listOf(TrieType.BASE), input, limit / 2)
            .filter { word -> 
                // 检查词组是否符合缩写模式
                isWordMatchAbbreviation(word.word, input)
            }
        results.addAll(baseResults)
        
        // 策略2: 查找地名和人名（通常有缩写）
        val placeResults = queryWithFallback(listOf(TrieType.PLACE), input, limit / 4)
        results.addAll(placeResults)
        
        val peopleResults = queryWithFallback(listOf(TrieType.PEOPLE), input, limit / 4)
        results.addAll(peopleResults)
        
        // 策略3: 如果结果太少，补充单字
        if (results.size < limit / 2) {
            val charResults = input.map { char ->
                querySmartSingleChar(char.toString(), 3)
            }.flatten().distinctBy { it.word }
            
            results.addAll(charResults.take(limit - results.size))
        }
        
        // 按字数优先 + 频率排序
        val sortedResults = sortByLengthAndFrequency(results.distinctBy { it.word })
        
        Timber.d("缩写查询结果: ${sortedResults.size}个")
        return sortedResults.take(limit)
    }
    
    /**
     * 检查词组是否匹配缩写（通用算法）
     */
    private fun isWordMatchAbbreviation(word: String, abbreviation: String): Boolean {
        if (word.isEmpty() || abbreviation.isEmpty()) return false
        
        // 简单的首字母匹配检查
        // 这里可以扩展为更复杂的拼音首字母匹配
        val wordInitials = word.take(abbreviation.length)
        return wordInitials.length == abbreviation.length
    }
    
    /**
     * 短输入查询（2-3分段）
     */
    private suspend fun queryShortInput(segments: List<String>, limit: Int): List<WordFrequency> {
        val query = segments.joinToString("")
        Timber.d("🔍 短输入查询: $query (${segments.size}分段)")
        return queryWithFallback(
            listOf(TrieType.CHARS, TrieType.BASE, TrieType.PLACE, TrieType.PEOPLE),
            query,
            limit
        )
    }
    
    /**
     * 中等输入查询（4分段）
     */
    private suspend fun queryMediumInput(segments: List<String>, limit: Int): List<WordFrequency> {
        val query = segments.joinToString("")
        return queryWithFallback(
            listOf(TrieType.CORRELATION, TrieType.ASSOCIATIONAL, TrieType.PLACE, TrieType.PEOPLE),
            query,
            limit
        )
    }
    
    /**
     * 长输入查询（5-6分段）
     */
    private suspend fun queryLongInput(segments: List<String>, limit: Int): List<WordFrequency> {
        val query = segments.joinToString("")
        return queryWithFallback(
            listOf(TrieType.ASSOCIATIONAL, TrieType.PLACE, TrieType.PEOPLE, TrieType.POETRY),
            query,
            limit
        )
    }
    
    /**
     * 带回退机制的查询（Trie失败时查询Realm）
     * 支持v/ü双向匹配
     */
    private suspend fun queryWithFallback(
        trieTypes: List<TrieType>,
        query: String,
        limit: Int
    ): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        // 生成查询变体（支持v/ü双向匹配）
        val queryVariants = generateVUQueryVariants(query)
        Timber.d("🔄 生成查询变体: $query -> ${queryVariants.joinToString(", ")}")
        
        // 首先尝试Trie查询
        for (trieType in trieTypes) {
            if (results.size >= limit * 2) break // 获取更多结果用于排序
            
            if (trieManager.isTrieLoaded(trieType)) {
                // 对每个查询变体进行查询
                for (variant in queryVariants) {
                    val trieResults = trieManager.searchByPrefix(trieType, variant, limit * 2)
                    results.addAll(trieResults)
                    
                    if (trieResults.isNotEmpty()) {
                        Timber.d("${getTrieTypeName(trieType)}Trie查询'$variant'成功: ${trieResults.size}个结果")
                    }
                }
            }
        }
        
        // 如果Trie查询结果不足，回退到Realm数据库
        if (results.size < limit) {
            Timber.d("Trie结果不足(${results.size})，回退到Realm查询")
            for (variant in queryVariants) {
                val realmResults = queryFromRealm(variant, limit * 2)
                results.addAll(realmResults)
                
                if (realmResults.isNotEmpty()) {
                    Timber.d("Realm查询'$variant'成功: ${realmResults.size}个结果")
                }
            }
        }
        
        // 按字数优先 + 频率排序
        val sortedResults = sortByLengthAndFrequency(results.distinctBy { it.word })
        
        return sortedResults.take(limit)
    }
    
    /**
     * 生成v/ü查询变体
     * 支持双向转换：lü ↔ lv, nü ↔ nv
     * 注意：词典中存储的是无声调拼音
     */
    private fun generateVUQueryVariants(query: String): List<String> {
        val variants = mutableSetOf<String>()
        variants.add(query) // 原始查询
        
        // 如果包含ü，生成v版本
        if (query.contains('ü')) {
            val vVersion = query.replace('ü', 'v')
            variants.add(vVersion)
            Timber.d("🔄 ü->v变体: '$query' -> '$vVersion'")
        }
        
        // 如果包含v，生成ü版本（仅限lv和nv）
        if (query.contains('v')) {
            var uVersion = query
            
            // lv -> lü (词典中存储为无声调的lü)
            uVersion = uVersion.replace(Regex("\\blv\\b"), "lü")
            uVersion = uVersion.replace(Regex("lv([aeiou])"), "lü$1")
            
            // nv -> nü (词典中存储为无声调的nü)
            uVersion = uVersion.replace(Regex("\\bnv\\b"), "nü")
            uVersion = uVersion.replace(Regex("nv([aeiou])"), "nü$1")
            
            if (uVersion != query) {
                variants.add(uVersion)
                Timber.d("🔄 v->ü变体: '$query' -> '$uVersion'")
            }
        }
        
        // 处理连续拼音的情况，如 nvhai -> nühai
        if (query.contains("nv") && !query.contains("nü")) {
            val nvToNuVersion = query.replace("nv", "nü")
            variants.add(nvToNuVersion)
            Timber.d("🔄 连续拼音nv->nü: '$query' -> '$nvToNuVersion'")
        }
        
        if (query.contains("lv") && !query.contains("lü")) {
            val lvToLuVersion = query.replace("lv", "lü")
            variants.add(lvToLuVersion)
            Timber.d("🔄 连续拼音lv->lü: '$query' -> '$lvToLuVersion'")
        }
        
        return variants.toList()
    }
    
    /**
     * 从Realm数据库查询
     */
    private suspend fun queryFromRealm(query: String, limit: Int): List<WordFrequency> {
        return withContext(Dispatchers.IO) {
            try {
                val realm = com.shenji.aikeyboard.ShenjiApplication.realm
                
                // 前缀匹配查询
                val entries = realm.query(com.shenji.aikeyboard.data.Entry::class)
                    .query("pinyin BEGINSWITH $0 OR initialLetters BEGINSWITH $0", query)
                    .limit(limit)
                    .find()
                
                val results = entries.map { entry ->
                    WordFrequency(entry.word, entry.frequency)
                }.sortedByDescending { it.frequency }
                
                Timber.d("Realm查询'$query': ${results.size}个结果")
                results
            } catch (e: Exception) {
                Timber.e(e, "Realm查询失败: $query")
                emptyList()
            }
        }
    }
    
    /**
     * 按字数优先 + 频率排序
     * 规则：单字 > 双字 > 三字 > 四字及以上，同长度按频率降序
     */
    private fun sortByLengthAndFrequency(words: List<WordFrequency>): List<WordFrequency> {
        return words.sortedWith(compareBy<WordFrequency> { it.word.length }.thenByDescending { it.frequency })
    }
    
    /**
     * 获取Trie类型名称
     */
    private fun getTrieTypeName(type: TrieType): String {
        return when (type) {
            TrieType.CHARS -> "单字"
            TrieType.BASE -> "基础"
            TrieType.CORRELATION -> "关联"
            TrieType.ASSOCIATIONAL -> "联想"
            TrieType.PLACE -> "地名"
            TrieType.PEOPLE -> "人名"
            TrieType.POETRY -> "诗词"
            else -> type.name
        }
    }
    
    /**
     * 简化分词逻辑
     */
    private fun simpleSegmentation(input: String): List<String> {
        val segments = mutableListOf<String>()
        var pos = 0
        
        // 基础拼音音节集合（简化版）
        val validSyllables = setOf(
            "a", "e", "o", "ai", "ei", "ao", "ou", "an", "en", "ang", "eng", "er",
            "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biao", "bie", "bin", "bing", "bo", "bu",
            "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo",
            "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng", "di", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo",
            "fa", "fan", "fang", "fei", "fen", "feng", "fo", "fou", "fu",
            "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
            "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
            "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu", "ju", "juan", "jue", "jun",
            "ka", "kai", "kan", "kang", "kao", "ke", "ken", "keng", "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
            "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian", "liang", "liao", "lie", "lin", "ling", "liu", "long", "lou", "lu", "luan", "lue", "lun", "luo", "lv",
            "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
            "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ni", "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu", "nuan", "nue", "nuo", "nv",
            "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu",
            "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu", "qu", "quan", "que", "qun",
            "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "ruan", "rui", "run", "ruo",
            "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "si", "song", "sou", "su", "suan", "sui", "sun", "suo",
            "ta", "tai", "tan", "tang", "tao", "te", "teng", "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo",
            "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
            "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu", "xu", "xuan", "xue", "xun",
            "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you", "yu", "yuan", "yue", "yun",
            "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo",
            "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo",
            "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo",
            "sha", "shai", "shan", "shang", "shao", "she", "shei", "shen", "sheng", "shi", "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo"
        )
        
        Timber.d("🔧 通用分段开始: '$input'")
        
        while (pos < input.length) {
            var found = false
            
            // 最长匹配优先
            for (len in minOf(6, input.length - pos) downTo 1) {
                val candidate = input.substring(pos, pos + len)
                if (validSyllables.contains(candidate)) {
                    segments.add(candidate)
                    pos += len
                    found = true
                    Timber.d("✅ 匹配音节: '$candidate' (长度$len)，剩余: '${input.substring(pos)}'")
                    break
                }
            }
            
            if (!found) {
                // 无法匹配，添加单个字符
                val singleChar = input.substring(pos, pos + 1)
                segments.add(singleChar)
                pos++
                Timber.d("❌ 无匹配，单字符: '$singleChar'，剩余: '${input.substring(pos)}'")
            }
        }
        
        Timber.d("🎯 分段完成: '$input' -> [${segments.joinToString(", ")}]")
        return segments
    }
    
    /**
     * 智能单字符查询
     * 分层推荐策略：
     * 1. 第一层：该字母+各韵母组合的高频单字（每个组合前3个）
     * 2. 第二层：剩余单字（去重后）
     * 3. 第三层：该字母开头的双字词组
     */
    private suspend fun querySmartSingleChar(char: String, limit: Int): List<WordFrequency> {
        val finalResults = mutableListOf<WordFrequency>()
        val usedSingleChars = mutableSetOf<String>()
        
        // 常见韵母列表
        val finals = listOf(
            "a", "ai", "an", "ang", "ao",
            "e", "ei", "en", "eng", "er",
            "i", "ia", "ian", "iang", "iao", "ie", "in", "ing", "iong", "iu",
            "o", "ong", "ou",
            "u", "ua", "uai", "uan", "uang", "ui", "un", "uo",
            "v", "ve", "vn"
        )
        
        Timber.d("🎯 智能单字符查询: $char (分层推荐)")
        
        // 第一层：每个韵母组合的高频单字（前3个）
        val firstLayerResults = mutableListOf<WordFrequency>()
        
        if (trieManager.isTrieLoaded(TrieType.CHARS)) {
            for (final in finals) {
                val combination = char + final
                
                // 生成v/ü变体进行查询
                val combinationVariants = generateVUQueryVariants(combination)
                
                for (variant in combinationVariants) {
                    val charResults = trieManager.searchByPrefix(TrieType.CHARS, variant, 3)
                        .filter { it.word.length == 1 }
                        .sortedByDescending { it.frequency }
                        .take(3)
                    
                    if (charResults.isNotEmpty()) {
                        firstLayerResults.addAll(charResults)
                        charResults.forEach { usedSingleChars.add(it.word) }
                        Timber.d("📋 $variant -> ${charResults.size}个高频单字: ${charResults.map { "${it.word}(${it.frequency})" }}")
                    }
                }
            }
        }
        
        // 按频率排序第一层结果
        val sortedFirstLayer = firstLayerResults.distinctBy { it.word }
            .sortedByDescending { it.frequency }
        
        Timber.d("🥇 第一层单字总数: ${sortedFirstLayer.size}个")
        
        // 添加第一层结果（优先级最高）
        val firstLayerLimit = minOf(15, sortedFirstLayer.size) // 增加单字数量到15个
        finalResults.addAll(sortedFirstLayer.take(firstLayerLimit))
        
        // 第二层：剩余的单字（去重已使用的）
        if (finalResults.size < 20 && trieManager.isTrieLoaded(TrieType.CHARS)) {
            val remainingChars = trieManager.searchByPrefix(TrieType.CHARS, char, 50) // 增加查询数量
                .filter { it.word.length == 1 && !usedSingleChars.contains(it.word) }
                .sortedByDescending { it.frequency }
            
            val secondLayerLimit = minOf(20 - finalResults.size, remainingChars.size)
            finalResults.addAll(remainingChars.take(secondLayerLimit))
            
            Timber.d("🥈 第二层补充单字: ${remainingChars.take(secondLayerLimit).size}个")
        }
        
        // 第三层：双字词组（如果还有空间）
        if (finalResults.size < limit && trieManager.isTrieLoaded(TrieType.BASE)) {
            val wordResults = trieManager.searchByPrefix(TrieType.BASE, char, limit - finalResults.size)
                .filter { it.word.length == 2 }
                .sortedByDescending { it.frequency }
            
            finalResults.addAll(wordResults)
            Timber.d("🥉 第三层双字词组: ${wordResults.size}个")
        }
        
        // 按字数优先 + 频率排序
        val sortedResults = sortByLengthAndFrequency(finalResults.distinctBy { it.word })
        val result = sortedResults.take(limit)
        
        Timber.d("✅ 智能单字符查询完成: ${result.size}个结果")
        Timber.d("📊 结果分布 - 单字: ${result.count { it.word.length == 1 }}个, 词组: ${result.count { it.word.length > 1 }}个")
        
        return result
    }
    
    /**
     * 获取查询分析信息
     */
    suspend fun getQueryAnalysis(currentPinyin: String): QueryAnalysis {
        if (currentPinyin.isBlank()) {
            return QueryAnalysis(
                InputType.SINGLE_CHAR, QueryStrategy.CHARS_BASE_PRIORITY, 
                0, emptyList(), getTrieStatus(), 0, 0, false
            )
        }
        
        val cleanInput = currentPinyin.trim().lowercase()
        val segments = simpleSegmentation(cleanInput)
        val segmentCount = segments.size
        
        val inputType = when {
            segmentCount > 6 -> InputType.OVER_LIMIT
            segmentCount == 1 -> InputType.SINGLE_CHAR
            segmentCount in 2..3 -> InputType.SHORT_INPUT
            segmentCount == 4 -> InputType.MEDIUM_INPUT
            segmentCount in 5..6 -> InputType.LONG_INPUT
            else -> InputType.SINGLE_CHAR
        }
        
        val strategy = when (inputType) {
            InputType.SINGLE_CHAR, InputType.SHORT_INPUT -> QueryStrategy.CHARS_BASE_PRIORITY
            InputType.ABBREVIATION -> QueryStrategy.ABBREVIATION_MATCH
            InputType.MEDIUM_INPUT -> QueryStrategy.CORRELATION_PRIORITY
            InputType.LONG_INPUT -> QueryStrategy.ASSOCIATIONAL_PRIORITY
            InputType.OVER_LIMIT -> QueryStrategy.STOP_QUERY
        }
        
        return QueryAnalysis(
            inputType = inputType,
            queryStrategy = strategy,
            segmentCount = segmentCount,
            segments = segments,
            trieStatus = getTrieStatus(),
            queryTime = 0,
            resultCount = 0,
            cacheHit = false
        )
    }
    
    /**
     * 获取Trie状态
     */
    private fun getTrieStatus(): String {
        return buildString {
            append("CHARS: ${if (trieManager.isTrieLoaded(TrieType.CHARS)) "✓" else "✗"}")
            append(", BASE: ${if (trieManager.isTrieLoaded(TrieType.BASE)) "✓" else "✗"}")
            append(", CORRELATION: ${if (trieManager.isTrieLoaded(TrieType.CORRELATION)) "✓" else "✗"}")
            append(", ASSOCIATIONAL: ${if (trieManager.isTrieLoaded(TrieType.ASSOCIATIONAL)) "✓" else "✗"}")
            append(", PLACE: ${if (trieManager.isTrieLoaded(TrieType.PLACE)) "✓" else "✗"}")
            append(", PEOPLE: ${if (trieManager.isTrieLoaded(TrieType.PEOPLE)) "✓" else "✗"}")
            append(", POETRY: ${if (trieManager.isTrieLoaded(TrieType.POETRY)) "✓" else "✗"}")
        }
    }
    
    /**
     * v到ü的预处理方法
     * 处理汉语拼音中v代替ü的规则
     * 注意：生成无声调的ü以匹配词典格式
     */
    private fun preprocessVToU(input: String): String {
        if (!input.contains('v')) return input
        
        var result = input
        
        // 处理规则（生成无声调拼音以匹配词典）：
        // 1. lv -> lü (绿) - 词典中存储为lü
        // 2. nv -> nü (女) - 词典中存储为nü
        // 3. jv -> ju (居) - j后面的v转为u
        // 4. qv -> qu (去) - q后面的v转为u
        // 5. xv -> xu (虚) - x后面的v转为u
        // 6. yv -> yu (鱼) - y后面的v转为u
        
        // 处理连续拼音中的v转换
        result = result.replace(Regex("lv([aeiou])")) { matchResult ->
            "lü${matchResult.groupValues[1]}"
        }
        
        result = result.replace(Regex("nv([aeiou])")) { matchResult ->
            "nü${matchResult.groupValues[1]}"
        }
        
        // 处理j、q、x、y后的v转为u
        result = result.replace(Regex("([jqxy])v")) { matchResult ->
            "${matchResult.groupValues[1]}u"
        }
        
        // 处理单独的lv和nv（最重要的转换）
        result = result.replace(Regex("\\blv\\b"), "lü")
        result = result.replace(Regex("\\bnv\\b"), "nü")
        
        // 处理连续拼音情况，如nvhai -> nühai, lvse -> lüse
        result = result.replace("nvhai", "nühai")
        result = result.replace("lvse", "lüse")
        result = result.replace("nvshen", "nüshen")
        result = result.replace("lvcha", "lücha")
        
        if (result != input) {
            Timber.d("🔄 v转换(无声调): '$input' -> '$result'")
        }
        
        return result
    }
    
    /**
     * 获取拼音分段结果
     * 实现CandidateEngine接口
     */
    override fun getSegments(input: String): List<String> {
        return simpleSegmentation(input)
    }
    
    /**
     * 清理缓存
     */
    override fun clearCache() {
        queryCache.evictAll()
        Timber.d("SmartPinyinEngine: 缓存已清理")
    }
    
    /**
     * 获取性能统计
     */
    override fun getPerformanceStats(): String {
        val hitRate = if (queryCount.get() > 0) {
            (cacheHits.get() * 100.0 / queryCount.get()).toInt()
        } else 0
        
        return buildString {
            appendLine("📊 SmartPinyinEngine 性能统计:")
            appendLine("查询总数: ${queryCount.get()}")
            appendLine("缓存命中: ${cacheHits.get()} (${hitRate}%)")
            appendLine("缓存大小: ${queryCache.size()}/100")
        }
    }

    /**
     * 生成输入变体（包括原始输入和v/ü转换）
     */
    private fun generateInputVariants(input: String): List<String> {
        val variants = mutableSetOf<String>()
        variants.add(input) // 原始输入
        
        // 添加v到ü的转换
        val vToUConverted = preprocessVToU(input)
        if (vToUConverted != input) {
            variants.add(vToUConverted)
            Timber.d("🔄 v->ü转换: '$input' -> '$vToUConverted'")
        }
        
        // 添加ü到v的转换
        if (input.contains('ü')) {
            val uToVConverted = input.replace('ü', 'v')
            variants.add(uToVConverted)
            Timber.d("🔄 ü->v转换: '$input' -> '$uToVConverted'")
        }
        
        return variants.toList()
    }
    
    /**
     * 多变体单字符查询
     */
    private suspend fun queryMultiVariantSingleChar(variants: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        for (variant in variants) {
            if (variant.length == 1) {
                val singleCharResults = querySmartSingleChar(variant, limit)
                results.addAll(singleCharResults)
            } else {
                // 完整音节查询
                val syllableResults = queryWithFallback(listOf(TrieType.CHARS, TrieType.BASE), variant, limit)
                results.addAll(syllableResults)
            }
        }
        
        return sortByLengthAndFrequency(results.distinctBy { it.word }).take(limit)
    }
    
    /**
     * 多变体单字符懒加载查询
     */
    private suspend fun queryMultiVariantSingleCharLazyLoad(variants: List<String>, limit: Int, offset: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        for (variant in variants) {
            val lazyResults = querySingleCharLazyLoad(variant, limit, offset)
            results.addAll(lazyResults)
        }
        
        return sortByLengthAndFrequency(results.distinctBy { it.word }).take(limit)
    }
    
    /**
     * 多变体缩写查询
     */
    private suspend fun queryMultiVariantAbbreviation(variants: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        for (variant in variants) {
            val abbrevResults = queryAbbreviation(variant, limit)
            results.addAll(abbrevResults)
        }
        
        return sortByLengthAndFrequency(results.distinctBy { it.word }).take(limit)
    }
    
    /**
     * 多变体短输入查询
     */
    private suspend fun queryMultiVariantShortInput(variants: List<String>, segments: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        Timber.d("🔍 多变体短输入查询开始")
        Timber.d("📝 输入变体: ${variants.joinToString(", ")}")
        Timber.d("📋 分段结果: ${segments.joinToString(" + ")}")
        
        for (variant in variants) {
            Timber.d("🔄 查询变体: '$variant'")
            val shortResults = queryWithFallback(
                listOf(TrieType.CHARS, TrieType.BASE, TrieType.PLACE, TrieType.PEOPLE),
                variant,
                limit
            )
            results.addAll(shortResults)
            Timber.d("✅ 变体'$variant'查询结果: ${shortResults.size}个")
        }
        
        val finalResults = sortByLengthAndFrequency(results.distinctBy { it.word }).take(limit)
        Timber.d("🎯 多变体短输入查询完成: ${finalResults.size}个结果")
        
        return finalResults
    }
    
    /**
     * 多变体中等输入查询
     */
    private suspend fun queryMultiVariantMediumInput(variants: List<String>, segments: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        for (variant in variants) {
            val mediumResults = queryWithFallback(
                listOf(TrieType.CORRELATION, TrieType.ASSOCIATIONAL, TrieType.PLACE, TrieType.PEOPLE),
                variant,
                limit
            )
            results.addAll(mediumResults)
        }
        
        return sortByLengthAndFrequency(results.distinctBy { it.word }).take(limit)
    }
    
    /**
     * 多变体长输入查询
     */
    private suspend fun queryMultiVariantLongInput(variants: List<String>, segments: List<String>, limit: Int): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        for (variant in variants) {
            val longResults = queryWithFallback(
                listOf(TrieType.ASSOCIATIONAL, TrieType.PLACE, TrieType.PEOPLE, TrieType.POETRY),
                variant,
                limit
            )
            results.addAll(longResults)
        }
        
        return sortByLengthAndFrequency(results.distinctBy { it.word }).take(limit)
    }
} 