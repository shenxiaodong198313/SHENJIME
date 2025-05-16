package com.shenji.aikeyboard.data

import android.content.Context
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.WordFrequency
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.find
import timber.log.Timber
import java.io.File
import kotlin.math.min
import java.util.concurrent.atomic.AtomicInteger

/**
 * 词典数据仓库类，用于提供词典相关的数据操作
 */
class DictionaryRepository {
    private val realm get() = com.shenji.aikeyboard.ShenjiApplication.realm
    
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
     * 作为备用，确保即使数据库查询失败也能显示基本的词典类型
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
     * 分页获取特定类型的词条，按词频降序排列
     * @param type 词典类型
     * @param offset 起始偏移量
     * @param limit 获取数量
     */
    fun getEntriesByTypeOrderedByFrequency(type: String, offset: Int, limit: Int): List<Entry> {
        return try {
            realm.query<Entry>("type == $0", type)
                .find()
                .asSequence()
                .sortedByDescending { it.frequency }  // 按词频降序排列
                .drop(offset)
                .take(limit)
                .toList()
        } catch (e: Exception) {
            Timber.e(e, "按词频获取${type}词条列表失败")
            emptyList()
        }
    }
    
    /**
     * 获取词典文件大小
     */
    fun getDictionaryFileSize(): Long {
        val context = com.shenji.aikeyboard.ShenjiApplication.appContext
        val dictFile = File(context.filesDir, "dictionaries/shenji_dict.realm")
        return if (dictFile.exists()) dictFile.length() else 0
    }
    
    /**
     * 获取词典数据库文件
     */
    fun getDictionaryFile(): File {
        val context = com.shenji.aikeyboard.ShenjiApplication.appContext
        return File(context.filesDir, "dictionaries/shenji_dict.realm")
    }
    
