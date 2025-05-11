package com.shenji.aikeyboard.data

import com.shenji.aikeyboard.ShenjiApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.system.measureTimeMillis

/**
 * 词典管理器类，负责管理内存词库和持久化词库
 */
class DictionaryManager private constructor() {
    
    companion object {
        // 单例实例
        val instance: DictionaryManager by lazy { DictionaryManager() }
        
        // 高频词库类型列表
        val HIGH_FREQUENCY_DICT_TYPES = listOf("chars", "base")
        
        // Trie树插入批次大小
        private const val TREE_INSERT_BATCH_SIZE = 2000
        
        // 最大并行批次数
        private const val MAX_PARALLEL_BATCHES = 8  // 增加并行度
        
        // 词典版本信息文件名
        private const val DICTIONARY_VERSION_FILENAME = "dictionary_versions.bin"
        
        // 初始化函数
        fun init() {
            instance.initialize()
        }
    }
    
    // 词典仓库实例
    private val repository = DictionaryRepository()
    
    // 高频词库Trie树
    private var trieTree = TrieTree()
    
    // 各类型词库的加载总词条数记录
    private val typeEntryCount = mutableMapOf<String, Int>()
    
    // 各类型词库的已加载词条数记录
    private val typeLoadedCount = mutableMapOf<String, Int>()
    
    // 各类型词库的已加载词条数记录公开访问器
    val typeLoadedCountMap: Map<String, Int>
        get() = typeLoadedCount.toMap()
    
    // 各类型词库的版本哈希值（用于检测更新）
    private val typeVersionHash = mutableMapOf<String, String>()
    
    // 日志记录
    private val loadingLogs = ConcurrentHashMap<Long, String>()
    private var logId: Long = 0
    
    // Trie树插入锁，确保并发安全
    private val treeInsertLock = Mutex()
    
    // 是否已加载标志
    private var initialized = false
    
    /**
     * 初始化词典管理器
     */
    fun initialize() {
        // 仅设置初始化标志，不执行任何加载操作
        initialized = true
        Timber.d("词典管理器初始化完成")
    }
    
    /**
     * 搜索匹配的词条
     * @param prefix 要匹配的前缀
     * @param limit 返回结果的最大数量
     * @return 匹配的词条列表
     */
    fun searchWords(prefix: String, limit: Int = 10): List<WordFrequency> {
        // 优先从内存Trie树中查询
        val memoryResults = if (trieTree.isLoaded()) {
            trieTree.search(prefix, limit)
        } else {
            emptyList()
        }
        
        return memoryResults
    }
    
    /**
     * 检查是否已加载完成
     */
    fun isLoaded(): Boolean = trieTree.isLoaded()
    
    /**
     * 重置词典管理器，清空内存词库
     */
    fun reset() {
        // 清空Trie树
        trieTree.clear()
        
        // 清空加载计数
        typeLoadedCount.clear()
        
        Timber.i("词典管理器已重置")
    }
    
    /**
     * 添加日志记录
     */
    private fun addLog(message: String) {
        val timestamp = System.currentTimeMillis()
        val id = logId++
        val formattedTimestamp = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        loadingLogs[id] = "[$formattedTimestamp] $message"
    }
    
    /**
     * 获取所有日志记录
     */
    fun getLogs(): List<String> {
        return loadingLogs.entries
            .sortedBy { it.key }
            .map { it.value }
    }
    
    /**
     * 清空所有日志记录
     */
    fun clearLogs() {
        loadingLogs.clear()
        logId = 0
    }
    
    /**
     * 将字节大小转换为可读性好的字符串形式
     */
    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
    
    /**
     * 导出词典信息到外部存储
     * 此方法导出词典相关的统计信息，用于分析和调试
     */
    fun exportPrecompiledTrieForBuilding() {
        val context = ShenjiApplication.appContext
        
        try {
            addLog("开始导出词典信息用于分析")
            
            // 准备导出文件夹
            val exportDir = File(context.getExternalFilesDir(null), "export")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            // 计算词典版本哈希
            calculateVersionHashes()
            
            // 导出词典版本信息
            val versionExportFile = File(exportDir, DICTIONARY_VERSION_FILENAME)
            ObjectOutputStream(FileOutputStream(versionExportFile)).use { out ->
                out.writeObject(typeVersionHash)
            }
            
            // 导出词库信息文件 (JSON格式便于检查)
            val infoFile = File(exportDir, "dict_info.json")
            val info = StringBuilder()
            info.append("{\n")
            info.append("  \"exportTime\": \"${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\",\n")
            info.append("  \"dictTypes\": [\n")
            
            HIGH_FREQUENCY_DICT_TYPES.forEachIndexed { index, type ->
                val count = repository.getEntryCountByType(type)
                val version = typeVersionHash[type] ?: ""
                info.append("    {\n")
                info.append("      \"type\": \"$type\",\n")
                info.append("      \"count\": $count,\n")
                info.append("      \"version\": \"$version\"\n")
                info.append("    }")
                if (index < HIGH_FREQUENCY_DICT_TYPES.size - 1) {
                    info.append(",")
                }
                info.append("\n")
            }
            
            info.append("  ]\n")
            info.append("}\n")
            
            infoFile.writeText(info.toString())
            
            addLog("词典信息导出成功，位置：${exportDir.absolutePath}")
            Timber.i("词典信息导出成功，位置：${exportDir.absolutePath}")
            
        } catch (e: Exception) {
            Timber.e(e, "导出词典信息失败")
            addLog("导出词典信息失败: ${e.message}")
        }
    }
    
    /**
     * 计算词典版本哈希
     */
    private fun calculateVersionHashes() {
        for (type in HIGH_FREQUENCY_DICT_TYPES) {
            val lastModTime = repository.getLastModifiedTime(type)
            val hash = "v-${lastModTime}-${repository.getEntryCountByType(type)}"
            typeVersionHash[type] = hash
        }
    }
} 