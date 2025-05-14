package com.shenji.aikeyboard.data

import android.content.Context
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.WordFrequency
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.find
import timber.log.Timber
import java.io.File
import kotlin.math.min

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
     * 根据拼音前缀从数据库搜索词条，支持带空格和不带空格的拼音匹配
     * @param prefix 拼音前缀
     * @param limit 最大返回数量
     * @param excludeTypes 要排除的词典类型（如高频词典，因为它们已经在内存中查询过）
     * @return 匹配的词条列表
     */
    fun searchEntries(prefix: String, limit: Int, excludeTypes: List<String>): List<WordFrequency> {
        if (prefix.isBlank()) return emptyList()
        
        return try {
            Timber.d("从Realm数据库中搜索拼音前缀: '$prefix'，字符数：${prefix.length}")
            
            // 带空格的规范化拼音格式
            val normalizedPrefix = splitPinyinIntoSyllables(prefix.lowercase().trim())
            
            // 无空格格式，用于匹配没有空格的记录
            val noSpacePrefix = prefix.replace(" ", "").lowercase().trim()
            
            Timber.d("规范化后的拼音格式: 带空格='$normalizedPrefix', 无空格='$noSpacePrefix'")
            
            // 存储所有匹配结果
            val allEntries = mutableSetOf<Entry>()
            
            // 分阶段查询策略，根据输入长度决定查询方式
            when {
                // 1-2字母：优先查询单字词库，精准匹配
                normalizedPrefix.length <= 2 -> {
                    // 精确匹配
                    val exactMatches = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'chars'", normalizedPrefix)
                        .limit(limit * 2)
                        .find()
                        .filter { it.type !in excludeTypes }
                    
                    allEntries.addAll(exactMatches)
                }
                
                // 3-4字母：基础词库，完全+前缀匹配
                normalizedPrefix.length <= 4 -> {
                    // 1. 精确匹配
                    val exactMatches = realm.query<Entry>("pinyin BEGINSWITH $0", normalizedPrefix)
                        .limit(limit * 2)
                        .find()
                        .filter { it.type !in excludeTypes }
                    
                    allEntries.addAll(exactMatches)
                    
                    // 2. 无空格匹配
                    if (allEntries.size < limit) {
                        val noSpaceMatches = realm.query<Entry>("pinyin BEGINSWITH $0", noSpacePrefix)
                            .limit(limit)
                            .find()
                            .filter { it.type !in excludeTypes && it !in allEntries }
                        
                        allEntries.addAll(noSpaceMatches)
                    }
                }
                
                // ≥5字母：联想词库，包含匹配
                else -> {
                    // 1. 前缀匹配
                    val exactMatches = realm.query<Entry>("pinyin BEGINSWITH $0", normalizedPrefix)
                        .limit(limit)
                        .find()
                        .filter { it.type !in excludeTypes }
                    
                    allEntries.addAll(exactMatches)
                    
                    // 2. 包含匹配（联想搜索）
                    if (allEntries.size < limit) {
                        val containsMatches = realm.query<Entry>("pinyin CONTAINS $0", normalizedPrefix)
                            .limit(limit)
                            .find()
                            .filter { it.type !in excludeTypes && it !in allEntries }
                        
                        allEntries.addAll(containsMatches)
                    }
                }
            }
            
            // 筛选结果 - 按词频降序排序并限制返回数量
            val matchedEntries = allEntries
                .sortedByDescending { it.frequency } // 按词频降序排序
                .take(limit) // 限制结果数量
            
            if (matchedEntries.isNotEmpty()) {
                // 记录匹配结果
                Timber.d("找到${matchedEntries.size}个匹配的词条")
                
                // 记录部分匹配词条的详情
                val samples = matchedEntries.take(3)
                Timber.d("样本词条: ${
                    samples.joinToString { 
                        "${it.word}[${it.pinyin}](类型:${it.type},频率:${it.frequency})" 
                    }
                }")
            } else {
                Timber.d("没有找到匹配前缀的词条，尝试查询使用: '$normalizedPrefix', '$noSpacePrefix'")
            }
            
            // 转换为WordFrequency对象
            matchedEntries.map { WordFrequency(it.word, it.frequency) }
        } catch (e: Exception) {
            Timber.e(e, "搜索词条失败: ${e.message}")
            emptyList()
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
        val withSpaces = splitPinyinIntoSyllables(normalized)
        
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
     */
    private fun splitPinyinIntoSyllables(pinyin: String): String {
        if (pinyin.isBlank()) return pinyin
        
        // 如果已经包含空格，直接返回
        if (pinyin.contains(" ")) return pinyin
        
        try {
            // 中文拼音音节列表（按长度排序，优先匹配较长的）
            val pinyinSyllables = listOf(
                // 常见四字母及以上音节
                "zhuang", "chuang", "shuang", "zhang", "chang", "shang", "zheng", "cheng", "sheng",
                "zhong", "chong", "jiang", "qiang", "xiang", "zhou", "chou", "shou", "zhen", "chen", "shen",
                "zhan", "chan", "shan", "bing", "ping", "ding", "ting", "ning", "ling", "jing", "qing", "xing", "ying",
                "zeng", "ceng", "seng", "duan", "tuan", "nuan", "luan", "guan", "kuan", "huan", "quan", "xuan", "yuan",
                // 常见三字母音节
                "zhi", "chi", "shi", "ang", "eng", "ing", "ong", "bai", "pai", "mai", "dai", "tai", "nai", "lai", "gai", "kai", "hai", "zai", "cai", "sai",
                "ban", "pan", "man", "fan", "dan", "tan", "nan", "lan", "gan", "kan", "han", "zan", "can", "san",
                "bao", "pao", "mao", "dao", "tao", "nao", "lao", "gao", "kao", "hao", "zao", "cao", "sao",
                "bie", "pie", "mie", "die", "tie", "nie", "lie", "jie", "qie", "xie", "yan", "jin", "qin", "xin",
                "bin", "pin", "min", "nin", "lin", "jin", "qin", "xin", "yin", "jiu", "qiu", "xiu",
                "bei", "pei", "mei", "fei", "dei", "tei", "nei", "lei", "gei", "kei", "hei", "zei", "cei", "sei",
                "ben", "pen", "men", "fen", "den", "nen", "gen", "ken", "hen", "zen", "cen", "sen",
                "zhu", "chu", "shu", "zhe", "che", "she", "zha", "cha", "sha", "zou", "cou", "sou", "zui", "cui", "sui", "zun", "cun", "sun",
                "zhuo", "chuo", "shuo", "zhen", "chen", "shen",
                // k开头音节补充
                "kuai", "kuan", "kuang",
                // 常见双字母音节
                "ba", "pa", "ma", "fa", "da", "ta", "na", "la", "ga", "ka", "ha", "za", "ca", "sa",
                "bo", "po", "mo", "fo", "lo", "wo", "yo", "zo", "co", "so",
                "bi", "pi", "mi", "di", "ti", "ni", "li", "ji", "qi", "xi", "yi",
                "bu", "pu", "mu", "fu", "du", "tu", "nu", "lu", "gu", "ku", "hu", "zu", "cu", "su", "wu", "yu",
                "ai", "ei", "ui", "ao", "ou", "iu", "ie", "ue", "ve", "er", "an", "en", "in", "un", "vn",
                "wu", "yu", "ju", "qu", "xu", "zi", "ci", "si", "ge", "he", "ne", "le", "me", "de", "te",
                "re", "ze", "ce", "se", "ye", "zh", "ch", "sh",
                // 单字母音节
                "a", "o", "e", "i", "u", "v"
            )
            
            // 贪婪匹配：从输入的开始位置尝试匹配最长的拼音音节
            var result = ""
            var position = 0
            
            while (position < pinyin.length) {
                var matched = false
                
                // 尝试匹配最长的音节
                for (syllable in pinyinSyllables) {
                    if (position + syllable.length <= pinyin.length &&
                        pinyin.substring(position, position + syllable.length) == syllable) {
                        // 匹配到音节，添加到结果中
                        result += if (result.isEmpty()) syllable else " $syllable"
                        position += syllable.length
                        matched = true
                        break
                    }
                }
                
                // 如果没有匹配到任何音节，只好单字符处理
                if (!matched) {
                    val char = pinyin[position]
                    result += if (result.isEmpty()) char.toString() else " $char"
                    position++
                    Timber.w("无法匹配拼音音节，单字符处理: $char 在位置 $position")
                }
            }
            
            Timber.d("拼音分词转换: '$pinyin' -> '$result'")
            return result
            
        } catch (e: Exception) {
            Timber.e(e, "拼音分词失败: ${e.message}")
            return pinyin // 出错返回原始拼音
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