package com.shenji.aikeyboard.data

import com.shenji.aikeyboard.ShenjiApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.Collections
import kotlin.math.min
import kotlin.system.measureTimeMillis

/**
 * 词典管理器类，负责管理内存词库和持久化词库
 */
class DictionaryManager private constructor() {
    
    companion object {
        // 单例实例
        val instance: DictionaryManager by lazy { DictionaryManager() }
        
        // 初始化函数
        fun init() {
            instance.initialize()
        }
    }
    
    // 词典仓库实例
    private val repository = DictionaryRepository()
    
    // 日志记录
    private val loadingLogs = ConcurrentHashMap<Long, String>()
    private var logId: Long = 0
    
    // 是否已加载标志
    private var initialized = false
    
    /**
     * 初始化词典管理器
     */
    fun initialize() {
        // 仅设置初始化标志
        initialized = true
        
        // 检查和更新initialLetters字段
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val updateCount = repository.checkAndUpdateInitialLetters()
                if (updateCount > 0) {
                    Timber.i("已更新${updateCount}个initialLetters字段")
                } else {
                    Timber.d("所有initialLetters字段已正确设置")
                }
            } catch (e: Exception) {
                Timber.e(e, "初始化词典管理器时检查和更新initialLetters字段失败: ${e.message}")
            }
        }
        
        Timber.d("词典管理器初始化完成")
    }
    
    /**
     * 通过拼音搜索词
     * @param prefix 拼音前缀
     * @param limit 限制结果数
     * @param excludeTypes 排除的词典类型
     * @return 结果集
     */
    suspend fun searchWords(prefix: String, limit: Int, excludeTypes: List<String> = emptyList()): List<WordFrequency> {
        // 规范化拼音
        val normalizedPrefix = normalizePinyin(prefix)
        
        // 记录开始时间
        val startTime = System.currentTimeMillis()
        
        Timber.d("搜索'$normalizedPrefix'(原始输入:'$prefix')")
        
        // 检查输入中是否包含中文字符
        val containsChinese = prefix.any { it.code in 0x4E00..0x9FFF }
        if (containsChinese) {
            Timber.d("检测到中文输入'$prefix'，使用汉字直接查询")
        } else {
            // 输出参考拼音列表
            val possiblePinyinChars = when (normalizedPrefix) {
                "wei" -> "为(wéi),位(wèi),未(wèi),维(wéi),围(wéi),委(wěi),卫(wèi),微(wēi),尾(wěi)"
                "wo" -> "我(wǒ),窝(wō),握(wò),卧(wò)"
                "ta" -> "他(tā),她(tā),它(tā),塔(tǎ),踏(tà)"
                "ni" -> "你(nǐ),尼(ní),拟(nǐ)"
                "ba" -> "把(bǎ),吧(ba),爸(bà),巴(bā)"
                else -> ""
            }
            if (possiblePinyinChars.isNotEmpty()) {
                Timber.d("参考:拼音'$normalizedPrefix'对应的常用汉字: $possiblePinyinChars")
            }
        }
        
        // 从Realm词库查询 (简化版 - 只基本查询)
        val realmResults = repository.searchBasicEntries(normalizedPrefix, limit, excludeTypes)
        
        // 记录搜索时间
        val searchTime = System.currentTimeMillis() - startTime
        Timber.d("搜索'$normalizedPrefix'耗时: ${searchTime}ms, 返回${realmResults.size}个结果")
        
        // 输出一部分结果
        if (realmResults.isNotEmpty()) {
            val previewResults = realmResults.take(5).joinToString { "${it.word}(${it.frequency})" }
            Timber.d("搜索结果前5项: $previewResults")
        }
        
        return realmResults
    }
    
    /**
     * 添加日志
     */
    fun addLog(message: String) {
        val id = logId++
        val timestamp = System.currentTimeMillis()
        val formattedMessage = "${timestamp}: $message"
        loadingLogs[id] = formattedMessage
        Timber.d(message)
    }
    
    /**
     * 获取全部日志
     */
    fun getAllLogs(): List<String> {
        return loadingLogs.entries
            .sortedBy { it.key }
            .map { it.value }
    }
    
    /**
     * 清除日志
     */
    fun clearLogs() {
        loadingLogs.clear()
        logId = 0
    }
    
    /**
     * 规范化拼音，确保拼音音节之间有空格分隔
     * @param pinyin 原始拼音输入
     * @return 规范化后的拼音
     */
    private fun normalizePinyin(pinyin: String): String {
        val normalized = com.shenji.aikeyboard.utils.PinyinUtils.normalize(pinyin)
        Timber.d("拼音转换: '$pinyin' -> '$normalized'")
        Timber.d("规范化后的拼音: '$normalized'")
        
        return normalized
    }
    
    /**
     * 检查词典管理器是否已完成初始化
     */
    fun isLoaded(): Boolean = initialized
} 