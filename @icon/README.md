# Android图标生成器

一个专业的Windows桌面应用程序，用于一键生成符合Android开发标准的各种尺寸图标。

## 🚀 功能特性

### 核心功能
- **一键生成**: 从单张原始图片生成所有Android所需的图标尺寸
- **标准规范**: 完全符合Android开发要求的格式、尺寸、名称和文件夹结构
- **无损压缩**: 支持高质量的图片压缩，减小文件大小
- **拖拽上传**: 支持拖拽图片文件到应用界面
- **实时预览**: 图片选择后立即显示预览和详细信息

### 生成规格
- **启动器图标 (mipmap)**:
  - mipmap-mdpi: 48×48px
  - mipmap-hdpi: 72×72px
  - mipmap-xhdpi: 96×96px
  - mipmap-xxhdpi: 144×144px
  - mipmap-xxxhdpi: 192×192px

- **应用内图标 (drawable)**:
  - drawable-mdpi: 48×48px
  - drawable-hdpi: 72×72px
  - drawable-xhdpi: 96×96px
  - drawable-xxhdpi: 144×144px
  - drawable-xxxhdpi: 192×192px

- **通知图标 (可选)**:
  - drawable-mdpi: 24×24px (白色图标)
  - drawable-hdpi: 36×36px (白色图标)
  - drawable-xhdpi: 48×48px (白色图标)
  - drawable-xxhdpi: 72×72px (白色图标)
  - drawable-xxxhdpi: 96×96px (白色图标)

### 技术特性
- **高质量缩放**: 使用Lanczos3算法进行图片缩放
- **智能压缩**: PNG格式无损压缩，可调节压缩质量
- **批量处理**: 一次操作生成所有尺寸
- **错误处理**: 完善的错误提示和处理机制
- **进度显示**: 实时显示生成进度

## 📋 系统要求

- **操作系统**: Windows 10/11
- **Node.js**: 16.0 或更高版本
- **内存**: 至少 4GB RAM
- **存储**: 至少 100MB 可用空间

## 🛠️ 安装和使用

### 开发环境安装

1. **克隆项目**
```bash
git clone <repository-url>
cd @icon
```

2. **安装依赖**
```bash
npm install
```

3. **启动应用**
```bash
npm start
```

4. **开发模式**
```bash
npm run dev
```

### 使用方法

1. **选择图片**
   - 点击"选择图片"按钮，或
   - 直接拖拽图片文件到上传区域
   - 支持格式: PNG, JPG, JPEG, BMP, GIF, WebP

2. **配置选项**
   - **图标名称**: 设置生成的图标文件名前缀（默认: ic_launcher）
   - **输出目录**: 选择图标文件的输出位置
   - **启用压缩**: 开启无损压缩以减小文件大小
   - **压缩质量**: 调节压缩质量（60-100%）
   - **生成通知图标**: 是否生成用于通知栏的白色图标

3. **生成图标**
   - 点击"生成Android图标"按钮
   - 等待生成完成
   - 查看生成结果和统计信息

4. **使用生成的图标**
   - 将生成的文件夹复制到Android项目的 `src/main/res/` 目录下
   - 在 `AndroidManifest.xml` 中配置应用图标

## 📁 输出结构

生成的文件将按照以下结构组织：

```
output/
├── drawable-mdpi/
│   ├── ic_launcher.png (48×48)
│   └── ic_launcher_notification.png (24×24)
├── drawable-hdpi/
│   ├── ic_launcher.png (72×72)
│   └── ic_launcher_notification.png (36×36)
├── drawable-xhdpi/
│   ├── ic_launcher.png (96×96)
│   └── ic_launcher_notification.png (48×48)
├── drawable-xxhdpi/
│   ├── ic_launcher.png (144×144)
│   └── ic_launcher_notification.png (72×72)
├── drawable-xxxhdpi/
│   ├── ic_launcher.png (192×192)
│   └── ic_launcher_notification.png (96×96)
├── mipmap-mdpi/
│   └── ic_launcher.png (48×48)
├── mipmap-hdpi/
│   └── ic_launcher.png (72×72)
├── mipmap-xhdpi/
│   └── ic_launcher.png (96×96)
├── mipmap-xxhdpi/
│   └── ic_launcher.png (144×144)
├── mipmap-xxxhdpi/
│   └── ic_launcher.png (192×192)
└── README.md (生成报告)
```

## 🔧 技术架构

### 技术栈
- **Electron**: 跨平台桌面应用框架
- **Sharp**: 高性能图片处理库
- **HTML5/CSS3**: 现代化用户界面
- **JavaScript ES6+**: 应用逻辑实现

### 架构设计
- **主进程 (main.js)**: 应用生命周期管理、文件系统操作
- **渲染进程 (renderer/)**: 用户界面和交互逻辑
- **图标生成器 (iconGenerator.js)**: 核心图片处理逻辑
- **IPC通信**: 主进程与渲染进程间的数据传输

### 设计原则
- **单一职责**: 每个模块专注于特定功能
- **开闭原则**: 易于扩展新功能
- **依赖倒置**: 通过接口解耦模块依赖
- **DRY原则**: 避免代码重复
- **KISS原则**: 保持简单易懂

## 📱 Android集成

### 在AndroidManifest.xml中配置

```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:theme="@style/AppTheme">
    
    <!-- 其他配置 -->
    
</application>
```

### 通知图标使用

```java
NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_launcher_notification)
    .setContentTitle("通知标题")
    .setContentText("通知内容");
```

## 🎨 最佳实践

### 图片要求
- **尺寸**: 建议使用至少 192×192 像素的正方形图片
- **格式**: 推荐使用PNG格式以获得最佳质量
- **内容**: 图标应简洁明了，在小尺寸下仍然清晰可辨
- **背景**: 避免使用透明背景，除非特殊需要

### 设计建议
- **对比度**: 确保图标在不同背景下都有良好的可见性
- **一致性**: 保持与应用整体设计风格的一致性
- **可缩放性**: 图标在各种尺寸下都应保持清晰
- **品牌识别**: 体现应用的品牌特色和功能特点

## 🐛 故障排除

### 常见问题

1. **图片无法加载**
   - 检查图片格式是否支持
   - 确认文件路径是否正确
   - 验证文件是否损坏

2. **生成失败**
   - 检查输出目录是否有写入权限
   - 确认磁盘空间是否充足
   - 查看错误日志获取详细信息

3. **图标质量问题**
   - 使用更高分辨率的原始图片
   - 调整压缩质量设置
   - 检查原始图片的质量

### 性能优化
- 使用SSD存储以提高处理速度
- 关闭不必要的后台程序
- 确保有足够的内存可用

## 📄 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

## 👨‍💻 开发者

**SHENJIME** - Android图标生成器

## 🔄 版本历史

### v1.0.0 (2024-12-19)
- 初始版本发布
- 支持所有Android标准图标尺寸生成
- 实现拖拽上传功能
- 添加无损压缩选项
- 提供现代化用户界面

---

如有问题或建议，请提交Issue或联系开发者。 