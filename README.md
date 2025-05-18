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

## 技术文档详情

### 系统环境与依赖

- **开发语言**: Kotlin 2.0.20
- **目标平台**: Android 9.0+ (API 28 及以上)
- **构建工具**: Gradle 8.3.0
- **JVM 版本**: Java 17

#### 第三方库版本

| 依赖库 | 版本 | 用途 |
|--------|------|------|
| Realm数据库 | 2.3.0 | 词典存储与查询 |
| AndroidX Core KTX | 1.12.0 | Android核心功能扩展 |
| AndroidX AppCompat | 1.6.1 | 向下兼容支持 |
| Material Components | 1.11.0 | UI组件 |
| ConstraintLayout | 2.1.4 | 布局管理 |
| AndroidX Lifecycle | 2.7.0 | 生命周期管理 |
| Facebook Shimmer | 0.5.0 | 加载效果 |
| SnakeYAML | 2.2 | YAML配置解析 |
| Kotlin Coroutines | 1.7.3 | 异步编程 |
| Timber | 5.0.1 | 日志管理 |

### 数据库架构

神迹输入法使用Realm数据库存储词典数据，主要结构如下：

#### 词条实体(Entry)

```kotlin
open class Entry : RealmObject {
    @PrimaryKey
    var id: String = ""         // 词条唯一标识符
    
    var word: String = ""       // 词条文本内容
    
    @Index
    var pinyin: String = ""     // 词条拼音，以空格分隔各字拼音
    
    @Index
    var initialLetters: String = ""  // 拼音首字母缩写
    
    var frequency: Int = 0      // 词频，用于排序
    
    var type: String = ""       // 词典类型
}
```

#### 索引设计
- `pinyin`: 加速拼音查询
- `initialLetters`: 加速首字母查询

### 候选词查询逻辑

应用采用策略模式实现多种候选词查询策略，由`CandidateManager`类统一管理。

#### 主要查询策略

1. **单字母策略**:
   - 用于单个字母输入
   - 查询以该字母开头的拼音对应汉字

2. **精确音节策略**:
   - 匹配标准拼音音节
   - 例如："bei"匹配以"bei"开头的词条

3. **拼音补全策略**:
   - 处理完整拼音输入
   - 例如："beijing"匹配"北京"

4. **首字母缩写策略**:
   - 匹配拼音首字母组合
   - 例如："bj"匹配"北京"(bei jing)

5. **音节拆分策略**:
   - 智能拆分复杂拼音组合
   - 作为兜底方案处理非标准输入

#### 候选词生成流程

1. 接收用户输入
2. 智能选择查询策略
3. 执行查询获取候选词
4. 根据词频排序结果
5. 如结果不足，使用备选策略补充
6. 返回最终候选词列表

#### 拼音拆分逻辑

系统使用`PinyinSplitter`类进行拼音拆分，支持：
- 智能拆分连续拼音为有效音节
- 处理特殊拼音组合和拼写错误
- 支持模糊拼音匹配

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








