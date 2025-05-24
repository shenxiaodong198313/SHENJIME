# 神迹输入法 (Shenji Input Method)

一款面向中文用户的智能Android输入法，支持Android 9至15设备，基于多层级词典系统提供高效输入体验。

## 核心功能

- **智能联想**：基于多种词典组合提供词语联想
- **标准键盘**：QWERTY键盘布局输入方式
- **词典管理**：包含9种专业词典，总计约260万词条
- **日志系统**：自动捕获崩溃信息并支持查看管理
- **输入测试**：提供专门的测试界面验证输入法功能和候选词列表

## 词典系统

| 词典类型 | 词条数量 | 说明 |
|---------|---------|------|
| chars | 10万 | 单字字典 |
| base | 78万 | 2-3字基础词组 |
| correlation | 57万 | 4字词组 |
| associational | 34万 | 5字以上词组 |
| compatible | 5千 | 多音字词组 |
| corrections | 137 | 错音错字词组 |
| place | 4.5万 | 地理位置词典 |
| people | 4万 | 人名词典 |
| poetry | 32万 | 诗词词典 |

### 管理功能

- 词典统计展示（词条总数、内存占用、模块数量）
- 词典模块浏览（支持分页加载，每页500条）
- 详细词条信息（词语、拼音、词频）

## 技术实现

### 架构设计
- **MVVM架构**：使用ViewModel与Repository模式
- **单例模式**：词典管理器采用单例设计
- **观察者模式**：使用LiveData和协程Flow监控状态

### 关键技术
- **后台加载**：不阻塞UI线程，保证应用流畅度
- **Realm数据库**：高效存储和查询大量词典数据

### 性能优化
- 分页加载避免一次性载入过多数据
- 使用DiffUtil优化列表更新性能
- 只加载必要的高频词典到内存

## 技术栈

- 开发语言：Kotlin
- 数据库：Realm-Kotlin
- 日志框架：Timber
- UI组件：RecyclerView、ViewBinding、Shimmer效果

## 使用方法

1. 安装应用
2. 在Android设置 → 语言和输入法 → 管理键盘中启用"神迹输入法"
3. 切换到"神迹输入法"作为默认输入法
4. 在主界面点击"输入测试"按钮可测试输入法功能



## 日志系统

应用会自动捕获崩溃并记录到外部存储：
- 日志路径：`Android/data/com.shenji.aikeyboard/files/logs/crash_log.txt`
- 可通过应用主界面的"查看日志"按钮访问并管理日志

## 开发环境

- Android Studio Arctic Fox+
- JDK 17
- Gradle 8.7+
- Android SDK 34+

## 构建命令

