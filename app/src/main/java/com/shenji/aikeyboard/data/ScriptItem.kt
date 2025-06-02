package com.shenji.aikeyboard.data

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

class ScriptItem : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var title: String = ""
    var content: String = ""
    var category: String = "" // "script" 或 "material"
    var tags: String = "" // 标签，用逗号分隔
    var createTime: Long = System.currentTimeMillis()
    var updateTime: Long = System.currentTimeMillis()
    var isActive: Boolean = true
} 