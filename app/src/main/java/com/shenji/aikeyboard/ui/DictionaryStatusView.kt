package com.shenji.aikeyboard.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import androidx.core.content.ContextCompat
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ShenjiApplication
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.data.trie.TrieManager
import com.shenji.aikeyboard.data.trie.TrieType
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File

/**
 * 词库状态显示组件
 * 显示Realm数据库状态和Trie词典状态的监控
 */
class DictionaryStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var realmStatusText: TextView
    private lateinit var trieStatusText: TextView
    private lateinit var refreshButton: Button
    
    private var refreshJob: Job? = null
    
    init {
        orientation = VERTICAL
        try {
            Timber.d("DictionaryStatusView开始初始化")
            initView()
            Timber.d("DictionaryStatusView视图初始化完成")
            refreshStatus()
            Timber.d("DictionaryStatusView初始化完成")
        } catch (e: Exception) {
            Timber.e(e, "DictionaryStatusView初始化失败: ${e.message}")
        }
    }
    
    private fun initView() {
        try {
            Timber.d("开始加载词库状态布局")
            LayoutInflater.from(context).inflate(R.layout.view_dictionary_status, this, true)
            Timber.d("布局加载完成")
            
            realmStatusText = findViewById(R.id.realm_status_text)
            trieStatusText = findViewById(R.id.trie_status_text)
            refreshButton = findViewById(R.id.refresh_button)
            Timber.d("视图组件初始化完成")
            
            refreshButton.setOnClickListener {
                refreshStatus()
            }
            Timber.d("事件监听器设置完成")
        } catch (e: Exception) {
            Timber.e(e, "initView失败: ${e.message}")
        }
    }
    
    /**
     * 刷新词库状态信息
     */
    fun refreshStatus() {
        // 取消之前的刷新任务
        refreshJob?.cancel()
        
        refreshJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // 显示刷新中状态
                refreshButton.isEnabled = false
                refreshButton.text = "刷新中..."
                
                // 获取Realm状态
                val realmStatus = withContext(Dispatchers.IO) {
                    getRealmStatus()
                }
                
                // 获取Trie状态
                val trieStatus = withContext(Dispatchers.IO) {
                    getTrieStatus()
                }
                
                // 更新UI
                updateRealmStatus(realmStatus)
                updateTrieStatus(trieStatus)
                
            } catch (e: Exception) {
                Timber.e(e, "刷新词库状态失败")
                realmStatusText.text = "Realm数据库状态：获取状态失败"
                trieStatusText.text = "Trie词典状态：获取状态失败"
            } finally {
                refreshButton.isEnabled = true
                refreshButton.text = "刷新"
            }
        }
    }
    
    /**
     * 获取Realm数据库状态
     */
    private suspend fun getRealmStatus(): RealmStatusInfo {
        return try {
            Timber.d("🔍 开始获取Realm数据库状态...")
            
            val realm = ShenjiApplication.realm
            val entryCount = realm.query(Entry::class).count().find()
            
            // 🔧 增强：检查数据库文件状态
            val context = ShenjiApplication.appContext
            val dictFile = File(context.filesDir, "dictionaries/shenji_dict.realm")
            val fileSize = if (dictFile.exists()) dictFile.length() else 0L
            val fileSizeMB = fileSize / (1024 * 1024)
            
            // 🔧 更详细的健康状态判断
            val isHealthy = when {
                entryCount > 1000 && fileSize > 10 * 1024 * 1024 -> {
                    Timber.d("✅ 数据库状态优秀：词条数=$entryCount, 文件大小=${fileSizeMB}MB")
                    true
                }
                entryCount > 100 && fileSize > 512 * 1024 -> {
                    Timber.d("⚠️ 数据库状态基本：词条数=$entryCount, 文件大小=${fileSizeMB}MB")
                    true
                }
                entryCount > 0 -> {
                    Timber.w("⚠️ 数据库状态较差：词条数=$entryCount, 文件大小=${fileSizeMB}MB")
                    true // 仍然认为是健康的，只是数据较少
                }
                else -> {
                    Timber.e("❌ 数据库状态异常：词条数=$entryCount, 文件大小=${fileSizeMB}MB")
                    false
                }
            }
            
            RealmStatusInfo(
                isInitialized = true,
                isHealthy = isHealthy,
                entryCount = entryCount,
                errorMessage = null,
                fileSize = fileSize
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ 获取Realm状态失败: ${e.message}")
            RealmStatusInfo(
                isInitialized = false,
                isHealthy = false,
                entryCount = 0,
                errorMessage = e.message,
                fileSize = 0L
            )
        }
    }
    
    /**
     * 获取Trie词典状态 - 只显示已加载的词典，chars和base永久显示
     */
    private suspend fun getTrieStatus(): TrieStatusInfo {
        return try {
            Timber.d("🔍 开始获取Trie词典状态...")
            
            val trieManager = TrieManager.instance
            val statusMap = mutableMapOf<TrieType, String>()
            
            // 🔧 增强：检查所有重要的Trie类型
            val importantTypes = listOf(TrieType.CHARS, TrieType.BASE)
            val allTypes = TrieType.values().toList()
            
            // 检查重要类型（总是显示）
            for (type in importantTypes) {
                val fileExists = trieManager.isTrieFileExists(type)
                val isLoaded = trieManager.isTrieLoaded(type)
                val isLoading = trieManager.isLoading(type)
                
                val status = when {
                    isLoading -> "加载中..."
                    isLoaded -> "已加载✅"
                    fileExists -> "文件存在，未加载"
                    else -> "文件不存在❌"
                }
                
                statusMap[type] = status
                Timber.d("📚 ${trieManager.getDisplayName(type)}: $status")
            }
            
            // 检查其他类型（只显示已加载或有文件的）
            for (type in allTypes) {
                if (type in importantTypes) continue
                
                val fileExists = trieManager.isTrieFileExists(type)
                val isLoaded = trieManager.isTrieLoaded(type)
                val isLoading = trieManager.isLoading(type)
                
                // 只有在有文件或已加载时才显示
                if (fileExists || isLoaded || isLoading) {
                    val status = when {
                        isLoading -> "加载中..."
                        isLoaded -> "已加载✅"
                        fileExists -> "文件存在"
                        else -> "未知状态"
                    }
                    
                    statusMap[type] = status
                    Timber.d("📚 ${trieManager.getDisplayName(type)}: $status")
                }
            }
            
            val loadedCount = statusMap.values.count { it.contains("已加载") }
            val totalAvailable = statusMap.size
            
            Timber.d("📊 Trie状态汇总：已加载$loadedCount/$totalAvailable")
            
            TrieStatusInfo(
                isInitialized = trieManager.isInitialized(),
                statusMap = statusMap,
                loadedCount = loadedCount,
                totalCount = totalAvailable,
                errorMessage = null
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ 获取Trie状态失败: ${e.message}")
            TrieStatusInfo(
                isInitialized = false,
                statusMap = emptyMap(),
                loadedCount = 0,
                totalCount = 0,
                errorMessage = e.message
            )
        }
    }
    
    /**
     * 更新Realm状态显示
     */
    private fun updateRealmStatus(status: RealmStatusInfo) {
        val fileSizeMB = status.fileSize / (1024 * 1024)
        val fileSizeText = if (status.fileSize > 0) "，文件大小：${fileSizeMB}MB" else ""
        
        val statusText = when {
            status.isInitialized && status.isHealthy && status.entryCount > 1000 -> {
                "Realm数据库状态：✅ 优秀（词条数：${status.entryCount}$fileSizeText）"
            }
            status.isInitialized && status.isHealthy && status.entryCount > 100 -> {
                "Realm数据库状态：⚠️ 基本可用（词条数：${status.entryCount}$fileSizeText）"
            }
            status.isInitialized && status.isHealthy -> {
                "Realm数据库状态：⚠️ 数据较少（词条数：${status.entryCount}$fileSizeText）"
            }
            status.isInitialized && !status.isHealthy -> {
                "Realm数据库状态：❌ 初始化完成但异常（${status.errorMessage ?: "未知错误"}）"
            }
            else -> {
                "Realm数据库状态：❌ 初始化失败（${status.errorMessage ?: "未知错误"}）"
            }
        }
        
        realmStatusText.text = statusText
        
        // 设置状态颜色
        val textColor = when {
            status.isInitialized && status.isHealthy && status.entryCount > 1000 -> {
                ContextCompat.getColor(context, android.R.color.holo_green_dark)
            }
            status.isInitialized && status.isHealthy -> {
                ContextCompat.getColor(context, android.R.color.holo_orange_light)
            }
            else -> {
                ContextCompat.getColor(context, android.R.color.holo_red_dark)
            }
        }
        realmStatusText.setTextColor(textColor)
    }
    
    /**
     * 更新Trie状态显示
     */
    private fun updateTrieStatus(status: TrieStatusInfo) {
        val statusText = if (status.isInitialized) {
            if (status.statusMap.isNotEmpty()) {
                val statusLines = mutableListOf<String>()
                statusLines.add("Trie词典状态：已初始化（${status.loadedCount}/${status.totalCount}已加载）")
                
                // 按重要性排序显示
                val importantTypes = listOf(TrieType.CHARS, TrieType.BASE)
                val otherTypes = status.statusMap.keys.filter { it !in importantTypes }
                
                for (type in importantTypes) {
                    status.statusMap[type]?.let { typeStatus ->
                        val displayName = getTrieDisplayName(type)
                        statusLines.add("  • $displayName: $typeStatus")
                    }
                }
                
                for (type in otherTypes) {
                    status.statusMap[type]?.let { typeStatus ->
                        val displayName = getTrieDisplayName(type)
                        statusLines.add("  • $displayName: $typeStatus")
                    }
                }
                
                statusLines.joinToString("\n")
            } else {
                "Trie词典状态：已初始化，但无可用词典文件"
            }
        } else {
            "Trie词典状态：未初始化（${status.errorMessage ?: "未知错误"}）"
        }
        
        trieStatusText.text = statusText
        
        // 设置状态颜色
        val textColor = when {
            status.isInitialized && status.loadedCount >= 2 -> {
                ContextCompat.getColor(context, android.R.color.holo_green_dark)
            }
            status.isInitialized && status.loadedCount > 0 -> {
                ContextCompat.getColor(context, android.R.color.holo_orange_light)
            }
            else -> {
                ContextCompat.getColor(context, android.R.color.holo_red_dark)
            }
        }
        trieStatusText.setTextColor(textColor)
    }
    
    /**
     * 获取Trie类型的显示名称
     */
    private fun getTrieDisplayName(type: TrieType): String {
        return when (type) {
            TrieType.CHARS -> "单字"
            TrieType.BASE -> "基础词典"
            TrieType.CORRELATION -> "关联词典"
            TrieType.ASSOCIATIONAL -> "联想词典"
            TrieType.PLACE -> "地名词典"
            TrieType.PEOPLE -> "人名词典"
            TrieType.POETRY -> "诗词词典"
            TrieType.CORRECTIONS -> "纠错词典"
            TrieType.COMPATIBLE -> "兼容词典"
        }
    }
    
    /**
     * 获取Trie类型的中文显示名称
     */
    private fun getDisplayName(type: TrieType): String {
        return when (type) {
            TrieType.CHARS -> "单字词典"
            TrieType.BASE -> "基础词典"
            TrieType.CORRELATION -> "关联词典"
            TrieType.ASSOCIATIONAL -> "联想词典"
            TrieType.PLACE -> "地名词典"
            TrieType.PEOPLE -> "人名词典"
            TrieType.POETRY -> "诗词词典"
            TrieType.CORRECTIONS -> "纠错词典"
            TrieType.COMPATIBLE -> "兼容词典"
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        refreshJob?.cancel()
    }
    
    /**
     * Realm状态信息数据类
     */
    private data class RealmStatusInfo(
        val isInitialized: Boolean,
        val isHealthy: Boolean,
        val entryCount: Long,
        val errorMessage: String?,
        val fileSize: Long = 0L
    )
    
    /**
     * Trie状态信息数据类
     */
    private data class TrieStatusInfo(
        val isInitialized: Boolean,
        val statusMap: Map<TrieType, String>,
        val loadedCount: Int,
        val totalCount: Int,
        val errorMessage: String?
    )
} 