package com.shenji.aikeyboard.utils

import timber.log.Timber

/**
 * 拼音音节管理器
 * 负责管理拼音音节表，提供音节查询和验证功能
 */
object PinyinSyllableManager {
    
    // 完整的拼音音节集合
    private val ALL_SYLLABLES = setOf(
        // 零声母
        "a", "ai", "an", "ang", "ao",
        "o", "ou",
        "e", "en", "eng", "er",
        "i", "ia", "ie", "iao", "iu", "iong", "in", "ing",
        "u", "ua", "uo", "uai", "ui", "uan", "un", "uang", "ung",
        "ü", "üe", "üan", "ün",
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
        "na", "nai", "ne", "nao", "nou", "nan", "nen", "neng", "ni", "nie", "niao", "niu", "nian", "nin", "niang", "ning", "nong", "nu", "nuan", "nün", "nuo", "nü", "nüe",
        // 声母 l
        "la", "le", "lo", "lai", "lei", "lao", "lou", "lan", "lang", "leng", "li", "lie", "liao", "liu", "lian", "lin", "liang", "ling", "long", "lu", "luan", "lun", "luo", "lü", "lüe",
        // 声母 g
        "ga", "ge", "gai", "gei", "gao", "gou", "gan", "gen", "gang", "geng", "gong", "gu", "gua", "guai", "guan", "guang", "gui", "guo",
        // 声母 k
        "ka", "ke", "kai", "kao", "kou", "kan", "ken", "kang", "keng", "kong", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
        // 声母 h
        "ha", "he", "hai", "hao", "hou", "han", "hen", "hang", "heng", "hong", "hu", "hua", "huai", "huan", "huang", "hui", "huo", "hun",
        // 声母 j
        "ji", "jia", "jie", "jiao", "jiu", "jian", "jin", "jiang", "jing", "jiong", "ju", "juan", "jun", "jue",
        // 声母 q
        "qi", "qia", "qie", "qiao", "qiu", "qian", "qin", "qiang", "qing", "qiong", "qu", "quan", "qun", "que",
        // 声母 x
        "xi", "xia", "xie", "xiao", "xiu", "xian", "xin", "xiang", "xing", "xiong", "xu", "xuan", "xun", "xue",
        // 声母 zh
        "zha", "zhe", "zhai", "zhao", "zhou", "zhan", "zhen", "zhang", "zheng", "zhong", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhun", "zhui", "zhuo",
        // 声母 ch
        "cha", "che", "chai", "chao", "chou", "chan", "chen", "chang", "cheng", "chong", "chu", "chua", "chuai", "chuan", "chuang", "chun", "chui", "chuo",
        // 声母 sh
        "sha", "she", "shai", "shao", "shou", "shan", "shen", "shang", "sheng", "shu", "shua", "shuai", "shuan", "shuang", "shun", "shui", "shuo",
        // 声母 r
        "re", "rao", "rou", "ran", "ren", "rang", "reng", "rong", "ru", "rui", "ruan", "run", "ruo",
        // 声母 z
        "za", "ze", "zai", "zao", "zou", "zan", "zen", "zeng", "zong", "zu", "zuan", "zun", "zui", "zuo",
        // 声母 c
        "ca", "ce", "cai", "cao", "cou", "can", "cen", "ceng", "cong", "cu", "cuan", "cun", "cui", "cuo",
        // 声母 s
        "sa", "se", "sai", "sao", "sou", "san", "sen", "sang", "seng", "song", "su", "suan", "sun", "sui", "suo"
    )
    
    // 按长度分组的音节表，用于贪婪匹配
    private val SYLLABLES_BY_LENGTH: Map<Int, Set<String>> = ALL_SYLLABLES
        .groupBy { it.length }
        .mapValues { it.value.toSet() }
    
    // 最大音节长度
    val MAX_SYLLABLE_LENGTH: Int = SYLLABLES_BY_LENGTH.keys.maxOrNull() ?: 6
    
    /**
     * 判断给定字符串是否为有效的拼音音节
     */
    fun isValidSyllable(syllable: String): Boolean {
        val lowercased = syllable.lowercase()
        val isValid = ALL_SYLLABLES.contains(lowercased)
        Timber.d("【PYDEBUG】检查音节 '$syllable' 是否有效: $isValid")
        
        // 对常见错误拆分进行特别日志记录
        if (!isValid && (lowercased == "bei" || lowercased == "jing" || 
                          lowercased == "tai" || lowercased == "wan" ||
                          lowercased == "wei" || lowercased == "xin")) {
            Timber.e("【PYDEBUG】关键音节 '$lowercased' 未被识别为有效音节! 所有音节表大小: ${ALL_SYLLABLES.size}")
            
            // 列出所有B开头的音节，帮助调试
            if (lowercased.startsWith("b")) {
                val bSyllables = ALL_SYLLABLES.filter { it.startsWith("b") }
                Timber.d("【PYDEBUG】所有B开头的音节: ${bSyllables.joinToString(", ")}")
            }
            
            // 列出所有J开头的音节，帮助调试
            if (lowercased.startsWith("j")) {
                val jSyllables = ALL_SYLLABLES.filter { it.startsWith("j") }
                Timber.d("【PYDEBUG】所有J开头的音节: ${jSyllables.joinToString(", ")}")
            }
        }
        
        return isValid
    }
    
    /**
     * 获取指定长度的所有音节
     */
    fun getSyllablesByLength(length: Int): Set<String> {
        return SYLLABLES_BY_LENGTH[length] ?: emptySet()
    }
    
    /**
     * 将特殊字符（如ü）转换为兼容形式（如v）
     */
    fun normalizeSpecialChar(input: String): String {
        return input.replace('ü', 'v')
    }
    
    /**
     * 将兼容形式（如v）转换回特殊字符（如ü）
     */
    fun restoreSpecialChar(input: String): String {
        // 处理特殊情况：在"nv", "lv"等情况下，v应该替换为ü
        val lv = input.replace(Regex("(^|\\s)(n|l)v"), "$1$2ü")
        
        // 检查其他可能的情况
        return lv
    }
} 