```bash
# 构建Debug版本
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

## 技术文档详情

### 系统环境与依赖

- **开发语言**: Kotlin 2.0.20
- **目标平台**: Android 9.0+ (API 28 及以上)
- **构建工具**: Gradle 8.7
- **JVM 版本**: Java 17

#### 第三方库版本

| 依赖库 | 版本 | 用途 |
|--------|------|------|
| Realm数据库 | 2.3.0 | 词典存储与查询 |
| AndroidX Core KTX | 1.12.0 | Android核心功能扩展 |
| AndroidX AppCompat | 1.7.0 | 向下兼容支持 |
| Material Components | 1.11.0 | UI组件 |
| ConstraintLayout | 2.1.4 | 布局管理 |
| AndroidX Lifecycle | 2.7.0 | 生命周期管理 |
| Facebook Shimmer | 0.5.0 | 加载效果 |
| SnakeYAML | 2.2 | YAML配置解析 |
| Kotlin Coroutines | 1.8.0 | 异步编程 |
| Timber | 5.0.1 | 日志管理 |
| Android Gradle Plugin | 8.6.0 | Android构建支持 |



# 修订后的双Trie开发方案
1. 整体架构设计（更新）
TrieNode类：构建Trie树的基本节点
PinyinTrie类：拼音Trie树实现
TrieBuilder服务：负责构建、管理、导出Trie树
TrieBuildActivity：可视化构建进度和导出功能
MemoryMonitor：监控内存使用情况
TrieManager：应用启动时快速加载预构建Trie文件
2. 实现流程（更新）
创建Trie数据结构
实现从词典到Trie的构建流程
开发构建进度可视化界面
实现内存使用统计
新增：添加Trie导出功能
新增：实现预构建Trie加载功能
新增：打包预构建Trie到app中

# 以下是对当前项目的整体总结，内容涵盖词典数据库、分词器、候选词等核心模块。

---

# 项目技术文档

## 1. 词典数据库

### 1.1 数据库类型

- **类型**：Realm（移动端高性能本地数据库）
- **文件位置**：`assets/shenji_dict.realm`
- **主要表结构**：`Entry`（词条表）

### 1.2 表结构与字段说明

#### Entry（词条表）

| 字段名         | 类型     | 说明                                                         | 示例           | 备注           |
| -------------- | -------- | ------------------------------------------------------------ | -------------- | -------------- |
| id             | String   | 主键，唯一标识词条                                           | "123456"       | @PrimaryKey    |
| word           | String   | 词条文本内容，存储汉字词语或单字                             | "北京"         |                |
| pinyin         | String   | 词条拼音，完整拼音，空格分隔每个字的拼音                     | "bei jing"     | @Index         |
| initialLetters | String   | 拼音首字母缩写，用于首字母输入匹配，如"北京"为"bj"           | "bj"           | @Index         |
| frequency      | Int      | 词频，数值越高表示越常用，用于候选词排序                     | 12345          |                |
| type           | String   | 词典类型（如chars、base、correlation等，见下表）             | "base"         |                |

#### type 字段取值说明

| 类型           | 中文说明   | 典型用途         |
| -------------- | ---------- | ---------------- |
| chars          | 单字词典   | 单字输入         |
| base           | 基础词典   | 常用词组         |
| correlation    | 关联词典   | 相关词推荐       |
| associational  | 联想词典   | 联想输入         |
| compatible     | 兼容词典   | 兼容老词库       |
| corrections    | 纠错词典   | 拼写纠错         |
| place          | 地名词典   | 地名输入         |
| people         | 人名词典   | 人名输入         |
| poetry         | 诗词词典   | 诗词输入         |

#### 字段格式示例

```json
{
  "id": "abc123",
  "word": "北京",
  "pinyin": "bei jing",
  "initialLetters": "bj",
  "frequency": 10000,
  "type": "base"
}
```

### 1.3 索引说明

- `id`：主键索引，唯一标识每条数据。
- `pinyin`：拼音字段建立索引，加速拼音查询。
- `initialLetters`：拼音首字母缩写字段建立索引，加速首字母缩写查询。

### 1.4 典型表内容示例

| word | pinyin    | initialLetters | frequency | type   |
|------|-----------|---------------|-----------|--------|
| 北京 | bei jing  | bj            | 10000     | base   |
| 你好 | ni hao    | nh            | 9000      | base   |
| 张三 | zhang san | zs            | 8000      | people |
| 上海 | shang hai | sh            | 9500      | place  |

---

# 分阶段候选词查询系统

## 说明

本项目已移除旧的策略模式候选词生成系统，全面采用新的分阶段查询系统。新系统基于多阶段查询、多维度排序、去重合并三大机制，实现了更精准的候选词生成和排序逻辑。

## 系统架构

整个候选词查询系统由以下几个主要组件构成：

1. **CandidateManager**: 候选词管理器，负责协调候选词生成和查询
2. **StagedDictionaryRepository**: 分阶段词典仓库，实现多阶段查询逻辑
3. **DictionaryRepository**: 基础词典仓库，提供底层数据查询功能
4. **CandidateWeight**: 候选词权重数据类，用于多维度排序
5. **CandidateEntry**: 候选词结果包装类，包含词条信息和相关权重数据
6. **DuplicateDetector**: 候选词去重检测器，负责处理重复结果
7. **QueryDebugHelper**: 查询调试助手，用于分析和优化查询效果

## 工作流程

新系统的工作流程如下：

1. 用户输入经过 `CandidateManager` 接收并规范化
2. `StagedDictionaryRepository` 根据输入类型(单字母/首字母缩写/拼音)选择合适的查询策略
3. 按照阶段次序查询不同的词典，例如：
   - 阶段1: chars 和 base 词典（基础词典）
   - 阶段2: correlation 和 associational 词典（关联和联想词典）
   - 阶段3: place、people、poetry 等专业词典
   - 阶段4: corrections 和 compatible 词典（纠错和兼容性词典）
4. 查询结果经过 `DuplicateDetector` 去重
5. 使用 `CandidateComparator` 对结果按多维度权重排序
6. 返回最终结果给 `CandidateManager`，再由键盘UI显示给用户

## 查询逻辑分类

系统根据不同的输入类型采用不同的查询逻辑：

### 1. 单字母输入（如输入 "b"）

- 仅查询 chars 词典中以 "b" 开头的单字
- 按照词频(frequency)降序排列
- 单阶段查询，不需要进行后续阶段

### 2. 首字母缩写输入（如输入 "bj"）

- 阶段1: 查询 chars 和 base 词典中 initialLetters="bj" 的词条
- 阶段2: 查询 place 和 people 词典中 initialLetters="bj" 的词条
- 阶段3: 查询 poetry 词典中 initialLetters="bj" 且长度>3 的词条
- 不同阶段的结果经过去重、排序后合并

### 3. 拼音输入（如输入 "beijing"）

- 阶段1: 查询 chars 和 base 词典中 pinyin 匹配的词条
- 若结果不足10个，继续查询阶段2 (correlation、associational)
- 若结果仍不足5个，继续查询阶段3 (place、people、poetry)
- 如果总结果仍不足5个，触发阶段4 (corrections、compatible)

## 排序规则

候选词按以下优先级排序：

1. 阶段优先级：低阶段 > 高阶段（1>2>3>4）
2. 匹配类型：精确匹配(0) > 前缀匹配(1) > 首字母匹配(2)
3. 词频(frequency) + 长度奖励(lengthBonus)

长词（>3字）会获得10分长度奖励，提高排名。

## 调试模式

系统提供了完善的调试功能：

1. `QueryDebugHelper`: 提供查询调试、信息收集和可视化
2. `CandidateDebugView`: 在界面上展示详细的查询过程和结果信息
3. `CandidateBadgeView`: 在候选词上显示词典来源和阶段标记

可在设置中开启调试模式，查看每个候选词的来源、权重和排序依据。

## 旧策略说明

以下旧的策略实现已被移除：

- `CandidateStrategy` 接口及其实现类
- `SingleCharStrategy` 单字母策略
- `ExactSyllableStrategy` 精确音节匹配策略
- `PinyinCompletionStrategy` 拼音补全策略
- `InitialAbbreviationStrategy` 首字母缩写策略
- `SyllableSplitStrategy` 音节拆分策略
- `SyllableSplitStrategyOptimized` 优化版音节拆分策略

所有这些功能已集成到更高效、更统一的分阶段查询系统中。

## Trie预编译工具包

### 概述

本项目提供了完整的Trie预编译工具包，用于将YAML格式的词典文件转换为高性能的二进制Trie数据文件。该工具包支持声调去除、词频筛选、Java兼容格式等功能。

### 工具特性

- **声调处理**：自动去除拼音中的声调符号（如：zhōng → zhong）
- **词频筛选**：支持按百分比筛选高频词汇（如：保留最高频50%的词条）
- **格式兼容**：生成Java ObjectInputStream兼容的二进制格式
- **性能优化**：支持并行加载和内存映射，查询响应时间1-3ms
- **多版本支持**：向后兼容多种数据格式

### 核心文件

| 文件名 | 功能说明 |
|--------|----------|
| `build_java_compatible_trie.py` | 主要构建脚本，生成Java兼容的Trie文件 |
| `TrieManager.kt` | Java端Trie管理器，支持多版本格式加载 |
| `PinyinTrie.kt` | Trie数据结构实现 |

### 使用方法

#### 1. 基础用法

```bash
# 构建基础词典Trie（保留50%高频词）
python build_java_compatible_trie.py

