package com.shenji.aikeyboard.data

import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.find
import timber.log.Timber

/**
 * 候选词策略接口，定义候选词查询的基本方法
 */
interface CandidateStrategy {
    /**
     * 查询候选词
     * @param realm Realm数据库实例
     * @param pinyin 用户输入的拼音
     * @param limit 返回候选词的最大数量
     * @param excludeTypes 需要排除的词典类型
     * @return 候选词列表，已按词频排序
     */
    fun queryCandidates(
        realm: Realm, 
        pinyin: String, 
        limit: Int, 
        excludeTypes: List<String> = emptyList()
    ): List<WordFrequency>
    
    /**
     * 获取策略名称，用于日志和调试
     */
    fun getStrategyName(): String
}

/**
 * 空输入或单字符输入策略（0-1个字符）
 * 显示高频单字和基础词
 */
class EmptyOrSingleCharStrategy : CandidateStrategy {
    
    override fun queryCandidates(
        realm: Realm, 
        pinyin: String, 
        limit: Int, 
        excludeTypes: List<String>
    ): List<WordFrequency> {
        Timber.d("执行空输入或单字符查询策略, 拼音: '$pinyin'")
        
        val results = mutableSetOf<Entry>()
        
        try {
            // 检测是否为有效音节
            val isValidSyllable = pinyin.isNotEmpty() && 
                !pinyin.contains(" ") && 
                com.shenji.aikeyboard.data.PinyinSplitter.isValidSyllable(pinyin.trim().lowercase())
            
            // 查询音节对应单字的数量占比
            val charsLimit = if (isValidSyllable) limit / 2 else limit / 3
            
            // 1. 首先从chars词典中获取高频单字
            val charsEntries = realm.query<Entry>("type == 'chars'")
                .limit(limit)
                .find()
                .filter { it.type !in excludeTypes }
                .sortedByDescending { it.frequency }
                .take(charsLimit) // 单字占比调整
            
            results.addAll(charsEntries)
            
            // 2. 如果有输入字符，查询以该字符开头的单字
            if (pinyin.isNotEmpty()) {
                val normalizedPrefix = pinyin.lowercase().trim()
                
                val matchedChars = realm.query<Entry>(
                    "pinyin BEGINSWITH $0 AND type == 'chars'", 
                    normalizedPrefix
                )
                .limit(limit)
                .find()
                .filter { it.type !in excludeTypes && it !in results }
                
                results.addAll(matchedChars)
            }
            
            // 3. 如果是有效音节，查询以该音节为前缀的词组
            if (isValidSyllable) {
                val normalizedPrefix = pinyin.lowercase().trim()
                
                // 查询以该音节为前缀的base词典词组
                val baseEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'base'", normalizedPrefix)
                    .limit(limit)
                    .find()
                    .filter { it.type !in excludeTypes && it !in results && it.word.length > 1 } // 只要多字词
                    .sortedByDescending { it.frequency }
                    .take((limit - results.size) * 2 / 3) // 多字词占剩余结果的2/3
                    
                results.addAll(baseEntries)
                
                // 如果结果仍不足，从correlation词典查询
                if (results.size < limit) {
                    val correlationEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'correlation'", normalizedPrefix)
                        .limit(limit - results.size)
                        .find()
                        .filter { it.type !in excludeTypes && it !in results }
                        .sortedByDescending { it.frequency }
                        .take(limit - results.size)
                        
                    results.addAll(correlationEntries)
                }
            }
            // 4. 如果结果不足，从base词典补充高频词
            else if (results.size < limit) {
                val baseEntries = realm.query<Entry>("type == 'base'")
                    .limit(limit - results.size)
                    .find()
                    .filter { it.type !in excludeTypes && it !in results }
                    .sortedByDescending { it.frequency }
                    .take(limit - results.size)
                    
                results.addAll(baseEntries)
            }
            
            Timber.d("空输入或单字符查询结果: ${results.size}个候选项")
            
        } catch (e: Exception) {
            Timber.e(e, "空输入或单字符候选词查询失败")
        }
        
        // 按词频排序并转换为WordFrequency
        return results
            .sortedWith(
                compareBy<Entry> {
                    // 单字优先于多字词
                    if (it.type == "chars" || it.word.length == 1) 0 else 1 
                }
                .thenByDescending { it.frequency }  // 然后按词频降序
            )
            .take(limit)
            .map { WordFrequency(it.word, it.frequency) }
    }
    
