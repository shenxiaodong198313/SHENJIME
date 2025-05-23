package com.shenji.aikeyboard.utils

import android.util.LruCache
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/**
 * 优化的拼音音节与分词管理器
 * 基于最长匹配优先原则实现拼音字符串的音节切分与分词
 * 解决了如 "nihao" 被错误分割为 "n + i + hao" 而不是 "ni + hao" 的问题
 * 
 * 新增功能：
 * - 智能LRU缓存机制
 * - 性能监控和统计
 * - 内存使用优化
 * - 详细的调试信息
 */
object PinyinSegmenterOptimized {
    
    // ==================== 缓存和性能监控 ====================
    
    /**
     * 拆分结果缓存
     * 使用LRU策略，缓存最近使用的拆分结果
     */
    private val splitCache = LruCache<String, List<String>>(300) // 缓存300个最近结果
    
    /**
     * 性能统计数据
     */
    data class PerformanceStats(
        val totalRequests: Long = 0,           // 总请求数
        val cacheHits: Long = 0,               // 缓存命中数
        val cacheMisses: Long = 0,             // 缓存未命中数
        val totalSplitTime: Long = 0,          // 总拆分耗时(纳秒)
        val averageSplitTime: Double = 0.0,    // 平均拆分耗时(毫秒)
        val cacheHitRate: Double = 0.0,        // 缓存命中率
        val maxInputLength: Int = 0,           // 处理过的最大输入长度
        val cacheSize: Int = 0                 // 当前缓存大小
    ) {
        override fun toString(): String {
            return """
                |拼音拆分性能统计:
                |  总请求数: $totalRequests
                |  缓存命中: $cacheHits (${String.format("%.1f", cacheHitRate)}%)
                |  缓存未命中: $cacheMisses
                |  平均耗时: ${String.format("%.2f", averageSplitTime)}ms
                |  最大输入长度: $maxInputLength
                |  当前缓存大小: $cacheSize/300
            """.trimMargin()
        }
    }
    
    // 性能计数器
    private val totalRequests = AtomicLong(0)
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val totalSplitTime = AtomicLong(0)
    private val maxInputLength = AtomicInteger(0)
    
    /**
     * 获取性能统计信息
     */
    fun getPerformanceStats(): PerformanceStats {
        val requests = totalRequests.get()
        val hits = cacheHits.get()
        val misses = cacheMisses.get()
        val totalTime = totalSplitTime.get()
        
        return PerformanceStats(
            totalRequests = requests,
            cacheHits = hits,
            cacheMisses = misses,
            totalSplitTime = totalTime,
            averageSplitTime = if (misses > 0) totalTime / 1_000_000.0 / misses else 0.0,
            cacheHitRate = if (requests > 0) hits * 100.0 / requests else 0.0,
            maxInputLength = maxInputLength.get(),
            cacheSize = splitCache.size()
        )
    }
    
    /**
     * 重置性能统计
     */
    fun resetPerformanceStats() {
        totalRequests.set(0)
        cacheHits.set(0)
        cacheMisses.set(0)
        totalSplitTime.set(0)
        maxInputLength.set(0)
        splitCache.evictAll()
        Timber.d("拼音拆分性能统计已重置")
    }
    
    /**
     * 清空缓存
     */
    fun clearCache() {
        splitCache.evictAll()
        Timber.d("拼音拆分缓存已清空")
    }
    
    // ==================== 原有的音节数据 ====================
    
    // 声母表（23个）
    private val smb = arrayOf(
        "b", "p", "m", "f",
        "d", "t", "n", "l",
        "g", "h", "k",
        "j", "q", "x",
        "zh", "ch", "sh", "r",
        "z", "c", "s",
        "y", "w"
    )
    
    // 韵母表（39个，按长度从长到短排序，确保最长匹配优先）
    private val ymbmax = arrayOf(
        // 按长度从大到小排序的韵母
        "iang", "iong", "uang", "uai", "uan", "iao", "ian", "ang", "eng", "ing", 
        "ong", "uai", "uan", "iao", "ian", "ua", "uo", "ue", "ui", "un", 
        "ai", "ei", "ao", "ou", "an", "en", "er", "in", "ia", "iu", "ie",
        "a", "o", "e", "i", "u", "v"
    )
    
    // 独立成字韵母表（12个，也按长度排序）
    private val ymbmin = arrayOf(
        "ang", "eng", "ai", "ao", "ou", "ei", "an", "en", "er", "a", "o", "e"
    )
    
