package com.shenji.aikeyboard.data

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import io.realm.kotlin.types.annotations.Index

/**
 * 词条数据模型类
 * 用于存储输入法词典中的词条信息
 */
open class Entry : RealmObject {
    /**
     * 词条唯一标识符
     * 主键，用于在数据库中唯一标识一个词条
     */
    @PrimaryKey
    var id: String = ""
    
    /**
     * 词条文本内容
     * 存储汉字词语或单字
     */
    var word: String = ""
    
    /**
     * 词条拼音
     * 存储词语的完整拼音，使用空格分隔各个字的拼音
     * 添加索引以加速拼音查询
     */
    @Index  // 添加索引注解
    var pinyin: String = ""
    
    /**
     * 拼音首字母缩写
     * 存储拼音的首字母组合，用于首字母输入匹配
     * 例如："bei jing" 的首字母为 "bj"
     * 添加索引以加速首字母查询
     */
    @Index  // 新增首字母索引字段
    var initialLetters: String = ""
    
    /**
     * 词频
     * 表示词条的使用频率，数值越高表示越常用
     * 用于排序候选词
     */
    var frequency: Int = 0
    
    /**
     * 词典类型
     * 用于区分不同类型的词典，如：
     * chars: 单字词典
     * base: 基础词典
     * correlation: 关联词典
     * associational: 联想词典
     * compatible: 兼容词典
     * corrections: 纠错词典
     * place: 地名词典
     * people: 人名词典
     * poetry: 诗词词典
     */
    var type: String = ""
    
    constructor()
    
    constructor(word: String, pinyin: String, frequency: Int, type: String) {
        this.word = word
        this.pinyin = pinyin
        this.frequency = frequency
        this.type = type
        this.initialLetters = generateInitialLetters(pinyin)
    }
    
    // 添加首字母生成方法
    private fun generateInitialLetters(pinyin: String): String {
        return pinyin.split(" ")
            .filter { it.isNotEmpty() }
            .joinToString("") { if (it.isNotEmpty()) it.first().toString() else "" }
    }
}

/**
 * 词频数据类，用于内存中表示词条及其频率
 */
data class WordFrequency(
    val word: String,
    val frequency: Int
) 