    override fun getStrategyName(): String = "空输入或单字符策略"
}

/**
 * 双字符输入策略（2-3个字符）
 * 主要从base基础词库查询2-3字词组
 */
class TwoToThreeCharStrategy : CandidateStrategy {
    
    override fun queryCandidates(
        realm: Realm, 
        pinyin: String, 
        limit: Int, 
        excludeTypes: List<String>
    ): List<WordFrequency> {
        val normalizedPrefix = pinyin.lowercase().trim()
        val noSpacePrefix = normalizedPrefix.replace(" ", "")
        
        Timber.d("执行2-3字符查询策略, 拼音: '$normalizedPrefix', 无空格: '$noSpacePrefix'")
        
        val results = mutableSetOf<Entry>()
        val exactMatches = mutableSetOf<Entry>() // 存储完全匹配的词条
        
        try {
            // 1. 查询完全匹配的词条（拼音完全相同）
            val exactMatchEntries = realm.query<Entry>("pinyin == $0 AND type == 'base'", normalizedPrefix)
                .limit(limit * 2)
                .find()
                .filter { it.type !in excludeTypes }
                
            Timber.d("完全匹配的词条数量: ${exactMatchEntries.size}")
            exactMatches.addAll(exactMatchEntries)
                
            // 2. 优先从base词库中查询2-3字词组（前缀匹配）
            if (exactMatches.size < limit) {
                val baseEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'base'", normalizedPrefix)
                    .limit(limit * 2)
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches }
                
                results.addAll(baseEntries)
            }
            
            // 3. 无空格匹配，如果结果不足
            if (exactMatches.size + results.size < limit) {
                val noSpaceEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'base'", noSpacePrefix)
                    .limit(limit)
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches && it !in results }
                
                results.addAll(noSpaceEntries)
            }
            
            // 4. 从compatible词库中查询多音字词组，如果结果不足
            if (exactMatches.size + results.size < limit) {
                val compatibleEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'compatible'", normalizedPrefix)
                    .limit(limit - results.size)
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches && it !in results }
                
                results.addAll(compatibleEntries)
                
                // 无空格匹配兼容词库
                if (exactMatches.size + results.size < limit) {
                    val noSpaceCompatibleEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'compatible'", noSpacePrefix)
                        .limit(limit - results.size)
                        .find()
                        .filter { it.type !in excludeTypes && it !in exactMatches && it !in results }
                    
                    results.addAll(noSpaceCompatibleEntries)
                }
            }
            
            // 5. 添加联想的3字词组，作为辅助提示
            if (exactMatches.size + results.size < limit) {
                val threeCharEntries = realm.query<Entry>("LENGTH(word) == 3 AND type == 'correlation'")
                    .limit((limit - results.size) / 2) // 联想词占一半空间
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches && it !in results }
                    .sortedByDescending { it.frequency }
                
                results.addAll(threeCharEntries)
            }
            
            Timber.d("2-3字符查询结果: 完全匹配${exactMatches.size}个, 前缀匹配${results.size}个")
            
        } catch (e: Exception) {
            Timber.e(e, "2-3字符候选词查询失败")
        }
        
        // 合并两个结果集，先放精确匹配，再放前缀匹配
        val combinedResults = exactMatches + results
        
        // 排序规则：
        // 1. 完全匹配的词排在最前面
        // 2. 然后按照词频从高到低排序
        // 3. 对于词频相同的词组，2字词组排在3字词组之前
        // 4. 联想的3字词组排在最后
        return combinedResults
            .sortedWith(
                compareBy<Entry> { if (it in exactMatches) 0 else 1 } // 完全匹配优先级更高
                .thenByDescending { it.frequency }  // 然后按词频降序
                .thenBy { it.word.length }  // 词长升序排列，2字词在3字词前面
                .thenBy { if (it.type == "correlation") 1 else 0 }  // 联想词排在后面
            )
            .take(limit)
            .map { WordFrequency(it.word, it.frequency) }
    }
    
    override fun getStrategyName(): String = "2-3字符策略"
}

