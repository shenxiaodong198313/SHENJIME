#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import struct
import os

def verify_chars_trie():
    """验证chars_trie.dat文件的格式和内容"""
    file_path = 'app/src/main/assets/trie/chars_trie.dat'
    
    if not os.path.exists(file_path):
        print(f"文件不存在: {file_path}")
        return False
    
    file_size = os.path.getsize(file_path)
    print(f"文件大小: {file_size} 字节 ({file_size / 1024:.2f} KB)")
    
    try:
        with open(file_path, 'rb') as f:
            # 读取版本号
            version_bytes = f.read(4)
            if len(version_bytes) != 4:
                print("无法读取版本号")
                return False
            
            version = struct.unpack('>i', version_bytes)[0]
            print(f"文件版本: {version}")
            
            # 读取拼音条目数量
            count_bytes = f.read(4)
            if len(count_bytes) != 4:
                print("无法读取拼音条目数量")
                return False
            
            count = struct.unpack('>i', count_bytes)[0]
            print(f"拼音条目数: {count}")
            
            if count <= 0:
                print("拼音条目数量异常")
                return False
            
            # 验证前几个拼音条目
            valid_entries = 0
            for i in range(min(5, count)):
                try:
                    # 读取拼音长度
                    pinyin_len_bytes = f.read(4)
                    if len(pinyin_len_bytes) != 4:
                        print(f"第{i+1}个条目: 无法读取拼音长度")
                        break
                    
                    pinyin_len = struct.unpack('>i', pinyin_len_bytes)[0]
                    if pinyin_len <= 0 or pinyin_len > 100:
                        print(f"第{i+1}个条目: 拼音长度异常 {pinyin_len}")
                        break
                    
                    # 读取拼音内容
                    pinyin_bytes = f.read(pinyin_len)
                    if len(pinyin_bytes) != pinyin_len:
                        print(f"第{i+1}个条目: 无法读取拼音内容")
                        break
                    
                    pinyin = pinyin_bytes.decode('utf-8')
                    
                    # 读取词语数量
                    word_count_bytes = f.read(4)
                    if len(word_count_bytes) != 4:
                        print(f"第{i+1}个条目: 无法读取词语数量")
                        break
                    
                    word_count = struct.unpack('>i', word_count_bytes)[0]
                    
                    print(f"第{i+1}个条目: 拼音='{pinyin}', 词语数={word_count}")
                    
                    # 跳过词语内容（简化验证）
                    for j in range(word_count):
                        # 读取词语长度
                        word_len_bytes = f.read(4)
                        if len(word_len_bytes) != 4:
                            break
                        word_len = struct.unpack('>i', word_len_bytes)[0]
                        
                        # 跳过词语内容
                        f.seek(f.tell() + word_len)
                        
                        # 跳过词频
                        f.seek(f.tell() + 4)
                    
                    valid_entries += 1
                    
                except Exception as e:
                    print(f"第{i+1}个条目解析失败: {e}")
                    break
            
            print(f"成功验证了 {valid_entries} 个拼音条目")
            
            if valid_entries > 0:
                print("✅ chars_trie.dat 文件格式正确")
                return True
            else:
                print("❌ chars_trie.dat 文件格式错误")
                return False
                
    except Exception as e:
        print(f"验证过程中发生错误: {e}")
        return False

if __name__ == "__main__":
    verify_chars_trie() 