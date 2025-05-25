#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
神迹输入法 - Base词典Trie预编译构建工具
功能：
1. 解析base.dict.yaml文件
2. 去掉拼音声调
3. 选择词频最高50%的词语
4. 构建Trie数据结构
5. 生成预编译文件
"""

import os
import sys
import re
import pickle
import struct
from typing import Dict, List, Tuple, Optional
from collections import defaultdict
import unicodedata

class WordItem:
    """词语项，对应Java中的WordItem类"""
    def __init__(self, word: str, frequency: int):
        self.word = word
        self.frequency = frequency
    
    def __repr__(self):
        return f"WordItem(word='{self.word}', frequency={self.frequency})"

class TrieNode:
    """Trie树节点，对应Java中的TrieNode类"""
    MAX_WORDS_PER_NODE = 50
    
    def __init__(self):
        self.children: Dict[str, 'TrieNode'] = {}
        self.words: List[WordItem] = []
        self.is_end_of_word = False
    
    def add_word(self, word: str, frequency: int) -> bool:
        """添加词语到节点"""
        # 如果列表未满，直接添加
        if len(self.words) < self.MAX_WORDS_PER_NODE:
            self.words.append(WordItem(word, frequency))
            self.words.sort(key=lambda x: x.frequency, reverse=True)
            return True
        
        # 如果列表已满，检查是否可以替换最低频率的词
        lowest_freq_item = min(self.words, key=lambda x: x.frequency)
        if frequency > lowest_freq_item.frequency:
            self.words.remove(lowest_freq_item)
            self.words.append(WordItem(word, frequency))
            self.words.sort(key=lambda x: x.frequency, reverse=True)
            return True
        
        return False
    
    def calculate_memory_stats(self) -> Tuple[int, int]:
        """计算内存统计信息：(节点数, 词语数)"""
        node_count = 1
        word_count = len(self.words)
        
        for child in self.children.values():
            child_nodes, child_words = child.calculate_memory_stats()
            node_count += child_nodes
            word_count += child_words
        
        return node_count, word_count

class PinyinTrie:
    """拼音Trie树，对应Java中的PinyinTrie类"""
    def __init__(self):
        self.root = TrieNode()
        self.syllable_separator = "'"
    
    def insert(self, pinyin: str, word: str, frequency: int):
        """插入拼音和对应的汉字"""
        current = self.root
        
        # 遍历拼音的每个字符
        for char in pinyin:
            if char not in current.children:
                current.children[char] = TrieNode()
            current = current.children[char]
        
        # 标记为词语结尾
        current.is_end_of_word = True
        
        # 添加词语到当前节点
        current.add_word(word, frequency)
    
    def get_memory_stats(self) -> Tuple[int, int]:
        """获取内存统计信息"""
        return self.root.calculate_memory_stats()
    
    def is_empty(self) -> bool:
        """判断Trie树是否为空"""
        return len(self.root.children) == 0

def remove_tone_marks(pinyin: str) -> str:
    """去除拼音中的声调符号"""
    # 声调符号映射表
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
                
                # 解析格式：词语\t拼音\t词频
                parts = line.split('\t')
                if len(parts) >= 3:
                    word = parts[0].strip()
                    pinyin = parts[1].strip()
                    try:
                        frequency = int(parts[2].strip())
                        
                        # 去除拼音声调
                        pinyin_no_tone = remove_tone_marks(pinyin)
                        
                        entries.append((word, pinyin_no_tone, frequency))
                    except ValueError:
                        print(f"警告：无法解析词频，跳过行 {line_count}: {line}")
                        continue
    
    except Exception as e:
        print(f"错误：解析文件失败 - {e}")
        return []
    
    print(f"解析完成，共获得 {len(entries)} 个词条")
    return entries

def filter_top_frequency_words(entries: List[Tuple[str, str, int]], percentage: float = 0.5) -> List[Tuple[str, str, int]]:
    """筛选词频最高的指定百分比的词语"""
    print(f"正在筛选词频最高的 {percentage*100}% 词语...")
    
    # 按词频排序
    sorted_entries = sorted(entries, key=lambda x: x[2], reverse=True)
    
    # 计算要保留的词语数量
    keep_count = int(len(sorted_entries) * percentage)
    
    filtered_entries = sorted_entries[:keep_count]
    
    print(f"筛选完成，从 {len(entries)} 个词条中选择了 {len(filtered_entries)} 个高频词条")
    print(f"词频范围：{filtered_entries[-1][2]} - {filtered_entries[0][2]}")
    
    return filtered_entries

def build_trie(entries: List[Tuple[str, str, int]]) -> PinyinTrie:
    """构建Trie树"""
    print("正在构建Trie树...")
    
    trie = PinyinTrie()
    
    for i, (word, pinyin, frequency) in enumerate(entries):
        if i % 10000 == 0:
            print(f"已插入 {i} 个词条...")
        
        # 将拼音转换为小写并去除多余空格
        pinyin_clean = ' '.join(pinyin.lower().split())
        
        # 插入到Trie树
        trie.insert(pinyin_clean, word, frequency)
    
    # 获取统计信息
    node_count, word_count = trie.get_memory_stats()
    print(f"Trie树构建完成！")
    print(f"统计信息：节点数 = {node_count}, 词语数 = {word_count}")
    
    return trie

def save_trie_to_file(trie: PinyinTrie, output_path: str):
    """保存Trie树到文件 - 使用版本3简化格式"""
    print(f"正在保存Trie树到文件: {output_path}")
    
    try:
        # 确保输出目录存在
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        
        # 收集所有拼音条目
        trie_data = {}
        
        def collect_words(node, current_pinyin=""):
            """递归收集所有词语"""
            if node.words:
                if current_pinyin not in trie_data:
                    trie_data[current_pinyin] = []
                for word_item in node.words:
                    trie_data[current_pinyin].append({
                        'word': word_item.word,
                        'frequency': word_item.frequency
                    })
            
            for char, child_node in node.children.items():
                collect_words(child_node, current_pinyin + char)
        
        collect_words(trie.root)
        
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
        
        # 验证文件
        file_size = os.path.getsize(output_path)
        print(f"文件保存成功！文件大小: {file_size} 字节 ({file_size/1024/1024:.2f} MB)")
        
        return True
        
    except Exception as e:
        print(f"错误：保存文件失败 - {e}")
        return False

def verify_trie_file(file_path: str) -> bool:
    """验证生成的Trie文件是否可用 - 版本3格式"""
    print(f"正在验证Trie文件: {file_path}")
    
    try:
        with open(file_path, 'rb') as f:
            # 读取版本号
            version_bytes = f.read(4)
            if len(version_bytes) != 4:
                print("错误：文件格式不正确，无法读取版本号")
                return False
            
            version = struct.unpack('<i', version_bytes)[0]
            print(f"文件版本号: {version}")
            
            if version != 3:
                print(f"错误：不支持的版本号 {version}，期望版本3")
                return False
            
            # 读取条目数量
            count_bytes = f.read(4)
            if len(count_bytes) != 4:
                print("错误：无法读取条目数量")
                return False
            
            count = struct.unpack('<i', count_bytes)[0]
            print(f"拼音条目数量: {count}")
            
            if count <= 0:
                print("错误：条目数量无效")
                return False
            
            print(f"验证成功！文件包含 {count} 个拼音条目")
            return True
            
    except Exception as e:
        print(f"错误：验证文件失败 - {e}")
        return False

def main():
    """主函数"""
    print("=" * 60)
    print("神迹输入法 - Base词典Trie预编译构建工具")
    print("=" * 60)
    
    # 文件路径
    input_file = "app/src/main/assets/cn_dicts/base.dict.yaml"
    output_file = "app/src/main/assets/trie/base_trie.dat"
    
    # 检查输入文件是否存在
    if not os.path.exists(input_file):
        print(f"错误：输入文件不存在 - {input_file}")
        return 1
    
    try:
        # 步骤1：解析词典文件
        entries = parse_dict_file(input_file)
        if not entries:
            print("错误：无法解析词典文件或文件为空")
            return 1
        
        # 步骤2：筛选高频词语（50%）
        filtered_entries = filter_top_frequency_words(entries, 0.5)
        if not filtered_entries:
            print("错误：筛选后没有词语")
            return 1
        
        # 步骤3：构建Trie树
        trie = build_trie(filtered_entries)
        if trie.is_empty():
            print("错误：构建的Trie树为空")
            return 1
        
        # 步骤4：保存Trie树
        if not save_trie_to_file(trie, output_file):
            print("错误：保存Trie文件失败")
            return 1
        
        # 步骤5：验证生成的文件
        if not verify_trie_file(output_file):
            print("错误：生成的文件验证失败")
            return 1
        
        print("\n" + "=" * 60)
        print("✅ Base词典Trie预编译文件构建成功！")
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