/**
 * 四字词组策略（4个字符）
 * 主要从correlation关联词库查询4字词组
 */
class FourCharStrategy : CandidateStrategy {
    
    override fun queryCandidates(
        realm: Realm, 
        pinyin: String, 
        limit: Int, 
        excludeTypes: List<String>
    ): List<WordFrequency> {
        val normalizedPrefix = pinyin.lowercase().trim()
        val noSpacePrefix = normalizedPrefix.replace(" ", "")
        
        Timber.d("执行4字符查询策略, 拼音: '$normalizedPrefix', 无空格: '$noSpacePrefix'")
        
        val results = mutableSetOf<Entry>()
        val exactMatches = mutableSetOf<Entry>() // 存储完全匹配的词条
        
        try {
            // 1. 查询完全匹配的词条（拼音完全相同）
            val exactMatchEntries = realm.query<Entry>("pinyin == $0", normalizedPrefix)
                .limit(limit * 2)
                .find()
                .filter { it.type !in excludeTypes }
                
            Timber.d("完全匹配的词条数量: ${exactMatchEntries.size}")
            exactMatches.addAll(exactMatchEntries)
            
            // 2. 优先从correlation关联词库查询4字词组
            if (exactMatches.size < limit) {
                val correlationEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'correlation'", normalizedPrefix)
                    .limit(limit * 2)
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches }
                
                results.addAll(correlationEntries)
            }
            
            // 3. 无空格匹配，如果结果不足
            if (exactMatches.size + results.size < limit) {
                val noSpaceEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'correlation'", noSpacePrefix)
                    .limit(limit)
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches && it !in results }
                
                results.addAll(noSpaceEntries)
            }
            
            // 4. 从base词库补充查询，如果结果不足
            if (exactMatches.size + results.size < limit) {
                val baseEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND LENGTH(word) == 4 AND type == 'base'", normalizedPrefix)
                    .limit(limit - results.size)
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches && it !in results }
                
                results.addAll(baseEntries)
            }
            
            // 5. 从compatible兼容词库查询多音字词组，如果结果不足
            if (exactMatches.size + results.size < limit) {
                val compatibleEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'compatible'", normalizedPrefix)
                    .limit(limit - results.size)
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches && it !in results }
                
                results.addAll(compatibleEntries)
            }
            
            // 6. 添加联想的5字以上词组，作为辅助提示
            if (exactMatches.size + results.size < limit) {
                val longWordEntries = realm.query<Entry>("LENGTH(word) >= 5 AND type == 'associational'")
                    .limit((limit - results.size) / 3) // 长词占较少空间
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches && it !in results }
                    .sortedByDescending { it.frequency }
                
                results.addAll(longWordEntries)
            }
            
            Timber.d("4字符查询结果: 完全匹配${exactMatches.size}个, 前缀匹配${results.size}个")
            
        } catch (e: Exception) {
            Timber.e(e, "4字符候选词查询失败")
        }
        
        // 合并两个结果集，先放精确匹配，再放前缀匹配
        val combinedResults = exactMatches + results
        
        // 排序规则：
        // 1. 完全匹配的词排在最前面
        // 2. 然后按照词频从高到低排序
        // 3. 对于词频相同的4字词组，按照字典序排列
        // 4. 联想的5字以上词组排在最后
        return combinedResults
            .sortedWith(
                compareBy<Entry> { if (it in exactMatches) 0 else 1 } // 完全匹配优先级更高
                .thenByDescending { it.frequency } // 然后按词频降序
                .thenBy { if (it.word.length > 4) 1 else 0 } // 4字词在5字以上词前面
                .thenBy { it.word } // 按字典序
            )
            .take(limit)
            .map { WordFrequency(it.word, it.frequency) }
    }
    
    override fun getStrategyName(): String = "4字符策略"
}

/**
 * 长词策略（5个字符以上）
 * 主要从associational联想词库查询5字以上词组
 */
