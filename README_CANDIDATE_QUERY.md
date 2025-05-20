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