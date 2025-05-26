#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
神迹输入法 v/ü转换功能测试脚本
"""

import subprocess
import time
import sys

def run_adb_command(command):
    """执行ADB命令"""
    try:
        result = subprocess.run(command, shell=True, capture_output=True, text=True, encoding='utf-8')
        return result.stdout, result.stderr, result.returncode
    except Exception as e:
        print(f"执行命令失败: {e}")
        return "", str(e), 1

def test_vu_conversion():
    """测试v/ü转换功能"""
    print("🔧 神迹输入法 v/ü转换功能测试")
    print("=" * 50)
    
    # 启动测试Activity
    print("📱 启动测试Activity...")
    cmd = 'adb shell am start -n com.shenji.aikeyboard/.ui.SmartPinyinMvpTestActivity'
    stdout, stderr, code = run_adb_command(cmd)
    
    if code != 0:
        print(f"❌ 启动Activity失败: {stderr}")
        return False
    
    print("✅ Activity启动成功")
    time.sleep(2)
    
    # 测试用例
    test_cases = [
        ("lv", "绿"),
        ("nv", "女"),
        ("lvse", "绿色"),
        ("nvhai", "女孩"),
        ("jv", "居"),
        ("qv", "去"),
        ("xv", "虚"),
        ("yv", "鱼")
    ]
    
    print("\n🧪 开始测试v/ü转换...")
    
    for input_text, expected_char in test_cases:
        print(f"\n测试输入: '{input_text}' (期望包含: '{expected_char}')")
        
        # 清空logcat
        run_adb_command("adb logcat -c")
        
        # 模拟输入测试
        # 这里我们通过Intent传递测试数据
        cmd = f'adb shell am start -n com.shenji.aikeyboard/.ui.SmartPinyinMvpTestActivity --es test_input "{input_text}"'
        stdout, stderr, code = run_adb_command(cmd)
        
        time.sleep(1)
        
        # 获取日志
        cmd = 'adb logcat -d -s "SmartPinyinEngine:D"'
        stdout, stderr, code = run_adb_command(cmd)
        
        if "v转换" in stdout:
            print("✅ v转换日志已记录")
        else:
            print("⚠️  未发现v转换日志")
        
        if "查询变体" in stdout:
            print("✅ 查询变体生成成功")
        else:
            print("⚠️  未发现查询变体日志")
    
    print("\n📊 测试完成")
    return True

def main():
    """主函数"""
    print("神迹输入法 v/ü转换功能测试工具")
    print("作者: AI助手")
    print("版本: 1.0")
    print()
    
    # 检查ADB连接
    print("🔍 检查ADB连接...")
    stdout, stderr, code = run_adb_command("adb devices")
    
    if code != 0 or "device" not in stdout:
        print("❌ 未检测到Android设备，请确保:")
        print("  1. 设备已连接并开启USB调试")
        print("  2. ADB驱动已正确安装")
        print("  3. 已授权ADB调试")
        return 1
    
    print("✅ ADB连接正常")
    
    # 检查应用是否已安装
    print("📦 检查应用安装状态...")
    cmd = "adb shell pm list packages | findstr com.shenji.aikeyboard"
    stdout, stderr, code = run_adb_command(cmd)
    
    if "com.shenji.aikeyboard" not in stdout:
        print("❌ 神迹输入法未安装，请先安装应用")
        return 1
    
    print("✅ 应用已安装")
    
    # 执行测试
    success = test_vu_conversion()
    
    if success:
        print("\n🎉 测试执行完成！")
        print("请查看手机上的测试Activity界面验证结果")
        return 0
    else:
        print("\n❌ 测试执行失败")
        return 1

if __name__ == "__main__":
    sys.exit(main()) 