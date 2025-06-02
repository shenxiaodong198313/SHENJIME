@echo off
echo ========================================
echo 神迹输入法实时日志监控
echo ========================================
echo 开始时间: %date% %time%
echo ========================================

REM 清空日志缓存
adb logcat -c

echo 正在监控以下内容:
echo - 神迹输入法所有日志
echo - AI引擎相关日志  
echo - 系统崩溃和异常
echo - 内存和性能问题
echo ========================================
echo.

REM 启动实时日志监控
adb logcat -v threadtime | findstr /i "shenji aikeyboard gemma3 crash fatal exception error outofmemory anr" 