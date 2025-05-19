package com.shenji.aikeyboard.data

import com.shenji.aikeyboard.utils.PinyinSegmenterOptimized

/**
 * 优化版拼音分词器 - 使用PinyinSegmenterOptimized进行音节分割
 * 解决了如 "nihao" 被错误分割为 "n + i + hao" 而不是 "ni + hao" 的问题
 */
class PinyinSplitterOptimized {
    
    /**
     * 获取拼音音节表
     */
    fun getPinyinSyllables(): Set<String> {
        // 通过获取PinyinSegmenterOptimized中的所有合法音节
        return PinyinSegmenterOptimized.getValidSyllables()
    }
    
    /**
     * 将无空格拼音拆分为有效音节序列
     * 使用优化版分词器进行拆分
     * @param input 原始拼音输入
     * @return 拆分后的音节列表，若无法拆分则返回空列表
     */
    fun splitPinyin(input: String): List<String> {
        // 清理输入：移除空格，全部转小写
        val cleanInput = input.trim().lowercase().replace(" ", "")
        
        if (cleanInput.isEmpty()) {
            return emptyList()
        }
        
        // 使用优化后的PinyinSegmenterOptimized进行分词
        val result = PinyinSegmenterOptimized.cut(cleanInput)
        
        // 如果分词结果与原输入相同，说明无法拆分
        if (result.size == 1 && result[0] == cleanInput) {
            return emptyList()
        }
        
        return result
    }
    
    /**
     * 获取输入的多种可能拆分方式
     * 目前只返回一种最优解，后续可扩展
     */
    fun getMultipleSplits(input: String): List<List<String>> {
        val result = splitPinyin(input)
        return if (result.isNotEmpty()) {
            listOf(result)
        } else {
            emptyList()
        }
    }
    
    /**
     * 检查是否为首字母+音节的混合模式
     * 不再单独实现，统一由PinyinSegmenterOptimized处理
     */
    fun checkMixedInitialAndSyllable(input: String): List<String> {
        return splitPinyin(input)
    }
    
    /**
     * 尝试多种拆分方式
     * 现在直接使用优化版分词器，不再需要多种备选方案
     */
    fun trySplitPinyin(input: String): List<String> {
        return splitPinyin(input)
    }
} 