#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
神迹输入法 - Java兼容的Base词典Trie预编译构建工具
使用简单的二进制格式生成数据文件
"""

import os
import sys
import struct
from typing import Dict, List, Tuple, Optional

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

def parse_dict_file(file_path: str) -> List[Tuple[str, str, int]]:
    """解析词典文件，返回(词语, 拼音, 词频)的列表"""
    entries = []
    
    print(f"正在解析词典文件: {file_path}")
    
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            line_count = 0
            for line in f:
                line_count += 1
                if line_count % 50000 == 0:
                    print(f"已处理 {line_count} 行...")
                
                line = line.strip()
                if not line:
                    continue
                
                parts = line.split('\t')
                if len(parts) >= 3:
                    word = parts[0].strip()
                    pinyin = parts[1].strip()
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
    return entries

def filter_top_frequency_words(entries: List[Tuple[str, str, int]], percentage: float = 0.5) -> List[Tuple[str, str, int]]:
    """筛选词频最高的指定百分比的词语"""
    print(f"正在筛选词频最高的 {percentage*100}% 词语...")
    
    sorted_entries = sorted(entries, key=lambda x: x[2], reverse=True)
    keep_count = int(len(sorted_entries) * percentage)
    filtered_entries = sorted_entries[:keep_count]
    
    print(f"筛选完成，从 {len(entries)} 个词条中选择了 {len(filtered_entries)} 个高频词条")
    print(f"词频范围：{filtered_entries[-1][2]} - {filtered_entries[0][2]}")
    
    return filtered_entries

def build_simple_trie_data(entries: List[Tuple[str, str, int]]) -> Dict:
    """构建简化的Trie数据结构"""
    print("正在构建简化Trie数据...")
    
    # 使用简单的字典结构存储数据
    trie_data = {}
    
    for i, (word, pinyin, frequency) in enumerate(entries):
        if i % 10000 == 0:
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
    
    # 对每个拼音的词语按频率排序
    for pinyin in trie_data:
        trie_data[pinyin].sort(key=lambda x: x['frequency'], reverse=True)
        # 只保留前50个最高频的词
        trie_data[pinyin] = trie_data[pinyin][:50]
    
    print(f"简化Trie构建完成！包含 {len(trie_data)} 个拼音条目")
    return trie_data

def save_simple_data_file(trie_data: Dict, output_path: str):
    """保存简化的数据文件"""
    print(f"正在保存简化数据到文件: {output_path}")
    
    try:
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        
        with open(output_path, 'wb') as f:
            # 写入版本号
            f.write(struct.pack('>i', 3))  # 使用版本3表示简化格式
            
            # 写入数据条目数量
            f.write(struct.pack('>i', len(trie_data)))
            
            # 写入每个条目
            for pinyin, words in trie_data.items():
                # 写入拼音长度和拼音
                pinyin_bytes = pinyin.encode('utf-8')
                f.write(struct.pack('>i', len(pinyin_bytes)))
                f.write(pinyin_bytes)
                
                # 写入词语数量
                f.write(struct.pack('>i', len(words)))
                
                # 写入每个词语
                for word_item in words:
                    word_bytes = word_item['word'].encode('utf-8')
                    f.write(struct.pack('>i', len(word_bytes)))
                    f.write(word_bytes)
                    f.write(struct.pack('>i', word_item['frequency']))
        
        file_size = os.path.getsize(output_path)
        print(f"文件保存成功！文件大小: {file_size} 字节 ({file_size/1024/1024:.2f} MB)")
        
        return True
        
    except Exception as e:
        print(f"错误：保存文件失败 - {e}")
        return False

def verify_simple_data_file(file_path: str) -> bool:
    """验证生成的简化数据文件"""
    print(f"正在验证数据文件: {file_path}")
    
    try:
        with open(file_path, 'rb') as f:
            # 读取版本号
            version_bytes = f.read(4)
            if len(version_bytes) != 4:
                print("错误：文件格式不正确")
                return False
            
            version = struct.unpack('>i', version_bytes)[0]
            print(f"文件版本号: {version}")
            
            # 读取数据条目数量
            count_bytes = f.read(4)
            count = struct.unpack('>i', count_bytes)[0]
            print(f"拼音条目数量: {count}")
            
            # 验证前几个条目
            total_words = 0
            for i in range(min(5, count)):
                # 读取拼音
                pinyin_len = struct.unpack('>i', f.read(4))[0]
                pinyin = f.read(pinyin_len).decode('utf-8')
                
                # 读取词语数量
                word_count = struct.unpack('>i', f.read(4))[0]
                total_words += word_count
                
                words = []
                for j in range(word_count):
                    word_len = struct.unpack('>i', f.read(4))[0]
                    word = f.read(word_len).decode('utf-8')
                    frequency = struct.unpack('>i', f.read(4))[0]
                    words.append(f"{word}({frequency})")
                
                print(f"   '{pinyin}' -> {', '.join(words[:3])}")
            
            print(f"验证成功！预计总词语数: {total_words * count // min(5, count)}")
            return True
            
    except Exception as e:
        print(f"错误：验证文件失败 - {e}")
        return False

def main():
    """主函数"""
    print("=" * 60)
    print("神迹输入法 - Java兼容Base词典构建工具")
    print("=" * 60)
    
    input_file = "app/src/main/assets/cn_dicts/base.dict.yaml"
    output_file = "app/src/main/assets/trie/base_simple.dat"
    
    if not os.path.exists(input_file):
        print(f"错误：输入文件不存在 - {input_file}")
        return 1
    
    try:
        # 解析词典文件
        entries = parse_dict_file(input_file)
        if not entries:
            return 1
        
        # 筛选高频词语
        filtered_entries = filter_top_frequency_words(entries, 0.5)
        if not filtered_entries:
            return 1
        
        # 构建简化数据
        trie_data = build_simple_trie_data(filtered_entries)
        if not trie_data:
            return 1
        
        # 保存数据文件
        if not save_simple_data_file(trie_data, output_file):
            return 1
        
        # 验证文件
        if not verify_simple_data_file(output_file):
            return 1
        
        print("\n" + "=" * 60)
        print("✅ Java兼容Base词典文件构建成功！")
        print(f"📁 输出文件: {output_file}")
        print("=" * 60)
        
        return 0
        
    except Exception as e:
        print(f"错误：程序执行失败 - {e}")
        import traceback
        traceback.print_exc()
        return 1

if __name__ == "__main__":
    sys.exit(main()) 