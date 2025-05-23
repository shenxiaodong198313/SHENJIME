#!/usr/bin/env kotlin

/**
 * 拼音拆分修复验证脚本
 * 用于快速验证修复效果
 */

// 模拟音节集合（简化版）
val syllables = setOf(
    // 基础音节
    "wo", "shi", "bei", "jing", "ren", "ni", "hao", "zhong", "guo",
    // 单字母音节
    "a", "o", "e", "i", "u", "v",
    // 常见音节
    "ba", "pa", "ma", "fa", "da", "ta", "na", "la", "ga", "ka", "ha",
    "bi", "pi", "mi", "di", "ti", "ni", "li", "ji", "qi", "xi",
    "bu", "pu", "mu", "fu", "du", "tu", "nu", "lu", "gu", "ku", "hu",
    "ban", "pan", "man", "fan", "dan", "tan", "nan", "lan", "gan", "kan", "han",
    "ben", "pen", "men", "fen", "den", "nen", "len", "gen", "ken", "hen",
    "bing", "ping", "ming", "ding", "ting", "ning", "ling", "jing", "qing", "xing",
    "bang", "pang", "mang", "fang", "dang", "tang", "nang", "lang", "gang", "kang", "hang",
    "bao", "pao", "mao", "dao", "tao", "nao", "lao", "gao", "kao", "hao",
    "bei", "pei", "mei", "fei", "dei", "nei", "lei", "gei", "hei",
    "bai", "pai", "mai", "dai", "tai", "nai", "lai", "gai", "kai", "hai",
    "bou", "pou", "mou", "fou", "dou", "tou", "nou", "lou", "gou", "kou", "hou"
)

fun isValidSyllable(s: String): Boolean {
    return syllables.contains(s)
}

/**
 * 动态规划拆分算法（修复版）
 */
fun splitPinyin(s: String): List<String> {
    val n = s.length
    if (n == 0) return emptyList()
    
    // dp[i] 表示前i个字符是否可以被拆分
    val dp = BooleanArray(n + 1)
    // prev[i] 表示前i个字符的最后一个音节的起始位置
    val prev = IntArray(n + 1) { -1 }
    
    dp[0] = true // 空字符串可以被拆分
    
    for (i in 1..n) {
        // 修复：从长到短尝试音节，确保优先选择最长的音节
        for (len in minOf(i, 6) downTo 1) { // 最长音节不超过6个字符
            val j = i - len
            if (dp[j]) {
                val syllable = s.substring(j, i)
                if (isValidSyllable(syllable)) {
                    dp[i] = true
                    prev[i] = j
                    break // 找到第一个匹配就停止（这样会优先选择较长的音节）
                }
            }
        }
    }
    
    if (!dp[n]) {
        println("DP拆分失败: '$s'")
        return emptyList()
    }
    
    // 回溯构建结果
    val result = mutableListOf<String>()
    var pos = n
    while (pos > 0) {
        val start = prev[pos]
        val syllable = s.substring(start, pos)
        result.add(0, syllable)
        pos = start
    }
    
    println("DP拆分成功: '$s' -> ${result.joinToString("+")}")
    return result
}

/**
 * 测试函数
 */
fun testSplit(input: String): String {
    val result = splitPinyin(input)
    val isValid = result.all { isValidSyllable(it) }
    val reconstructed = result.joinToString("")
    val isComplete = reconstructed == input.lowercase()
    
    return """
        |测试输入: '$input'
        |拆分结果: ${result.joinToString(" + ")}
        |音节有效性: ${if (isValid) "✓ 全部有效" else "✗ 包含无效音节"}
        |完整性检查: ${if (isComplete) "✓ 完整" else "✗ 不完整"}
        |重构结果: '$reconstructed'
    """.trimMargin()
}

/**
 * 主测试函数
 */
fun main() {
    println("=== 拼音拆分修复验证测试 ===")
    println()
    
    val testCases = listOf(
        "wo", "shi", "bei", "jing", "ren",
        "nihao", "beijing", "zhongguo",
        "woshibeijingren", "nihaoshijie"
    )
    
    testCases.forEach { testCase ->
        println(testSplit(testCase))
        println()
    }
    
    println("=== 测试完成 ===")
}

// 运行测试
main() 