class LongWordStrategy : CandidateStrategy {
    
    override fun queryCandidates(
        realm: Realm, 
        pinyin: String, 
        limit: Int, 
        excludeTypes: List<String>
    ): List<WordFrequency> {
        val normalizedPrefix = pinyin.lowercase().trim()
        val noSpacePrefix = normalizedPrefix.replace(" ", "")
        
        Timber.d("执行5字符以上查询策略, 拼音: '$normalizedPrefix', 无空格: '$noSpacePrefix'")
        
        val results = mutableSetOf<Entry>()
        val exactMatches = mutableSetOf<Entry>() // 存储完全匹配的词条
        
        try {
            // 1. 查询完全匹配的词条（拼音完全相同）
            val exactMatchEntries = realm.query<Entry>("pinyin == $0", normalizedPrefix)
                .limit(limit * 2)
                .find()
                .filter { it.type !in excludeTypes }
                
            Timber.d("完全匹配的词条数量: ${exactMatchEntries.size}")
            exactMatches.addAll(exactMatchEntries)
            
            // 2. 优先从associational联想词库查询
            if (exactMatches.size < limit) {
                val associationalEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'associational'", normalizedPrefix)
                    .limit(limit * 2)
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches }
                
                results.addAll(associationalEntries)
            }
            
            // 3. 无空格匹配，如果结果不足
            if (exactMatches.size + results.size < limit) {
                val noSpaceEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'associational'", noSpacePrefix)
                    .limit(limit)
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches && it !in results }
                
                results.addAll(noSpaceEntries)
            }
            
            // 4. 包含匹配（联想搜索），如果结果不足
            if (exactMatches.size + results.size < limit) {
                val containsEntries = realm.query<Entry>("pinyin CONTAINS $0 AND type == 'associational'", normalizedPrefix)
                    .limit(limit - results.size)
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches && it !in results }
                
                results.addAll(containsEntries)
            }
            
            // 5. 从correlation和base词库补充查询长词
            if (exactMatches.size + results.size < limit) {
                val otherLongEntries = realm.query<Entry>("LENGTH(word) >= 5 AND (type == 'correlation' OR type == 'base')")
                    .limit(limit - results.size)
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches && it !in results }
                    .sortedByDescending { it.frequency }
                
                results.addAll(otherLongEntries)
            }
            
            // 6. 保留一些4字词组作为参考
            if (exactMatches.size + results.size < limit) {
                val fourCharEntries = realm.query<Entry>("LENGTH(word) == 4 AND type == 'correlation'")
                    .limit((limit - results.size) / 4) // 4字词占较少空间
                    .find()
                    .filter { it.type !in excludeTypes && it !in exactMatches && it !in results }
                    .sortedByDescending { it.frequency }
                
                results.addAll(fourCharEntries)
            }
            
            Timber.d("5字符以上查询结果: 完全匹配${exactMatches.size}个, 前缀匹配${results.size}个")
            
        } catch (e: Exception) {
            Timber.e(e, "5字符以上候选词查询失败")
        }
        
        // 合并两个结果集，先放精确匹配，再放前缀匹配
        val combinedResults = exactMatches + results
        
        // 排序规则：
        // 1. 完全匹配的词排在最前面
        // 2. 然后按照词频从高到低排序
        // 3. 对于词频相同的词组，按照字典序排列
        return combinedResults
            .sortedWith(
                compareBy<Entry> { if (it in exactMatches) 0 else 1 } // 完全匹配优先级更高
                .thenByDescending { it.frequency } // 然后按词频降序
                .thenBy { it.word } // 按字典序
            )
            .take(limit)
            .map { WordFrequency(it.word, it.frequency) }
    }
    
    override fun getStrategyName(): String = "5字符以上策略"
}

/**
 * 拼音首字母查询策略
 * 用于查询类似"wx"(微信),"zdl"(知道了)这样的首字母缩写
 */
class InitialsQueryStrategy : CandidateStrategy {
    
