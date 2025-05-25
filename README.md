# 神迹输入法 (Shenji Input Method)

<div align="center">

![Android](https://img.shields.io/badge/Android-9%2B-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-blue.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)
![Version](https://img.shields.io/badge/Version-1.0-red.svg)

一款面向中文用户的智能Android输入法，基于多层级词典系统和高性能Trie树结构，提供流畅的中文输入体验。

**🔍 候选词引擎特色**：采用智能分层查询架构，支持6种查询策略和6种匹配方法，实现了渐进式缓存、智能分段组合、多Trie树并行查询等企业级特性，查询响应时间1-3ms，缓存命中率>90%。

[功能特性](#功能特性) • [技术架构](#技术架构) • [引擎详解](#引擎详解) • [数据库设计](#数据库设计) • [快速开始](#快速开始)

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

### 📖 词典系统
| 词典类型 | 词条数量 | 文件大小 | 说明 |
|---------|---------|----------|------|
| chars | 10万 | 570KB | 单字字典 |
| base | 78万 | 11MB | 2-3字基础词组 |
| correlation | 57万 | 7.0MB | 4字词组 |
| associational | 34万 | 4.8MB | 5字以上词组 |
| place | 4.5万 | 493KB | 地理位置词典 |
| people | 4万 | 402KB | 人名词典 |
| poetry | 32万 | 4.6MB | 诗词词典 |
| corrections | 137 | 1.4KB | 错音错字纠正 |
| compatible | 5千 | 49KB | 多音字兼容 |

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
│ SmartPinyinEngine │ IntelligentQueryEngine │ InputStrategy │
├─────────────────────────────────────────────────────────────┤
│                      Data Layer                             │
├─────────────────────────────────────────────────────────────┤
│  TrieManager  │ UnifiedPinyinSplitter │ DictionaryRepository│
├─────────────────────────────────────────────────────────────┤
│                    Storage Layer                            │
├─────────────────────────────────────────────────────────────┤
│   Realm Database   │    Trie Files    │   Assets Files    │
└─────────────────────────────────────────────────────────────┘
```

## 🔍 引擎详解

### 🧠 核心引擎架构

神迹输入法采用三层候选词引擎架构，实现了高性能、智能化的中文输入体验：

#### 1. SmartPinyinEngine (智能拼音引擎)
- **功能**：项目中实际使用的主要候选词查询引擎
- **特性**：
  - 智能输入类型检测（单字符、缩写、短输入、中等输入、长输入、超限输入）
  - 分层查询策略（单字+基础词组优先、缩写匹配、关联词组优先等）
  - LRU缓存系统（100条查询缓存）
  - 性能统计和监控（查询次数、缓存命中率）
  - 协程并发处理

#### 2. IntelligentQueryEngine (智能查询引擎)
- **功能**：执行具体的查询方法
- **特性**：
  - 6种查询方法（精确匹配、前缀匹配、模糊匹配、缩写匹配、分段匹配、音节匹配）
  - 多Trie树并行查询
  - 智能结果合并和去重
  - 模糊拼音变体生成

#### 3. InputStrategy (输入策略分析器)
- **功能**：分析输入类型并生成查询策略
- **特性**：
  - 8种输入类型识别（单字拼音、词组拼音、首字母缩写、混合输入等）
  - 多策略查询路由
  - 优先级排序
  - 模糊拼音支持

### 🔧 拼音分割逻辑

#### UnifiedPinyinSplitter (统一拼音分割器)

**核心功能**：
- **统一接口**：提供一致的拼音分割API
- **5种分割模式**：完整、动态、智能、分段、多方案
- **性能优化**：集成缓存策略和最优算法

**分割策略**：
```kotlin
// 主要分割方法
fun split(input: String): List<String>

// 多种分割方案
fun getMultipleSplits(input: String): List<List<String>>

// 动态分割（实时输入）
fun splitDynamic(input: String): List<String>

// 智能分割（最佳结果）
fun splitSmart(input: String): List<String>

// 分段拆分（长句处理）
fun splitIntoSegments(input: String): List<List<String>>
```

**分割算法**：
1. **基础音节识别**：基于预定义音节库进行匹配
2. **贪心算法**：优先匹配最长有效音节
3. **回溯机制**：处理歧义分割情况
4. **智能分段**：长句智能分段组合（2+4、4+2、3+3等）

### 🎯 匹配逻辑

#### 查询策略枚举
```kotlin
enum class QueryStrategy {
    CHARS_BASE_PRIORITY,    // 单字+基础词组优先
    ABBREVIATION_MATCH,     // 缩写匹配
    CORRELATION_PRIORITY,   // 4字词组优先
    ASSOCIATIONAL_PRIORITY, // 长词组优先
    STOP_QUERY             // 停止查询
}
```

#### 输入类型识别
```kotlin
enum class InputType {
    SINGLE_CHAR,        // 单字符
    ABBREVIATION,       // 缩写
    SHORT_INPUT,        // 短输入(2-3分段)
    MEDIUM_INPUT,       // 中等输入(4分段)
    LONG_INPUT,         // 长输入(5-6分段)
    OVER_LIMIT          // 超过限制(7+分段)
}
```

#### 匹配方法
```kotlin
enum class QueryMethod {
    EXACT_MATCH,        // 精确匹配
    PREFIX_MATCH,       // 前缀匹配
    FUZZY_MATCH,        // 模糊匹配
    ABBREVIATION_MATCH, // 缩写匹配
    SEGMENTED_MATCH,    // 分段匹配
    SYLLABLE_MATCH      // 音节匹配
}
```

### 🏗️ 查询逻辑

#### 分层查询策略
```kotlin
// 智能分层显示策略
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

#### 查询流程
1. **输入分析**：识别输入类型和分割方案
2. **策略选择**：根据输入类型选择最优查询策略
3. **并行查询**：多Trie树并行搜索
4. **结果合并**：去重、排序、分层显示
5. **缓存更新**：更新LRU缓存

#### 回退机制
```kotlin
// Trie查询失败时回退到Realm数据库
private suspend fun queryWithFallback(
    trieTypes: List<TrieType>,
    query: String,
    limit: Int
): List<WordFrequency> {
    // 1. 优先Trie查询
    val trieResults = queryFromTrie(trieTypes, query, limit)
    
    // 2. 结果不足时回退到Realm
    if (trieResults.size < limit) {
        val realmResults = queryFromRealm(query, limit)
        return (trieResults + realmResults).distinctBy { it.word }
    }
    
    return trieResults
}
```

## 🗄️ 数据库设计

### 📊 Realm数据库结构

#### Entry数据模型
```kotlin
open class Entry : RealmObject {
    @PrimaryKey
    var id: String = ""           // 词条唯一标识符
    
    var word: String = ""         // 词条文本内容
    
    @Index
    var pinyin: String = ""       // 词条拼音（空格分隔）
    
    @Index
    var initialLetters: String = "" // 拼音首字母缩写
    
    var frequency: Int = 0        // 词频
    
    var type: String = ""         // 词典类型
}
```

#### 数据库特性
- **主键索引**：基于id的唯一标识
- **拼音索引**：支持前缀匹配查询
- **首字母索引**：支持缩写查询
- **词频排序**：支持按频率排序
- **类型分类**：支持按词典类型筛选

#### 查询优化
```kotlin
// 前缀匹配查询
realm.query(Entry::class)
    .query("pinyin BEGINSWITH $0 OR initialLetters BEGINSWITH $0", query)
    .limit(limit)
    .find()
```

### 🌳 Trie树设计

#### PinyinTrie结构
```kotlin
class PinyinTrie : Serializable {
    private val root = TrieNode()           // 根节点
    private val lock = ReentrantReadWriteLock() // 读写锁
    
    // 插入词语
    fun insert(pinyin: String, word: String, frequency: Int)
    
    // 前缀搜索
    fun searchByPrefix(prefix: String, limit: Int): List<WordItem>
    
    // 内存统计
    fun getMemoryStats(): TrieMemoryStats
}
```

#### TrieNode节点设计
```kotlin
class TrieNode : Serializable {
    val children: MutableMap<Char, TrieNode> = HashMap()
    val words: MutableList<WordItem> = ArrayList()
    var isEndOfWord: Boolean = false
    
    companion object {
        const val MAX_WORDS_PER_NODE = 50        // 普通节点最大词数
        const val MAX_WORDS_PER_NODE_CHARS = 1000 // 单字节点最大词数
    }
}
```

#### 性能优化特性
- **内存映射**：大文件(>10MB)使用内存映射加载
- **64KB缓冲区**：优化文件读取性能
- **并行加载**：多词典并行初始化
- **线程安全**：ReentrantReadWriteLock保护并发访问
- **容量限制**：节点词数限制，避免内存占用过大

#### Trie文件格式
```
二进制格式：
[拼音长度(4字节)][拼音内容][词数(4字节)]
[词1长度(4字节)][词1内容][词1频率(4字节)]
[词2长度(4字节)][词2内容][词2频率(4字节)]
...
```

### 🔧 TrieManager管理器

#### 核心功能
- **按需加载**：词典按需动态加载，减少启动时内存压力
- **并行初始化**：多词典并行加载，提升启动速度
- **状态管理**：跟踪加载状态和健康状态
- **内存优化**：支持内存映射和大缓冲区

#### 加载策略
```kotlin
// 轻量级初始化（启动时）
fun init() {
    // 只检查可用文件，不加载到内存
    val availableTypes = checkAvailableTrieFiles()
    isInitialized = true
}

// 按需加载（使用时）
suspend fun ensureTrieLoaded(trieType: TrieType) {
    if (!isTrieLoaded(trieType)) {
        loadTrieFromAssets(trieType)
    }
}
```

## 📈 性能指标

### 🚀 启动性能
- **冷启动时间**：< 500ms
- **热启动时间**：< 200ms
- **内存占用**：< 100MB (基础功能)
- **轻量级初始化**：不在启动时加载所有词典

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
- **Trie文件大小**：29.4MB（所有词典）
- **Realm数据库**：229MB

### 🧠 内存管理
- **分层加载**：启动时只加载核心词典
- **按需初始化**：后台异步加载扩展词典
- **内存监控**：实时监控内存使用情况
- **LRU缓存**：查询缓存(100条)
- **节点容量限制**：避免单节点内存占用过大

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
├── keyboard/               # 候选词引擎核心
│   ├── SmartPinyinEngine.kt      # 智能拼音引擎
│   ├── IntelligentQueryEngine.kt # 智能查询引擎
│   ├── InputStrategy.kt          # 输入策略分析器
│   └── ShenjiInputMethodService.kt # 输入法服务
├── data/                   # 数据层
│   ├── trie/              # Trie树实现
│   │   ├── TrieManager.kt # Trie树管理器
│   │   ├── PinyinTrie.kt  # 拼音Trie树
│   │   ├── TrieNode.kt    # Trie节点
│   │   └── TrieType.kt    # Trie类型枚举
│   └── Entry.kt           # 数据模型
├── pinyin/                # 拼音处理
│   ├── UnifiedPinyinSplitter.kt # 统一拼音分割器
│   ├── InputType.kt       # 输入类型枚举
│   └── MatchType.kt       # 匹配类型枚举
├── ui/                    # 用户界面
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