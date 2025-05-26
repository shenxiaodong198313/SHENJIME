#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试lv输入功能
"""

import subprocess
import time

def run_adb_command(command):
    """执行ADB命令"""
    try:
        result = subprocess.run(command, shell=True, capture_output=True, text=True, encoding='utf-8')
        return result.stdout, result.stderr, result.returncode
    except Exception as e:
        print(f"执行命令失败: {e}")
        return "", str(e), 1

def test_lv_input():
    """测试lv输入"""
    print("🧪 测试lv输入功能")
    print("=" * 40)
    
    # 启动测试Activity
    print("📱 启动测试Activity...")
    cmd = 'adb shell am start -n com.shenji.aikeyboard/.ui.SmartPinyinMvpTestActivity'
    stdout, stderr, code = run_adb_command(cmd)
    
    if code != 0:
        print(f"❌ 启动Activity失败: {stderr}")
        return False
    
    print("✅ Activity启动成功")
    time.sleep(2)
    
    # 清空logcat
    print("🧹 清空logcat...")
    run_adb_command("adb logcat -c")
    
    print("\n📝 请在手机上的测试界面输入 'lv' 并查看候选词")
    print("⏰ 等待10秒钟让您进行测试...")
    time.sleep(10)
    
    # 获取日志
    print("\n📋 获取日志...")
    cmd = 'adb logcat -d -s "SmartPinyinEngine:D" "TrieManager:D"'
    stdout, stderr, code = run_adb_command(cmd)
    
    if stdout:
        print("✅ 找到相关日志:")
        print("-" * 40)
        print(stdout)
        print("-" * 40)
        
        # 分析日志
        if "v->ü转换" in stdout:
            print("✅ v到ü转换功能正常")
        else:
            print("⚠️  未发现v到ü转换日志")
            
        if "输入变体" in stdout:
            print("✅ 输入变体生成功能正常")
        else:
            print("⚠️  未发现输入变体日志")
            
        if "查询变体" in stdout:
            print("✅ 查询变体功能正常")
        else:
            print("⚠️  未发现查询变体日志")
            
        if "Trie查询" in stdout:
            print("✅ Trie查询功能正常")
        else:
            print("⚠️  未发现Trie查询日志")
            
    else:
        print("❌ 未找到相关日志")
    
    return True

if __name__ == "__main__":
    test_lv_input() 