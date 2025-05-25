# 神迹输入法 (Shenji Input Method)

<div align="center">

![Android](https://img.shields.io/badge/Android-9%2B-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-blue.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)
![Version](https://img.shields.io/badge/Version-1.0-red.svg)

一款面向中文用户的智能Android输入法，基于多层级词典系统和高性能Trie树结构，提供流畅的中文输入体验。

**🔍 候选词引擎特色**：采用智能分层查询架构，支持6种查询策略和6种匹配方法，实现了渐进式缓存、智能分段组合、多Trie树并行查询等企业级特性，查询响应时间1-3ms，缓存命中率>90%。

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
- **🎯 智能分层**：按音节数智能分层显示候选词，优化用户体验
- **🔄 渐进式查询**：支持分页加载和缓存优化，避免性能瓶颈
- **🧩 多策略引擎**：6种查询策略 + 6种匹配方法，智能选择最优方案

## ✨ 功能特性

### 🎹 输入功能
- **标准QWERTY键盘**：支持全拼输入
- **智能候选词**：基于词频和上下文的智能排序
- **首字母缩写**：支持快速首字母输入（如：bj → 北京）
- **音节拆分**：智能拼音分词和组合
- **模糊拼音**：支持zh/z、ch/c、sh/s等模糊音
- **分层候选词**：智能分层显示，优先显示与音节数匹配的词组
- **渐进式查询**：支持分页加载，避免一次性加载过多候选词
- **实时分割**：动态拼音分割，支持输入过程中的实时候选词更新

### 🧠 候选词引擎核心特性
- **多策略查询**：6种查询策略（单字优先、Trie优先、混合查询等）
- **智能分层**：按音节数智能分层显示候选词
- **缓存优化**：LRU缓存机制，支持500条候选词缓存
- **并行查询**：多Trie树并行搜索，提升查询效率
- **智能分段**：长句智能分段组合（如：2+4、4+2、3+3分段）
- **输入类型识别**：自动识别8种输入类型并选择最优查询策略

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
- **查询分析器**：实时查询策略分析和性能统计
- **缓存监控**：LRU缓存命中率和内存使用监控

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

#### 1. 智能候选词引擎系统
```kotlin
// 核心引擎组件
OptimizedCandidateEngine // 主要候选词引擎（生产环境使用）
SmartPinyinEngine        // 实验性候选词引擎（仅测试使用）
IntelligentQueryEngine   // 智能查询引擎
UnifiedPinyinSplitter    // 统一拼音分割器
TrieManager             // 高性能Trie树管理器

// 查询策略枚举
enum class QueryStrategy {
    CHAR_TRIE_ONLY,     // 仅查询单字Trie
    TRIE_PRIORITY,      // Trie优先
    HYBRID_QUERY,       // 混合查询
    DATABASE_PRIORITY,  // 数据库优先
    PROGRESSIVE_FILTER, // 渐进式过滤
    ABBREVIATION_MATCH  // 缩写匹配
}

// 匹配方法
enum class QueryMethod {
    EXACT_MATCH,        // 精确匹配
    PREFIX_MATCH,       // 前缀匹配
    FUZZY_MATCH,        // 模糊匹配
    ABBREVIATION_MATCH, // 缩写匹配
    SEGMENTED_MATCH,    // 分段匹配
    SYLLABLE_MATCH      // 音节匹配
}
```

#### 2. 高性能Trie树系统
- **内存优化**：分层加载，按需初始化
- **查询优化**：前缀匹配 + 内存映射
- **存储优化**：简化二进制格式，压缩率60%+
- **并行加载**：多词典并行初始化，支持64KB大缓冲区
- **线程安全**：ReentrantReadWriteLock保护并发访问

#### 3. 智能拼音处理
- **声调处理**：自动去除声调符号
- **音节拆分**：智能识别拼音边界
- **模糊匹配**：支持多种模糊音规则
- **统一分割器**：支持5种分割模式（完整、动态、智能、分段、多方案）
- **输入类型识别**：自动识别8种输入类型并选择最优策略

#### 4. 分层候选词策略
```kotlin
// 智能分层显示策略
第1层: 完整短语匹配（与音节数相同的词组）
第2层: 智能分段组合（2+4、4+2、3+3等分段）
第3层: 常用短语优先（2-3字高频词组）
第4-N层: 按音节顺序的单字候选

// 单音节输入策略
第1层: 单字候选（优先）
第2层: 2字词组
第3层: 3字及以上词组
```

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

## 🔍 候选词引擎详解

### 🧠 核心引擎架构

神迹输入法的候选词引擎采用多层级智能查询架构，包含以下核心组件：

#### 1. OptimizedCandidateEngine (主要候选词引擎)
- **功能**：项目中实际使用的主要候选词查询引擎
- **特性**：
  - 多策略智能查询和结果优化
  - 多级缓存系统（候选词缓存500条 + 策略缓存200条）
  - 性能统计和监控
  - 协程并发处理
  - 与InputStrategy和IntelligentQueryEngine协同工作

#### 2. SmartPinyinEngine (实验性引擎)
- **功能**：实验性候选词查询引擎（仅用于测试）
- **特性**：
  - 支持6种查询策略（单字优先、Trie优先、混合查询等）
  - 智能分层候选词显示
  - 渐进式缓存查询
  - 支持智能分段组合
  - **注意**：仅在测试Activity中使用，不在生产环境中运行

#### 3. IntelligentQueryEngine (智能查询引擎)
- **功能**：执行具体的查询方法
- **特性**：
  - 6种查询方法（精确匹配、前缀匹配、模糊匹配等）
  - 多Trie树并行查询
  - 智能结果合并

#### 4. UnifiedPinyinSplitter (统一拼音分割器)
- **功能**：统一的拼音分割处理
- **特性**：
  - 5种分割模式（完整、动态、智能、分段、多方案）
  - 支持长句智能分段
  - 性能缓存和统计

### 🎯 查询策略详解

#### 查询策略枚举
```kotlin
enum class QueryStrategy {
    CHAR_TRIE_ONLY,     // 仅查询单字Trie - 适用于单字输入
    TRIE_PRIORITY,      // Trie优先 - 优先查询内存Trie树
    HYBRID_QUERY,       // 混合查询 - 结合多种数据源
    DATABASE_PRIORITY,  // 数据库优先 - 优先查询数据库
    PROGRESSIVE_FILTER, // 渐进式过滤 - 基于前缀结果过滤
    ABBREVIATION_MATCH  // 缩写匹配 - 专门处理首字母缩写
}
```

#### 输入类型识别
```kotlin
enum class InputType {
    SINGLE_CHAR,        // 单字拼音：ni, hao
    WORD_PINYIN,        // 词组拼音：nihao, shijie
    ABBREVIATION,       // 首字母缩写：nh, sj
    MIXED_INPUT,        // 混合输入：ni'h, hao'sj
    LONG_SENTENCE,      // 长句拼音：nihaoshijie
    FUZZY_PINYIN,       // 模糊拼音：zi->zhi, ci->chi
    TONE_INPUT,         // 声调输入：ni3hao3
    SEGMENTED_INPUT     // 分词输入：ni hao
}
```

### 🏗️ 分层候选词策略

#### 多音节输入分层策略
```kotlin
// 新策略（支持智能分段组合）：
第1层: 完整短语匹配（与音节数相同的词组）
第2层: 智能分段组合（2+4、4+2、3+3等分段）
第3层: 常用短语优先（2-3字高频词组）
第4-N层: 按音节顺序的单字候选

// 示例：jin tian tian qi hen hao (6个音节)
第1层: 6字完整短语
第2层: "今天" + "天气很好" (2+4分段)
第3层: "今天天气" + "很好" (4+2分段)
第4层: "今天天" + "气很好" (3+3分段)
```

#### 单音节输入分层策略
```kotlin
第1层: 单字候选（最重要）
第2层: 2字词组
第3层: 3字及以上词组
```

### 🔧 性能优化机制

#### 缓存系统
- **渐进式缓存**：查询结果智能缓存，支持分页加载
- **LRU候选词缓存**：500条热点候选词缓存
- **策略缓存**：200条查询策略缓存
- **分割结果缓存**：避免重复计算拼音分割

#### 并发优化
- **协程并发**：全异步查询处理
- **并行Trie查询**：多个Trie树并行搜索
- **线程安全**：ReentrantReadWriteLock保护并发访问

#### 内存优化
- **按需加载**：词典按需动态加载
- **内存映射**：大文件(>10MB)使用内存映射
- **64KB大缓冲区**：优化文件读取性能

### 📊 性能监控

#### 查询分析
```kotlin
data class QueryAnalysis(
    val inputType: InputType,           // 输入类型
    val queryStrategy: QueryStrategy,   // 查询策略
    val segmentations: List<String>,    // 分割方案
    val trieStatus: Map<TrieType, Boolean>, // Trie状态
    val queryTime: Long,                // 查询耗时
    val resultCount: Int,               // 结果数量
    val cacheHit: Boolean              // 缓存命中
)
```

#### 性能统计
- **查询次数统计**：AtomicLong计数器
- **缓存命中率**：实时监控缓存效果
- **查询耗时分析**：毫秒级性能追踪
- **内存使用监控**：实时内存占用统计

## 🛠️ 开发指南

### 📁 项目结构
```
app/src/main/java/com/shenji/aikeyboard/
├── data/                    # 数据层
│   ├── trie/               # Trie树实现
│   │   ├── TrieManager.kt  # Trie树管理器
│   │   ├── PinyinTrie.kt   # 拼音Trie树
│   │   ├── TrieNode.kt     # Trie节点
│   │   └── TrieType.kt     # Trie类型枚举
│   ├── CandidateManager.kt # 候选词管理
│   └── DictionaryRepository.kt # 词典仓库
├── keyboard/               # 候选词引擎核心
│   ├── SmartPinyinEngine.kt      # 主要候选词查询引擎
│   ├── OptimizedCandidateEngine.kt # 智能候选词引擎
│   ├── IntelligentQueryEngine.kt   # 智能查询引擎
│   ├── InputStrategy.kt            # 输入策略分析器
│   └── ShenjiInputMethodService.kt # 输入法服务
├── pinyin/                 # 拼音处理
│   ├── UnifiedPinyinSplitter.kt # 统一拼音分割器
│   ├── InputType.kt        # 输入类型枚举
│   └── MatchType.kt        # 匹配类型枚举
├── ui/                     # 用户界面
│   ├── MainActivity.kt
│   ├── dictionary/         # 词典管理界面
│   └── trie/              # Trie构建界面
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
- **LRU缓存**：候选词缓存(500条) + 策略缓存(200条)
- **内存映射**：大文件(>10MB)使用内存映射加载

#### 查询优化
- **多策略查询**：6种查询策略智能选择
- **渐进式过滤**：基于前缀结果过滤
- **并行查询**：多Trie树并行搜索
- **智能路由**：根据输入类型选择最优查询策略
- **结果去重**：按词语去重，保留最高频率

#### 分割优化
- **统一分割器**：5种分割模式（完整、动态、智能、分段、多方案）
- **智能分段**：长句智能分段组合（2+4、4+2、3+3等）
- **缓存分割结果**：避免重复计算分割方案

## 📈 性能指标

### 🚀 启动性能
- **冷启动时间**：< 500ms
- **热启动时间**：< 200ms
- **内存占用**：< 100MB (基础功能)

### ⚡ 查询性能
- **单次查询**：1-3ms
- **并发查询**：支持多线程
- **缓存命中率**：> 90%
- **分层查询**：智能分层显示，优化用户体验
- **渐进式加载**：支持分页加载，避免一次性加载过多候选词
- **多策略路由**：根据输入类型自动选择最优查询策略

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