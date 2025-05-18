package com.shenji.aikeyboard.data

/**
 * 拼音分词器 - 用于拼音音节分割
 */
class PinyinSplitter {

    /**
     * 完整汉语拼音音节表
     */
    private val PINYIN_SYLLABLES = setOf(
        // 零声母
        "a", "ai", "an", "ang", "ao",
        "o", "ou",
        "e", "en", "eng", "er",
        "i", "ia", "ie", "iao", "iu", "iong", "in", "ing",
        "u", "ua", "uo", "uai", "ui", "uan", "un", "uang", "ung",
        "ü", "üe", "üan", "ün",
        // v替代ü的写法
        "v", "ve", "van", "vn",
        // 整体认读
        "zhi", "chi", "shi", "ri", "zi", "ci", "si", "yi", "wu", "yu", "ye", "yue", "yuan", "yin", "yun", "ying",
        // 声母 b
        "ba", "bo", "bai", "bei", "bao", "ban", "ben", "bang", "beng", "bi", "bie", "biao", "bian", "bin", "bing", "bu",
        // 声母 p
        "pa", "po", "pai", "pao", "pou", "pan", "pen", "pei", "pang", "peng", "pi", "pie", "piao", "pian", "pin", "ping", "pu",
        // 声母 m
        "ma", "mo", "me", "mai", "mao", "mou", "man", "men", "mei", "mang", "meng", "mi", "mie", "miao", "miu", "mian", "min", "ming", "mu",
        // 声母 f
        "fa", "fo", "fei", "fou", "fan", "fen", "fang", "feng", "fu",
        // 声母 d
        "da", "de", "dai", "dao", "dan", "dang", "deng", "di", "die", "diao", "diu", "dian", "ding", "dong", "dou", "du", "duan", "dun", "duo",
        // 声母 t
        "ta", "te", "tai", "tao", "tou", "tan", "tang", "teng", "ti", "tie", "tiao", "tian", "ting", "tong", "tu", "tuan", "tun", "tuo",
        // 声母 n
        "na", "nai", "ne", "nao", "nou", "nan", "nen", "neng", "ni", "nie", "niao", "niu", "nian", "nin", "niang", "ning", "nong", "nu", "nuan", "nun", "nuo", "nü", "nüe",
        // 声母 l
        "la", "le", "lo", "lai", "lei", "lao", "lou", "lan", "lang", "leng", "li", "lie", "liao", "liu", "lian", "lin", "liang", "ling", "long", "lu", "luan", "lun", "luo", "lü", "lüe",
        // n,l声母 + v(ü)
        "nv", "nve", "lv", "lve",
        // 声母 g
        "ga", "ge", "gai", "gei", "gao", "gou", "gan", "gen", "gang", "geng", "gong", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
        // 声母 k
        "ka", "ke", "kai", "kao", "kou", "kan", "ken", "kang", "keng", "kong", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
        // 声母 h
        "ha", "he", "hai", "hao", "hou", "han", "hen", "hang", "heng", "hong", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
        // 声母 j
        "ji", "jia", "jie", "jiao", "jiu", "jian", "jin", "jiang", "jing", "jiong", "ju", "juan", "jun", "jue",
        // 声母 q
        "qi", "qia", "qie", "qiao", "qiu", "qian", "qin", "qiang", "qing", "qiong", "qu", "quan", "qun", "que",
        // 声母 x
        "xi", "xia", "xie", "xiao", "xiu", "xian", "xin", "xiang", "xing", "xiong", "xu", "xuan", "xun", "xue",
        // 声母 zh
        "zhi", "zha", "zhe", "zhai", "zhao", "zhou", "zhan", "zhen", "zhang", "zheng", "zhong", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhun", "zhui", "zhuo",
        // 声母 ch
        "chi", "cha", "che", "chai", "chao", "chou", "chan", "chen", "chang", "cheng", "chong", "chu", "chua", "chuai", "chuan", "chuang", "chun", "chui", "chuo",
        // 声母 sh
        "shi", "sha", "she", "shai", "shao", "shou", "shan", "shen", "shang", "sheng", "shu", "shua", "shuai", "shuan", "shuang", "shun", "shui", "shuo",
        // 声母 r
        "ri", "re", "rao", "rou", "ran", "ren", "rang", "reng", "rong", "ru", "rui", "ruan", "run", "ruo",
        // 声母 z
        "zi", "za", "ze", "zai", "zao", "zou", "zan", "zen", "zang", "zeng", "zong", "zu", "zuan", "zun", "zui", "zuo",
        // 声母 c
        "ci", "ca", "ce", "cai", "cao", "cou", "can", "cen", "ceng", "cong", "cu", "cuan", "cun", "cui", "cuo",
        // 声母 s
        "si", "sa", "se", "sai", "sao", "sou", "san", "sen", "sang", "seng", "song", "su", "suan", "sun", "sui", "suo",
        // 额外补充音节
        "wa", "wo", "wai", "wei", "wan", "wen", "wang", "weng",
        "ya", "yo", "yao", "you", "yan", "yang", "yong"
    )