    // 整体认读音节和所有合法音节集合（包含所有主流输入法支持的音节）
    private val syllableSet: Set<String> by lazy {
        val set = mutableSetOf<String>()
        // 1. 整体认读音节
        set.addAll(listOf(
            "zhi", "chi", "shi", "ri", "zi", "ci", "si",
            "yi", "ya", "yo", "ye", "yao", "you", "yan", "yin", "yang", "ying", "yuan", "yun", "yue",
            "wu", "wa", "wo", "wai", "wei", "wan", "wen", "weng",
            "yu", "yue", "yuan", "yun"
        ))
        // 2. 声母+韵母组合
        for (sm in smb) {
            for (ym in ymbmax) {
                set.add(sm + ym)
            }
        }
        // 3. 独立成字韵母
        for (ym in ymbmin) {
            set.add(ym)
        }
        // 4. 补充常见简拼音节
        set.addAll(listOf("a", "o", "e", "ai", "ei", "ao", "ou", "an", "en", "ang", "eng", "er", "i", "u", "v"))
        // 5. 补充用户提供的完整音节表
        set.addAll(listOf(
            "b", "ba", "bo", "bai", "bei", "bao", "ban", "ben", "bang", "beng", "bi", "bie", "biao", "bian", "bin", "bing", "bu",
            "p", "pa", "po", "pai", "pao", "pou", "pan", "pen", "pei", "pang", "peng", "pi", "pie", "piao", "pian", "pin", "ping", "pu",
            "m", "ma", "mo", "me", "mai", "mao", "mou", "man", "men", "mei", "mang", "meng", "mi", "mie", "miao", "miu", "mian", "min", "ming", "mu",
            "f", "fa", "fo", "fei", "fou", "fan", "fen", "fang", "feng", "fu",
            "d", "da", "de", "dai", "dei", "dao", "dou", "dan", "dang", "den", "deng", "di", "die", "diao", "diu", "dian", "ding", "dong", "du", "duan", "dun", "dui", "duo",
            "t", "ta", "te", "tai", "tao", "tou", "tan", "tang", "teng", "ti", "tie", "tiao", "tian", "ting", "tong", "tu", "tuan", "tun", "tui", "tuo",
            "n", "na", "nai", "nei", "nao", "ne", "nen", "nan", "nang", "neng", "ni", "nie", "niao", "niu", "nian", "nin", "niang", "ning", "nong", "nou", "nu", "nuan", "nun", "nuo", "nü", "nüe",
            "l", "la", "le", "lo", "lai", "lei", "lao", "lou", "lan", "lang", "leng", "li", "lia", "lie", "liao", "liu", "lian", "lin", "liang", "ling", "long", "lu", "luo", "lou", "luan", "lun", "lü", "lüe",
            "g", "ga", "ge", "gai", "gei", "gao", "gou", "gan", "gen", "gang", "geng", "gong", "gu", "gua", "guai", "guan", "guang", "gui", "guo",
            "k", "ka", "ke", "kai", "kao", "kou", "kan", "ken", "kang", "keng", "kong", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
            "h", "ha", "he", "hai", "han", "hei", "hao", "hou", "hen", "hang", "heng", "hong", "hu", "hua", "huai", "huan", "hui", "huo", "hun", "huang",
            "j", "ji", "jia", "jie", "jiao", "jiu", "jian", "jin", "jiang", "jing", "jiong", "ju", "juan", "jun", "jue",
            "q", "qi", "qia", "qie", "qiao", "qiu", "qian", "qin", "qiang", "qing", "qiong", "qu", "quan", "qun", "que",
            "x", "xi", "xia", "xie", "xiao", "xiu", "xian", "xin", "xiang", "xing", "xiong", "xu", "xuan", "xun", "xue",
            "zh", "zha", "zhe", "zhi", "zhai", "zhao", "zhou", "zhan", "zhen", "zhang", "zheng", "zhong", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhun", "zhui", "zhuo",
            "ch", "cha", "che", "chi", "chai", "chao", "chou", "chan", "chen", "chang", "cheng", "chong", "chu", "chua", "chuai", "chuan", "chuang", "chun", "chui", "chuo",
            "sh", "sha", "she", "shi", "shai", "shao", "shou", "shan", "shen", "shang", "sheng", "shu", "shua", "shuai", "shuan", "shuang", "shun", "shui", "shuo",
            "r", "re", "ri", "rao", "rou", "ran", "ren", "rang", "reng", "rong", "ru", "rui", "ruan", "run", "ruo",
            "z", "za", "ze", "zi", "zai", "zao", "zan", "zou", "zang", "zei", "zen", "zeng", "zong", "zu", "zuan", "zun", "zui", "zuo",
            "c", "ca", "ce", "ci", "cai", "cao", "cou", "can", "cen", "cang", "ceng", "cong", "cu", "cuan", "cun", "cui", "cuo",
            "s", "sa", "se", "si", "sai", "sao", "sou", "san", "sen", "sang", "seng", "song", "su", "suan", "sun", "sui", "suo",
            "y", "ya", "yao", "you", "yan", "yang", "yu", "ye", "yue", "yuan", "yi", "yin", "yun", "ying", "yo", "yong",
            "w", "wa", "wo", "wai", "wei", "wan", "wen", "wang", "weng", "wu"
        ))
        set
    }

    // ==================== 主要接口方法 ====================

