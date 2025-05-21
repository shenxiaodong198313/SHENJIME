package com.shenji.aikeyboard.pinyin

/**
 * 拼音分词器 - 标准化模块
 * 
 * 负责拼音音节的分割和处理
 */
class PinyinSplitter {

    companion object {
        // 单例实例
        private var instance: PinyinSplitter? = null
        
        /**
         * 获取PinyinSplitter单例实例
         * @return PinyinSplitter实例
         */
        @JvmStatic
        fun getInstance(): PinyinSplitter {
            if (instance == null) {
                instance = PinyinSplitter()
            }
            return instance!!
        }
    }

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
     * 将无空格拼音拆分为有效音节序列
     * 支持连续拼音输入拆分，如"nihao"拆分为["ni", "hao"]
     * 
     * @param input 原始拼音输入
     * @return 拆分后的音节列表，若无法拆分则返回空列表
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
        
        // 检查是否是首字母+音节的混合模式
        val mixedResult = checkMixedInitialAndSyllable(cleanInput)
        if (mixedResult.isNotEmpty()) {
            return mixedResult
        }
        
        // 尝试使用动态规划算法进行音节拆分
        val dpResult = splitByDP(cleanInput)
        if (dpResult.isNotEmpty()) {
            return dpResult
        }
        
        // 贪心拆分：从左到右查找最长有效音节
        val greedyResult = greedySplit(cleanInput)
        if (greedyResult.isNotEmpty()) {
            return greedyResult
        }
        
        // 所有方法都失败，返回空列表
        return emptyList()
    }
    
    /**
     * 检查是否为首字母+音节的混合模式
     * 例如：sji -> s + ji, bma -> b + ma
     * 
     * @param input 用户输入
     * @return 拆分结果，形如 [s, ji] 或 [b, ma]，若不符合此模式则返回空列表
     */
    private fun checkMixedInitialAndSyllable(input: String): List<String> {
        // 输入长度至少为2，才可能是首字母+音节
        if (input.length < 2) return emptyList()
        
        // 提取第一个字符作为可能的首字母
        val initial = input.substring(0, 1)
        
        // 检查是否是有效的首字母(a-z)
        if (!initial.matches(Regex("[a-z]"))) return emptyList()
        
        // 提取剩余部分
        val remaining = input.substring(1)
        
        // 尝试将剩余部分识别为一个或多个完整音节
        
        // 方法1：检查整个剩余部分是否是一个完整音节
        if (PINYIN_SYLLABLES.contains(remaining)) {
            return listOf(initial, remaining)
        }
        
        // 方法2：尝试对剩余部分进行音节拆分
        val remainingSyllables = splitByDP(remaining)
        if (remainingSyllables.isNotEmpty()) {
            return listOf(initial) + remainingSyllables
        }
        
        // 方法3：尝试贪心拆分剩余部分
        val greedySyllables = greedySplit(remaining)
        if (greedySyllables.isNotEmpty()) {
            return listOf(initial) + greedySyllables
        }
        
        // 如果无法将剩余部分识别为有效音节，返回空列表
        return emptyList()
    }
    
    /**
     * 使用动态规划算法拆分拼音
     * 
     * @param input 拼音输入
     * @return 拆分结果
     */
    private fun splitByDP(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        
        val n = input.length
        
        // dp[i] 表示前i个字符是否可以拆分为有效音节
        val dp = BooleanArray(n + 1)
        dp[0] = true  // 空字符串可以拆分（基础情况）
        
        // prev[i] 存储前i个字符的最后一个音节的起始位置
        val prev = IntArray(n + 1) { -1 }
        
        // 填充dp数组
        for (i in 1..n) {
            for (j in 0 until i) {
                val syllable = input.substring(j, i)
                if (dp[j] && PINYIN_SYLLABLES.contains(syllable)) {
                    dp[i] = true
                    prev[i] = j
                    break
                }
            }
        }
        
        // 如果整个字符串不可拆分，返回空列表
        if (!dp[n]) {
            return emptyList()
        }
        
        // 回溯构建结果
        val result = mutableListOf<String>()
        var pos = n
        while (pos > 0) {
            val start = prev[pos]
            result.add(0, input.substring(start, pos))
            pos = start
        }
        
        return result
    }
    
    /**
     * 使用贪心算法拆分拼音
     * 从左到右查找最长有效音节
     * 
     * @param input 拼音输入
     * @return 拆分结果
     */
    private fun greedySplit(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        
        val result = mutableListOf<String>()
        var startIndex = 0
        
        while (startIndex < input.length) {
            var found = false
            // 查找尽可能长的音节
            for (syllable in ORDERED_SYLLABLES) {
                if (startIndex + syllable.length <= input.length && 
                    input.substring(startIndex, startIndex + syllable.length) == syllable) {
                    result.add(syllable)
                    startIndex += syllable.length
                    found = true
                    break
                }
            }
            // 如果没有找到匹配的音节，拆分失败
            if (!found) {
                return emptyList()
            }
        }
        
        return result
    }
    
    /**
     * 计算拼音的音节数量
     */
    fun countSyllables(pinyin: String): Int {
        val syllables = splitPinyin(pinyin)
        return syllables.size
    }
    
    /**
     * 生成拼音首字母缩写
     */
    fun generateInitials(pinyin: String): String {
        if (pinyin.isEmpty()) return ""
        
        // 如果拼音包含空格，按空格分割
        if (pinyin.contains(" ")) {
            return pinyin.split(" ")
                .filter { it.isNotEmpty() }
                .joinToString("") { if (it.isNotEmpty()) it.first().toString() else "" }
        } 
        // 如果拼音不包含空格，尝试拆分
        else {
            val syllables = splitPinyin(pinyin)
            if (syllables.isNotEmpty()) {
                return syllables.joinToString("") { 
                    if (it.isNotEmpty()) it.first().toString() else "" 
                }
            }
            
            // 如果无法拆分，将整个拼音的首字母作为缩写
            return if (pinyin.isNotEmpty()) pinyin.first().toString() else ""
        }
    }
} 