    // 有序的音节列表，按照音节长度从长到短排序
    private val ORDERED_SYLLABLES: List<String> by lazy {
        PINYIN_SYLLABLES.sortedByDescending { it.length }
    }

    /**
     * 返回完整拼音音节表
     */
    fun getPinyinSyllables(): Set<String> {
        return PINYIN_SYLLABLES
    }

    /**
     * 获取输入的多种可能拆分方式
     * 返回按优先级排序的拆分结果列表
     */
    fun getMultipleSplits(input: String): List<List<String>> {
        val cleanInput = input.trim().lowercase().replace(" ", "")
        
        if (cleanInput.isEmpty()) {
            return emptyList()
        }
        
        val results = mutableListOf<List<String>>()
        
        // 检查1: 如果输入本身是一个有效音节，这是最高优先级
        if (PINYIN_SYLLABLES.contains(cleanInput)) {
            results.add(listOf(cleanInput))
        }
        
        // 检查2: 尝试动态规划拆分
        val dpResult = split(cleanInput)
        if (dpResult.isNotEmpty() && (dpResult.size > 1 || dpResult[0] != cleanInput)) {
            results.add(dpResult)
        }
        
        // 检查3: 尝试贪心拆分
        val greedyResult = greedySplit(cleanInput)
        if (greedyResult.isNotEmpty() && 
            !results.contains(greedyResult) && 
            (greedyResult.size > 1 || greedyResult[0] != cleanInput)) {
            results.add(greedyResult)
        }
        
        // 检查4: 尝试部分匹配拆分
        val partialResult = findPartialMatch(cleanInput)
        if (partialResult.isNotEmpty() && 
            !results.contains(partialResult) && 
            (partialResult.size > 1 || partialResult[0] != cleanInput)) {
            results.add(partialResult)
        }
        
        // 检查5: 尝试右到左拆分
        val rightToLeftResult = splitPinyinRightToLeft(cleanInput)
        if (rightToLeftResult.isNotEmpty() && 
            !results.contains(rightToLeftResult) && 
            (rightToLeftResult.size > 1 || rightToLeftResult[0] != cleanInput)) {
            results.add(rightToLeftResult)
        }
        
        return results
    }

    /**
     * 智能拆分输入字符串，尝试查找最佳音节分割点
     * 对于无法完整分词的情况，保留有效音节，将剩余部分视为新音节的开始
     */
    fun splitPinyin(input: String): List<String> {
        // 清理输入：移除空格，全部转小写
        val cleanInput = input.trim().lowercase().replace(" ", "")
        
        if (cleanInput.isEmpty()) {
            return emptyList()
        }
        
        // 如果输入本身就是一个有效音节，直接返回
        if (PINYIN_SYLLABLES.contains(cleanInput)) {
            return listOf(cleanInput)
        }
        
        // 尝试使用动态规划算法进行音节拆分
        val result = split(cleanInput)
        if (result.isNotEmpty()) {
            return result
        }
        
        // 贪心拆分：从左到右查找最长有效音节
        val greedyResult = greedySplit(cleanInput)
        if (greedyResult.isNotEmpty()) {
            return greedyResult
        }
        
        // 如果仍未成功拆分，尝试部分匹配：匹配最长有效前缀，剩余部分作为单独部分
        val partialResult = findPartialMatch(cleanInput)
        if (partialResult.isNotEmpty()) {
            return partialResult
        }
        
        // 所有方法都失败，返回空列表
        return emptyList()
    }
    
