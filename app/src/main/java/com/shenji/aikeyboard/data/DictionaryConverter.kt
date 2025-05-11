package com.shenji.aikeyboard.data

import android.content.Context
import android.os.Environment
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.yaml.snakeyaml.Yaml
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.Date

/**
 * 词典转换服务
 */
class DictionaryConverter(private val context: Context) {

    // 词典列表
    private val dictionaries = mapOf(
        "chars.dict.yaml" to "字表：包含CJK字库基础区所有具有读音的字",
        "base.dict.yaml" to "基础词库：包含2-3字词组",
        "correlation.dict.yaml" to "关联词库：包含4字词组",
        "associational.dict.yaml" to "联想词库：包含5字以上词组",
        "compatible.dict.yaml" to "兼容词库：包含多音字词组",
        "corrections.dict.yaml" to "错音错字：错音错字词组",
        "place.dict.yaml" to "地区表：包含中华人民共和国地理位置",
        "people.dict.yaml" to "人名表：包含常用姓名、历史名人大全",
        "poetry.dict.yaml" to "诗词表：诗词大全"
    )

    // 转换进度信息
    data class ConversionProgress(
        val currentDict: String = "",
        val currentCount: Int = 0,
        val totalDicts: Int = 0,
        val isCompleted: Boolean = false,
        val dbPath: String = "",
        val error: String? = null
    )

    /**
     * 执行词典转换
     */
    fun convertDictionaries(): Flow<ConversionProgress> = flow {
        val totalDicts = dictionaries.size
        var currentDictIndex = 0
        var dbPath = ""

        try {
            // 配置Realm
            val config = RealmConfiguration.create(schema = setOf(Entry::class, DictionaryInfo::class))
            val realm = Realm.open(config)
            
            // 删除旧数据
            realm.writeBlocking {
                val entries = this.query<Entry>().find()
                delete(entries)
                
                val dictInfos = this.query<DictionaryInfo>().find()
                delete(dictInfos)
            }

            // 处理每个词典
            dictionaries.forEach { (fileName, description) ->
                currentDictIndex++
                emit(ConversionProgress(
                    currentDict = fileName,
                    currentCount = 0,
                    totalDicts = totalDicts
                ))

                // 解析词典
                val entries = parseYamlDict(fileName)
                var processedCount = 0

                // 批量写入Realm
                entries.chunked(1000).forEach { chunk ->
                    realm.writeBlocking {
                        chunk.forEach { entry ->
                            copyToRealm(entry)
                            processedCount++
                            
                            // 每1000条更新一次进度
                            if (processedCount % 1000 == 0) {
                                // 不能从这里emit，因为在事务内部
                            }
                        }
                    }
                    
                    // 事务外更新进度
                    emit(ConversionProgress(
                        currentDict = fileName,
                        currentCount = processedCount,
                        totalDicts = totalDicts
                    ))
                }

                // 添加词典信息
                realm.writeBlocking {
                    val dictInfo = DictionaryInfo().apply {
                        dictName = fileName.substringBefore(".dict.yaml")
                        this.fileName = fileName
                        this.description = description
                        entryCount = processedCount
                        convertedAt = System.currentTimeMillis()
                    }
                    copyToRealm(dictInfo)
                }

                // 发送词典处理完成进度
                emit(ConversionProgress(
                    currentDict = fileName,
                    currentCount = processedCount,
                    totalDicts = totalDicts
                ))
            }

            // 复制数据库文件到下载目录
            dbPath = copyRealmFile(realm.configuration.path)
            
            // 关闭Realm
            realm.close()
            
            // 发送完成信息
            emit(ConversionProgress(
                currentDict = "完成",
                currentCount = 0,
                totalDicts = totalDicts,
                isCompleted = true,
                dbPath = dbPath
            ))
            
        } catch (e: Exception) {
            Timber.e(e, "词典转换失败")
            emit(ConversionProgress(
                currentDict = "错误",
                totalDicts = totalDicts,
                error = e.message
            ))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 解析YAML词典文件
     */
    private fun parseYamlDict(fileName: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        try {
            val inputStream: InputStream = context.assets.open("cn_dicts/$fileName")
            inputStream.bufferedReader().use { reader ->
                reader.lineSequence()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .forEach { line ->
                        val parts = line.split("\t")
                        if (parts.size >= 3) {
                            val word = parts[0].trim()
                            val pinyin = parts[1].trim()
                            val frequency = parts[2].trim().toIntOrNull() ?: 0
                            
                            if (word.isNotBlank() && pinyin.isNotBlank()) {
                                entries.add(Entry().apply {
                                    this.word = word
                                    this.pinyin = pinyin
                                    this.frequency = frequency
                                    this.type = fileName.substringBefore(".dict.yaml")
                                })
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            Timber.e(e, "解析词典文件失败: $fileName")
        }
        return entries
    }

    /**
     * 复制Realm文件到下载目录
     */
    private fun copyRealmFile(realmPath: String): String {
        val realmFile = File(realmPath)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destFile = File(downloadsDir, "shenji_dict.realm")
        
        if(realmFile.exists()) {
            realmFile.copyTo(destFile, overwrite = true)
            return destFile.absolutePath
        }
        
        return ""
    }
} 