    // 常见首字母缩写和对应单词映射
    private val commonInitialsMap = mapOf(
        "wx" to "微信",
        "qq" to "QQ",
        "tx" to "腾讯",
        "bd" to "百度",
        "yd" to "有道",
        "dt" to "动态",
        "pyq" to "朋友圈",
        "xt" to "系统",
        "sjsm" to "手机扫码",
        "zfb" to "支付宝",
        "rz" to "日志",
        "hb" to "红包",
        "zdl" to "知道了",
        "xsz" to "心声扬",
        "tql" to "太强了",
        "yyds" to "永远的神",
        "nh" to "你好",
        "wq" to "晚安",
        "zy" to "注意",
        "zj" to "资金",
        "zzy" to "正在写",
        "gzs" to "工作室"
    )
    
    override fun queryCandidates(
        realm: Realm, 
        pinyin: String, 
        limit: Int, 
        excludeTypes: List<String>
    ): List<WordFrequency> {
        if (pinyin.isBlank()) return emptyList()
        
        val initials = pinyin.lowercase().trim()
        Timber.d("执行首字母查询策略: '$initials'")
        
        val resultList = mutableListOf<Entry>()
        
        try {
            // 1. 精确匹配首字母
            val matchList1 = realm.query<Entry>("initialLetters == $0", initials)
                .find()
                .filter { it.type !in excludeTypes }
                .sortedByDescending { it.frequency }
                .toList()
            
            Timber.d("精确匹配首字母结果数: ${matchList1.size}")
            
            if (matchList1.isNotEmpty()) {
                val samplesText = matchList1.take(3).joinToString { "${it.word}[${it.pinyin}]" }
                Timber.d("精确匹配样本: $samplesText")
            }
            
            resultList.addAll(matchList1)
            
            // 2. 如果结果不足，尝试首字母前缀匹配
            if (resultList.size < limit) {
                val matchList2 = realm.query<Entry>("initialLetters BEGINSWITH $0", initials)
                    .find()
                    .filter { it.type !in excludeTypes && it !in resultList }
                    .sortedByDescending { it.frequency }
                    .take(limit - resultList.size)
                    .toList()
                
                Timber.d("前缀匹配结果数: ${matchList2.size}")
                
                if (matchList2.isNotEmpty()) {
                    val samplesText = matchList2.take(3).joinToString { "${it.word}[${it.pinyin}]" }
                    Timber.d("前缀匹配样本: $samplesText")
                }
                
                resultList.addAll(matchList2)
            }
            
            // 3. 如果结果仍然不足，尝试部分匹配
            if (resultList.size < limit) {
                val matchList3 = realm.query<Entry>("(type == 'base' OR type == 'correlation') AND LENGTH(word) <= 4")
                    .limit(limit * 2)
                    .find()
                    .filter { 
                        it.type !in excludeTypes && 
                        it !in resultList && 
                        it.initialLetters.contains(initials) // 包含匹配
                    }
                    .sortedByDescending { it.frequency }
                    .take(limit - resultList.size)
                    .toList()
                
                Timber.d("部分匹配结果数: ${matchList3.size}")
                resultList.addAll(matchList3)
            }
            
            // 4. 检查是否在常见首字母缩写列表中
            if (resultList.isEmpty() && commonInitialsMap.containsKey(initials)) {
                val commonWord = commonInitialsMap[initials]
                Timber.d("使用内置缩写: $initials -> $commonWord")
                
                // 创建虚拟词条
                val syntheticEntry = Entry().apply {
                    id = "synthetic_${initials}"
                    word = commonWord ?: ""
                    initialLetters = initials
                    frequency = 10000 
                    type = "synthetic"
                }
                
                resultList.add(syntheticEntry)
            }
            
            Timber.d("首字母查询最终结果: ${resultList.size}个候选项")
            
            // 5. 如果空结果，返回高频词
            if (resultList.isEmpty() && !commonInitialsMap.containsKey(initials)) {
                Timber.w("首字母查询无结果，返回高频词")
                val highFreqEntries = realm.query<Entry>("type == 'base'")
                    .limit(limit)
                    .find()
                    .filter { it.type !in excludeTypes }
                    .sortedByDescending { it.frequency }
                    .take(limit)
                    .toList()
                
                resultList.addAll(highFreqEntries)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "首字母查询失败: ${e.message}")
        }
        
        return resultList
            .sortedByDescending { it.frequency }
            .take(limit)
            .map { WordFrequency(it.word, it.frequency) }
    }
    
