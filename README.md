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

## 输入测试功能

测试界面包含以下功能：
- Realm词典连接状态检测
- 输入法日志实时显示（可复制和清除）
- 拼音输入测试区域
- 候选词列表展示

测试界面会捕获所有与输入法相关的日志，帮助排查问题。

## 日志系统

应用会自动捕获崩溃并记录到外部存储：
- 日志路径：`Android/data/com.shenji.aikeyboard/files/logs/crash_log.txt`
- 可通过应用主界面的"查看日志"按钮访问并管理日志

## 开发环境

- Android Studio Arctic Fox+
- JDK 17
- Gradle 8.3+
- Android SDK 34+

## 构建命令

```bash
# 构建Debug版本
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```



## 神迹输入法是一款功能完善的Android中文输入法，支持Android 9至15版本。项目具有清晰的架构和丰富的词典系统，采用了MVVM架构和现代Android开发技术。

## 以下是对项目的总结：

## 神迹输入法项目总结

这是一款功能完善的Android中文输入法，支持Android 9至15系统，提供智能的中文输入体验。项目采用MVVM架构设计，使用Kotlin语言开发。

### 产品功能

1. **智能联想**：基于多种词典组合提供智能词语联想
2. **QWERTY标准键盘**：提供标准的拼音输入方式
3. **候选词展示**：水平滚动和扩展网格布局两种展示方式
4. **单字/全拼切换**：支持单字输入和全拼输入两种模式
5. **词典管理**：提供词典统计及浏览功能
6. **输入测试**：专门的测试界面验证输入法功能
7. **日志系统**：自动捕获崩溃信息并支持查看管理

### 词典系统（重点）

词典系统是该输入法的核心，具有以下特点：

1. **多层级词典架构**：
   - 总计9种专业词典，约260万词条
   - 采用分类管理，不同词典用于不同场景优化

2. **词典类型及规模**：
   - 单字词典(chars)：10万条，提供基本汉字输入
   - 基础词典(base)：78万条，包含2-3字常用词组
   - 关联词典(correlation)：57万条，包含4字词组
   - 联想词典(associational)：34万条，包含5字以上词组
   - 兼容词典(compatible)：5千条，处理多音字情况
   - 纠错词典(corrections)：137条，处理常见错误输入
   - 地名词典(place)：4.5万条，专门收录地理位置名称
   - 人名词典(people)：4万条，收录常见人名
   - 诗词词典(poetry)：32万条，收录古典诗词

3. **高效存储设计**：
   - 使用Realm数据库存储词典数据
   - Entry词条模型设计完善，包含词语、拼音、词频、类型等信息
   - 为拼音字段添加索引以加速查询

4. **智能查询算法**：
   - 按输入长度采用不同查询策略：
     * 1-2字母：优先查询单字词库，精准匹配
     * 3-4字母：基础词库，完全+前缀匹配
     * 5字母以上：联想词库，包含匹配
   - 同时支持带空格和无空格的拼音匹配



5. **词典管理功能**：
   - 词典统计展示（词条总数、内存占用、模块数量）
   - 词典模块浏览（支持分页加载，每页500条）
   - 详细词条信息（词语、拼音、词频）

### 技术实现

1. **架构设计**：
   - MVVM架构：使用ViewModel与Repository模式
   - 单例模式：词典管理器采用单例设计
   - 观察者模式：使用LiveData和协程Flow监控状态

2. **关键技术**：
   - 高效的Realm数据库用于词典存储
   - 协程处理异步查询，确保UI流畅
   - Kotlin语言特性的充分利用

3. **性能优化**：
   - 分页加载避免一次性载入过多数据
   - 数据库索引优化查询效率
   - 拼音正则化处理提高匹配准确率

