package com.shenji.aikeyboard.utils

import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.CandidateManager
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.model.WordFrequency
import com.shenji.aikeyboard.model.Candidate
import com.shenji.aikeyboard.pinyin.InputType
import com.shenji.aikeyboard.pinyin.PinyinQueryEngine
import com.shenji.aikeyboard.viewmodel.PinyinTestViewModel
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
    
    // 标准化的拼音查询引擎
    private val pinyinQueryEngine = PinyinQueryEngine.getInstance()
    
    /**
     * 检查结果
     */
    data class CheckResult(
        val input: String,
        val inputType: InputType,
        val syllables: List<String>,
        val consistent: Boolean,
        val inputMethodResults: List<String>,
        val testToolResults: List<String>,
        val missingInInputMethod: List<String>,
        val missingInTestTool: List<String>
    )
    
    /**
     * 检查指定输入的查询结果是否一致
     */
    suspend fun checkConsistency(input: String): CheckResult = withContext(Dispatchers.IO) {
        Timber.d("检查查询一致性: '$input'")
        
        // 1. 获取输入法的候选词结果
        val inputMethodResults = candidateManager.generateCandidates(input, 20)
        
        // 2. 使用标准化模块获取输入类型和音节
        val queryResult = pinyinQueryEngine.query(input, 1, false)
        val inputType = queryResult.inputType
        val syllables = queryResult.syllables
        
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
            inputType = inputType,
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
                report.appendLine("类型: ${result.inputType}")
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