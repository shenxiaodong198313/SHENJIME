package com.shenji.aikeyboard.data.trie

import android.content.Context
import com.shenji.aikeyboard.data.DictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Trie树构建器
 * 负责从词典构建Trie树并提供序列化/反序列化功能
 */
class TrieBuilder(private val context: Context) {

    // 词典仓库，用于获取词语数据
    private val repository = DictionaryRepository()
    
    // Trie树类型枚举
    enum class TrieType {
        CHARS, // 单字词典
        BASE,  // 基础词典
    }
    
    /**
     * 构建单字Trie树
     * @param progressCallback 进度回调函数
     * @return 构建完成的PinyinTrie对象
     */
    suspend fun buildCharsTrie(progressCallback: (Int, String) -> Unit): PinyinTrie = withContext(Dispatchers.IO) {
        val trie = PinyinTrie()
        
        try {
            progressCallback(0, "开始从单字词典构建Trie树")
            
            // 获取单字列表
            val chars = repository.getAllChars()
            val totalCount = chars.size
            
            progressCallback(10, "获取到${totalCount}个单字，开始构建")
            
            // 统计数据
            var processedCount = 0
            var skipCount = 0
            var multiPinyinCount = 0
            var totalPinyinCount = 0
            
            // 插入所有单字到Trie树
            chars.forEachIndexed { index, entry ->
                // 获取并处理拼音
                val pinyin = entry.pinyin?.lowercase()
                if (pinyin.isNullOrBlank()) {
                    skipCount++
                    return@forEachIndexed
                }
                
                // 拆分多音字情况
                val pinyinList = pinyin.split(",").filter { it.isNotBlank() }
                if (pinyinList.size > 1) {
                    multiPinyinCount++
                }
                
                if (pinyinList.isEmpty()) {
                    skipCount++
                    return@forEachIndexed
                }
                
                totalPinyinCount += pinyinList.size
                processedCount++
                
                // 为每个拼音创建Trie路径
                pinyinList.forEach { p -> 
                    trie.insert(p.trim(), entry.word, entry.frequency ?: 0)
                }
                
                // 每处理100个单字更新一次进度
                if (index % 100 == 0) {
                    val progress = 10 + (index * 80) / totalCount
                    progressCallback(progress, "已处理 ${index}/${totalCount} 个单字")
                }
            }
            
            progressCallback(90, "Trie树构建完成，正在优化内存")
            
            // 获取内存统计信息
            val stats = trie.getMemoryStats()
            
            // 打印详细的构建统计信息
            val statsSummary = "单字总数: $totalCount, 实际处理: $processedCount, " +
                      "跳过: $skipCount, 多音字: $multiPinyinCount, " +
                      "拼音总数: $totalPinyinCount"
            Timber.d("Trie构建统计: $statsSummary")
            
            progressCallback(100, "完成: ${stats.toString()} ($statsSummary)")
            
            return@withContext trie
        } catch (e: Exception) {
            Timber.e(e, "构建单字Trie树失败")
            progressCallback(-1, "构建失败: ${e.message}")
            throw e
        }
    }
    
    /**
     * 保存Trie树到文件
     * @param trie 要保存的Trie树
     * @param type Trie树类型
     * @return 保存的文件对象
     */
    fun saveTrie(trie: PinyinTrie, type: TrieType): File {
        val fileName = when (type) {
            TrieType.CHARS -> "chars_trie.dat"
            TrieType.BASE -> "base_trie.dat"
        }
        
        val file = File(context.filesDir, "trie/$fileName")
        
        try {
            // 确保目录存在
            file.parentFile?.mkdirs()
            
            // 直接使用ObjectOutputStream，不再使用GZIP压缩
            FileOutputStream(file).use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(trie)
                }
            }
            
            Timber.d("Trie保存成功: ${file.path}, 文件大小: ${file.length() / 1024}KB")
            return file
        } catch (e: Exception) {
            Timber.e(e, "Trie保存失败: ${e.message}")
            throw e
        }
    }
    
    /**
     * 从文件加载Trie树
     * @param type Trie树类型
     * @return 加载的PinyinTrie对象，如果加载失败返回null
     */
    fun loadTrie(type: TrieType): PinyinTrie? {
        val fileName = when (type) {
            TrieType.CHARS -> "chars_trie.dat"
            TrieType.BASE -> "base_trie.dat"
        }
        
        val file = File(context.filesDir, "trie/$fileName")
        
        if (!file.exists()) {
            Timber.d("Trie文件不存在: ${file.path}")
            return null
        }
        
        try {
            // 尝试多种方式读取文件，兼容旧格式
            return tryLoadWithDirectObjectStream(file) ?: tryLoadWithGZIP(file)
        } catch (e: Exception) {
            Timber.e(e, "Trie加载失败: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 尝试使用直接的ObjectInputStream加载
     */
    private fun tryLoadWithDirectObjectStream(file: File): PinyinTrie? {
        return try {
            FileInputStream(file).use { fis ->
                ObjectInputStream(fis).use { ois ->
                    val trie = ois.readObject() as PinyinTrie
                    
                    // 验证Trie树是否为空
                    if (trie.isEmpty()) {
                        Timber.w("加载的Trie树为空")
                        null
                    } else {
                        Timber.d("使用标准ObjectInputStream加载Trie成功: ${file.path}")
                        trie
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d("标准ObjectInputStream加载失败: ${e.message}, 尝试其他方式")
            null
        }
    }
    
    /**
     * 尝试使用GZIP解压缩加载（兼容旧格式）
     */
    private fun tryLoadWithGZIP(file: File): PinyinTrie? {
        return try {
            FileInputStream(file).use { fis ->
                java.util.zip.GZIPInputStream(fis).use { gis ->
                    ObjectInputStream(gis).use { ois ->
                        val trie = ois.readObject() as PinyinTrie
                        
                        // 验证Trie树是否为空
                        if (trie.isEmpty()) {
                            Timber.w("加载的GZIP Trie树为空")
                            null
                        } else {
                            Timber.d("使用GZIP加载Trie成功: ${file.path}")
                            trie
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "GZIP加载失败: ${e.message}")
            null
        }
    }
} 