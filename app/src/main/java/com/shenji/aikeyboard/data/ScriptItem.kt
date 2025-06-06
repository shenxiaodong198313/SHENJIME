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
    var type: String = "text" // "text", "image", "mixed"
    var imagePath: String = "" // 图片路径（仅当type为"image"时使用）
    var imagePaths: String = "" // 多张图片路径，用逗号分隔
    var textList: String = "" // 多条文本列表，用|分隔（图文混合模式）
    var mixedImagePaths: String = "" // 图文混合模式下的图片路径，用逗号分隔
} 