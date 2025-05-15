package com.shenji.aikeyboard.data

import timber.log.Timber

/**
 * 拼音分词器类，用于将无空格拼音转换为有空格分隔的音节
 */
object PinyinSplitter {

    // 完整拼音音节表，按音节长度降序排序，同长度按字典序排序
    private val sortedSyllables: List<String> = listOf(
        // 6字母音节
        "chuang", "shuang", "zhuang",
        // 5字母音节
        "chang", "cheng", "chong", "chuai", "duang", "guang", "huang", "jiang", "jiong", "kiang", "kuang", "liang", "niang", "qiang", "qiong", "shang", "sheng", "shuai", "xiong", "xiang", "zhang", "zheng", "zhong", "zhuai",
        // 4字母音节
        "bang", "biao", "bian", "bing", "cang", "chan", "chai", "chai", "chen", "chao", "chui", "chun", "chua", "cong", "chou", "dang", "dian", "ding", "diao", "dong", "duan", "fang", "gang", "gong", "guan", "guai", "hang", "heng", "hong", "huan", "huai", "jian", "jiao", "jing", "juan", "kang", "keng", "kong", "kuan", "kuai", "lang", "lian", "liao", "ling", "long", "luan", "mang", "mian", "miao", "ming", "nang", "nian", "niao", "ning", "nong", "nuan", "pang", "pian", "piao", "ping", "quan", "qian", "qiao", "qing", "rong", "ruan", "sang", "shan", "shai", "shen", "shao", "shei", "shui", "shun", "shua", "song", "shou", "tang", "tian", "ting", "tiao", "tong", "tuan", "wang", "weng", "xian", "xiao", "xing", "xuan", "yang", "ying", "yong", "yuan", "yuen", "yong", "zang", "zhan", "zhai", "zhen", "zhao", "zhou", "zhui", "zhun", "zhua", "zong",  
        // 3字母音节 
        "ang", "bai", "ban", "bao", "bei", "ben", "bie", "bin", "cai", "can", "cao", "cei", "cen", "cha", "che", "chi", "chu", "cui", "cun", "cuo", "dai", "dan", "dao", "dei", "den", "dia", "die", "diu", "dou", "dui", "dun", "duo", "eng", "fan", "fei", "fen", "fou", "gai", "gan", "gao", "gei", "gen", "gou", "gui", "gun", "guo", "hai", "han", "hao", "hei", "hen", "hou", "hua", "hui", "hun", "huo", "ing", "jia", "jie", "jin", "jiu", "jun", "jue", "kai", "kan", "kao", "kei", "ken", "kou", "kui", "kun", "kuo", "lai", "lan", "lao", "lei", "lie", "lin", "liu", "lou", "lue", "lun", "luo", "mai", "man", "mao", "mei", "men", "mie", "min", "miu", "mou", "nai", "nan", "nao", "nei", "nen", "nie", "nin", "niu", "nou", "nue", "nuo", "nuo", "ong", "pai", "pan", "pao", "pei", "pen", "pie", "pin", "pou", "qia", "qie", "qin", "qiu", "qun", "que", "ran", "rao", "ren", "rou", "rui", "run", "ruo", "sai", "san", "sao", "sei", "sen", "sha", "she", "shi", "shu", "sui", "sun", "suo", "tai", "tan", "tao", "tei", "tie", "tou", "tui", "tun", "tuo", "wai", "wan", "wei", "wen", "xia", "xie", "xin", "xiu", "xue", "yai", "yan", "yao", "yin", "you", "yue", "yun", "zai", "zan", "zao", "zei", "zen", "zha", "zhe", "zhi", "zhu", "zui", "zun", "zuo",
        // 2字母音节
        "ai", "an", "ao", "ba", "bi", "bo", "bu", "ca", "ce", "ci", "cu", "da", "de", "di", "du", "ei", "en", "er", "fa", "fo", "fu", "ga", "ge", "gu", "ha", "he", "hu", "ia", "ie", "in", "iu", "ji", "ju", "ka", "ke", "ku", "la", "le", "li", "lo", "lu", "lv", "ma", "me", "mi", "mo", "mu", "na", "ne", "ni", "nu", "nv", "ou", "pa", "pi", "po", "pu", "qi", "qu", "re", "ri", "ru", "sa", "se", "sh", "si", "su", "ta", "te", "ti", "tu", "ui", "un", "uo", "wa", "wo", "wu", "xi", "xu", "ya", "ye", "yi", "yo", "yu", "za", "ze", "zh", "zi", "zu", 
        // 1字母音节
        "a", "e", "i", "o", "u", "v"
    )
    
