package com.shenji.aikeyboard.data

import android.content.Context
import com.shenji.aikeyboard.ShenjiApplication
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
     * 获取词典文件大小
     */
    fun getDictionaryFileSize(): Long {
        val context = com.shenji.aikeyboard.ShenjiApplication.appContext
        val dictFile = File(context.filesDir, "dictionaries/shenji_dict.realm")
        return if (dictFile.exists()) dictFile.length() else 0
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
            
            // 计算预编译词典占用的内存
            val precompiledMemoryUsage = if (isPrecompiledDictLoaded) {
                Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            } else {
                0L
            }
            
            // 获取预编译词典加载的词条数
            val charsCount = dictManager.typeLoadedCountMap["chars"] ?: 0
            val baseCount = dictManager.typeLoadedCountMap["base"] ?: 0
            
            // 获取所有词典类型
            val types = getAllDictionaryTypes()
            Timber.d("获取到词典类型列表: ${types.joinToString()}")
            
            // 添加各个词典类型，无论是否预编译
            for (type in types) {
                val count = getEntryCountByType(type)
                val chineseName = getChineseNameForType(type)
                
                // 判断是否为高频词典类型
                val isHighFrequencyType = type in DictionaryManager.HIGH_FREQUENCY_DICT_TYPES
                
                // 对于高频词典类型
                if (isHighFrequencyType) {
                    // 如果预编译已加载完成，使用内存中的词条数，否则使用数据库中的词条数
                    val entryCount = if (isPrecompiledDictLoaded) {
                        when (type) {
                            "chars" -> charsCount
                            "base" -> baseCount
                            else -> count
                        }
                    } else {
                        count
                    }
                    
                    // 始终添加高频词典，但根据加载状态设置不同的属性
                    modules.add(
                        DictionaryModule(
                            type = type,
                            chineseName = if (isPrecompiledDictLoaded) "$chineseName (已加载到内存)" else chineseName,
                            entryCount = entryCount,
                            isInMemory = isPrecompiledDictLoaded,
                            memoryUsage = 0, // 不单独计算内存
                            isPrecompiled = true,
                            isMemoryLoaded = isPrecompiledDictLoaded
                        )
                    )
                } else {
                    // 对于非高频词典类型，始终添加
                    modules.add(
                        DictionaryModule(
                            type = type,
                            chineseName = chineseName,
                            entryCount = count,
                            isInMemory = false,
                            memoryUsage = 0L,
                            isMemoryLoaded = false
                        )
                    )
                }
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
                val isHighFrequencyType = type in DictionaryManager.HIGH_FREQUENCY_DICT_TYPES
                
                modules.add(
                    DictionaryModule(
                        type = type,
                        chineseName = if (isHighFrequencyType) "$name (已加载到内存)" else name,
                        entryCount = count,
                        isInMemory = isHighFrequencyType,
                        memoryUsage = 0,
                        isPrecompiled = isHighFrequencyType,
                        isMemoryLoaded = isHighFrequencyType
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