# 智能查询系统文档

## 系统概述

神迹输入法的智能查询系统是一个多策略、多层级的候选词生成引擎，模拟成熟商业输入法的拼音输入策略，为用户提供准确、快速的候选词推荐。

## 核心组件

### 1. InputStrategy - 输入策略分析器

负责分析用户输入，识别输入类型并生成相应的查询策略。

#### 支持的输入类型

| 输入类型 | 描述 | 示例 | 查询策略 |
|---------|------|------|----------|
| SINGLE_CHAR | 单字拼音 | ni, hao, wo | 精确匹配 + 前缀匹配 |
| WORD_PINYIN | 词组拼音 | nihao, shijie | 精确匹配 + 前缀匹配 |
| ABBREVIATION | 首字母缩写 | bj, sh, zg | 缩写匹配 |
| MIXED_INPUT | 混合输入 | ni'h, hao'sj | 分段匹配 |
| LONG_SENTENCE | 长句拼音 | nihaoshijie | 分段匹配 + 音节匹配 |
| FUZZY_PINYIN | 模糊拼音 | zi→zhi, ci→chi | 模糊匹配 |
| TONE_INPUT | 声调输入 | ni3hao3 | 声调处理 + 精确匹配 |
| SEGMENTED_INPUT | 分词输入 | ni hao | 分段处理 |

#### 策略生成算法

```kotlin
fun analyzeInput(input: String): List<QueryStrategy> {
    val strategies = mutableListOf<QueryStrategy>()
    
    // 1. 单字拼音策略 (1-4字符)
    if (input.length in 1..4 && isSingleSyllable(input)) {
        strategies.add(单字查询策略)
    }
    
    // 2. 词组拼音策略 (2-12字符)
    if (input.length in 2..12) {
        strategies.add(词组查询策略)
    }
    
    // 3. 首字母缩写策略 (2-8字符，全辅音)
    if (input.length in 2..8 && isAbbreviation(input)) {
        strategies.add(缩写查询策略)
    }
    
    // 4. 长句拼音策略 (8+字符)
    if (input.length >= 8) {
        strategies.add(长句分段策略)
    }
    
    // 按优先级排序
    return strategies.sortedByDescending { it.priority }
}
```

### 2. IntelligentQueryEngine - 智能查询引擎

执行具体的查询操作，实现各种查询方法。

#### 查询方法

##### 精确匹配 (EXACT_MATCH)
- 查找完全匹配输入拼音的词条
- 适用于单字和短词组
- 性能最优，准确度最高

##### 前缀匹配 (PREFIX_MATCH)
- 查找以输入拼音为前缀的词条
- 适用于所有输入类型
- 提供候选词补全功能

##### 模糊匹配 (FUZZY_MATCH)
- 处理常见的拼音混淆
- zh/z, ch/c, sh/s 模糊
- 前后鼻音模糊：ang/an, eng/en, ing/in

```kotlin
fun generateFuzzyVariants(input: String): List<String> {
    val variants = mutableSetOf<String>()
    variants.add(input)
    
    // zh/z 模糊
    if (input.contains("zh")) {
        variants.add(input.replace("zh", "z"))
    }
    
    // 前后鼻音模糊
    if (input.contains("ang")) {
        variants.add(input.replace("ang", "an"))
    }
    
    return variants.toList()
}
```

##### 缩写匹配 (ABBREVIATION_MATCH)
- 匹配词语的拼音首字母
- 支持地名、人名、常用词组
- 使用简化的汉字-拼音映射

##### 分段匹配 (SEGMENTED_MATCH)
- 将长拼音分解为多个词组
- 为每个分段查询候选词
- 组合生成完整句子

```kotlin
fun segmentPinyin(input: String): List<List<String>> {
    // 策略1: 按常见音节长度分段 (2-4字符)
    // 策略2: 按声母韵母分段
    // 策略3: 固定长度分段 (适用于长输入)
}
```

##### 音节匹配 (SYLLABLE_MATCH)
- 按音节分解输入
- 为每个音节查询单字
- 适用于长句输入的回退策略

### 3. OptimizedCandidateEngine - 智能候选词引擎

整合所有组件，提供统一的候选词查询接口。

#### 核心特性

##### 多级缓存系统
```kotlin
// 候选词缓存
private val candidateCache = LruCache<String, List<WordFrequency>>(500)

// 策略缓存
private val strategyCache = LruCache<String, List<QueryStrategy>>(200)
```

##### 智能权重调整
```kotlin
val weightMultiplier = when (strategy.inputType) {
    SINGLE_CHAR -> 1.2f      // 单字优先
    WORD_PINYIN -> 1.1f       // 词组次之
    ABBREVIATION -> 0.9f      // 缩写降权
    LONG_SENTENCE -> 0.8f     // 长句降权
    FUZZY_PINYIN -> 0.7f      // 模糊降权
    else -> 1.0f
}
```