# 自定义输入输出路径
python build_java_compatible_trie.py \
  --input "app/src/main/assets/cn_dicts/base.dict.yaml" \
  --output "app/src/main/assets/trie/base_trie.dat" \
  --percentage 0.5
```

#### 2. 批量构建多个词典

```bash
# 构建关联词典（30%高频词）
python build_java_compatible_trie.py \
  --input "app/src/main/assets/cn_dicts/correlation.dict.yaml" \
  --output "app/src/main/assets/trie/correlation_trie.dat" \
  --percentage 0.3

# 构建联想词典（30%高频词）
python build_java_compatible_trie.py \
  --input "app/src/main/assets/cn_dicts/associational.dict.yaml" \
  --output "app/src/main/assets/trie/associational_trie.dat" \
  --percentage 0.3

# 构建诗词词典（30%高频词）
python build_java_compatible_trie.py \
  --input "app/src/main/assets/cn_dicts/poetry.dict.yaml" \
  --output "app/src/main/assets/trie/poetry_trie.dat" \
  --percentage 0.3
```

#### 3. 验证生成的文件

```bash
# 验证文件格式和内容
python -c "
import struct
with open('app/src/main/assets/trie/base_trie.dat', 'rb') as f:
    version = struct.unpack('>i', f.read(4))[0]
    count = struct.unpack('>i', f.read(4))[0]
    print(f'版本: {version}, 拼音条目数: {count}')
