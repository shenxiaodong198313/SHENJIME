#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
神迹输入法 - 实时日志监控工具
用于观察应用启动过程中的详细日志，特别是单字词典加载过程
"""

import subprocess
import sys
import time
import re
from datetime import datetime
import threading
import signal

class AppLogMonitor:
    def __init__(self):
        self.package_name = "com.shenji.aikeyboard"
        self.process = None
        self.running = False
        self.start_time = None
        
        # 关键词过滤
        self.keywords = [
            "单字词典", "chars", "Trie", "内存", "Memory", "加载", "loading",
            "SplashActivity", "MainActivity", "TrieManager", "OutOfMemoryError",
            "GC", "垃圾回收", "启动", "初始化"
        ]
        
        # 统计信息
        self.stats = {
            "total_logs": 0,
            "error_logs": 0,
            "memory_logs": 0,
            "trie_logs": 0
        }
    
    def clear_logcat(self):
        """清空logcat缓冲区"""
        try:
            subprocess.run(["adb", "logcat", "-c"], check=True, capture_output=True)
            print("✅ Logcat缓冲区已清空")
        except subprocess.CalledProcessError as e:
            print(f"❌ 清空logcat失败: {e}")
    
    def start_monitoring(self):
        """开始监控日志"""
        self.running = True
        self.start_time = datetime.now()
        
        print("=" * 80)
        print("🚀 神迹输入法实时日志监控")
        print(f"📱 包名: {self.package_name}")
        print(f"⏰ 开始时间: {self.start_time.strftime('%H:%M:%S')}")
        print("=" * 80)
        print("📋 监控关键词:", ", ".join(self.keywords[:5]), "...")
        print("=" * 80)
        
        try:
            # 启动logcat进程
            cmd = [
                "adb", "logcat", 
                "-v", "time",  # 显示时间戳
                "--pid=$(adb shell pidof com.shenji.aikeyboard)"  # 只显示应用进程的日志
            ]
            
            # 如果pidof不工作，使用包名过滤
            fallback_cmd = [
                "adb", "logcat",
                "-v", "time",
                "|", "grep", self.package_name
            ]
            
            try:
                self.process = subprocess.Popen(
                    ["adb", "logcat", "-v", "time"],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    universal_newlines=True,
                    encoding='utf-8',
                    errors='ignore',
                    bufsize=1
                )
            except Exception as e:
                print(f"❌ 启动logcat失败: {e}")
                return
            
            print("🔍 开始监控日志... (按Ctrl+C停止)")
            print("-" * 80)
            
            # 读取日志行
            for line in iter(self.process.stdout.readline, ''):
                if not self.running:
                    break
                
                line = line.strip()
                if line:
                    self.process_log_line(line)
            
        except KeyboardInterrupt:
            print("\n\n⏹️  用户中断监控")
        except Exception as e:
            print(f"\n❌ 监控过程出错: {e}")
        finally:
            self.stop_monitoring()
    
    def process_log_line(self, line):
        """处理单行日志"""
        self.stats["total_logs"] += 1
        
        # 检查是否包含关键词或包名
        if any(keyword.lower() in line.lower() for keyword in self.keywords) or self.package_name in line:
            
            # 提取时间戳
            time_match = re.match(r'(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})', line)
            timestamp = time_match.group(1) if time_match else "??:??:??"
            
            # 计算相对时间
            relative_time = ""
            if self.start_time:
                try:
                    current_time = datetime.now()
                    elapsed = (current_time - self.start_time).total_seconds()
                    relative_time = f"[+{elapsed:.1f}s]"
                except:
                    pass
            
            # 分类日志
            log_type = "📝"
            if "error" in line.lower() or "exception" in line.lower() or "failed" in line.lower():
                log_type = "❌"
                self.stats["error_logs"] += 1
            elif "memory" in line.lower() or "内存" in line.lower() or "gc" in line.lower():
                log_type = "🧠"
                self.stats["memory_logs"] += 1
            elif "trie" in line.lower() or "词典" in line.lower() or "加载" in line.lower():
                log_type = "📚"
                self.stats["trie_logs"] += 1
            elif "success" in line.lower() or "完成" in line.lower() or "成功" in line.lower():
                log_type = "✅"
            
            # 高亮显示重要信息
            highlighted_line = line
            for keyword in ["单字词典", "chars_trie", "OutOfMemoryError", "加载完成", "加载失败"]:
                if keyword in line:
                    highlighted_line = line.replace(keyword, f"🔥{keyword}🔥")
            
            # 输出格式化日志
            print(f"{log_type} {timestamp} {relative_time} {highlighted_line}")
            
            # 特殊处理：如果是内存相关日志，额外显示统计
            if "内存" in line or "Memory" in line:
                self.extract_memory_info(line)
    
    def extract_memory_info(self, line):
        """提取内存信息"""
        # 尝试提取内存数值
        memory_pattern = r'(\d+(?:\.\d+)?)\s*MB'
        matches = re.findall(memory_pattern, line)
        if matches:
            print(f"    💾 检测到内存数值: {', '.join(matches)} MB")
    
    def stop_monitoring(self):
        """停止监控"""
        self.running = False
        if self.process:
            self.process.terminate()
            self.process.wait()
        
        # 显示统计信息
        print("\n" + "=" * 80)
        print("📊 监控统计")
        print("=" * 80)
        print(f"总日志行数: {self.stats['total_logs']}")
        print(f"错误日志: {self.stats['error_logs']}")
        print(f"内存相关: {self.stats['memory_logs']}")
        print(f"词典相关: {self.stats['trie_logs']}")
        
        if self.start_time:
            duration = (datetime.now() - self.start_time).total_seconds()
            print(f"监控时长: {duration:.1f}秒")
        print("=" * 80)

def signal_handler(sig, frame):
    """处理Ctrl+C信号"""
    print("\n🛑 接收到中断信号，正在停止监控...")
    sys.exit(0)

def main():
    # 注册信号处理器
    signal.signal(signal.SIGINT, signal_handler)
    
    monitor = AppLogMonitor()
    
    print("🔧 准备监控环境...")
    
    # 检查ADB连接
    try:
        result = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True)
        if "device" not in result.stdout:
            print("❌ 未检测到Android设备，请确保设备已连接并启用USB调试")
            return
        print("✅ Android设备连接正常")
    except subprocess.CalledProcessError:
        print("❌ ADB命令失败，请检查ADB是否正确安装")
        return
    
    # 清空日志缓冲区
    monitor.clear_logcat()
    
    print("\n�� 现在请启动神迹输入法应用...")
    print("💡 提示：启动应用后，此工具将显示详细的加载过程")
    print("⚠️  特别关注单字词典加载时间和内存使用情况")
    
    # 等待用户确认
    input("\n按回车键开始监控...")
    
    # 开始监控
    monitor.start_monitoring()

if __name__ == "__main__":
    main() 