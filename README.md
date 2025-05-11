# 神迹输入法 (Shenji Input Method)

一个简单的Android输入法应用，支持Android 9到Android 15设备。

## 功能特点

- 基本英文输入键盘
- 支持大小写切换
- 内置崩溃日志系统
- 日志管理界面

## 技术栈

- 开发语言：Kotlin
- 数据库：Realm-Kotlin
- 日志框架：Timber

## 如何使用

1. 安装应用
2. 在Android设置->语言和输入法->管理键盘中启用"神迹输入法"
3. 切换到"神迹输入法"作为默认输入法

## 日志系统

应用会自动捕获崩溃并记录到外部存储：
- 日志路径：`Android/data/com.shenji.aikeyboard/files/logs/crash_log.txt`
- 可通过应用主界面的"查看日志"按钮访问并管理日志

## 开发环境要求

- Android Studio Arctic Fox或更高版本
- JDK 17
- Gradle 8.3+
- Android SDK 34+

## 编译构建

```bash
# 使用Gradle构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
``` 