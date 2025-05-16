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
            // 1. 首先从chars词典中获取高频单字
            val charsEntries = realm.query<Entry>("type == 'chars'")
                .limit(limit)
                .find()
                .filter { it.type !in excludeTypes }
                .sortedByDescending { it.frequency }
                .take(limit / 2) // 单字占一半
            
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
            
            // 3. 如果结果不足，从base词典补充高频词
            if (results.size < limit) {
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
            .sortedByDescending { it.frequency }
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
    
    override fun queryCandidates(
        realm: Realm, 
        pinyin: String, 
        limit: Int, 
        excludeTypes: List<String>
    ): List<WordFrequency> {
        if (pinyin.isBlank()) return emptyList()
        
        val initials = pinyin.lowercase().trim()
        Timber.d("执行首字母查询策略, 首字母: '$initials'")
        
        val results = mutableListOf<Entry>()
        
        try {
            // 1. 精确匹配首字母
            val exactMatches = realm.query<Entry>("initialLetters == $0", initials)
                .find()
                .filter { it.type !in excludeTypes }
                .sortedByDescending { it.frequency }
                .take(limit)
                
            results.addAll(exactMatches)
            
            // 2. 如果结果不足，尝试首字母前缀匹配
            if (results.size < limit) {
                val prefixMatches = realm.query<Entry>("initialLetters BEGINSWITH $0", initials)
                    .find()
                    .filter { it.type !in excludeTypes && it !in results }
                    .sortedByDescending { it.frequency }
                    .take(limit - results.size)
                    
                results.addAll(prefixMatches)
            }
            
            Timber.d("首字母查询结果: ${results.size}个候选项")
            
        } catch (e: Exception) {
            Timber.e(e, "首字母候选词查询失败: ${e.message}")
        }
        
        return results
            .sortedByDescending { it.frequency }
            .take(limit)
            .map { WordFrequency(it.word, it.frequency) }
    }
    
    override fun getStrategyName(): String = "首字母查询策略"
}

/**
 * 候选词策略工厂，根据输入长度返回对应的策略
 */
object CandidateStrategyFactory {
    
    /**
     * 根据输入拼音长度获取相应的候选词策略
     * @param pinyinLength 输入的拼音长度
     * @param rawInput 原始输入，用于检测是否为首字母输入
     * @return 对应的候选词策略
     */
    fun getStrategy(pinyinLength: Int, rawInput: String = ""): CandidateStrategy {
        // 检查是否为首字母输入模式
        if (rawInput.isNotEmpty() && com.shenji.aikeyboard.utils.PinyinInitialUtils.isPossibleInitials(rawInput)) {
            return InitialsQueryStrategy()
        }
        
        // 原有的逻辑
        return when {
            pinyinLength <= 1 -> EmptyOrSingleCharStrategy()
            pinyinLength <= 3 -> TwoToThreeCharStrategy()
            pinyinLength == 4 -> FourCharStrategy()
            else -> LongWordStrategy()
        }
    }
} 