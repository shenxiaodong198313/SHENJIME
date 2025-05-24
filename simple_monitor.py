#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
神迹输入法 - 简化启动监控工具
"""

import subprocess
import sys
import time
from datetime import datetime

def monitor_app_startup():
    print("🚀 神迹输入法启动监控")
    print("=" * 60)
    
    # 清空logcat
    try:
        subprocess.run(["adb", "logcat", "-c"], check=True)
        print("✅ 日志缓冲区已清空")
    except:
        print("❌ 清空日志失败")
    
    print("\n🎯 请现在启动神迹输入法应用...")
    print("⏰ 监控将持续60秒，关注单字词典加载过程")
    print("-" * 60)
    
    start_time = time.time()
    
    try:
        # 启动logcat，只显示应用相关日志
        process = subprocess.Popen(
            ["adb", "logcat", "-v", "time", "*:I"],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            encoding='utf-8',
            errors='ignore'
        )
        
        line_count = 0
        for line in process.stdout:
            if time.time() - start_time > 60:  # 60秒后停止
                break
                
            line = line.strip()
            if not line:
                continue
                
            # 过滤应用相关日志
            if any(keyword in line for keyword in [
                "com.shenji.aikeyboard", "SplashActivity", "MainActivity", 
                "TrieManager", "单字词典", "chars", "Trie", "内存", "Memory",
                "加载", "loading", "OutOfMemoryError"
            ]):
                elapsed = time.time() - start_time
                print(f"[+{elapsed:5.1f}s] {line}")
                line_count += 1
                
                # 如果检测到关键事件，高亮显示
                if any(keyword in line for keyword in ["单字词典", "chars_trie", "加载完成", "OutOfMemoryError"]):
                    print("    🔥 关键事件！")
        
        process.terminate()
        
    except KeyboardInterrupt:
        print("\n⏹️ 用户中断监控")
    except Exception as e:
        print(f"\n❌ 监控出错: {e}")
    
    print(f"\n📊 监控完成，共捕获 {line_count} 条相关日志")

if __name__ == "__main__":
    monitor_app_startup() 