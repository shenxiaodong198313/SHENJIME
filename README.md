# 神迹输入法 (Shenji Input Method)

<div align="center">

![Android](https://img.shields.io/badge/Android-9%2B-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-blue.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)
![Version](https://img.shields.io/badge/Version-1.0-red.svg)

一款面向中文用户的智能Android输入法，基于多层级词典系统和高性能Trie树结构，提供流畅的中文输入体验。

[功能特性](#功能特性) • [技术架构](#技术架构) • [快速开始](#快速开始) • [开发指南](#开发指南) • [贡献指南](#贡献指南)

</div>

## 📱 项目概述

神迹输入法是一款专为Android平台设计的中文智能输入法，支持Android 9至15设备。项目采用现代化的技术栈，通过多层级词典系统和优化的数据结构，为用户提供高效、准确的中文输入体验。

### 🎯 核心特色

- **🚀 高性能**：基于Trie树的内存词典，查询响应时间1-3ms
- **🧠 智能联想**：多维度候选词生成，支持拼音、首字母、音节拆分
- **📚 丰富词库**：260万词条，涵盖基础词汇、地名、人名、诗词等
- **⚡ 快速启动**：分层加载策略，启动时间<500ms
- **🔧 开发友好**：完整的调试工具和性能监控

## ✨ 功能特性

### 🎹 输入功能
- **标准QWERTY键盘**：支持全拼输入
- **智能候选词**：基于词频和上下文的智能排序
- **首字母缩写**：支持快速首字母输入（如：bj → 北京）
- **音节拆分**：智能拼音分词和组合
- **模糊拼音**：支持zh/z、ch/c、sh/s等模糊音

### 📖 词典系统
| 词典类型 | 词条数量 | 说明 |
|---------|---------|------|
| chars | 10万 | 单字字典 |
| base | 78万 | 2-3字基础词组 |
| correlation | 57万 | 4字词组 |
| associational | 34万 | 5字以上词组 |
| place | 4.5万 | 地理位置词典 |
| people | 4万 | 人名词典 |
| poetry | 32万 | 诗词词典 |
| corrections | 137 | 错音错字纠正 |
| compatible | 5千 | 多音字兼容 |

### 🛠️ 开发工具
- **Trie构建器**：可视化词典构建和管理
- **输入调试器**：实时候选词分析和性能监控
- **词典管理器**：词典统计、浏览和维护
- **系统检查器**：内存使用和性能分析
- **日志系统**：完整的错误追踪和调试信息

## 🏗️ 技术架构

### 📋 技术栈
- **开发语言**：Kotlin 2.0.20
- **最低版本**：Android 9.0 (API 28)
- **目标版本**：Android 15 (API 34)
- **构建工具**：Gradle 8.7 + JDK 17
- **数据库**：Realm-Kotlin 2.3.0
- **UI框架**：ViewBinding + Material Design
- **异步处理**：Kotlin Coroutines 1.8.0
- **日志框架**：Timber 5.0.1

### 🏛️ 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
├─────────────────────────────────────────────────────────────┤
│  MainActivity  │  InputMethodService  │  DebugActivities   │
├─────────────────────────────────────────────────────────────┤
│                     Business Layer                          │
├─────────────────────────────────────────────────────────────┤
│ CandidateManager │ InputMethodEngine │ PinyinSplitter     │
├─────────────────────────────────────────────────────────────┤
│                      Data Layer                             │
├─────────────────────────────────────────────────────────────┤
│  TrieManager  │ DictionaryRepository │ StagedRepository   │
├─────────────────────────────────────────────────────────────┤
│                    Storage Layer                            │
├─────────────────────────────────────────────────────────────┤
│   Realm Database   │    Trie Files    │   Assets Files    │
└─────────────────────────────────────────────────────────────┘
```

### 🧩 核心组件

#### 1. 分阶段候选词查询系统
```kotlin
// 多阶段查询策略
阶段1: chars + base (基础词典)
阶段2: correlation + associational (关联词典)  
阶段3: place + people + poetry (专业词典)
阶段4: corrections + compatible (纠错词典)
```

#### 2. 高性能Trie树系统
- **内存优化**：分层加载，按需初始化
- **查询优化**：前缀匹配 + 内存映射
- **存储优化**：简化二进制格式，压缩率60%+

#### 3. 智能拼音处理
- **声调处理**：自动去除声调符号
- **音节拆分**：智能识别拼音边界
- **模糊匹配**：支持多种模糊音规则

## 🚀 快速开始

### 📋 环境要求
- **JDK**: 17+
- **Android Studio**: Arctic Fox+
- **Gradle**: 8.7+
- **Android SDK**: 34+
- **最小内存**: 8GB RAM (推荐16GB)

### 🔧 安装步骤

1. **克隆项目**
```bash
git clone https://github.com/your-username/shenji-input-method.git
cd shenji-input-method
```

2. **配置环境**
```bash
# 设置Java环境
export JAVA_HOME=/path/to/jdk17

# 配置Gradle
./gradlew --version
```

3. **构建项目**
```bash
# 构建Debug版本
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

4. **启用输入法**
   - 进入 设置 → 语言和输入法 → 管理键盘
   - 启用"神迹输入法"
   - 设置为默认输入法

### 📱 使用指南

1. **基础输入**：直接输入拼音，选择候选词
2. **快速输入**：使用首字母缩写（如：bj → 北京）
3. **调试模式**：在设置中开启调试模式查看详细信息
4. **词典管理**：通过主界面进入词典管理中心

## 🛠️ 开发指南

### 📁 项目结构
```
app/src/main/java/com/shenji/aikeyboard/
├── data/                    # 数据层
│   ├── trie/               # Trie树实现
│   ├── CandidateManager.kt # 候选词管理
│   └── DictionaryRepository.kt # 词典仓库
├── engine/                 # 输入引擎
│   ├── InputMethodEngine.kt
│   └── CombinationCandidateGenerator.kt
├── ui/                     # 用户界面
│   ├── MainActivity.kt
│   ├── dictionary/         # 词典管理界面
│   └── trie/              # Trie构建界面
├── keyboard/              # 键盘服务
├── utils/                 # 工具类
└── ShenjiApplication.kt   # 应用入口
```

### 🔧 Trie预编译工具包

项目提供了完整的Trie预编译工具包，用于将YAML格式的词典文件转换为高性能的二进制Trie数据文件。

#### 工具特性
- **声调处理**：自动去除拼音中的声调符号
- **词频筛选**：支持按百分比筛选高频词汇
- **格式兼容**：生成Java ObjectInputStream兼容的二进制格式
- **性能优化**：查询响应时间1-3ms

#### 使用方法

```bash
# 构建基础词典Trie（保留60%高频词）
python build_universal_trie.py --type base --percentage 0.6 --verify

# 构建关联词典（30%高频词）
python build_universal_trie.py --type correlation --percentage 0.3 --verify

# 批量构建所有词典
for dict_type in base correlation associational poetry; do
    python build_universal_trie.py --type $dict_type --percentage 0.6 --verify
done
```

#### 性能指标
| 指标 | 基础词典 | 说明 |
|------|----------|------|
| 原始词条数 | 780,654 | 完整词典大小 |
| 筛选后词条数 | 468,392 | 保留60%高频词 |
| 文件大小 | 13.13MB | 压缩后二进制文件 |
| 查询响应 | 1-3ms | 单次查询耗时 |

### 🧪 测试和调试

#### 单元测试
```bash
# 运行所有测试
./gradlew test

# 运行特定测试
./gradlew testDebugUnitTest
```

#### 调试工具
- **输入调试器**：实时查看候选词生成过程
- **Trie构建器**：可视化词典构建和测试
- **性能监控**：内存使用和查询性能分析

### 📊 性能优化

#### 内存管理
- **分层加载**：启动时只加载核心词典
- **按需初始化**：后台异步加载扩展词典
- **内存监控**：实时监控内存使用情况

#### 查询优化
- **多阶段查询**：按优先级分阶段查询词典
- **缓存机制**：热点词汇内存缓存
- **索引优化**：拼音和首字母双重索引

## 📈 性能指标

### 🚀 启动性能
- **冷启动时间**：< 500ms
- **热启动时间**：< 200ms
- **内存占用**：< 100MB (基础功能)

### ⚡ 查询性能
- **单次查询**：1-3ms
- **并发查询**：支持多线程
- **缓存命中率**：> 90%

### 💾 存储效率
- **词典压缩率**：60%+
- **索引大小**：< 50MB
- **总安装包**：< 250MB

## 🤝 贡献指南

### 🔀 提交流程
1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

### 📝 代码规范
- 遵循 Kotlin 官方编码规范
- 使用有意义的变量和函数名
- 添加必要的注释和文档
- 确保所有测试通过

### 🐛 问题报告
请使用 [GitHub Issues](https://github.com/your-username/shenji-input-method/issues) 报告问题，包含：
- 设备信息和Android版本
- 详细的重现步骤
- 相关的日志信息
- 预期行为和实际行为

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🙏 致谢

- [Realm-Kotlin](https://github.com/realm/realm-kotlin) - 高性能移动数据库
- [Timber](https://github.com/JakeWharton/timber) - Android日志框架
- [Material Components](https://github.com/material-components/material-components-android) - UI组件库
- [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) - YAML解析库

## 📞 联系方式

- **项目主页**：[GitHub Repository](https://github.com/your-username/shenji-input-method)
- **问题反馈**：[GitHub Issues](https://github.com/your-username/shenji-input-method/issues)
- **邮箱**：your-email@example.com

---

<div align="center">

**如果这个项目对你有帮助，请给它一个 ⭐️**

Made with ❤️ by [Your Name](https://github.com/your-username)

</div> 