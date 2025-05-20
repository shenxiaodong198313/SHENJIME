package com.shenji.aikeyboard.utils

import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.CandidateManager
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.data.PinyinSplitterOptimized
import com.shenji.aikeyboard.data.WordFrequency
import com.shenji.aikeyboard.model.Candidate
import com.shenji.aikeyboard.viewmodel.PinyinTestViewModel
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 查询一致性检查器
 * 用于验证输入法和测试工具的查询结果是否一致
 */
class QueryConsistencyChecker {
    
    // 候选词管理器（输入法使用）
    private val candidateManager = CandidateManager(DictionaryRepository())
    
    // 拼音测试ViewModel（测试工具使用）
    private val testViewModel = PinyinTestViewModel()
    
    // 拼音分词器
    private val pinyinSplitter = PinyinSplitterOptimized()
    
    /**
     * 检查结果
     */
    data class CheckResult(
        val input: String,
        val stage: String,
        val syllables: List<String>,
        val consistent: Boolean,
        val inputMethodResults: List<String>,
        val testToolResults: List<String>,
        val missingInInputMethod: List<String>,
        val missingInTestTool: List<String>
    )
    
    /**
     * 判断输入阶段
     * 与PinyinTestViewModel中的classifyInputStage方法逻辑相同
     */
    private fun classifyInputStage(input: String): PinyinTestViewModel.InputStage {
        if (input.isEmpty()) {
            return PinyinTestViewModel.InputStage.UNKNOWN
        }

        if (input.length == 1 && input.matches(Regex("^[a-z]$"))) {
            return PinyinTestViewModel.InputStage.INITIAL_LETTER // 首字母阶段
        }
        
        // 优先检查是否是单字母组合（如"wx", "nh"等），每个字符都是单个字母
        if (input.all { it in 'a'..'z' } && input.length > 1) {
            // 检查每个字符是否是可能的首字母（非有效拼音音节）
            val allSingleLetters = input.all { 
                val singleChar = it.toString()
                !isValidPinyin(singleChar) // 不是有效拼音音节
            }
            
            if (allSingleLetters) {
                Timber.d("识别为单字母组合: '$input'")
                return PinyinTestViewModel.InputStage.ACRONYM // 首字母缩写阶段
            }
        }

        // 单个完整拼音音节，直接归类为拼音补全阶段
        if (isValidPinyin(input) && !input.contains(" ")) {
            return PinyinTestViewModel.InputStage.PINYIN_COMPLETION // 拼音补全阶段
        }

        // 其他情况，尝试音节拆分或作为缩写处理
        val canSplit = canSplitToValidSyllables(input)
        
        // 输出调试信息
        if (!canSplit) {
            Timber.d("无法进行音节拆分，作为首字母缩写处理: '$input'")
        }
        
        return when {
            canSplit -> PinyinTestViewModel.InputStage.SYLLABLE_SPLIT // 音节拆分阶段
            else -> PinyinTestViewModel.InputStage.ACRONYM // 无法拆分则作为首字母缩写阶段
        }
    }
    
    /**
     * 验证是否为有效的拼音音节
     */
    private fun isValidPinyin(input: String): Boolean {
        return pinyinSplitter.getPinyinSyllables().contains(input)
    }
    
    /**
     * 判断是否可以拆分为有效音节
     */
    private fun canSplitToValidSyllables(input: String): Boolean {
        val result = pinyinSplitter.splitPinyin(input)
        return result.isNotEmpty()
    }
    
    /**
     * 检查指定输入的查询结果是否一致
     */
    suspend fun checkConsistency(input: String): CheckResult = withContext(Dispatchers.IO) {
        Timber.d("检查查询一致性: '$input'")
        
        // 1. 获取输入法的候选词结果
        val inputMethodResults = candidateManager.generateCandidates(input, 20)
        
        // 2. 使用测试工具的方式获取结果
        val stage = classifyInputStage(input)
        val syllables = when (stage) {
            PinyinTestViewModel.InputStage.SYLLABLE_SPLIT -> pinyinSplitter.splitPinyin(input)
            else -> emptyList()
        }
        
        // 3. 获取测试工具的候选词结果
        testViewModel.processInput(input)
        val testToolCandidates = testViewModel.candidates.value ?: emptyList()
        
        // 4. 转换为可比较的格式
        val inputMethodWords = inputMethodResults.map { it.word }
        val testToolWords = testToolCandidates.map { it.word }
        
        // 5. 比较结果
        val missingInInputMethod = testToolWords.filter { it !in inputMethodWords }
        val missingInTestTool = inputMethodWords.filter { it !in testToolWords }
        val consistent = missingInInputMethod.isEmpty() && missingInTestTool.isEmpty()
        
        // 6. 记录结果
        if (consistent) {
            Timber.d("查询结果一致: '$input'")
        } else {
            Timber.w("查询结果不一致: '$input'")
            Timber.w("输入法缺少: ${missingInInputMethod.joinToString(", ")}")
            Timber.w("测试工具缺少: ${missingInTestTool.joinToString(", ")}")
        }
        
        return@withContext CheckResult(
            input = input,
            stage = stage.toString(),
            syllables = syllables,
            consistent = consistent,
            inputMethodResults = inputMethodWords,
            testToolResults = testToolWords,
            missingInInputMethod = missingInInputMethod,
            missingInTestTool = missingInTestTool
        )
    }
    
    /**
     * 运行一组测试用例
     */
    suspend fun runTestCases(testCases: List<String>): List<CheckResult> = withContext(Dispatchers.IO) {
        testCases.map { input ->
            checkConsistency(input)
        }
    }
    
    /**
     * 生成测试报告
     */
    fun generateReport(results: List<CheckResult>): String {
        val consistentCount = results.count { it.consistent }
        val inconsistentCount = results.size - consistentCount
        
        val report = StringBuilder()
        report.appendLine("查询一致性测试报告")
        report.appendLine("====================")
        report.appendLine("总测试用例: ${results.size}")
        report.appendLine("一致结果: $consistentCount")
        report.appendLine("不一致结果: $inconsistentCount")
        report.appendLine()
        
        if (inconsistentCount > 0) {
            report.appendLine("不一致详情:")
            results.filterNot { it.consistent }.forEach { result ->
                report.appendLine("-----------------------")
                report.appendLine("输入: '${result.input}'")
                report.appendLine("阶段: ${result.stage}")
                if (result.syllables.isNotEmpty()) {
                    report.appendLine("音节拆分: ${result.syllables.joinToString("+")}")
                }
                report.appendLine("输入法缺少: ${result.missingInInputMethod.joinToString(", ")}")
                report.appendLine("测试工具缺少: ${result.missingInTestTool.joinToString(", ")}")
            }
        }
        
        return report.toString()
    }
    
    /**
     * 运行标准测试集
     */
    fun runStandardTests(): String = runBlocking {
        val testCases = listOf(
            "w",            // 单字母
            "wei",          // 单音节
            "nihao",        // 音节拆分
            "wx",           // 首字母缩写
            "weix",         // 首字母缩写
            "weixin",       // 音节拆分
            "beijing",      // 音节拆分
            "zhongwen",     // 音节拆分
            "zhongguo"      // 音节拆分
        )
        
        val results = runTestCases(testCases)
        generateReport(results)
    }
} 