package com.shenji.aikeyboard.utils

/**
 * 优化的拼音音节与分词管理器
 * 基于最长匹配优先原则实现拼音字符串的音节切分与分词
 * 解决了如 "nihao" 被错误分割为 "n + i + hao" 而不是 "ni + hao" 的问题
 */
object PinyinSegmenterOptimized {
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

    /**
     * 将汉语拼音连写字符串分割成音节List
     */
    fun cut(s: String): List<String> {
        val list = cutWithWholeSyllablePriority(s, 0)
        if (list == null || list.isEmpty()) return listOf(s)
        if (list.last().isEmpty()) {
            return list.dropLast(1)
        }
        return list
    }

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