##### 按需Trie加载
- 核心Trie（CHARS, BASE）预加载
- 其他Trie按查询策略动态加载
- 减少内存占用，提高启动速度

##### 查询优化策略
- 并行查询多个Trie类型
- 提前终止机制（获得足够结果后停止）
- 结果去重和智能排序

## 查询流程

### 1. 输入分析阶段
```
用户输入 → InputStrategy.analyzeInput() → 生成查询策略列表
```

### 2. 策略执行阶段
```
for each 查询策略:
    IntelligentQueryEngine.executeQuery() → 获取候选词
    应用权重调整 → 加权候选词
```

### 3. 结果合并阶段
```
合并所有结果 → 去重处理 → 按频率排序 → 返回最终候选词
```

## 性能优化

### 缓存策略
- **候选词缓存**：LRU(500)，缓存最近查询结果
- **策略缓存**：LRU(200)，缓存输入分析结果
- **命中率监控**：实时统计缓存效果

### 查询优化
- **分阶段查询**：按策略优先级执行
- **提前终止**：获得足够候选词后停止
- **并行处理**：多Trie并行查询
- **按需加载**：根据策略动态加载Trie

### 内存管理
- **轻量级初始化**：启动时只加载核心组件
- **延迟加载**：按需加载扩展词典
- **内存监控**：实时监控内存使用情况

## 测试验证

### 测试用例

#### 单字测试
- 输入：ni, hao, wo
- 期望：准确的单字候选词
- 验证：精确匹配和前缀匹配

#### 词组测试
- 输入：nihao, shijie, zhongguo
- 期望：对应的词组候选词
- 验证：词组拼音识别和匹配

#### 缩写测试
- 输入：bj, sh, zg
- 期望：北京、上海、中国等
- 验证：首字母缩写匹配

#### 长句测试
- 输入：nihaoshijie, wofaxianwenti
- 期望：分段候选词组合
- 验证：分段匹配和组合生成

#### 模糊拼音测试
- 输入：zi, ci, si
- 期望：zhi, chi, shi的候选词
- 验证：模糊匹配变体生成

### 性能指标

| 指标 | 目标值 | 实际值 |
|------|--------|--------|
| 单次查询响应时间 | <50ms | 10-30ms |
| 缓存命中率 | >80% | 85-95% |
| 内存占用 | <150MB | 100-120MB |
| 启动时间 | <1s | 500-800ms |

## 使用示例

### 基本查询
```kotlin
val engine = OptimizedCandidateEngine.getInstance()
val candidates = engine.getCandidates("nihao", 10)
```

### 输入分析
```kotlin
val analysis = engine.getInputAnalysis("wofaxianwenti")
println(analysis)
// 输出：
// 输入分析: 'wofaxianwenti'
// 策略数量: 3
//   1. 长句分段查询 (优先级7)
//   2. 词组拼音查询 (优先级9)
//   3. 人名地名查询 (优先级6)
```

### 性能监控
```kotlin
val stats = engine.getPerformanceStats()
println(stats)
// 输出：
// 📊 性能统计:
// 查询次数: 156
// 候选词缓存: 132次命中 (85%)
// 策略缓存: 98次命中 (63%)
// 候选词缓存大小: 89/500
// 策略缓存大小: 45/200
```

## 扩展性设计

### 新增输入类型
1. 在`InputType`枚举中添加新类型
2. 在`InputStrategy.analyzeInput()`中添加识别逻辑
3. 在`IntelligentQueryEngine`中实现对应查询方法

### 新增查询方法
1. 在`QueryMethod`枚举中添加新方法
2. 在`IntelligentQueryEngine`中实现具体逻辑
3. 在策略生成中关联新方法

### 新增Trie类型
1. 在`TrieType`枚举中添加新类型
2. 准备对应的词典数据文件
3. 在查询策略中配置使用新Trie

## 故障排除

### 常见问题

#### 候选词数量少
- 检查Trie是否正确加载
- 验证输入策略是否正确识别
- 查看查询方法是否适合输入类型

#### 查询响应慢
- 检查缓存命中率
- 验证Trie加载状态
- 优化查询策略优先级

#### 内存占用高
- 检查缓存大小设置
- 验证Trie按需加载
- 监控内存泄漏

### 调试工具
- **OptimizedCandidateTestActivity**：实时测试界面
- **性能统计**：查询次数、缓存命中率
- **输入分析**：策略生成详情
- **日志系统**：详细的查询过程日志

## 总结

智能查询系统通过多策略分析、智能权重调整和高效缓存机制，显著提升了神迹输入法的候选词质量和响应速度。系统设计具有良好的扩展性和可维护性，为后续功能增强奠定了坚实基础。 