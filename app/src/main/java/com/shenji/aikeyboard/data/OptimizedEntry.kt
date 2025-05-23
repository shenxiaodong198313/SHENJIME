package com.shenji.aikeyboard.data

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey

/**
 * 优化的词典条目数据类
 * 第二阶段优化：添加新的索引字段以提升查询性能
 */
class OptimizedEntry : RealmObject {
    @PrimaryKey
    var id: String = ""                    // 主键，唯一标识词条
    
    var word: String = ""                  // 词条文本内容
    
    @Index
    var pinyin: String = ""                // 完整拼音，空格分隔
    
    @Index
    var initialLetters: String = ""        // 拼音首字母缩写
    
    var frequency: Int = 0                 // 词频
    
    @Index
    var type: String = ""                  // 词典类型
    
    // === 第二阶段优化新增字段 ===
    
    @Index
    var pinyinLength: Int = 0              // 拼音长度，用于快速过滤
    
    @Index  
    var wordLength: Int = 0                // 词语长度，用于排序优化
    
    @Index
    var pinyinHash: String = ""            // 拼音哈希值，用于精确匹配
    
    @Index
    var frequencyLevel: Int = 0            // 词频等级（1-10），用于分层查询
    
    // === 复合索引字段 ===
    
    @Index
    var typeFrequency: String = ""         // type + frequency组合索引
    
    @Index  
    var lengthType: String = ""            // length + type组合索引
    
    /**
     * 转换为原始Entry对象（向后兼容）
     */
    fun toEntry(): Entry {
        return Entry().apply {
            id = this@OptimizedEntry.id
            word = this@OptimizedEntry.word
            pinyin = this@OptimizedEntry.pinyin
            initialLetters = this@OptimizedEntry.initialLetters
            frequency = this@OptimizedEntry.frequency
            type = this@OptimizedEntry.type
        }
    }
    
    companion object {
        /**
         * 从原始Entry对象创建优化Entry
         */
        fun fromEntry(entry: Entry): OptimizedEntry {
            return OptimizedEntry().apply {
                id = entry.id
                word = entry.word
                pinyin = entry.pinyin
                initialLetters = entry.initialLetters
                frequency = entry.frequency
                type = entry.type
                
                // 计算优化字段
                pinyinLength = pinyin.length
                wordLength = word.length
                pinyinHash = pinyin.hashCode().toString()
                frequencyLevel = calculateFrequencyLevel(frequency, type)
                typeFrequency = "${type}_${frequency}"
                lengthType = "${wordLength}_${type}"
            }
        }
        
        /**
         * 计算词频等级
         */
        private fun calculateFrequencyLevel(frequency: Int, type: String): Int {
            return when (type) {
                "chars" -> when {
                    frequency >= 1000 -> 10
                    frequency >= 500 -> 9
                    frequency >= 200 -> 8
                    frequency >= 100 -> 7
                    frequency >= 50 -> 6
                    frequency >= 20 -> 5
                    frequency >= 10 -> 4
                    frequency >= 5 -> 3
                    frequency >= 2 -> 2
                    else -> 1
                }
                "base" -> when {
                    frequency >= 500 -> 10
                    frequency >= 200 -> 9
                    frequency >= 100 -> 8
                    frequency >= 50 -> 7
                    frequency >= 20 -> 6
                    frequency >= 10 -> 5
                    frequency >= 5 -> 4
                    frequency >= 2 -> 3
                    frequency >= 1 -> 2
                    else -> 1
                }
                else -> when {
                    frequency >= 100 -> 10
                    frequency >= 50 -> 9
                    frequency >= 20 -> 8
                    frequency >= 10 -> 7
                    frequency >= 5 -> 6
                    frequency >= 3 -> 5
                    frequency >= 2 -> 4
                    frequency >= 1 -> 3
                    else -> 1
                }
            }
        }
    }
} 