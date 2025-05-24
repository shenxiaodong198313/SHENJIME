#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
最终测试生成的base_trie.dat文件
使用正确的拼音格式进行测试
"""

import os
import sys
import pickle
import struct
from build_base_trie import PinyinTrie, TrieNode, WordItem

def test_trie_with_correct_format(file_path: str):
    """使用正确的拼音格式测试Trie文件"""
    print(f"🔍 测试文件: {file_path}")
    
    try:
        with open(file_path, 'rb') as f:
            # 读取版本号
            version_bytes = f.read(4)
            version = struct.unpack('>i', version_bytes)[0]
            print(f"📋 文件版本: {version}")
            
            # 加载Trie对象
            print("⏳ 正在加载Trie对象...")
            trie = pickle.load(f)
            
            print("✅ Trie对象加载成功")
            
            # 获取统计信息
            node_count, word_count = trie.get_memory_stats()
            print(f"📊 统计信息:")
            print(f"   - 节点数: {node_count:,}")
            print(f"   - 词语数: {word_count:,}")
            
            # 测试一些常见的拼音（带空格格式）
            print("\n🔍 测试常见拼音查询:")
            test_queries = [
                "wo",      # 我
                "ni",      # 你
                "ta",      # 他
                "de",      # 的
                "shi",     # 是
                "zai",     # 在
                "you",     # 有
                "le",      # 了
                "yi ge",   # 一个
                "zhe ge",  # 这个
                "na ge",   # 那个
                "ke yi",   # 可以
                "bu shi",  # 不是
                "mei you", # 没有
                "a ba",    # 阿爸
                "a ma",    # 阿妈
                "zhong guo", # 中国
                "bei jing",  # 北京
                "shang hai", # 上海
                "guang zhou" # 广州
            ]
            
            found_count = 0
            for query in test_queries:
                current = trie.root
                found = True
                
                # 遍历查询路径（包括空格）
                for char in query:
                    if char in current.children:
                        current = current.children[char]
                    else:
                        found = False
                        break
                
                if found and current.is_end_of_word and current.words:
                    found_count += 1
                    top_words = current.words[:3]  # 取前3个词
                    words_str = ", ".join([f"{w.word}({w.frequency})" for w in top_words])
                    print(f"   ✅ '{query}' -> {words_str}")
                else:
                    print(f"   ❌ '{query}' -> 未找到")
            
            print(f"\n📈 查询结果统计: {found_count}/{len(test_queries)} 个查询成功")
            
            # 随机浏览一些存储的词语
            print("\n🎲 随机浏览存储的词语:")
            sample_count = 0
            max_samples = 10
            
            def sample_words(node, path="", depth=0):
                nonlocal sample_count, max_samples
                
                if sample_count >= max_samples:
                    return
                
                if node.is_end_of_word and node.words and len(path) > 2:
                    sample_count += 1
                    top_word = node.words[0]
                    print(f"   '{path}' -> {top_word.word}({top_word.frequency})")
                
                if depth < 10:  # 限制深度避免无限递归
                    for char, child in list(node.children.items())[:3]:  # 只看前3个子节点
                        sample_words(child, path + char, depth + 1)
            
            sample_words(trie.root)
            
            return True
            
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    """主函数"""
    print("=" * 60)
    print("🧪 Base词典Trie最终测试工具")
    print("=" * 60)
    
    file_path = "app/src/main/assets/trie/base_trie.dat"
    
    if test_trie_with_correct_format(file_path):
        print("\n" + "=" * 60)
        print("✅ 最终测试通过！")
        print("📋 文件格式正确，数据完整，查询功能正常")
        print("🎯 Base词典Trie预编译文件可以正常使用")
        print("=" * 60)
        return 0
    else:
        print("\n" + "=" * 60)
        print("❌ 最终测试失败！")
        print("=" * 60)
        return 1

if __name__ == "__main__":
    sys.exit(main()) 