4. **用户界面**：
   - 骨架屏加载效果提升用户体验
   - 滚动和网格两种候选词展示方式
   - 自适应键盘布局


   ```
## 完整汉语拼音音节表

val PINYIN_SYLLABLES = setOf(
    // 零声母
    "a", "ai", "an", "ang", "ao",
    "o", "ou",
    "e", "en", "eng", "er",
    "i", "ia", "ie", "iao", "iu", "iong", "in", "ing",
    "u", "ua", "uo", "uai", "ui", "uan", "un", "uang", "ung",
    "ü", "üe", "üan", "ün",
    // 整体认读
    "zhi", "chi", "shi", "ri", "zi", "ci", "si", "yi", "wu", "yu", "ye", "yue", "yuan", "yin", "yun", "ying",
    // 声母 b
    "ba", "bo", "bai", "bei", "bao", "ban", "ben", "bang", "beng", "bi", "bie", "biao", "bian", "bin", "bing", "bu",
    // 声母 p
    "pa", "po", "pai", "pao", "pou", "pan", "pen", "pei", "pang", "peng", "pi", "pie", "piao", "pian", "pin", "ping", "pu",
    // 声母 m
    "ma", "mo", "me", "mai", "mao", "mou", "man", "men", "mei", "mang", "meng", "mi", "mie", "miao", "miu", "mian", "min", "ming", "mu",
    // 声母 f
    "fa", "fo", "fei", "fou", "fan", "fen", "fang", "feng", "fu",
    // 声母 d
    "da", "de", "dai", "dai", "dan", "dang", "deng", "di", "die", "diao", "diu", "dian", "ding", "dong", "dou", "du", "duan", "dun", "duo",
    // 声母 t
    "ta", "te", "tai", "tao", "tou", "tan", "tang", "teng", "ti", "tie", "tiao", "tian", "ting", "tong", "tu", "tuan", "tun", "tuo",
    // 声母 n
    "na", "nai", "ne", "nao", "nou", "nan", "nen", "neng", "ni", "nie", "niao", "niu", "nian", "nin", "niang", "ning", "nong", "nu", "nuan", "nun", "nuo", "nü", "nüe",
    // 声母 l
    "la", "le", "lo", "lai", "lei", "lao", "lou", "lan", "lang", "leng", "li", "lie", "liao", "liu", "lian", "lin", "liang", "ling", "long", "lu", "luan", "lun", "luo", "lü", "lüe",
    // 声母 g
    "ga", "ge", "gai", "gei", "gao", "gou", "gan", "gen", "gang", "geng", "gong", "gu", "gua", "guai", "guan", "guang", "gui", "guo",
    // 声母 k
    "ka", "ke", "kai", "kao", "kou", "kan", "ken", "kang", "keng", "kong", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
    // 声母 h
    "ha", "he", "hai", "hao", "hou", "han", "hen", "hang", "heng", "hong", "hu", "hua", "huai", "huan", "huang", "hui", "huo", "hun",
    // 声母 j
    "ji", "jia", "jie", "jiao", "jiu", "jian", "jin", "jiang", "jing", "jiong", "ju", "juan", "jun", "jue",
    // 声母 q
    "qi", "qia", "qie", "qiao", "qiu", "qian", "qin", "qiang", "qing", "qiong", "qu", "quan", "qun", "que",
    // 声母 x
    "xi", "xia", "xie", "xiao", "xiu", "xian", "xin", "xiang", "xing", "xiong", "xu", "xuan", "xun", "xue",
    // 声母 zh
    "zhi", "zha", "zhe", "zhi", "zhai", "zhao", "zhou", "zhan", "zhen", "zhang", "zheng", "zhong", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhun", "zhui", "zhuo",
    // 声母 ch
    "chi", "cha", "che", "chi", "chai", "chao", "chou", "chan", "chen", "chang", "cheng", "chong", "chu", "chua", "chuai", "chuan", "chuang", "chun", "chui", "chuo",
    // 声母 sh
    "shi", "sha", "she", "shi", "shai", "shao", "shou", "shan", "shen", "shang", "sheng", "shu", "shua", "shuai", "shuan", "shuang", "shun", "shui", "shuo",
    // 声母 r
    "ri", "re", "rao", "rou", "ran", "ren", "rang", "reng", "rong", "ru", "rui", "ruan", "run", "ruo",
    // 声母 z
    "zi", "za", "ze", "zuo", "zan", "zou", "zang", "zen", "zeng", "zong", "zu", "zuan", "zun", "zui", "zuo",
    // 声母 c
    "ci", "ca", "ce", "cuo", "can", "cou", "cang", "cen", "ceng", "cong", "cu", "cuan", "cun", "cui", "cuo",
    // 声母 s
    "si", "sa", "se", "suo", "san", "sou", "sang", "sen", "seng", "song", "su", "suan", "sun", "sui", "suo"
)


## 当前的Realm数据库设计:
这是一个Android输入法应用（ShenjiAIKeyboard）中的Realm数据库设计，主要用于管理输入法的词典数据。
数据模型设计
Entry类（核心数据模型）:
id: 主键（@PrimaryKey），字符串类型
word: 词语/汉字，字符串类型
pinyin: 拼音，字符串类型，带有索引（@Index）优化查询
initialLetters: 拼音首字母，字符串类型，带有索引（@Index）优化查询
frequency: 词频，整型
type: 词典类型，字符串类型
数据库配置
Realm版本: 1.11.0（使用Kotlin版Realm）
数据库文件: "shenji_dict.realm"，存储在app内部存储的"dictionaries"目录
初始数据: 从assets资源中预构建并复制到app内部存储
配置方式: 使用RealmConfiguration.Builder配置，包含Entry模型类
词典类型划分
系统包含多种词典类型:
chars: 单字词典
base: 基础词典
correlation: 关联词典
associational: 联想词典
compatible: 兼容词典
corrections: 纠错词典
place: 地名词典
people: 人名词典
poetry: 诗词词典
查询策略模式
采用策略模式处理不同输入场景的候选词查询:
EmptyOrSingleCharStrategy: 处理空输入或单字符输入，主要提供高频单字和基础词
TwoToThreeCharStrategy: 处理2-3字符输入，主要从base基础词库查询
查询逻辑特点:
优先考虑完全匹配的词条
按词频排序
考虑拼音前缀匹配
根据输入长度自动选择不同策略
数据库操作主要功能
初始化与预加载:
应用启动时初始化Realm数据库
延迟加载词典以避免启动卡顿
如果词典文件不存在则从assets中复制预构建数据库
查询功能:
根据拼音前缀搜索候选词
处理首字母输入模式
拼音分词处理（如"beijing"→"bei jing"）
根据汉字查询词条
管理功能:
获取各类型词典的词条数量
分页获取词条数据
获取词典样本条目
词典模块信息获取
设计特点
性能优化:
使用索引（@Index）加速拼音和首字母查询
策略模式根据输入长度优化查询方法
延迟加载避免启动卡顿
查询优先级:
完全匹配 > 前缀匹配
高词频 > 低词频
短词组 > 长词组（同词频条件下）
错误处理:
全面的异常捕获和日志记录
在查询失败时提供默认值或空结果






# 神机输入法候选词生成规则详细分析

## 一、策略模式设计

整个候选词生成系统采用了策略模式（Strategy Pattern），根据输入拼音的长度和特征选择不同的查询策略：

1. **策略接口**：`CandidateStrategy`定义了候选词查询的基本接口
2. **策略工厂**：`CandidateStrategyFactory`根据输入特征动态选择合适的策略
3. **具体策略实现**：
   - `EmptyOrSingleCharStrategy`：空输入或单字符输入策略
   - `TwoToThreeCharStrategy`：2-3字符输入策略
   - `FourCharStrategy`：4字符输入策略
   - `LongWordStrategy`：5字符以上输入策略
   - `InitialsQueryStrategy`：拼音首字母查询策略

## 二、策略选择规则

候选词查询策略是通过`CandidateStrategyFactory`根据以下规则选择的：

```kotlin
fun getStrategy(pinyinLength: Int, rawInput: String = ""): CandidateStrategy {
    // 1. 首先检测是否为首字母输入模式
    if (rawInput.isNotEmpty() && PinyinInitialUtils.isPossibleInitials(rawInput)) {
        return InitialsQueryStrategy()
    }
    
    // 2. 根据拼音长度选择策略
    return when {
        pinyinLength <= 1 -> EmptyOrSingleCharStrategy()  // 0-1个字符
        pinyinLength <= 3 -> TwoToThreeCharStrategy()     // 2-3个字符
        pinyinLength == 4 -> FourCharStrategy()           // 4个字符
        else -> LongWordStrategy()                        // 5个字符以上
    }
}
```

## 三、拼音处理机制

在查询候选词前，会对输入的拼音进行规范化处理：

1. **拼音分词处理**：通过`PinyinSplitter`将无空格拼音转为带空格分隔的标准音节
   - 如：`"jiating"` → `"jia ting"`
   - 采用贪婪匹配算法，从右到左优先尝试最长的音节
   - 内置完整拼音音节表（按长度从6字母到1字母排序）

2. **首字母模式检测**：通过`PinyinInitialUtils.isPossibleInitials()`识别首字母输入模式
   - 规则：纯小写字母且无空格的输入被视为可能的首字母缩写

3. **音节有效性验证**：检查分词结果中的每个音节是否为有效拼音音节

## 四、各策略的查询逻辑详解

### 1. 空输入或单字符策略（EmptyOrSingleCharStrategy）

设计用于用户首次输入或输入单个字符时，主要查询核心是：

```kotlin
// 查询逻辑：
// 1. 从chars词典获取高频单字（占总限制结果的一半空间）
val charsEntries = realm.query<Entry>("type == 'chars'")
    .limit(limit)
    .find()
    .filter { it.type !in excludeTypes }
    .sortedByDescending { it.frequency }
    .take(limit / 2)

