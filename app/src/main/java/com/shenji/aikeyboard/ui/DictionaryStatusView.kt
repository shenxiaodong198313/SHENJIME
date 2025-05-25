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
            val realm = ShenjiApplication.realm
            val entryCount = realm.query(Entry::class).count().find()
            val isHealthy = entryCount >= 0 // 能查询就认为是健康的
            
            RealmStatusInfo(
                isInitialized = true,
                isHealthy = isHealthy,
                entryCount = entryCount,
                errorMessage = null
            )
        } catch (e: Exception) {
            Timber.e(e, "获取Realm状态失败")
            RealmStatusInfo(
                isInitialized = false,
                isHealthy = false,
                entryCount = 0,
                errorMessage = e.message
            )
        }
    }
    
    /**
     * 获取Trie词典状态 - 只显示已加载的词典，chars和base永久显示
     */
    private suspend fun getTrieStatus(): List<TrieStatusInfo> {
        val trieManager = TrieManager.instance
        val statusList = mutableListOf<TrieStatusInfo>()
        
        for (trieType in TrieType.values()) {
            try {
                // chars和base词典永久显示，其他词典只在已加载时显示
                val shouldShow = when (trieType) {
                    TrieType.CHARS, TrieType.BASE -> true
                    else -> trieManager.isTrieLoaded(trieType)
                }
                
                if (!shouldShow) continue
                
                val trie = trieManager.getTrie(trieType)
                if (trie != null) {
                    // 已加载到内存
                    val memoryStats = trie.getMemoryStats()
                    // 计算内存使用量（估算：每个节点约100字节，每个词条约50字节）
                    val memoryUsageMB = ((memoryStats.nodeCount * 100L) + (memoryStats.wordCount * 50L)) / (1024.0 * 1024.0)
                    statusList.add(
                        TrieStatusInfo(
                            type = trieType,
                            isLoaded = true,
                            isLoading = false,
                            memoryUsageMB = memoryUsageMB,
                            nodeCount = memoryStats.nodeCount,
                            wordCount = memoryStats.wordCount,
                            status = "已加载"
                        )
                    )
                } else {
                    // 未加载（只有chars和base会显示这个状态）
                    val isLoading = trieManager.isLoading(trieType)
                    statusList.add(
                        TrieStatusInfo(
                            type = trieType,
                            isLoaded = false,
                            isLoading = isLoading,
                            memoryUsageMB = 0.0,
                            nodeCount = 0,
                            wordCount = 0,
                            status = if (isLoading) "加载中..." else "未加载"
                        )
                    )
                    
                    // 如果检测到chars词典未加载，自动加载
                    if (trieType == TrieType.CHARS && !isLoading) {
                        Timber.d("检测到chars词典未加载，开始自动加载...")
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                val loaded = trieManager.loadTrieToMemory(TrieType.CHARS)
                                if (loaded) {
                                    Timber.i("chars词典自动加载成功")
                                    // 在主线程中刷新状态
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        refreshStatus()
                                    }
                                } else {
                                    Timber.w("chars词典自动加载失败")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "chars词典自动加载异常")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "获取${getDisplayName(trieType)}状态失败")
                // 对于chars和base，即使出错也要显示错误状态
                if (trieType == TrieType.CHARS || trieType == TrieType.BASE) {
                    statusList.add(
                        TrieStatusInfo(
                            type = trieType,
                            isLoaded = false,
                            isLoading = false,
                            memoryUsageMB = 0.0,
                            nodeCount = 0,
                            wordCount = 0,
                            status = "错误"
                        )
                    )
                }
            }
        }
        
        // 按优先级排序：chars > base > 其他（按类型顺序）
        return statusList.sortedWith { a, b ->
            when {
                a.type == TrieType.CHARS -> -1
                b.type == TrieType.CHARS -> 1
                a.type == TrieType.BASE -> -1
                b.type == TrieType.BASE -> 1
                else -> a.type.ordinal.compareTo(b.type.ordinal)
            }
        }
    }
    
    /**
     * 更新Realm状态显示
     */
    private fun updateRealmStatus(status: RealmStatusInfo) {
        val statusText = if (status.isInitialized && status.isHealthy) {
            "Realm数据库状态：初始化完成，数据库正常（词条数：${status.entryCount}）"
        } else if (status.isInitialized && !status.isHealthy) {
            "Realm数据库状态：初始化完成，数据库异常（${status.errorMessage ?: "未知错误"}）"
        } else {
            "Realm数据库状态：初始化未完成，数据库异常（${status.errorMessage ?: "未知错误"}）"
        }
        
        realmStatusText.text = statusText
        
        // 设置状态颜色
        val textColor = if (status.isInitialized && status.isHealthy) {
            ContextCompat.getColor(context, android.R.color.holo_green_dark)
        } else {
            ContextCompat.getColor(context, android.R.color.holo_red_dark)
        }
        realmStatusText.setTextColor(textColor)
    }
    
    /**
     * 更新Trie状态显示
     */
    private fun updateTrieStatus(statusList: List<TrieStatusInfo>) {
        val statusBuilder = StringBuilder()
        
        if (statusList.isEmpty()) {
            statusBuilder.append("📚 词典状态：无可用词典")
        } else {
            statusBuilder.append("📚 词典状态：\n")
            
            for (status in statusList) {
                val typeName = getTrieDisplayName(status.type)
                val statusIcon = when {
                    status.isLoading -> "🔄"
                    status.isLoaded -> "✅"
                    status.status == "错误" -> "❌"
                    else -> "⭕"
                }
                
                statusBuilder.append("${statusIcon} ${typeName}：")
                
                if (status.isLoaded) {
                    statusBuilder.append("${status.status}，")
                    statusBuilder.append("内存${String.format("%.1f", status.memoryUsageMB)}MB，")
                    statusBuilder.append("节点${status.nodeCount}，")
                    statusBuilder.append("词语${status.wordCount}")
                } else {
                    statusBuilder.append(status.status)
                }
                
                statusBuilder.append("\n")
            }
        }
        
        trieStatusText.text = statusBuilder.toString().trimEnd()
        
        // 根据整体状态设置颜色
        val textColor = when {
            statusList.any { it.status == "错误" } -> ContextCompat.getColor(context, android.R.color.holo_red_light)
            statusList.any { it.isLoading } -> ContextCompat.getColor(context, android.R.color.holo_orange_light)
            statusList.all { it.isLoaded } -> ContextCompat.getColor(context, android.R.color.holo_green_light)
            else -> ContextCompat.getColor(context, android.R.color.darker_gray)
        }
        
        trieStatusText.setTextColor(textColor)
        
        Timber.d("Trie状态显示已更新，共${statusList.size}个词典")
    }
    
    /**
     * 获取Trie类型的显示名称
     */
    private fun getTrieDisplayName(type: TrieType): String {
        return when (type) {
            TrieType.CHARS -> "chars"
            TrieType.BASE -> "base"
            TrieType.CORRELATION -> "correlation"
            TrieType.ASSOCIATIONAL -> "associational"
            TrieType.PLACE -> "place"
            TrieType.PEOPLE -> "people"
            TrieType.POETRY -> "poetry"
            TrieType.CORRECTIONS -> "corrections"
            TrieType.COMPATIBLE -> "compatible"
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
    data class RealmStatusInfo(
        val isInitialized: Boolean,
        val isHealthy: Boolean,
        val entryCount: Long,
        val errorMessage: String?
    )
    
    /**
     * Trie状态信息数据类
     */
    data class TrieStatusInfo(
        val type: TrieType,
        val isLoaded: Boolean,
        val isLoading: Boolean,
        val memoryUsageMB: Double,
        val nodeCount: Int,
        val wordCount: Int,
        val status: String
    )
} 