#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
神迹输入法 - 通用词典Trie构建工具
支持构建各种类型的词典文件
"""

import os
import sys
import struct
from typing import Dict, List, Tuple

def remove_tone_marks(pinyin: str) -> str:
    """去除拼音中的声调符号"""
    tone_map = {
        'ā': 'a', 'á': 'a', 'ǎ': 'a', 'à': 'a',
        'ē': 'e', 'é': 'e', 'ě': 'e', 'è': 'e',
        'ī': 'i', 'í': 'i', 'ǐ': 'i', 'ì': 'i',
        'ō': 'o', 'ó': 'o', 'ǒ': 'o', 'ò': 'o',
        'ū': 'u', 'ú': 'u', 'ǔ': 'u', 'ù': 'u',
        'ǖ': 'ü', 'ǘ': 'ü', 'ǚ': 'ü', 'ǜ': 'ü', 'ü': 'v',
        'ń': 'n', 'ň': 'n', 'ǹ': 'n'
    }
    
    result = ""
    for char in pinyin:
        result += tone_map.get(char, char)
    
    return result

def parse_dict_file(file_path: str, percentage: float = 0.3) -> List[Tuple[str, str, int]]:
    """解析词典文件并筛选指定比例的高频词条"""
    entries = []
    
    print(f"正在解析词典文件: {file_path}")
    
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            line_count = 0
            for line in f:
                line_count += 1
                if line_count % 25000 == 0:
                    print(f"已处理 {line_count} 行...")
                
                line = line.strip()
                if not line:
                    continue
                
                parts = line.split('\t')
                if len(parts) >= 3:
                    word = parts[0].strip()
                    pinyin = parts[1].strip()
                    
                    if not pinyin or len(pinyin.strip()) == 0:
                        continue
                    
                    try:
                        frequency = int(parts[2].strip())
                        pinyin_no_tone = remove_tone_marks(pinyin)
                        entries.append((word, pinyin_no_tone, frequency))
                    except ValueError:
                        continue
    
    except Exception as e:
        print(f"错误：解析文件失败 - {e}")
        return []
    
    print(f"解析完成，共获得 {len(entries)} 个词条")
    
    # 按词频排序并筛选高频词
    print(f"正在筛选词频最高的 {percentage*100}% 词语...")
    sorted_entries = sorted(entries, key=lambda x: x[2], reverse=True)
    keep_count = int(len(sorted_entries) * percentage)
    filtered_entries = sorted_entries[:keep_count]
    
    print(f"筛选完成，从 {len(entries)} 个词条中选择了 {len(filtered_entries)} 个高频词条")
    if filtered_entries:
        print(f"词频范围：{filtered_entries[-1][2]} - {filtered_entries[0][2]}")
    
    return filtered_entries

def build_trie_data(entries: List[Tuple[str, str, int]], max_words_per_pinyin: int = 40) -> Dict:
    """构建Trie数据结构"""
    print("正在构建Trie数据...")
    
    trie_data = {}
    
    for i, (word, pinyin, frequency) in enumerate(entries):
        if i % 5000 == 0:
            print(f"已处理 {i} 个词条...")
        
        # 清理拼音格式
        pinyin_clean = ' '.join(pinyin.lower().split())
        
        # 存储到字典中
        if pinyin_clean not in trie_data:
            trie_data[pinyin_clean] = []
        
        trie_data[pinyin_clean].append({
            'word': word,
            'frequency': frequency
        })
    
    # 对每个拼音的词语按频率排序并限制数量
    total_words = 0
    for pinyin in trie_data:
        trie_data[pinyin].sort(key=lambda x: x['frequency'], reverse=True)
        trie_data[pinyin] = trie_data[pinyin][:max_words_per_pinyin]
        total_words += len(trie_data[pinyin])
    
    print(f"Trie构建完成！包含 {len(trie_data)} 个拼音条目，总词数: {total_words}")
    return trie_data

def save_trie_data_file(trie_data: Dict, output_path: str) -> bool:
    """保存Trie数据文件"""
    print(f"正在保存Trie数据到文件: {output_path}")
    
    try:
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        
        with open(output_path, 'wb') as f:
            # 写入版本号（使用LITTLE_ENDIAN）
            f.write(struct.pack('<i', 3))  # 版本3，简化格式，LITTLE_ENDIAN
            
            # 写入数据条目数量
            f.write(struct.pack('<i', len(trie_data)))
            
            # 写入每个条目
            for pinyin, words in trie_data.items():
                # 写入拼音长度和拼音
                pinyin_bytes = pinyin.encode('utf-8')
                f.write(struct.pack('<i', len(pinyin_bytes)))
                f.write(pinyin_bytes)
                
                # 写入词语数量
                f.write(struct.pack('<i', len(words)))
                
                # 写入每个词语
                for word_item in words:
                    word_bytes = word_item['word'].encode('utf-8')
                    f.write(struct.pack('<i', len(word_bytes)))
                    f.write(word_bytes)
                    f.write(struct.pack('<i', word_item['frequency']))
        
        file_size = os.path.getsize(output_path)
        print(f"文件保存成功！文件大小: {file_size} 字节 ({file_size/1024/1024:.2f} MB)")
        
        return True
        
    except Exception as e:
        print(f"错误：保存文件失败 - {e}")
        return False

def build_dict_trie(dict_name: str, percentage: float = 0.3, max_words: int = 40):
    """构建指定词典的Trie文件"""
    input_path = f"app/src/main/assets/cn_dicts/{dict_name}.dict.yaml"
    output_path = f"app/src/main/assets/trie/{dict_name}_trie.dat"
    
    print("=" * 60)
    print(f"神迹输入法 - {dict_name}词典Trie构建工具")
    print("=" * 60)
    print(f"输入文件: {input_path}")
    print(f"输出文件: {output_path}")
    print(f"策略: 保留{percentage*100}%高频词，每个拼音最多{max_words}个词")
    print("=" * 60)
    
    # 检查输入文件是否存在
    if not os.path.exists(input_path):
        print(f"❌ 输入文件不存在: {input_path}")
        return False
    
    # 解析并筛选词典文件
    entries = parse_dict_file(input_path, percentage)
    if not entries:
        print("❌ 解析词典文件失败")
        return False
    
    # 构建Trie数据
    trie_data = build_trie_data(entries, max_words)
    if not trie_data:
        print("❌ 构建Trie数据失败")
        return False
    
    # 保存文件
    if not save_trie_data_file(trie_data, output_path):
        print("❌ 保存文件失败")
        return False
    
    print("=" * 60)
    print(f"✅ {dict_name}词典Trie文件构建成功！")
    print(f"📁 输出文件: {output_path}")
    print("=" * 60)
    
    return True

def main():
    """主函数"""
    if len(sys.argv) < 2:
        print("用法: python build_universal_trie.py <词典名称> [筛选比例] [每拼音最大词数]")
        print("示例: python build_universal_trie.py correlation 0.3 40")
        print("可用词典: correlation, associational, place, people, poetry, corrections, compatible")
        return 1
    
    dict_name = sys.argv[1]
    percentage = float(sys.argv[2]) if len(sys.argv) > 2 else 0.3
    max_words = int(sys.argv[3]) if len(sys.argv) > 3 else 40
    
    success = build_dict_trie(dict_name, percentage, max_words)
    return 0 if success else 1

if __name__ == "__main__":
    exit(main()) 