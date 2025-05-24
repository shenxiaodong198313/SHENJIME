#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
验证Trie文件格式
"""

import struct
import sys
import os

def verify_trie_file(file_path):
    if not os.path.exists(file_path):
        print(f"❌ 文件不存在: {file_path}")
        return False
    
    file_size = os.path.getsize(file_path)
    print(f"📁 文件: {file_path}")
    print(f"📏 大小: {file_size} 字节 ({file_size/1024:.1f} KB)")
    
    try:
        with open(file_path, 'rb') as f:
            # 读取版本号
            version_data = f.read(4)
            if len(version_data) < 4:
                print("❌ 文件太小，无法读取版本号")
                return False
            
            version = struct.unpack('<I', version_data)[0]
            print(f"🔢 版本号: {version}")
            
            if version == 3:
                print("✅ 版本号正确 (版本3)")
                
                # 读取拼音条目数
                pinyin_count_data = f.read(4)
                if len(pinyin_count_data) < 4:
                    print("❌ 无法读取拼音条目数")
                    return False
                
                pinyin_count = struct.unpack('<I', pinyin_count_data)[0]
                print(f"📝 拼音条目数: {pinyin_count}")
                
                if pinyin_count > 1000:
                    print("⚠️  拼音条目数异常，可能文件损坏")
                    return False
                
                print("✅ 文件格式验证通过")
                return True
            else:
                print(f"❌ 版本号错误: {version} (期望: 3)")
                print("🔍 文件头16字节内容:")
                f.seek(0)
                header = f.read(16)
                for i, byte in enumerate(header):
                    print(f"  字节{i}: {byte} (0x{byte:02x})")
                return False
                
    except Exception as e:
        print(f"❌ 验证过程出错: {e}")
        return False

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("用法: python verify_trie_file.py <文件路径>")
        sys.exit(1)
    
    file_path = sys.argv[1]
    success = verify_trie_file(file_path)
    sys.exit(0 if success else 1) 