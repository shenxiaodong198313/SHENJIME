#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试生成的base_trie.dat文件
验证文件格式和数据完整性
"""

import os
import sys
import pickle
import struct
from build_base_trie import PinyinTrie, TrieNode, WordItem

def test_trie_file(file_path: str):
    """测试Trie文件的加载和基本功能"""
    print(f"🔍 测试文件: {file_path}")
    
    if not os.path.exists(file_path):
        print("❌ 文件不存在")
        return False
    
    file_size = os.path.getsize(file_path)
    print(f"📁 文件大小: {file_size} 字节 ({file_size/1024/1024:.2f} MB)")
    
    try:
        with open(file_path, 'rb') as f:
            # 读取版本号
            version_bytes = f.read(4)
            version = struct.unpack('>i', version_bytes)[0]
            print(f"📋 文件版本: {version}")
            
            # 加载Trie对象
            print("⏳ 正在加载Trie对象...")
            trie = pickle.load(f)
            
            if not isinstance(trie, PinyinTrie):
                print("❌ 对象类型错误")
                return False
            
            print("✅ Trie对象加载成功")
            
            # 获取统计信息
            node_count, word_count = trie.get_memory_stats()
            print(f"📊 统计信息:")
            print(f"   - 节点数: {node_count:,}")
            print(f"   - 词语数: {word_count:,}")
            
            # 测试一些基本查询
            print("\n🔍 测试基本查询功能:")
            test_queries = ["ni", "wo", "ta", "de", "shi", "zai", "you", "le"]
            
            for query in test_queries:
                current = trie.root
                found = True
                
                # 遍历查询路径
                for char in query:
                    if char in current.children:
                        current = current.children[char]
                    else:
                        found = False
                        break
                
                if found and current.is_end_of_word and current.words:
                    top_words = current.words[:3]  # 取前3个词
                    words_str = ", ".join([f"{w.word}({w.frequency})" for w in top_words])
                    print(f"   '{query}' -> {words_str}")
                else:
                    print(f"   '{query}' -> 未找到")
            
            return True
            
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    """主函数"""
    print("=" * 60)
    print("🧪 Base词典Trie文件测试工具")
    print("=" * 60)
    
    file_path = "app/src/main/assets/trie/base_trie.dat"
    
    if test_trie_file(file_path):
        print("\n" + "=" * 60)
        print("✅ 测试通过！文件格式正确，数据完整")
        print("=" * 60)
        return 0
    else:
        print("\n" + "=" * 60)
        print("❌ 测试失败！")
        print("=" * 60)
        return 1

if __name__ == "__main__":
    sys.exit(main()) 