// 2. 如有输入，查询以该字符开头的单字
if (pinyin.isNotEmpty()) {
    val matchedChars = realm.query<Entry>(
        "pinyin BEGINSWITH $0 AND type == 'chars'", 
        normalizedPrefix
    ).limit(limit).find()...
    
// 3. 结果不足，从base词典补充高频词
val baseEntries = realm.query<Entry>("type == 'base'")
    .limit(limit - results.size)
    .find()...
```

### 2. 2-3字符输入策略（TwoToThreeCharStrategy）

设计用于短词输入场景，核心逻辑：

```kotlin
// 查询逻辑：
// 1. 查询完全匹配的词条（拼音完全相同）
val exactMatchEntries = realm.query<Entry>("pinyin == $0 AND type == 'base'", normalizedPrefix)...

// 2. 从base词库中查询2-3字词组（前缀匹配）
val baseEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'base'", normalizedPrefix)...

// 3. 无空格匹配查询
val noSpaceEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'base'", noSpacePrefix)...

// 4. 从compatible词库中查询多音字词组
val compatibleEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'compatible'", normalizedPrefix)...

// 5. 添加联想的3字词组，作为辅助提示
val threeCharEntries = realm.query<Entry>("LENGTH(word) == 3 AND type == 'correlation'")...
```

**结果排序规则**:
1. 完全匹配的词排在最前面
2. 然后按照词频从高到低排序
3. 对于词频相同的词组，2字词组排在3字词组之前
4. 联想的3字词组排在最后

### 3. 4字符输入策略（FourCharStrategy）

专为成语和四字词组设计，核心逻辑：

```kotlin
// 查询逻辑：
// 1. 查询完全匹配的词条
val exactMatchEntries = realm.query<Entry>("pinyin == $0", normalizedPrefix)...
    
// 2. 优先从correlation关联词库查询4字词组
val correlationEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'correlation'", normalizedPrefix)...
    
// 3. 无空格匹配查询
val noSpaceEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'correlation'", noSpacePrefix)...
    
// 4. 从base词库补充查询
val baseEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND LENGTH(word) == 4 AND type == 'base'", normalizedPrefix)...
    
// 5. 从compatible兼容词库查询多音字词组
val compatibleEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'compatible'", normalizedPrefix)...
    
// 6. 添加联想的5字以上词组，作为辅助提示
val longWordEntries = realm.query<Entry>("LENGTH(word) >= 5 AND type == 'associational'")...
```

**结果排序规则**:
1. 完全匹配的词排在最前面
2. 然后按照词频从高到低排序
3. 对于词频相同的词组，4字词在5字以上词前面
4. 同类词组按字典序排列

### 4. 长词策略（LongWordStrategy）

针对5个字符以上较长输入，核心逻辑：

```kotlin
// 查询逻辑：
// 1. 查询完全匹配的词条
val exactMatchEntries = realm.query<Entry>("pinyin == $0", normalizedPrefix)...
    
// 2. 优先从associational联想词库查询
val associationalEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'associational'", normalizedPrefix)...
    
// 3. 无空格匹配查询
val noSpaceEntries = realm.query<Entry>("pinyin BEGINSWITH $0 AND type == 'associational'", noSpacePrefix)...
    
// 4. 包含匹配（联想搜索）
val containsEntries = realm.query<Entry>("pinyin CONTAINS $0 AND type == 'associational'", normalizedPrefix)...
    
// 5. 从correlation和base词库补充查询长词
val otherLongEntries = realm.query<Entry>("LENGTH(word) >= 5 AND (type == 'correlation' OR type == 'base')")...
    
// 6. 保留一些4字词组作为参考
val fourCharEntries = realm.query<Entry>("LENGTH(word) == 4 AND type == 'correlation'")...
```

**结果排序规则**:
1. 完全匹配的词优先
2. 按照词频从高到低排序
3. 最后按字典序排列

### 5. 首字母查询策略（InitialsQueryStrategy）

专为首字母输入模式设计，如"wx"(微信)，"zdl"(知道了)，核心逻辑：

```kotlin
// 查询逻辑：
// 1. 精确匹配首字母
val exactMatches = realm.query<Entry>("initialLetters == $0", initials)...
    
// 2. 如结果不足，尝试首字母前缀匹配
val prefixMatches = realm.query<Entry>("initialLetters BEGINSWITH $0", initials)...
```

这种模式依靠`Entry`模型中的`initialLetters`字段，该字段存储了拼音的首字母缩写。

## 五、词典类型分层与查询优先级

系统将词典分为多种类型，每种类型有特定用途，查询时有优先级顺序：

1. **chars**: 单字词典，用于单字推荐
2. **base**: 基础词典，日常高频词汇
3. **correlation**: 关联词典，主要用于4字成语和相关词
4. **associational**: 联想词典，用于长词和联想式推荐
5. **compatible**: 兼容词典，处理多音字和特殊发音情况
6. **corrections**: 纠错词典，处理常见输入错误
7. **place**: 地名词典
8. **people**: 人名词典
9. **poetry**: 诗词词典

**查询优先级规则**：
- 完全匹配 > 前缀匹配 > 包含匹配
- 不同类型词典有各自适用的场景和优先级
- 具体策略会根据输入长度和特征，优先从最相关的词典类型查询

## 六、结果优化与排序机制

无论使用哪种策略，系统都遵循以下候选词排序原则：

1. **匹配精确度**：完全匹配 > 前缀匹配 > 包含匹配
2. **词频优先**：高词频的候选词排在前面
3. **词长优先**：在词频相同的情况下，短词优先于长词
4. **词典类型**：不同策略会优先考虑特定类型的词典
5. **多音字处理**：通过compatible词典类型处理多音字情况
6. **辅助联想**：会适当插入一些与主要查询无直接关系的联想词，增强输入体验

## 七、特殊处理机制

1. **无空格拼音处理**：
   - 用户输入的无空格拼音会被自动分词，如"nihao" → "ni hao"
   - 同时也保留无空格查询，确保兼容不同输入习惯

2. **首字母缩写处理**：
   - 能识别和处理拼音首字母缩写输入模式
   - 通过`initialLetters`索引字段加速首字母匹配查询

3. **异常处理**：
   - 各个查询步骤均有异常捕获，确保即使部分查询失败也能返回可用结果
   - 在无法匹配时提供默认候选项

4. **性能优化**：
   - 使用limit限制查询范围
   - 合理设置查询条件，避免全表扫描
   - 利用索引字段(pinyin和initialLetters)加速查询
   - 错误情况下提供最小可用结果集

这个候选词生成系统通过多层次、多策略的设计，结合拼音分词、词典分类和排序规则，实现了高效、精准的输入推荐功能，能够适应各种输入场景和用户习惯。
