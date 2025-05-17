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
        "da", "de", "dai", "dai", "dan", "dang", "deng", "di", "die", "diao", "diu", "dian", "ding", "dong", "dou", "du", "duan", "dun", "duo",
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
        "zhi", "zha", "zhe", "zhi", "zhai", "zhao", "zhou", "zhan", "zhen", "zhang", "zheng", "zhong", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhun", "zhui", "zhuo",
        // 声母 ch
        "chi", "cha", "che", "chi", "chai", "chao", "chou", "chan", "chen", "chang", "cheng", "chong", "chu", "chua", "chuai", "chuan", "chuang", "chun", "chui", "chuo",
        // 声母 sh
        "shi", "sha", "she", "shi", "shai", "shao", "shou", "shan", "shen", "shang", "sheng", "shu", "shua", "shuai", "shuan", "shuang", "shun", "shui", "shuo",
        // 声母 r
        "ri", "re", "rao", "rou", "ran", "ren", "rang", "reng", "rong", "ru", "rui", "ruan", "run", "ruo",
        // 声母 z
        "zi", "za", "ze", "zuo", "zan", "zou", "zang", "zen", "zeng", "zong", "zu", "zuan", "zun", "zui", "zuo",
        // 声母 c
        "ci", "ca", "ce", "cuo", "can", "cou", "cang", "cen", "ceng", "cong", "cu", "cuan", "cun", "cui", "cuo",
        // 声母 s
        "si", "sa", "se", "suo", "san", "sou", "sang", "sen", "seng", "song", "su", "suan", "sun", "sui", "suo",
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
     * 将无空格拼音分割为有效音节序列
     * 采用从左到右的贪婪匹配算法，优先匹配最长音节
     */
    fun splitPinyin(input: String): List<String> {
        // 预处理：处理ü字符
        val processedInput = preprocessInput(input)
        
        val result = mutableListOf<String>()
        var remainingInput = processedInput
        
        // 循环直到剩余字符串为空
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
    
    /**
     * 处理输入字符串，支持ü和v的互换
     */
    private fun preprocessInput(input: String): String {
        // 如果包含ü，则转换为v以便于处理
        // 注意：在实际应用中，可能需要更复杂的映射逻辑
        return input.replace('ü', 'v')
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
} 