    override fun getStrategyName(): String = "首字母查询策略"
}

/**
 * 候选词策略工厂，根据输入拼音的长度和特征选择不同的查询策略
 */
object CandidateStrategyFactory {
    
    /**
     * 根据输入拼音长度获取相应的候选词策略
     * @param pinyinLength 输入的拼音长度
     * @param rawInput 原始输入，用于检测是否为首字母输入
     * @return 对应的候选词策略
     */
    fun getStrategy(pinyinLength: Int, rawInput: String = ""): CandidateStrategy {
        // 增强首字母模式检测：优先处理首字母输入模式
        if (rawInput.isNotEmpty()) {
            if (com.shenji.aikeyboard.utils.PinyinInitialUtils.isPossibleInitials(rawInput)) {
                Timber.d("策略工厂检测到首字母输入模式: '$rawInput'")
                return InitialsQueryStrategy()
            }
            
            // 新增：检测是否为完整的拼音音节，如"tai"、"xiu"等
            val trimmedInput = rawInput.trim().lowercase()
            if (trimmedInput.isNotEmpty() && !trimmedInput.contains(" ") && 
                com.shenji.aikeyboard.data.PinyinSplitter.isValidSyllable(trimmedInput)) {
                Timber.d("策略工厂检测到完整拼音音节: '$trimmedInput'，使用单字符策略")
                return CompleteSyllableStrategy(trimmedInput)
            }
        }
        
        // 根据拼音长度选择策略
        return when {
            pinyinLength <= 1 -> EmptyOrSingleCharStrategy()
            pinyinLength <= 3 -> TwoToThreeCharStrategy()
            pinyinLength == 4 -> FourCharStrategy()
            else -> LongWordStrategy()
        }
    }
}

/**
 * 完整拼音音节策略 - 专门用于处理单个完整拼音音节输入
 * 这是一个简单直接的策略类，优先从chars词典中获取对应的汉字
 */
class CompleteSyllableStrategy(private val syllable: String) : CandidateStrategy {
    
    override fun queryCandidates(
        realm: Realm, 
        pinyin: String, 
        limit: Int, 
        excludeTypes: List<String>
    ): List<WordFrequency> {
        Timber.d("执行完整音节查询策略, 音节: '$syllable'")
        
        val results = mutableListOf<Entry>()
        
        try {
            // 1. 直接精确匹配拼音
            val exactMatches = realm.query<Entry>("pinyin == $0 AND type == 'chars'", syllable)
                .find()
                .sortedByDescending { it.frequency }
                .toList()
                
            Timber.d("完整音节精确匹配结果: ${exactMatches.size}个")
            results.addAll(exactMatches)
            
            // 2. 如果结果太少，尝试前缀匹配
            if (results.size < 5) {
                val prefixMatches = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'chars'", syllable)
                    .find()
                    .filter { it !in results }
                    .sortedByDescending { it.frequency }
                    .take(limit - results.size)
                    .toList()
                
                Timber.d("完整音节前缀匹配结果: ${prefixMatches.size}个")
                results.addAll(prefixMatches)
            }
            
            // 3. 如果需要，添加一些常用词组
            if (results.size < limit) {
                val wordMatches = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'base'", syllable)
                    .find()
                    .filter { it.word.length > 1 } // 只要多字词
                    .sortedByDescending { it.frequency }
                    .take(limit - results.size)
                    .toList()
                
                Timber.d("添加以该音节开头的词组: ${wordMatches.size}个")
                results.addAll(wordMatches)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "完整音节查询失败: ${e.message}")
        }
        
        // 如果结果为空，添加音节本身
        if (results.isEmpty()) {
            Timber.w("完整音节'$syllable'查询无结果，返回音节本身")
            return listOf(WordFrequency(syllable, 100))
        }
        
        return results.map { WordFrequency(it.word, it.frequency) }
    }
    
    override fun getStrategyName(): String = "完整音节策略"
} 