"
```

### 技术实现

#### 数据格式（版本3）

```
[4字节] 版本号 (3)
[4字节] 拼音条目数量
对于每个拼音条目:
  [4字节] 拼音长度
  [N字节] 拼音内容 (UTF-8)
  [4字节] 词语数量
  对于每个词语:
    [4字节] 词语长度
    [N字节] 词语内容 (UTF-8)
    [4字节] 词频
```

#### Java端加载

```kotlin
// 在TrieManager中自动识别并加载
val trieManager = TrieManager.getInstance(context)
trieManager.init() // 自动加载所有预编译Trie文件

// 查询使用
val results = trieManager.searchByPrefix(TrieBuilder.TrieType.BASE, "zh", 20)
```

### 性能指标

| 指标 | 基础词典 | 说明 |
|------|----------|------|
| 原始词条数 | 780,654 | 完整词典大小 |
| 筛选后词条数 | 390,309 | 保留50%高频词 |
| Trie节点数 | 697,397 | 内存中节点数量 |
| 文件大小 | 10.63MB | 压缩后二进制文件 |
| 加载时间 | 4.7秒 | 并行加载耗时 |
| 查询响应 | 1-3ms | 单次查询耗时 |

### 故障排除

#### 常见问题

1. **内存不足**：减少筛选百分比或分批处理
2. **编码错误**：确保输入文件为UTF-8编码
3. **Java兼容性**：使用版本3格式，避免Python pickle

#### 调试模式

```python
# 启用详细日志
import logging
logging.basicConfig(level=logging.DEBUG)

# 验证中间结果
python build_java_compatible_trie.py --debug --verify
```

### 扩展开发

#### 添加新词典类型

1. 在`TrieBuilder.TrieType`枚举中添加新类型
2. 在`TrieManager.getTypeString()`中添加映射
3. 使用构建脚本生成对应的Trie文件

#### 自定义筛选逻辑

```python
def custom_filter(entries, percentage):
    # 自定义筛选逻辑
    # 例如：按词长度和频率综合排序
    sorted_entries = sorted(entries, 
        key=lambda x: (x[2], -len(x[0])), reverse=True)
    return sorted_entries[:int(len(entries) * percentage)]
```

### 更新历史

- **v3.0**: 引入简化二进制格式，提升Java兼容性
- **v2.0**: 支持Java序列化格式
- **v1.0**: 初始版本，基础Trie构建功能 