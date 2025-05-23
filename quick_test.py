#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
拼音拆分算法验证脚本
用于快速验证修复后的动态规划算法逻辑
"""

# 模拟音节集合
syllables = {
    # 基础音节
    "wo", "shi", "bei", "jing", "ren", "ni", "hao", "zhong", "guo",
    # 单字母音节
    "a", "o", "e", "i", "u", "v",
    # 常见音节
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
}

def is_valid_syllable(s):
    """检查是否为有效音节"""
    return s in syllables

def split_pinyin_dp(s):
    """
    动态规划拆分算法（修复版）
    优先选择最长的音节
    """
    n = len(s)
    if n == 0:
        return []
    
    # dp[i] 表示前i个字符是否可以被拆分
    dp = [False] * (n + 1)
    # prev[i] 表示前i个字符的最后一个音节的起始位置
    prev = [-1] * (n + 1)
    
    dp[0] = True  # 空字符串可以被拆分
    
    for i in range(1, n + 1):
        # 修复：从长到短尝试音节，确保优先选择最长的音节
        for length in range(min(i, 6), 0, -1):  # 最长音节不超过6个字符
            j = i - length
            if dp[j]:
                syllable = s[j:i]
                if is_valid_syllable(syllable):
                    dp[i] = True
                    prev[i] = j
                    break  # 找到第一个匹配就停止（这样会优先选择较长的音节）
    
    if not dp[n]:
        print(f"DP拆分失败: '{s}'")
        return []
    
    # 回溯构建结果
    result = []
    pos = n
    while pos > 0:
        start = prev[pos]
        syllable = s[start:pos]
        result.insert(0, syllable)
        pos = start
    
    print(f"DP拆分成功: '{s}' -> {'+'.join(result)}")
    return result

def test_split(input_str):
    """测试函数"""
    result = split_pinyin_dp(input_str)
    is_valid = all(is_valid_syllable(syl) for syl in result)
    reconstructed = ''.join(result)
    is_complete = reconstructed == input_str.lower()
    
    return f"""测试输入: '{input_str}'
拆分结果: {' + '.join(result)}
音节有效性: {'✓ 全部有效' if is_valid else '✗ 包含无效音节'}
完整性检查: {'✓ 完整' if is_complete else '✗ 不完整'}
重构结果: '{reconstructed}'"""

def main():
    """主测试函数"""
    print("=== 拼音拆分修复验证测试 ===")
    print()
    
    test_cases = [
        "wo", "shi", "bei", "jing", "ren",
        "nihao", "beijing", "zhongguo", 
        "woshibeijingren", "nihaoshijie"
    ]
    
    for test_case in test_cases:
        print(test_split(test_case))
        print()
    
    print("=== 测试完成 ===")

if __name__ == "__main__":
    main() 