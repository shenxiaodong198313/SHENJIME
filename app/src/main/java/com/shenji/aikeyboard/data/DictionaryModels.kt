package com.shenji.aikeyboard.data

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.UUID
import java.util.Date

// 词条模型
class Entry : RealmObject {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var word: String = ""      // 词语
    var pinyin: String = ""    // 拼音
    var frequency: Int = 0     // 词频
    var type: String = ""      // 词典类型
}

// 词典信息模型
class DictionaryInfo : RealmObject {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var dictName: String = ""      // 词典名称
    var fileName: String = ""      // 文件名
    var description: String = ""   // 描述
    var entryCount: Int = 0        // 词条数量
    var convertedAt: Long = 0      // 转换时间
} 