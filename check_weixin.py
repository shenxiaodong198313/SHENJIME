#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import struct
import sys

def read_trie_file(filename):
    try:
        with open(filename, 'rb') as f:
            # 读取版本号（大端序）
            version_bytes = f.read(4)
            if len(version_bytes) < 4:
                print('无法读取版本号')
                return
            
            version = struct.unpack('>I', version_bytes)[0]
            print(f'文件版本: {version}')
            
            if version != 3:
                print(f'不支持的版本: {version}')
                return
            
            # 读取拼音条目数量（大端序）
            count_bytes = f.read(4)
            if len(count_bytes) < 4:
                print('无法读取拼音条目数量')
                return
            
            count = struct.unpack('>I', count_bytes)[0]
            print(f'拼音条目数量: {count}')
            
            # 搜索包含wei的词条
            found_wei = []
            found_weixin = []
            
            for i in range(min(count, 5000)):  # 检查前5000个
                try:
                    # 读取拼音长度（大端序）
                    pinyin_len_bytes = f.read(4)
                    if len(pinyin_len_bytes) < 4:
                        break
                    pinyin_len = struct.unpack('>I', pinyin_len_bytes)[0]
                    
                    # 读取拼音
                    pinyin_bytes = f.read(pinyin_len)
                    if len(pinyin_bytes) < pinyin_len:
                        break
                    pinyin = pinyin_bytes.decode('utf-8')
                    
                    # 读取词语数量（大端序）
                    word_count_bytes = f.read(4)
                    if len(word_count_bytes) < 4:
                        break
                    word_count = struct.unpack('>I', word_count_bytes)[0]
                    
                    # 读取每个词语
                    for j in range(word_count):
                        # 读取词语长度（大端序）
                        word_len_bytes = f.read(4)
                        if len(word_len_bytes) < 4:
                            break
                        word_len = struct.unpack('>I', word_len_bytes)[0]
                        
                        # 读取词语
                        word_bytes = f.read(word_len)
                        if len(word_bytes) < word_len:
                            break
                        word = word_bytes.decode('utf-8')
                        
                        # 读取词频（大端序）
                        freq_bytes = f.read(4)
                        if len(freq_bytes) < 4:
                            break
                        frequency = struct.unpack('>I', freq_bytes)[0]
                        
                        # 检查是否是weixin
                        if pinyin.lower() == 'weixin':
                            found_weixin.append((pinyin, word, frequency))
                        
                        # 检查是否包含wei
                        if 'wei' in pinyin.lower() and len(found_wei) < 50:
                            found_wei.append((pinyin, word, frequency))
                        
                        # 检查是否包含微信
                        if '微信' in word:
                            found_weixin.append((pinyin, word, frequency))
                            
                except Exception as e:
                    print(f'读取第{i}个拼音条目时出错: {e}')
                    break
            
            print(f'\n找到{len(found_weixin)}个weixin/微信词条:')
            for pinyin, word, freq in found_weixin:
                print(f'  {pinyin} -> {word} ({freq})')
            
            print(f'\n找到{len(found_wei)}个包含wei的词条:')
            for pinyin, word, freq in found_wei[:15]:
                print(f'  {pinyin} -> {word} ({freq})')
                
    except Exception as e:
        print(f'读取文件失败: {e}')

if __name__ == '__main__':
    read_trie_file('app/src/main/assets/trie/base_trie.dat') 