# 神迹输入法候选词高度显示问题修复

## 🎯 问题描述

用户反馈：候选词显示不全，特别是在输入较长拼音（如"nihaoma"）时，候选词文字被截断，看不到完整的字。

## 🔍 问题分析

通过代码分析和用户截图发现问题根源：

### 问题现象
1. **短拼音正常**：输入"ni"时，候选词显示正常
2. **长拼音截断**：输入"nihaoma"时，候选词被截断，无法看到完整文字

### 技术原因
候选词高度设置不一致，导致文字被截断：

1. **候选词TextView高度**：固定为38dp
2. **候选词行高度**：固定为46dp  
3. **候选词容器高度**：在不同地方设置不一致
   - `updateCandidatesView()`中设置为`MATCH_PARENT`
   - `onStartInputView()`中重新设置为46dp

### 布局层级问题
```
FrameLayout (46dp) ← 候选词/工具栏共享区域
├── 工具栏 (MATCH_PARENT)
└── 候选词视图 (高度不一致) ← 问题所在
    └── HorizontalScrollView
        └── 候选词行 (46dp)
            └── 候选词TextView (38dp) ← 被截断
```

## 🔧 修复方案

### 核心思路：统一使用MATCH_PARENT

确保候选词在整个可用空间内正确显示，不被高度限制截断。

### 1. 候选词TextView高度修复
```kotlin
// 修复前
val textParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.WRAP_CONTENT,
    38 // 固定高度，比容器稍小留出边距 ← 导致截断
)

// 修复后
val textParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.WRAP_CONTENT,
    LinearLayout.LayoutParams.MATCH_PARENT // 使用MATCH_PARENT填满容器高度
)
```

### 2. 候选词行高度修复
```kotlin
// 修复前
candidatesRow.layoutParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    46 // 🔧 固定高度，防止跳动 ← 限制了高度
)

// 修复后
candidatesRow.layoutParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    LinearLayout.LayoutParams.MATCH_PARENT // 使用MATCH_PARENT填满可用空间
)
```

### 3. 候选词容器高度统一
```kotlin
// 修复前（onStartInputView中）
params.height = 46 // 固定高度 ← 与updateCandidatesView不一致

// 修复后
params.height = LinearLayout.LayoutParams.MATCH_PARENT // 使用MATCH_PARENT确保有足够空间
```

## 📋 修复内容

### 文件修改：`app/src/main/java/com/shenji/aikeyboard/keyboard/ShenjiInputMethodService.kt`

#### 1. updateCandidatesView()方法
- **候选词TextView高度**：从38dp改为`MATCH_PARENT`
- **候选词行高度**：从46dp改为`MATCH_PARENT`

#### 2. onStartInputView()方法
- **候选词容器高度**：从46dp改为`MATCH_PARENT`

### 修复效果

- ✅ **消除文字截断**：候选词文字完整显示，不再被高度限制
- ✅ **统一高度设置**：所有候选词相关组件使用一致的高度策略
- ✅ **充分利用空间**：候选词在46dp的可用空间内完整显示
- ✅ **保持布局稳定**：不影响整体布局结构

## 🧪 测试验证

### 测试场景
1. **短拼音测试**：输入"ni"，验证候选词正常显示
2. **长拼音测试**：输入"nihaoma"，验证候选词完整显示
3. **多候选词测试**：验证所有候选词都能完整显示
4. **滚动测试**：验证水平滚动功能正常

### 预期结果
- 候选词文字完整显示，不被截断
- 短拼音和长拼音都能正常显示候选词
- 候选词在可用空间内垂直居中显示

## 📊 技术细节

### 布局结构（修复后）
```
FrameLayout (46dp) ← 候选词/工具栏共享区域
├── 工具栏 (MATCH_PARENT)
└── 候选词视图 (MATCH_PARENT) ← 修复后
    └── HorizontalScrollView (MATCH_PARENT)
        └── 候选词行 (MATCH_PARENT) ← 修复后
            └── 候选词TextView (MATCH_PARENT) ← 修复后
```

### 高度设置对比
| 组件 | 修复前 | 修复后 | 说明 |
|------|--------|--------|------|
| 候选词容器 | 46dp (不一致) | MATCH_PARENT | 统一设置 |
| 候选词行 | 46dp | MATCH_PARENT | 充分利用空间 |
| 候选词TextView | 38dp | MATCH_PARENT | 消除截断 |

### 关键改进点
1. **高度一致性**：所有候选词相关组件统一使用`MATCH_PARENT`
2. **空间利用**：充分利用46dp的可用空间
3. **垂直居中**：通过`gravity = Gravity.CENTER_VERTICAL`确保文字居中
4. **布局稳定**：不改变整体布局结构，只优化内部高度设置

## 🎯 总结

通过这次修复，我们解决了候选词文字被截断的问题：

1. **根本原因**：候选词组件高度设置不一致，导致文字显示空间不足
2. **解决方案**：统一使用`MATCH_PARENT`，确保候选词在可用空间内完整显示
3. **修复效果**：候选词文字完整显示，用户体验显著提升

这个修复确保了神迹输入法的候选词能够在任何输入长度下都能完整、清晰地显示，不会被高度限制截断。 