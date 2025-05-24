#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
神机输入法 - 通用Trie预编译构建工具
支持多种词典类型和自定义参数
"""

import os
import sys
import struct
import argparse
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
    if filtered_entries:
        print(f"词频范围：{filtered_entries[-1][2]} - {filtered_entries[0][2]}")
    
    return filtered_entries

def build_simple_trie_data(entries: List[Tuple[str, str, int]], max_words_per_pinyin: int = 50) -> Dict:
    """构建简化的Trie数据结构"""
    print("正在构建简化Trie数据...")
    
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
    
    # 对每个拼音的词语按频率排序并限制数量
    for pinyin in trie_data:
        trie_data[pinyin].sort(key=lambda x: x['frequency'], reverse=True)
        trie_data[pinyin] = trie_data[pinyin][:max_words_per_pinyin]
    
    print(f"简化Trie构建完成！包含 {len(trie_data)} 个拼音条目")
    return trie_data

def save_simple_data_file(trie_data: Dict, output_path: str) -> bool:
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

def get_dict_info(dict_type: str) -> Tuple[str, str]:
    """获取词典类型对应的文件路径信息"""
    dict_mapping = {
        'base': ('base.dict.yaml', 'base_trie.dat'),
        'correlation': ('correlation.dict.yaml', 'correlation_trie.dat'),
        'associational': ('associational.dict.yaml', 'associational_trie.dat'),
        'poetry': ('poetry.dict.yaml', 'poetry_trie.dat'),
        'chars': ('chars.dict.yaml', 'chars_trie.dat'),
        'place': ('place.dict.yaml', 'place_trie.dat'),
        'people': ('people.dict.yaml', 'people_trie.dat'),
        'compatible': ('compatible.dict.yaml', 'compatible_trie.dat'),
        'corrections': ('corrections.dict.yaml', 'corrections_trie.dat')
    }
    
    if dict_type in dict_mapping:
        return dict_mapping[dict_type]
    else:
        raise ValueError(f"不支持的词典类型: {dict_type}")

def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='神机输入法 - 通用Trie预编译构建工具')
    parser.add_argument('--input', '-i', type=str, help='输入词典文件路径')
    parser.add_argument('--output', '-o', type=str, help='输出Trie文件路径')
    parser.add_argument('--type', '-t', type=str, choices=['base', 'correlation', 'associational', 'poetry', 'chars', 'place', 'people', 'compatible', 'corrections'], help='词典类型')
    parser.add_argument('--percentage', '-p', type=float, default=0.5, help='保留的高频词百分比 (0.0-1.0)')
    parser.add_argument('--max-words', '-m', type=int, default=50, help='每个拼音最多保留的词语数量')
    parser.add_argument('--verify', '-v', action='store_true', help='验证生成的文件')
    
    args = parser.parse_args()
    
    # 确定输入输出文件路径
    if args.type:
        input_file, output_file = get_dict_info(args.type)
        input_path = args.input or f"app/src/main/assets/cn_dicts/{input_file}"
        output_path = args.output or f"app/src/main/assets/trie/{output_file}"
    else:
        if not args.input or not args.output:
            print("错误：必须指定 --type 或同时指定 --input 和 --output")
            return 1
        input_path = args.input
        output_path = args.output
    
    print("=" * 60)
    print("神机输入法 - 通用Trie预编译构建工具")
    print("=" * 60)
    print(f"输入文件: {input_path}")
    print(f"输出文件: {output_path}")
    print(f"词频筛选: {args.percentage*100}%")
    print(f"最大词数: {args.max_words}")
    print("=" * 60)
    
    if not os.path.exists(input_path):
        print(f"错误：输入文件不存在 - {input_path}")
        return 1
    
    try:
        # 解析词典文件
        entries = parse_dict_file(input_path)
        if not entries:
            return 1
        
        # 筛选高频词语
        filtered_entries = filter_top_frequency_words(entries, args.percentage)
        if not filtered_entries:
            return 1
        
        # 构建简化数据
        trie_data = build_simple_trie_data(filtered_entries, args.max_words)
        if not trie_data:
            return 1
        
        # 保存数据文件
        if not save_simple_data_file(trie_data, output_path):
            return 1
        
        # 验证文件
        if args.verify:
            if not verify_simple_data_file(output_path):
                return 1
        
        print("\n" + "=" * 60)
        print("✅ Trie文件构建成功！")
        print(f"📁 输出文件: {output_path}")
        print("=" * 60)
        
        return 0
        
    except Exception as e:
        print(f"错误：程序执行失败 - {e}")
        import traceback
        traceback.print_exc()
        return 1

if __name__ == "__main__":
    sys.exit(main()) 