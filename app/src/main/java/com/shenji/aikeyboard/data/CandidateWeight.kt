package com.shenji.aikeyboard.data

/**
 * 候选词权重数据类，用于调试显示
 */
data class CandidateWeight(
    val stage: Int,           // 查询阶段
    val frequency: Int,       // 词频
    val matchType: Int,       // 匹配类型：0-精确，1-前缀，2-首字母，3-模糊
    val lengthBonus: Int      // 长度奖励
)

/**
 * 候选词结果包装类
 * 包含词条信息和相关权重数据
 */
data class CandidateEntry(
    val word: String,      // 词条文本
    val pinyin: String,    // 拼音
    val frequency: Int,    // 词频
    val type: String,      // 词典类型
    val stage: Int,        // 查询阶段
    val matchType: Int,    // 匹配类型
    val lengthBonus: Int   // 长度奖励
) {
    // 从Entry对象构造
    constructor(entry: Entry, stage: Int, matchType: Int): this(
        word = entry.word,
        pinyin = entry.pinyin,
        frequency = entry.frequency,
        type = entry.type,
        stage = stage,
        matchType = matchType,
        lengthBonus = if (entry.word.length > 3) 10 else 0
    )
    
    // 转换为WordFrequency
    fun toWordFrequency(): WordFrequency {
        return WordFrequency(word, frequency)
    }
}

/**
 * 候选词比较器
 * 实现多维度排序逻辑
 */
object CandidateComparator {
    fun compare(a: CandidateEntry, b: CandidateEntry): Int {
        // 1. 阶段优先级
        if (a.stage != b.stage) 
            return a.stage.compareTo(b.stage)
        
        // 2. 匹配类型
        if (a.matchType != b.matchType)
            return a.matchType.compareTo(b.matchType)
        
        // 3. 词长优先（短词优先）
        if (a.word.length != b.word.length)
            return b.word.length.compareTo(a.word.length)  // 反向比较，使短词排在前面
        
        // 4. 词频 + 长度奖励（仅在词长相同时比较）
        val scoreA = a.frequency + a.lengthBonus
        val scoreB = b.frequency + b.lengthBonus
        return scoreB.compareTo(scoreA)
    }
}

/**
 * 候选词去重检测器
 */
class DuplicateDetector {
    private val seen = mutableMapOf<String, CandidateEntry>()
    private val duplicates = mutableListOf<Pair<String, String>>()

    fun process(entry: CandidateEntry): Boolean {
        val key = entry.word + entry.pinyin
        if (key in seen) {
            // 记录冲突
            duplicates.add(Pair(entry.word, "${entry.type} vs ${seen[key]?.type}"))
            
            // 保留更高权重的结果
            if (CandidateComparator.compare(entry, seen[key]!!) < 0) {
                seen[key] = entry
            }
            return false
        }
        seen[key] = entry
        return true
    }
    
    // 获取所有去重后的候选词
    fun getResults(): List<CandidateEntry> {
        return seen.values.toList()
    }
    
    // 获取冲突记录（用于调试）
    fun getDuplicates(): List<Pair<String, String>> {
        return duplicates
    }
    
    // 清空记录
    fun clear() {
        seen.clear()
        duplicates.clear()
    }
} 