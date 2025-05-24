#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import struct
import os
from typing import List, Tuple, Dict

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

def parse_chars_dict_file(file_path: str) -> List[Tuple[str, str, int]]:
    """解析chars词典文件，返回(词语, 拼音, 词频)的列表"""
    entries = []
    filtered_count = 0
    
    print(f"正在解析chars词典文件: {file_path}")
    
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
                    
                    # 过滤掉拼音为"无"的词条
                    if pinyin == "无":
                        filtered_count += 1
                        continue
                    
                    # 过滤掉空拼音或无效拼音
                    if not pinyin or len(pinyin.strip()) == 0:
                        filtered_count += 1
                        continue
                    
                    try:
                        frequency = int(parts[2].strip())
                        pinyin_no_tone = remove_tone_marks(pinyin)
                        entries.append((word, pinyin_no_tone, frequency))
                    except ValueError:
                        filtered_count += 1
                        continue
    
    except Exception as e:
        print(f"错误：解析文件失败 - {e}")
        return []
    
    print(f"解析完成，共获得 {len(entries)} 个有效词条")
    print(f"过滤掉 {filtered_count} 个无效词条（拼音为'无'或空）")
    return entries

def build_unlimited_trie_data(entries: List[Tuple[str, str, int]]) -> Dict:
    """构建无限制的Trie数据结构（不限制每个拼音的词数）"""
    print("正在构建无限制Trie数据...")
    
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
    
    # 对每个拼音的词语按频率排序（不限制数量）
    total_words = 0
    for pinyin in trie_data:
        trie_data[pinyin].sort(key=lambda x: x['frequency'], reverse=True)
        total_words += len(trie_data[pinyin])
    
    print(f"无限制Trie构建完成！包含 {len(trie_data)} 个拼音条目，总词数: {total_words}")
    return trie_data

def save_trie_data_file(trie_data: Dict, output_path: str) -> bool:
    """保存Trie数据文件"""
    print(f"正在保存Trie数据到文件: {output_path}")
    
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

def verify_trie_data_file(file_path: str) -> bool:
    """验证生成的Trie数据文件"""
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
                for j in range(min(3, word_count)):  # 只显示前3个词
                    word_len = struct.unpack('>i', f.read(4))[0]
                    word = f.read(word_len).decode('utf-8')
                    frequency = struct.unpack('>i', f.read(4))[0]
                    words.append(f"{word}({frequency})")
                
                # 跳过剩余的词语
                for j in range(3, word_count):
                    word_len = struct.unpack('>i', f.read(4))[0]
                    f.seek(f.tell() + word_len + 4)  # 跳过词语内容和频率
                
                print(f"   '{pinyin}' -> {', '.join(words)} (共{word_count}个词)")
            
            # 计算总词数（估算）
            f.seek(8)  # 回到数据开始位置
            actual_total_words = 0
            for i in range(count):
                # 跳过拼音
                pinyin_len = struct.unpack('>i', f.read(4))[0]
                f.seek(f.tell() + pinyin_len)
                
                # 读取词语数量
                word_count = struct.unpack('>i', f.read(4))[0]
                actual_total_words += word_count
                
                # 跳过所有词语
                for j in range(word_count):
                    word_len = struct.unpack('>i', f.read(4))[0]
                    f.seek(f.tell() + word_len + 4)  # 跳过词语内容和频率
            
            print(f"验证成功！实际总词语数: {actual_total_words}")
            return True
            
    except Exception as e:
        print(f"错误：验证文件失败 - {e}")
        return False

def main():
    """主函数"""
    input_path = "app/src/main/assets/cn_dicts/chars.dict.yaml"
    output_path = "app/src/main/assets/trie/chars_trie.dat"
    
    print("=" * 60)
    print("神机输入法 - 无限制chars Trie构建工具")
    print("=" * 60)
    print(f"输入文件: {input_path}")
    print(f"输出文件: {output_path}")
    print("=" * 60)
    
    # 解析词典文件
    entries = parse_chars_dict_file(input_path)
    if not entries:
        print("❌ 解析词典文件失败")
        return 1
    
    # 构建Trie数据
    trie_data = build_unlimited_trie_data(entries)
    if not trie_data:
        print("❌ 构建Trie数据失败")
        return 1
    
    # 保存文件
    if not save_trie_data_file(trie_data, output_path):
        print("❌ 保存文件失败")
        return 1
    
    # 验证文件
    if not verify_trie_data_file(output_path):
        print("❌ 验证文件失败")
        return 1
    
    print("=" * 60)
    print("✅ 无限制chars Trie文件构建成功！")
    print(f"📁 输出文件: {output_path}")
    print("=" * 60)
    
    return 0

if __name__ == "__main__":
    exit(main()) 