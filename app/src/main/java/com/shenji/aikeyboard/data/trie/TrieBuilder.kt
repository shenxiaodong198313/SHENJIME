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
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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
            
            // 插入所有单字到Trie树
            chars.forEachIndexed { index, entry ->
                val pinyin = entry.pinyin?.lowercase() ?: return@forEachIndexed
                // 为每个拼音创建Trie路径
                pinyin.split(",").forEach { p -> 
                    trie.insert(p, entry.word, entry.frequency ?: 0)
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
            progressCallback(100, "完成: ${stats.toString()}")
            
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
            
            // 使用GZIP压缩以减小文件大小
            FileOutputStream(file).use { fos ->
                GZIPOutputStream(fos).use { gzos ->
                    ObjectOutputStream(gzos).use { oos ->
                        oos.writeObject(trie)
                    }
                }
            }
            
            Timber.d("Trie保存成功: ${file.path}, 文件大小: ${file.length() / 1024}KB")
            return file
        } catch (e: Exception) {
            Timber.e(e, "Trie保存失败")
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
            // 使用GZIP解压缩
            FileInputStream(file).use { fis ->
                GZIPInputStream(fis).use { gzis ->
                    ObjectInputStream(gzis).use { ois ->
                        val trie = ois.readObject() as PinyinTrie
                        
                        // 验证Trie树是否为空
                        if (trie.isEmpty()) {
                            Timber.w("加载的Trie树为空")
                            return null
                        }
                        
                        Timber.d("Trie加载成功: ${file.path}")
                        return trie
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Trie加载失败")
            return null
        }
    }
} 