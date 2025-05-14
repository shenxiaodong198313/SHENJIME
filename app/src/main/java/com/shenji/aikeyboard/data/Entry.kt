package com.shenji.aikeyboard.data

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import io.realm.kotlin.types.annotations.Index

/**
 * 词条数据模型类
 */
open class Entry : RealmObject {
    @PrimaryKey
    var id: String = ""
    
    var word: String = ""
    
    @Index  // 添加索引注解
    var pinyin: String = ""
    
    var frequency: Int = 0
    var type: String = ""
    
    constructor()
    
    constructor(word: String, pinyin: String, frequency: Int, type: String) {
        this.word = word
        this.pinyin = pinyin
        this.frequency = frequency
        this.type = type
    }
}

/**
 * 词频数据类，用于内存中表示词条及其频率
 */
data class WordFrequency(
    val word: String,
    val frequency: Int
) 