    /**
     * 寻找部分匹配：从输入中找出最长的有效音节前缀，剩余部分单独保留
     * 这样可以处理类似"nih"这样的输入，将其拆分为"ni" + "h"
     */
    private fun findPartialMatch(input: String): List<String> {
        // 查找最长的有效音节前缀
        for (i in input.length downTo 1) {
            val prefix = input.substring(0, i)
            if (PINYIN_SYLLABLES.contains(prefix)) {
                // 找到有效前缀
                val result = mutableListOf(prefix)
                // 将剩余部分作为一个单独部分（可能是下一个音节的开始）
                if (i < input.length) {
                    result.add(input.substring(i))
                }
                return result
            }
        }
        return emptyList()
    }
    
    /**
     * 备用方法：从右到左的匹配算法（保留以供参考）
     */
    fun splitPinyinRightToLeft(input: String): List<String> {
        val result = mutableListOf<String>()
        var pos = input.length
        
        while (pos > 0) {
            var matched = false
            // 从最长的可能音节开始尝试匹配（最长6个字符）
            for (length in 6 downTo 1) {
                if (pos - length < 0) continue
                
                val substring = input.substring(pos - length, pos)
                if (substring in PINYIN_SYLLABLES) {
                    result.add(substring)
                    pos -= length
                    matched = true
                    break
                }
            }
            
            if (!matched) {
                // 无法匹配，返回空列表表示分割失败
                return emptyList()
            }
        }
        
        return result.reversed()
    }
    
    /**
     * 尝试多种拆分方式，当主方法失败时尝试备用方法
     */
    fun trySplitPinyin(input: String): List<String> {
        // 首先尝试从左到右优先匹配最长音节
        val primaryResult = splitPinyin(input)
        if (primaryResult.isNotEmpty()) {
            return primaryResult
        }
        
        // 如果主方法失败，尝试从右到左匹配
        val backupResult = splitPinyinRightToLeft(input)
        if (backupResult.isNotEmpty()) {
            return backupResult
        }
        
        // 所有方法都失败
        return emptyList()
    }

    /**
     * 动态规划分词算法
     * 尝试找到最优的音节分割方案
     */
    fun split(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        
        // dp[i]表示前i个字符能否被分词
        val dp = BooleanArray(input.length + 1)
        // prev[i]表示前i个字符的最后一个音节的起始位置
        val prev = IntArray(input.length + 1) { -1 }
        
        // 空字符串可以被分词
        dp[0] = true
        
        for (i in 1..input.length) {
            for (j in 0 until i) {
                val syllable = input.substring(j, i)
                if (dp[j] && PINYIN_SYLLABLES.contains(syllable)) {
                    dp[i] = true
                    prev[i] = j
                    break
                }
            }
        }
        
        // 如果整个字符串不能被分词，返回空列表
        if (!dp[input.length]) return emptyList()
        
        // 回溯构建分词结果
        val result = mutableListOf<String>()
        var pos = input.length
        while (pos > 0) {
            val start = prev[pos]
            result.add(0, input.substring(start, pos))
            pos = start
        }
        
        return result
    }
    
    /**
     * 贪心分词方法
     * 从左到右查找最长有效音节
     */
    fun greedySplit(input: String): List<String> {
        val result = mutableListOf<String>()
        var remainingInput = input
        
        while (remainingInput.isNotEmpty()) {
            var matched = false
            
            // 从最长音节开始尝试匹配
            for (syllable in ORDERED_SYLLABLES) {
                if (remainingInput.startsWith(syllable)) {
                    result.add(syllable)
                    remainingInput = remainingInput.substring(syllable.length)
                    matched = true
                    break
                }
            }
            
            // 如果没有匹配到任何音节，则分词失败
            if (!matched) {
                return emptyList()
            }
        }
        
        return result
    }
} 