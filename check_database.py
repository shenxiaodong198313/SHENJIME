#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
检查神迹输入法数据库中的拼音存储格式
"""

import subprocess
import sys

def run_adb_command(command):
    """执行ADB命令"""
    try:
        result = subprocess.run(command, shell=True, capture_output=True, text=True, encoding='utf-8')
        return result.stdout, result.stderr, result.returncode
    except Exception as e:
        print(f"执行命令失败: {e}")
        return "", str(e), 1

def check_database():
    """检查数据库中的拼音存储格式"""
    print("🔍 检查神迹输入法数据库中的拼音存储格式")
    print("=" * 60)
    
    # 检查数据库文件
    print("📁 检查数据库文件...")
    cmd = 'adb shell "run-as com.shenji.aikeyboard find /data/data/com.shenji.aikeyboard -name \'*.realm\' -type f"'
    stdout, stderr, code = run_adb_command(cmd)
    
    if code != 0:
        print(f"❌ 查找数据库文件失败: {stderr}")
        return False
    
    print(f"✅ 找到数据库文件:\n{stdout}")
    
    # 测试查询"绿"字相关的数据
    test_queries = [
        ("绿字查询", "word = '绿'"),
        ("lv拼音查询", "pinyin LIKE '%lv%'"),
        ("lü拼音查询", "pinyin LIKE '%lü%'"),
        ("lu拼音查询", "pinyin LIKE '%lu%'"),
        ("lue拼音查询", "pinyin LIKE '%lue%'"),
        ("女字查询", "word = '女'"),
        ("nv拼音查询", "pinyin LIKE '%nv%'"),
        ("nü拼音查询", "pinyin LIKE '%nü%'"),
        ("nu拼音查询", "pinyin LIKE '%nu%'"),
    ]
    
    print("\n🔍 执行数据库查询...")
    
    for query_name, where_clause in test_queries:
        print(f"\n📋 {query_name}:")
        
        # 使用sqlite3查询（如果是SQLite数据库）
        cmd = f'adb shell "run-as com.shenji.aikeyboard sqlite3 /data/data/com.shenji.aikeyboard/files/dictionaries/shenji_dict.realm \\"SELECT word, pinyin, frequency FROM Entry WHERE {where_clause} LIMIT 5;\\""'
        stdout, stderr, code = run_adb_command(cmd)
        
        if code == 0 and stdout.strip():
            print(f"✅ 查询结果:\n{stdout}")
        else:
            print(f"❌ 查询失败或无结果: {stderr}")
            
            # 尝试使用realm-cli（如果是Realm数据库）
            print("🔄 尝试Realm查询...")
            # 这里可以添加Realm特定的查询方法
    
    # 检查Trie文件
    print("\n📁 检查Trie文件...")
    cmd = 'adb shell "run-as com.shenji.aikeyboard find /data/data/com.shenji.aikeyboard -name \'*.dat\' -type f"'
    stdout, stderr, code = run_adb_command(cmd)
    
    if code == 0:
        print(f"✅ 找到Trie文件:\n{stdout}")
    else:
        print(f"❌ 查找Trie文件失败: {stderr}")
    
    # 检查assets中的Trie文件
    print("\n📁 检查assets中的Trie文件...")
    try:
        with open("app/src/main/assets/trie/base_trie.dat", "rb") as f:
            file_size = len(f.read())
            print(f"✅ base_trie.dat 文件大小: {file_size} 字节")
    except Exception as e:
        print(f"❌ 读取base_trie.dat失败: {e}")
    
    return True

def main():
    """主函数"""
    print("神迹输入法数据库检查工具")
    print("作者: AI助手")
    print("版本: 1.0")
    print()
    
    # 检查ADB连接
    print("🔍 检查ADB连接...")
    stdout, stderr, code = run_adb_command("adb devices")
    
    if code != 0 or "device" not in stdout:
        print("❌ 未检测到Android设备")
        return 1
    
    print("✅ ADB连接正常")
    
    # 检查应用是否已安装
    print("📦 检查应用安装状态...")
    cmd = "adb shell pm list packages | findstr com.shenji.aikeyboard"
    stdout, stderr, code = run_adb_command(cmd)
    
    if "com.shenji.aikeyboard" not in stdout:
        print("❌ 神迹输入法未安装")
        return 1
    
    print("✅ 应用已安装")
    
    # 执行检查
    success = check_database()
    
    if success:
        print("\n🎉 数据库检查完成！")
        return 0
    else:
        print("\n❌ 数据库检查失败")
        return 1

if __name__ == "__main__":
    sys.exit(main()) 