    /**
     * 将汉语拼音连写字符串分割成音节List
     * 带缓存优化的主入口方法
     */
    fun cut(s: String): List<String> {
        // 输入预处理和验证
        if (s.isBlank()) {
            return emptyList()
        }
        
        val cleanInput = s.trim().lowercase()
        if (cleanInput.isEmpty()) {
            return emptyList()
        }
        
        // 更新统计信息
        totalRequests.incrementAndGet()
        
        // 更新最大输入长度记录
        if (cleanInput.length > maxInputLength.get()) {
            maxInputLength.set(cleanInput.length)
        }
        
        // 检查缓存
        splitCache.get(cleanInput)?.let { cachedResult ->
            cacheHits.incrementAndGet()
            Timber.v("拼音拆分缓存命中: '$cleanInput' -> ${cachedResult.joinToString("+")}")
            return cachedResult
        }
        
        // 缓存未命中，执行实际拆分
        cacheMisses.incrementAndGet()
        val startTime = System.nanoTime()
        
        try {
            val result = performSplit(cleanInput)
            val endTime = System.nanoTime()
            val splitTime = endTime - startTime
            totalSplitTime.addAndGet(splitTime)
            
            // 缓存结果
            splitCache.put(cleanInput, result)
            
            // 记录调试信息
            val splitTimeMs = splitTime / 1_000_000.0
            Timber.v("拼音拆分完成: '$cleanInput' -> ${result.joinToString("+")} (耗时: ${String.format("%.2f", splitTimeMs)}ms)")
            
            // 定期输出性能统计（每100次请求）
            if (totalRequests.get() % 100 == 0L) {
                Timber.d(getPerformanceStats().toString())
            }
            
            return result
            
        } catch (e: Exception) {
            Timber.e(e, "拼音拆分异常: '$cleanInput'")
            // 异常情况下返回原输入
            return listOf(cleanInput)
        }
    }
    
    /**
     * 执行实际的拆分逻辑
     * 从原有的cut方法中提取出来
     */
    private fun performSplit(s: String): List<String> {
        val list = cutWithWholeSyllablePriority(s, 0)
        if (list == null || list.isEmpty()) {
            return listOf(s)
        }
        if (list.last().isEmpty()) {
            return list.dropLast(1)
        }
        return list
    }

    // ==================== 原有的拆分算法 ====================

    // 优先整体音节分割
    private fun cutWithWholeSyllablePriority(s: String, index: Int): List<String>? {
        if (index >= s.length) return listOf("")
        
        // 1. 优先尝试最长合法音节
        for (len in (s.length - index) downTo 1) {
            val part = s.substring(index, index + len)
            if (isValidSyllable(part)) {
                val left = cutWithWholeSyllablePriority(s, index + len)
                if (!left.isNullOrEmpty()) {
                    val ans = mutableListOf<String>()
                    ans.add(part)
                    ans.addAll(left)
                    return ans
                }
            }
        }
        
        // 2. 如果没有整体音节，再走优化后的声母+韵母递归
        val wordLength = findWord(s, index)
        if (wordLength <= 0) return null
        
        val left = cutWithWholeSyllablePriority(s, index + wordLength)
        if (!left.isNullOrEmpty()) {
            val ans = mutableListOf<String>()
            ans.add(s.substring(index, index + wordLength))
            ans.addAll(left)
            return ans
        }
        
        return null
    }

    // 找声母
    private fun findSm(s: String, index: Int): Int {
        val n = s.length
        for (asm in smb) {
            if (s.startsWith(asm, index)) {
                val nextidx = index + asm.length
                if (nextidx < n) {
                    val next = s.substring(nextidx, nextidx + 1)
                    var smAgain = false
                    for (asm2 in smb) {
                        if (next == asm2) {
                            smAgain = true
                            break
                        }
                    }
                    if (!smAgain) {
                        return asm.length
                    }
                }
            }
        }
        return 0
    }

    // 找独立成字的韵母 - 返回最长匹配长度
    private fun findDlym(s: String, index: Int): Int {
        var maxLength = 0
        for (ym in ymbmin) {
            if (s.startsWith(ym, index) && ym.length > maxLength) {
                maxLength = ym.length
            }
        }
        return maxLength
    }

    // 找韵母 - 返回最长匹配长度
    private fun findYm(s: String, index: Int): Int {
        var maxLength = 0
        for (ym in ymbmax) {
            if (s.startsWith(ym, index) && ym.length > maxLength) {
                maxLength = ym.length
            }
        }
        return maxLength
    }

    // 找单字 - 返回最长匹配组合长度
    private fun findWord(s: String, index: Int): Int {
        if (index >= s.length) return 0
        
        val smLen = findSm(s, index)
        
        // 如果有声母，尝试声母+韵母组合
        if (smLen > 0) {
            val ymLen = findYm(s, index + smLen)
            if (ymLen > 0) {
                return smLen + ymLen // 声母 + 最长韵母
            }
        } else {
            // 如果没有声母，尝试独立韵母
            val ymLen = findDlym(s, index)
            if (ymLen > 0) {
                return ymLen // 独立韵母
            }
        }
        
        return 0
    }

    /**
     * 判断字符串是否为合法音节（整体音节表优先）
     */
    fun isValidSyllable(s: String): Boolean {
        return syllableSet.contains(s)
    }

    /**
     * 获取所有有效的拼音音节
     * @return 所有合法拼音音节的集合
     */
    fun getValidSyllables(): Set<String> {
        return syllableSet
    }
} 