    // 音节长度范围（用于优化匹配算法）
    private val maxSyllableLength = sortedSyllables.maxOfOrNull { it.length } ?: 6
    private val minSyllableLength = sortedSyllables.minOfOrNull { it.length } ?: 1
    
    // 按长度分组的拼音音节表，用于快速查询特定长度的音节
    private val syllablesByLength: Map<Int, Set<String>> = sortedSyllables
        .groupBy { it.length }
        .mapValues { it.value.toSet() }
    
    // 所有有效拼音音节集合(用于验证)
    private val validSyllables: Set<String> = sortedSyllables.toSet()
    
    /**
     * 将无空格拼音转换为带空格的拼音音节
     * 例如: "jiating" -> "jia ting"
     * 使用从右到左的最长匹配策略
     */
    fun split(pinyin: String): String {
        if (pinyin.isBlank()) return pinyin
        
        // 如果已经包含空格，直接返回
        if (pinyin.contains(" ")) return pinyin
        
        try {
            val input = pinyin.lowercase().trim()
            val result = mutableListOf<String>()
            var remaining = input
            
            // 贪婪匹配：从右到左，优先尝试最长的音节
            while (remaining.isNotEmpty()) {
                var matched = false
                
                // 从最长音节长度开始尝试匹配
                for (length in maxSyllableLength downTo minSyllableLength) {
                    // 如果剩余字符串不够长，跳过此长度
                    if (remaining.length < length) continue
                    
                    val prefix = remaining.substring(0, length)
                    if (syllablesByLength[length]?.contains(prefix) == true) {
                        // 匹配成功，添加到结果并更新剩余字符串
                        result.add(prefix)
                        remaining = remaining.substring(length)
                        matched = true
                        break
                    }
                }
                
                // 如果没有匹配到任何音节
                if (!matched) {
                    // 添加单个字符并继续
                    val char = remaining.first()
                    result.add(char.toString())
                    remaining = remaining.substring(1)
                    Timber.w("无法匹配拼音音节，单字符处理: $char")
                }
            }
            
            // 验证结果中的每个分词是否为有效拼音音节
            var allValid = true
            for (syllable in result) {
                if (!validSyllables.contains(syllable) && syllable.length > 1) {
                    Timber.w("分词结果包含无效拼音音节: $syllable")
                    allValid = false
                }
            }
            
            if (!allValid) {
                Timber.w("分词结果包含无效音节，可能需要改进分词算法")
            }
            
            val splitResult = result.joinToString(" ")
            Timber.d("拼音分词转换: '${input}' -> '${splitResult}'")
            return splitResult
            
        } catch (e: Exception) {
            Timber.e(e, "拼音分词失败: ${e.message}")
            return pinyin // 出错返回原始拼音
        }
    }
    
    /**
     * 检查一个字符串是否为有效的拼音音节
     */
    fun isValidSyllable(syllable: String): Boolean {
        return validSyllables.contains(syllable.lowercase())
    }
    
    /**
     * 获取所有有效拼音音节
     */
    fun getAllValidSyllables(): Set<String> {
        return validSyllables
    }
    
    /**
     * 获取当前音节表的大小
     */
    fun getSyllableCount(): Int {
        return validSyllables.size
    }
} 