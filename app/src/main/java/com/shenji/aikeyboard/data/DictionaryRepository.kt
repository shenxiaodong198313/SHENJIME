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
     * 获取特定类型的词典的最后修改时间戳
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
     * 简化版的基本词条查询，直接使用拼音前缀匹配
     * @param pinyin 拼音前缀
     * @param limit 限制结果数量
     * @param excludeTypes 排除的词典类型列表
     * @return 结果集合
     */
    fun searchBasicEntries(pinyin: String, limit: Int, excludeTypes: List<String> = emptyList()): List<WordFrequency> {
        val results = mutableListOf<WordFrequency>()
        
        try {
            // 记录查询开始时间
            val startTime = System.currentTimeMillis()
            
            // 先查询完全匹配的词条
            val exactMatches = realm.query<Entry>("pinyin == $0", pinyin)
                .find()
                .filter { it.type !in excludeTypes }
                .map { WordFrequency(it.word, it.frequency) }
                .sortedByDescending { it.frequency }
                .take(limit / 2)
            
            results.addAll(exactMatches)
            
            // 如果还有空间，查询前缀匹配的词条
            if (results.size < limit) {
                val prefixMatches = realm.query<Entry>("pinyin BEGINSWITH $0", pinyin)
                    .find()
                    .filter { it.type !in excludeTypes && it.pinyin != pinyin } // 排除已添加的完全匹配项
                    .map { WordFrequency(it.word, it.frequency) }
                    .sortedByDescending { it.frequency }
                    .take(limit - results.size)
                
                results.addAll(prefixMatches)
            }
            
            // 查询耗时
            val searchTime = System.currentTimeMillis() - startTime
            Timber.d("基本搜索'$pinyin'耗时: ${searchTime}ms, 找到${results.size}个结果")
            
        } catch (e: Exception) {
            Timber.e(e, "基本词条搜索失败: ${e.message}")
        }
        
        return results
    }
    
    /**
     * 获取标准化拼音，用于与数据库中的拼音匹配
     * @param pinyin 原始拼音输入
     * @return 规范化后的拼音
     */
    fun normalizePinyin(pinyin: String): String {
        // 标准化格式：转小写并去除前后空格
        val normalized = pinyin.lowercase().trim()
        Timber.d("拼音规范化：'$pinyin' -> '$normalized'")
        return normalized
    }
    
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
     * 检查并更新词条的initialLetters字段
     * @return 更新的词条数量
     */
    fun checkAndUpdateInitialLetters(): Int {
        val updateCount = AtomicInteger(0)
        
        try {
            // 找出所有缺少initialLetters的词条
            val entriesToUpdate = realm.query<Entry>("initialLetters == null OR initialLetters == ''")
                .find()
            
            Timber.d("找到${entriesToUpdate.size}个缺少initialLetters字段的词条")
            
            if (entriesToUpdate.isNotEmpty()) {
                // 分批处理，每批100个词条
                val batchSize = 100
                val totalEntries = entriesToUpdate.size
                
                for (i in 0 until totalEntries step batchSize) {
                    val endIndex = min(i + batchSize, totalEntries)
                    val batch = entriesToUpdate.subList(i, endIndex)
                    
                    realm.writeBlocking {
                        batch.forEach { entry ->
                            val copyToUpdate = findLatest(entry)
                            if (copyToUpdate != null) {
                                // 简单地使用第一个字母作为initialLetters
                                val pinyinParts = entry.pinyin.split(" ")
                                val initials = pinyinParts.joinToString("") { it.take(1) }
                                
                                copyToUpdate.initialLetters = initials
                                updateCount.incrementAndGet()
                            }
                        }
                    }
                    
                    Timber.d("已更新${i + batch.size}/${totalEntries}个词条")
                }
            }
            
            Timber.d("初始化词典的initialLetters字段，共更新了${updateCount.get()}个词条")
            
        } catch (e: Exception) {
            Timber.e(e, "更新initialLetters字段失败: ${e.message}")
        }
        
        return updateCount.get()
    }
    
    // 预定义的汉字查询辅助方法
    private fun getHardcodedCharsForPinyin(pinyin: String): List<WordFrequency> {
        val frequencyMap = mapOf(
            "wo" to listOf(WordFrequency("我", 9000), WordFrequency("窝", 2000), WordFrequency("卧", 1500)),
            "ni" to listOf(WordFrequency("你", 9500), WordFrequency("尼", 2500), WordFrequency("泥", 1800)),
            "ta" to listOf(WordFrequency("他", 9000), WordFrequency("她", 8500), WordFrequency("它", 8000), WordFrequency("塔", 3000)),
            "de" to listOf(WordFrequency("的", 9999), WordFrequency("得", 8000), WordFrequency("地", 7500)),
            "bu" to listOf(WordFrequency("不", 9800), WordFrequency("步", 3500), WordFrequency("布", 3000))
        )
        
        return frequencyMap[pinyin] ?: emptyList()
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