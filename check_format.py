#!/usr/bin/env python3
import struct

with open('app/src/main/assets/trie/base_trie.dat', 'rb') as f:
    # 读取前16字节
    data = f.read(16)
    print('前16字节:', [hex(b) for b in data])
    
    # 尝试不同的解释
    print('前4字节作为大端序int:', struct.unpack('>I', data[:4])[0])
    print('前4字节作为小端序int:', struct.unpack('<I', data[:4])[0])
    
    # 检查是否是Java序列化格式
    if data[:2] == b'\xac\xed':
        print('这是Java序列化格式')
    else:
        print('这不是Java序列化格式')
        
    # 检查是否是我们的自定义格式
    version = struct.unpack('>I', data[:4])[0]
    if version == 3:
        count = struct.unpack('>I', data[4:8])[0]
        print(f'自定义格式：版本={version}, 条目数={count}')
    else:
        print('未知格式') 