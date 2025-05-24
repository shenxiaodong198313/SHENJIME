#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
调试Trie树，查看实际存储的数据格式
"""

import os
import sys
import pickle
import struct
from build_base_trie import PinyinTrie, TrieNode, WordItem

def debug_trie_structure(file_path: str):
    """调试Trie树结构"""
    print(f"🔍 调试文件: {file_path}")
    
    try:
        with open(file_path, 'rb') as f:
            # 读取版本号
            version_bytes = f.read(4)
            version = struct.unpack('>i', version_bytes)[0]
            print(f"📋 文件版本: {version}")
            
            # 加载Trie对象
            trie = pickle.load(f)
            
            print("🔍 查看根节点的子节点:")
            root_children = list(trie.root.children.keys())[:20]  # 只显示前20个
            print(f"   根节点子节点: {root_children}")
            
            # 查看一些具体的路径
            print("\n🔍 查看具体的拼音路径:")
            
            def explore_path(node, path, max_depth=3, current_depth=0):
                """递归探索路径"""
                if current_depth >= max_depth:
                    return
                
                for char, child_node in list(node.children.items())[:5]:  # 只看前5个
                    new_path = path + char
                    if child_node.is_end_of_word and child_node.words:
                        top_word = child_node.words[0]
                        print(f"   '{new_path}' -> {top_word.word}({top_word.frequency})")
                    
                    if current_depth < max_depth - 1:
                        explore_path(child_node, new_path, max_depth, current_depth + 1)
            
            explore_path(trie.root, "", max_depth=4)
            
            # 查看一些常见拼音
            print("\n🔍 测试常见拼音:")
            common_pinyin = ["wo", "ni", "ta", "de", "shi", "zai", "you", "le", "a", "ai", "an"]
            
            for pinyin in common_pinyin:
                current = trie.root
                found = True
                
                for char in pinyin:
                    if char in current.children:
                        current = current.children[char]
                    else:
                        found = False
                        break
                
                if found and current.is_end_of_word and current.words:
                    top_words = current.words[:3]
                    words_str = ", ".join([f"{w.word}({w.frequency})" for w in top_words])
                    print(f"   '{pinyin}' -> {words_str}")
                elif found:
                    print(f"   '{pinyin}' -> 路径存在但不是完整词")
                else:
                    print(f"   '{pinyin}' -> 未找到")
            
            return True
            
    except Exception as e:
        print(f"❌ 调试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    """主函数"""
    print("=" * 60)
    print("🐛 Base词典Trie调试工具")
    print("=" * 60)
    
    file_path = "app/src/main/assets/trie/base_trie.dat"
    debug_trie_structure(file_path)

if __name__ == "__main__":
    main() 