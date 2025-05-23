# Cursor Cygwin 终端配置完成

## 配置概述

已成功将 Cygwin 配置为 Cursor 的默认终端，配置包括：

- **终端路径**: `C:\cygwin64\bin\mintty.exe`
- **启动参数**: `-i /Cygwin-Terminal.ico -`
- **图标**: terminal-bash

## 配置文件位置

### 1. 项目级配置
- **路径**: `.vscode/settings.json`
- **作用范围**: 仅当前项目

### 2. 全局用户配置
- **路径**: `C:\Users\15811\AppData\Roaming\Cursor\User\settings.json`
- **作用范围**: 所有 Cursor 项目

## 配置内容

```json
{
    "terminal.integrated.defaultProfile.windows": "Cygwin",
    "terminal.integrated.profiles.windows": {
        "Cygwin": {
            "path": "C:\\cygwin64\\bin\\mintty.exe",
            "args": ["-i", "/Cygwin-Terminal.ico", "-"],
            "icon": "terminal-bash"
        },
        "PowerShell": {
            "source": "PowerShell",
            "icon": "terminal-powershell"
        },
        "Command Prompt": {
            "path": [
                "${env:windir}\\Sysnative\\cmd.exe",
                "${env:windir}\\System32\\cmd.exe"
            ],
            "args": [],
            "icon": "terminal-cmd"
        },
        "Git Bash": {
            "source": "Git Bash"
        }
    },
    "terminal.integrated.automationProfile.windows": {
        "path": "C:\\cygwin64\\bin\\mintty.exe",
        "args": ["-i", "/Cygwin-Terminal.ico", "-"]
    }
}
```

## 验证步骤

1. ✅ 验证 Cygwin 安装路径存在
2. ✅ 创建项目级配置文件
3. ✅ 创建全局用户配置文件
4. ✅ 配置文件内容验证成功

## 使用说明

### 重启 Cursor
配置完成后，请重启 Cursor 以使配置生效。

### 打开终端
- 使用快捷键 `Ctrl + `` (反引号) 打开终端
- 或通过菜单：Terminal → New Terminal

### 切换终端类型
如需临时使用其他终端：
1. 点击终端右上角的下拉箭头
2. 选择所需的终端类型（PowerShell、Command Prompt、Git Bash）

## 配置优势

1. **全局生效**: 配置将应用到所有 Cursor 项目
2. **保留选择**: 仍可选择使用其他终端类型
3. **自动化支持**: 包含自动化脚本的终端配置
4. **图标识别**: 使用合适的图标便于识别

## 故障排除

如果终端无法正常启动：

1. **检查路径**: 确认 `C:\cygwin64\bin\mintty.exe` 文件存在
2. **检查权限**: 确保有权限访问 Cygwin 安装目录
3. **重置配置**: 删除配置文件重新创建
4. **重启应用**: 完全关闭并重新启动 Cursor

## 配置状态

- ✅ 项目级配置完成
- ✅ 全局配置完成
- ✅ 配置验证通过
- ✅ 文档记录完成

---

**配置完成时间**: 2025年5月24日  
**配置版本**: v1.0  
**适用环境**: Windows 10/11 + Cygwin + Cursor 