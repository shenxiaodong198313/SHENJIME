# 词典数据库导出功能

## 功能概述

在词典管理中心的Realm词典管理列表页面右上角新增了一个"导出"按钮，用户可以点击该按钮将当前的Realm数据库文件导出到手机的外部存储Download目录下。

## 功能特性

### 1. 权限管理
- 自动检测外部存储写入权限
- 支持Android 10及以下版本的`WRITE_EXTERNAL_STORAGE`权限
- 支持Android 11+版本的`MANAGE_EXTERNAL_STORAGE`权限
- 权限被拒绝时自动弹出权限请求对话框

### 2. 文件导出
- 将当前Realm数据库文件复制到`/storage/emulated/0/Download/`目录
- 生成带时间戳的文件名：`shenji_dict_export_YYYYMMDD_HHMMSS.realm`
- 使用8KB缓冲区进行高效文件复制
- 复制完成后验证文件完整性

### 3. 用户体验
- 导出过程中显示进度对话框
- 导出成功后显示文件路径和大小
- 支持一键复制文件路径到剪贴板
- 详细的错误信息提示

## 技术实现

### 核心类

1. **PermissionManager** (`app/src/main/java/com/shenji/aikeyboard/utils/PermissionManager.kt`)
   - 处理外部存储权限的检查和请求
   - 兼容不同Android版本的权限模型

2. **DatabaseExporter** (`app/src/main/java/com/shenji/aikeyboard/utils/DatabaseExporter.kt`)
   - 负责数据库文件的导出逻辑
   - 文件复制和验证功能

3. **DictionaryListActivity** (修改)
   - 添加导出菜单项
   - 集成导出功能和权限处理

### 文件结构

```
app/src/main/
├── java/com/shenji/aikeyboard/
│   ├── ui/dictionary/
│   │   └── DictionaryListActivity.kt (修改)
│   └── utils/
│       ├── PermissionManager.kt (新增)
│       └── DatabaseExporter.kt (新增)
├── res/
│   ├── menu/
│   │   └── menu_dictionary_list.xml (新增)
│   └── drawable/
│       └── ic_export.xml (新增)
```

## 使用方法

1. 打开应用，进入"词典管理中心"
2. 点击"进入Realm词典管理"
3. 在词典列表页面右上角点击"导出"按钮
4. 如果没有权限，系统会自动弹出权限请求
5. 授权后，系统开始导出数据库文件
6. 导出完成后显示文件位置，可点击"复制路径"

## 权限要求

### AndroidManifest.xml 中已包含的权限：

```xml
<!-- 写入外部存储权限（Android 10及以下） -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<!-- Android 11+需要特殊权限 -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
    tools:ignore="ScopedStorage" />
```

## 错误处理

- **权限被拒绝**：显示权限请求对话框，引导用户手动授权
- **源文件不存在**：提示数据库文件不存在
- **存储空间不足**：提示存储空间不足
- **文件复制失败**：显示具体错误信息
- **目录访问失败**：提示无法访问下载目录

## 安全考虑

1. 导出的文件存储在公共下载目录，用户可以自由访问
2. 文件名包含时间戳，避免覆盖已有文件
3. 复制过程中验证文件完整性
4. 权限检查确保用户明确授权

## 兼容性

- **最低支持版本**：Android API 21 (Android 5.0)
- **目标版本**：Android API 34 (Android 14)
- **特殊处理**：Android 11+ 的分区存储权限

## 测试建议

1. 在不同Android版本上测试权限请求流程
2. 测试存储空间不足的情况
3. 测试权限被拒绝后的重试流程
4. 验证导出文件的完整性和可用性
5. 测试文件路径复制功能 