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
- **Trie树算法**：实现高效前缀查询
- **预编译词典**：使用序列化/反序列化机制优化加载
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

5. **拼音处理技术**：
   - 自动分词：将连续拼音分割为正确音节（如beijing→bei jing）
   - 支持超过200个音节的识别和匹配
   - 实现贪婪匹配算法确保最长音节优先匹配

6. **词典管理功能**：
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

### 词典实现核心技术细节

1. **词条模型设计**：
   ```kotlin
   open class Entry : RealmObject {
       @PrimaryKey
       var id: String = ""
       var word: String = ""
       @Index  // 添加索引注解提高查询效率
       var pinyin: String = ""
       var frequency: Int = 0
       var type: String = ""
   }
   ```

2. **多策略查询**：根据输入长度自动调整查询策略，如短拼音精确匹配，长拼音采用模糊匹配

3. **智能拼音分词**：将无空格拼音自动拆分为标准音节，如：
   ```kotlin
   private fun splitPinyinIntoSyllables(pinyin: String): String {
       // 实现贪婪匹配，优先匹配较长的拼音音节
       // 支持200多个中文拼音音节
   }
   ```

4. **候选词排序算法**：将查询结果按词频降序排列，确保高频词优先展示
   ```kotlin
   val matchedEntries = allEntries
       .sortedByDescending { it.frequency } 
       .take(limit)
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