    /**
     * 获取Realm数据库占用内存
     * 注意：这是一个估算值
     */
    fun getMemoryUsage(): Long {
        // 由于无法直接获取Realm内存占用，这里返回文件大小作为估算值
        return getDictionaryFileSize()
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
     * 返回所有词典模块的平级列表，没有分类标题
     */
    fun getDictionaryModules(): List<DictionaryModule> {
        val modules = mutableListOf<DictionaryModule>()
        
        try {
            // 获取预编译词典加载状态
            val dictManager = DictionaryManager.instance
            val isPrecompiledDictLoaded = dictManager.isLoaded()
            
            // 获取所有词典类型
            val types = getAllDictionaryTypes()
            Timber.d("获取到词典类型列表: ${types.joinToString()}")
            
            // 添加各个词典类型，无论是否预编译
            for (type in types) {
                val count = getEntryCountByType(type)
                val chineseName = getChineseNameForType(type)
                
                // 添加词典模块，所有词典都不再显示为已加载到内存
                modules.add(
                    DictionaryModule(
                        type = type,
                        chineseName = chineseName,
                        entryCount = count,
                        isInMemory = false,
                        memoryUsage = 0, // 不单独计算内存
                        isPrecompiled = false,
                        isMemoryLoaded = false
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "构建词典模块列表失败，使用测试数据")
            
            // 出错时使用测试数据，确保至少能显示一些内容
            modules.clear()
            
            // 测试数据 - 添加所有词典类型，无分组
            val testTypes = listOf(
                Triple("chars", "单字词典", 100000),
                Triple("base", "基础词典", 773490),
                Triple("correlation", "关联词典", 570000),
                Triple("associational", "联想词典", 340000),
                Triple("compatible", "兼容词典", 5000),
                Triple("corrections", "纠错词典", 137),
                Triple("place", "地名词典", 45000),
                Triple("people", "人名词典", 40000),
                Triple("poetry", "诗词词典", 320000)
            )
            
            for ((type, name, count) in testTypes) {
                modules.add(
                    DictionaryModule(
                        type = type,
                        chineseName = name,
                        entryCount = count,
                        isInMemory = false,
                        memoryUsage = 0,
                        isPrecompiled = false,
                        isMemoryLoaded = false
                    )
                )
            }
        }
        
        return modules
    }
    
    /**
     * 根据词典类型获取中文名称
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
            "BASIC" -> "基础词库"
            "COMMON" -> "常用词库"
            "SPECIAL" -> "专业词库"
            "PERSON" -> "人名词库"
            "PLACE" -> "地名词库"
            "ORG" -> "机构词库"
            "NETWORK" -> "网络词库"
            "EMOJI" -> "表情词库"
            "USER" -> "用户词库"
            else -> "${type}词库"
        }
    }
    
    /**
     * 获取特定类型词典的最后修改时间戳
     * 注意：由于Realm不提供单个表的修改时间，这里使用数据库文件修改时间和类型数量作为特征
     */
    fun getLastModifiedTime(type: String): Long {
        val context = com.shenji.aikeyboard.ShenjiApplication.appContext
        val dictFile = File(context.filesDir, "dictionaries/shenji_dict.realm")
        
        // 获取文件的最后修改时间
        val fileModTime = if (dictFile.exists()) dictFile.lastModified() else 0L
        
        try {
            // 结合词条数量，以检测该类型词典内容变化
            val entryCount = getEntryCountByType(type)
            // 使用文件修改时间和词条数量的组合作为特征
            return fileModTime + entryCount
        } catch (e: Exception) {
            Timber.e(e, "获取词典类型 $type 的最后修改时间失败")
            return fileModTime
        }
    }
    
    /**
     * 根据拼音前缀从数据库搜索词条，使用策略模式根据输入长度选择合适的查询策略
     * @param prefix 拼音前缀
     * @param limit 最大返回数量
     * @param excludeTypes 要排除的词典类型（如高频词典，因为它们已经在内存中查询过）
     * @return 匹配的词条列表
     */
    fun searchEntries(prefix: String, limit: Int, excludeTypes: List<String>): List<WordFrequency> {
        if (prefix.isBlank()) {
            // 空输入，使用空输入策略
            return CandidateStrategyFactory.getStrategy(0)
                .queryCandidates(realm, "", limit, excludeTypes)
        }
        
        try {
            val input = prefix.lowercase().trim()
            Timber.d("查询候选词: '$input'")
            
            // 检查输入是否为汉字
            val isChineseInput = input.any { it.code in 0x4E00..0x9FFF }
            if (isChineseInput) {
                // 如果输入为汉字，直接按汉字查询
                Timber.d("检测到汉字输入: '$input'，直接搜索汉字")
                return searchByWord(input, limit)
            }
            
            // 先检查是否为可能的首字母输入模式
            if (com.shenji.aikeyboard.utils.PinyinInitialUtils.isPossibleInitials(input)) {
                Timber.d("检测到首字母输入模式: '$input'")
                // 对于首字母模式，使用专门的首字母查询策略，直接传入原始input，不进行拼音分词处理
                return InitialsQueryStrategy().queryCandidates(realm, input, limit, excludeTypes)
            }
            
            // 如果是短音节(2-3个字母，如"ba","wei"等)或完整音节，优先查询单字
            val isSingleSyllable = !input.contains(" ") && 
                (PinyinSplitter.isValidSyllable(input) || input.length <= 3)
                
            if (isSingleSyllable) {
                Timber.d("检测到单个拼音音节: '$input'，优先查询对应单字")
                
                // 存储查询结果
                val results = mutableListOf<Entry>()
                
                // 1. 首先查询chars词典中的单字 - 精确匹配
                val queryCondition = "pinyin == '$input' AND type == 'chars'"
                Timber.d("执行Realm查询: $queryCondition")
                val exactCharsEntries = realm.query<Entry>("pinyin == $0 AND type == 'chars'", input)
                    .find()
                    .filter { it.type !in excludeTypes }
                    .sortedByDescending { it.frequency }
                    .toList()
                
                Timber.d("精确匹配拼音'$input'的单字: ${exactCharsEntries.size}个")
                if (exactCharsEntries.isNotEmpty()) {
                    val previewChars = exactCharsEntries.take(3).joinToString { "${it.word}(${it.frequency})" }
                    Timber.d("拼音'$input'的部分单字: $previewChars")
                } else {
                    Timber.d("数据库中没有拼音等于'$input'的单字")
                    
                    // 如果数据库中没有找到对应的拼音单字，使用硬编码的常用拼音表
                    val hardcodedChars = getHardcodedCharsForPinyin(input)
                    if (hardcodedChars.isNotEmpty()) {
                        Timber.d("使用硬编码常用拼音-汉字映射表提供备选结果")
                        return hardcodedChars
                    }
                }
                results.addAll(exactCharsEntries)
                
                // 2. 如果精确匹配结果不足，查询前缀匹配的单字
                if (results.size < limit / 2) {
                    val prefixCondition = "pinyin BEGINSWITH '$input' AND type == 'chars'"
                    Timber.d("执行前缀匹配Realm查询: $prefixCondition")
                    val prefixCharsEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'chars'", input)
                        .find()
                        .filter { it.type !in excludeTypes && it !in results && it.word.length == 1 }
                        .sortedByDescending { it.frequency }
                        .toList()
                    
                    Timber.d("前缀匹配拼音'$input'的单字: ${prefixCharsEntries.size}个")
                    if (prefixCharsEntries.isNotEmpty()) {
                        val previewChars = prefixCharsEntries.take(3).joinToString { "${it.word}(${it.frequency})" }
                        Timber.d("前缀匹配的部分单字: $previewChars")
                    }
                    results.addAll(prefixCharsEntries)
                }
                
                // 3. 查找其他词典类型的精确匹配
                if (results.size < limit / 2) {
                    // 查询所有词典类型
                    val otherTypeEntries = realm.query<Entry>("pinyin == $0 AND type != 'chars'", input)
                        .find()
                        .filter { it.type !in excludeTypes && it !in results }
                        .sortedByDescending { it.frequency }
                        .take(limit / 4)  // 限制这部分结果的数量
                        .toList()
                        
                    Timber.d("其他词典中拼音='$input'的词条: ${otherTypeEntries.size}个")
                    results.addAll(otherTypeEntries)
                }
                
                // 4. 添加一些词组，但不超过结果总数的一半
                val targetWordLimit = limit - min(results.size, limit / 2)
                if (targetWordLimit > 0) {
                    // 先找精确匹配的词组
                    val exactWordEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'base'", input)
                        .find()
                        .filter { it.type !in excludeTypes && it !in results && it.word.length > 1 }
                        .sortedByDescending { it.frequency }
                        .take(targetWordLimit)
                        .toList()
                        
                    Timber.d("以拼音'$input'开头的词组: ${exactWordEntries.size}个")
                    if (exactWordEntries.isNotEmpty()) {
                        val previewWords = exactWordEntries.take(3).joinToString { "${it.word}(${it.frequency})" }
                        Timber.d("以'$input'开头的部分词组: $previewWords")
                    }
                    results.addAll(exactWordEntries)
                }
                
                if (results.isNotEmpty()) {
                    // 优先返回单字，然后是词组
                    val sortedResults = results.sortedWith(
                        compareBy<Entry> { it.word.length > 1 } // 单字优先
                          .thenByDescending { it.frequency }    // 然后按词频排序
                    )
                    
                    val finalResults = sortedResults.map { WordFrequency(it.word, it.frequency) }
                    Timber.d("拼音'$input'的查询结果: 共${finalResults.size}个，前5个: ${
                        finalResults.take(5).joinToString { it.word }
                    }")
                    
                    return finalResults
                } else {
                    Timber.w("拼音'$input'查询无结果")
                }
            }
            
            // 非单音节输入，进行标准的拼音分词处理
            val normalizedPrefix = PinyinSplitter.split(input)
            Timber.d("规范化后的拼音: '$normalizedPrefix'")
            
            // 选择合适的策略
            val strategy = CandidateStrategyFactory.getStrategy(normalizedPrefix.length, input)
            
            Timber.d("使用策略: ${strategy.getStrategyName()} 查询拼音'$normalizedPrefix'的候选词")
            
            // 使用策略查询候选词
            val results = strategy.queryCandidates(realm, normalizedPrefix, limit, excludeTypes)
            if (results.isNotEmpty()) {
                Timber.d("拼音'$normalizedPrefix'匹配到${results.size}个词条，前5个: ${
                    results.take(5).joinToString { it.word }
                }")
            } else {
                Timber.d("拼音'$normalizedPrefix'没有匹配到任何词条")
            }
            return results
        } catch (e: Exception) {
            Timber.e(e, "搜索候选词失败: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * 获取标准化拼音，用于与数据库中的拼音匹配
     * 将无空格拼音转换为带空格的格式（如 beijing -> bei jing）
     */
    fun normalizePinyin(pinyin: String): String {
        // 首先标准化格式：转小写并确保适当的分词
        val normalized = pinyin.lowercase().trim()
        
        // 如果已经包含空格，直接返回
        if (normalized.contains(" ")) {
            return normalized
        }
        
        // 将无空格拼音转换为有空格格式
        val withSpaces = PinyinSplitter.split(normalized)
        
        Timber.d("拼音规范化：'$pinyin' -> '$withSpaces'")
        
        return withSpaces
    }
    
    // 声调相关功能已删除
    
    /**
     * 获取指定类型的词典样本条目
     * @param type 词典类型
     * @param count 样本数量
     * @return 样本条目列表
     */
    fun getSampleEntries(type: String, count: Int): List<Entry> {
        try {
            Timber.d("获取类型'$type'的词典样本，数量$count")
            
            // 查询指定类型的所有条目
            val entries = realm.query<Entry>("type == $0", type)
                .find()
                .take(count)
                .toList()
                
            Timber.d("获取到${entries.size}个样本条目")
            
            if (entries.isNotEmpty()) {
                // 打印部分样本信息进行调试
                val firstSample = entries.first()
                Timber.d("第一个样本: 词='${firstSample.word}', 拼音='${firstSample.pinyin}', 频率=${firstSample.frequency}")
            }
            
            return entries
        } catch (e: Exception) {
            Timber.e(e, "获取词典样本失败: ${e.message}")
            return emptyList()
        }
    }

    /**
     * 根据汉字查询词条
     * @param word 要查询的汉字词语
     * @param limit 最大返回数量
     * @return 匹配的词条列表
     */
    fun searchByWord(word: String, limit: Int): List<WordFrequency> {
        if (word.isBlank()) return emptyList()
        
        return try {
            Timber.d("通过汉字在Realm数据库中搜索: '$word'")
            
            // 构建查询条件：word以输入开头
            val entries = realm.query<Entry>("word BEGINSWITH $0", word)
                .find()
                .sortedByDescending { it.frequency } // 按词频降序排序
                .take(limit) // 限制结果数量
            
            // 记录查询结果
            Timber.d("通过汉字查询找到${entries.size}个匹配的词条")
            
            // 记录部分匹配词条的详情
            if (entries.isNotEmpty()) {
                val samples = entries.take(3)
                Timber.d("汉字查询样本词条: ${
                    samples.joinToString { 
                        "${it.word}[${it.pinyin}](类型:${it.type},频率:${it.frequency})" 
                    }
                }")
            }
            
            // 转换为WordFrequency对象
            entries.map { WordFrequency(it.word, it.frequency) }
        } catch (e: Exception) {
            Timber.e(e, "通过汉字搜索词条失败: ${e.message}")
            emptyList()
        }
    }

    /**
     * 将无空格拼音转换为带空格的拼音音节（如beijing -> bei jing）
     * @deprecated 使用 PinyinSplitter.split() 替代
     */
    @Deprecated("使用PinyinSplitter.split()替代", ReplaceWith("PinyinSplitter.split(pinyin)"))
    private fun splitPinyinIntoSyllables(pinyin: String): String {
        return PinyinSplitter.split(pinyin)
    }

    /**
     * 检查并更新数据库中所有词条的initialLetters字段
     * 对于没有initialLetters值的词条，根据拼音生成新的initialLetters
     * @return 更新后的词条数量
     */
    fun checkAndUpdateInitialLetters(): Int {
        val updateCount = AtomicInteger(0)
        val batchSize = 1000 // 每次处理的词条数量
        
        try {
            Timber.d("开始检查并更新initialLetters字段")
            
            // 查询所有没有initialLetters值的词条
            val entries = realm.query<Entry>("initialLetters == '' OR initialLetters == null")
                .find()
            
            val totalCount = entries.size
            Timber.d("发现${totalCount}个需要更新initialLetters的词条")
            
            if (totalCount == 0) {
                Timber.d("所有词条的initialLetters字段已全部正确设置")
                return 0
            }
            
            // 分批处理词条
            val batches = entries.chunked(batchSize)
            
            for ((batchIndex, batch) in batches.withIndex()) {
                Timber.d("正在处理第${batchIndex + 1}/${batches.size}批")
                
                realm.writeBlocking {
                    for (entry in batch) {
                        val initialLetters = com.shenji.aikeyboard.utils.PinyinInitialUtils.generateInitials(entry.pinyin)
                        findLatest(entry)?.initialLetters = initialLetters
                        updateCount.incrementAndGet()
                    }
                }
                
                Timber.d("完成第${batchIndex + 1}批更新，已更新${updateCount.get()}个词条")
            }
            
            Timber.d("所有词条的initialLetters字段已全部更新完成，共更新${updateCount.get()}个词条")
            
        } catch (e: Exception) {
            Timber.e(e, "更新initialLetters字段时出错: ${e.message}")
        }
        
        return updateCount.get()
    }

    /**
     * 为常用拼音提供硬编码的单字候选词
     * 这主要用于数据库中未能找到匹配时的备用方案
     */
    private fun getHardcodedCharsForPinyin(pinyin: String): List<WordFrequency> {
        Timber.d("使用硬编码映射表查找拼音'$pinyin'对应的汉字")
        
        return when (pinyin.lowercase()) {
            "wei" -> listOf(
                WordFrequency("为", 982),
                WordFrequency("位", 957),
                WordFrequency("未", 925),
                WordFrequency("维", 885),
                WordFrequency("围", 862),
                WordFrequency("委", 845),
                WordFrequency("卫", 832),
                WordFrequency("微", 815),
                WordFrequency("尾", 795)
            )
            "wo" -> listOf(
                WordFrequency("我", 995),
                WordFrequency("窝", 825),
                WordFrequency("握", 785),
                WordFrequency("卧", 765)
            )
            "ta" -> listOf(
                WordFrequency("他", 990),
                WordFrequency("她", 985),
                WordFrequency("它", 980),
                WordFrequency("塔", 850),
                WordFrequency("踏", 830)
            )
            "ni" -> listOf(
                WordFrequency("你", 995),
                WordFrequency("尼", 870),
                WordFrequency("拟", 845)
            )
            "ba" -> listOf(
                WordFrequency("把", 960),
                WordFrequency("吧", 950),
                WordFrequency("爸", 940),
                WordFrequency("巴", 920)
            )
            "shi" -> listOf(
                WordFrequency("是", 999),
                WordFrequency("时", 980),
                WordFrequency("事", 975),
                WordFrequency("使", 965),
                WordFrequency("师", 955),
                WordFrequency("十", 950)
            )
            "li" -> listOf(
                WordFrequency("里", 980),
                WordFrequency("力", 970),
                WordFrequency("利", 960),
                WordFrequency("理", 950),
                WordFrequency("立", 940)
            )
            "de" -> listOf(
                WordFrequency("的", 999),
                WordFrequency("得", 980),
                WordFrequency("德", 950)
            )
            else -> emptyList()
        }.also {
            if (it.isNotEmpty()) {
                Timber.d("硬编码映射表返回${it.size}个结果，前3个: ${
                    it.take(3).joinToString { char -> char.word }
                }")
            } else {
                Timber.d("硬编码映射表中没有拼音'$pinyin'的映射")
            }
        }
    }
}

/**
 * 词典模块数据类
 */
data class DictionaryModule(
    val type: String,         // 词典类型代码
    val chineseName: String,  // 中文名称
    val entryCount: Int,      // 词条数量
    val isInMemory: Boolean = false, // 是否已加载到内存
    val memoryUsage: Long = 0L,       // 内存占用大小(字节)
    val isGroupHeader: Boolean = false, // 是否为组标题
    val isPrecompiled: Boolean = false,  // 是否为预编译词典
    val isMemoryLoaded: Boolean = false  // 是否已完成预编译加载到内存
) 