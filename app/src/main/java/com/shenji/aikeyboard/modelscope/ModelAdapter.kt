package com.shenji.aikeyboard.modelscope

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * 在线模型适配器
 */
class ModelAdapter(
    private val onDownloadClick: (ModelScopeModel) -> Unit
) : RecyclerView.Adapter<ModelAdapter.ModelViewHolder>() {
    
    private var models = listOf<ModelScopeModel>()
    private val downloadTasks = mutableMapOf<String, DownloadTask>()
    
    fun updateModels(newModels: List<ModelScopeModel>) {
        models = newModels
        notifyDataSetChanged()
    }
    
    fun updateDownloadProgress(task: DownloadTask) {
        downloadTasks[task.modelId] = task
        val position = models.indexOfFirst { it.modelId == task.modelId }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_online_model, parent, false)
        return ModelViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val model = models[position]
        val downloadTask = downloadTasks[model.modelId]
        holder.bind(model, downloadTask, onDownloadClick)
    }
    
    override fun getItemCount() = models.size
    
    class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.modelNameText)
        private val authorText: TextView = itemView.findViewById(R.id.authorText)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val downloadsText: TextView = itemView.findViewById(R.id.downloadsText)
        private val sizeText: TextView = itemView.findViewById(R.id.sizeText)
        private val frameworkText: TextView = itemView.findViewById(R.id.frameworkText)
        private val taskText: TextView = itemView.findViewById(R.id.taskText)
        private val downloadButton: Button = itemView.findViewById(R.id.downloadButton)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.downloadProgressBar)
        private val progressText: TextView = itemView.findViewById(R.id.progressText)
        private val tagsLayout: LinearLayout = itemView.findViewById(R.id.tagsLayout)
        
        fun bind(
            model: ModelScopeModel, 
            downloadTask: DownloadTask?,
            onDownloadClick: (ModelScopeModel) -> Unit
        ) {
            nameText.text = model.modelName
            authorText.text = "作者: ${model.author}"
            descriptionText.text = model.description.ifBlank { "暂无描述" }
            downloadsText.text = "下载: ${formatNumber(model.downloads)}"
            sizeText.text = "大小: ${formatFileSize(model.modelSize)}"
            frameworkText.text = "框架: ${model.framework}"
            taskText.text = "任务: ${model.task}"
            
            // 显示标签
            tagsLayout.removeAllViews()
            model.tags.take(3).forEach { tag ->
                val tagView = TextView(itemView.context)
                tagView.text = tag
                tagView.setBackgroundResource(R.drawable.tag_background)
                tagView.setPadding(16, 8, 16, 8)
                tagView.textSize = 12f
                tagsLayout.addView(tagView)
            }
            
            // 处理下载状态
            when (downloadTask?.status) {
                DownloadStatus.DOWNLOADING -> {
                    downloadButton.visibility = View.GONE
                    progressBar.visibility = View.VISIBLE
                    progressText.visibility = View.VISIBLE
                    val progressPercent = (downloadTask.progress * 100).toInt()
                    progressBar.progress = progressPercent
                    progressText.text = "${progressPercent}%"
                    android.util.Log.d("ModelAdapter", "显示下载进度: ${model.modelName} - ${progressPercent}%")
                }
                DownloadStatus.COMPLETED -> {
                    downloadButton.text = "已下载"
                    downloadButton.isEnabled = false
                    progressBar.visibility = View.GONE
                    progressText.visibility = View.GONE
                    android.util.Log.d("ModelAdapter", "下载完成: ${model.modelName}")
                }
                DownloadStatus.FAILED -> {
                    downloadButton.text = "重新下载"
                    downloadButton.isEnabled = true
                    progressBar.visibility = View.GONE
                    progressText.visibility = View.GONE
                    android.util.Log.d("ModelAdapter", "下载失败: ${model.modelName}")
                }
                else -> {
                    downloadButton.text = "下载"
                    downloadButton.isEnabled = true
                    progressBar.visibility = View.GONE
                    progressText.visibility = View.GONE
                    if (downloadTask != null) {
                        android.util.Log.d("ModelAdapter", "下载状态未知: ${model.modelName} - ${downloadTask.status}")
                    }
                }
            }
            
            downloadButton.setOnClickListener {
                onDownloadClick(model)
            }
        }
        
        private fun formatNumber(number: Long): String {
            return when {
                number >= 1_000_000 -> "${number / 1_000_000}M"
                number >= 1_000 -> "${number / 1_000}K"
                else -> number.toString()
            }
        }
        
        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= 1_073_741_824 -> "${bytes / 1_073_741_824}GB"
                bytes >= 1_048_576 -> "${bytes / 1_048_576}MB"
                bytes >= 1_024 -> "${bytes / 1_024}KB"
                else -> "${bytes}B"
            }
        }
    }
}

/**
 * 本地模型适配器
 */
class LocalModelAdapter(
    private val onActivate: (LocalModel) -> Unit,
    private val onDelete: (LocalModel) -> Unit,
    private val onViewDetails: (LocalModel) -> Unit
) : RecyclerView.Adapter<LocalModelAdapter.LocalModelViewHolder>() {
    
    private var models = listOf<LocalModel>()
    
    fun updateModels(newModels: List<LocalModel>) {
        models = newModels
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocalModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_local_model, parent, false)
        return LocalModelViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: LocalModelViewHolder, position: Int) {
        val model = models[position]
        holder.bind(model, onActivate, onDelete, onViewDetails)
    }
    
    override fun getItemCount() = models.size
    
    class LocalModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.modelNameText)
        private val pathText: TextView = itemView.findViewById(R.id.pathText)
        private val sizeText: TextView = itemView.findViewById(R.id.sizeText)
        private val frameworkText: TextView = itemView.findViewById(R.id.frameworkText)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val downloadedText: TextView = itemView.findViewById(R.id.downloadedText)
        private val activateButton: Button = itemView.findViewById(R.id.activateButton)
        private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
        private val detailsButton: Button = itemView.findViewById(R.id.detailsButton)
        
        fun bind(
            model: LocalModel,
            onActivate: (LocalModel) -> Unit,
            onDelete: (LocalModel) -> Unit,
            onViewDetails: (LocalModel) -> Unit
        ) {
            nameText.text = model.modelName
            pathText.text = "路径: ${model.localPath}"
            sizeText.text = "大小: ${formatFileSize(model.modelSize)}"
            frameworkText.text = "框架: ${model.framework}"
            
            // 状态显示
            statusText.text = when (model.status) {
                ModelStatus.DOWNLOADED -> "已下载"
                ModelStatus.INSTALLED -> "已安装"
                ModelStatus.ACTIVE -> "使用中"
                ModelStatus.ERROR -> "错误"
                else -> "未知"
            }
            
            // 设置状态颜色
            val statusColor = when (model.status) {
                ModelStatus.ACTIVE -> android.graphics.Color.GREEN
                ModelStatus.ERROR -> android.graphics.Color.RED
                else -> android.graphics.Color.GRAY
            }
            statusText.setTextColor(statusColor)
            
            // 下载时间
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            downloadedText.text = "下载时间: ${dateFormat.format(model.downloadedAt)}"
            
            // 激活按钮状态
            if (model.isActive) {
                activateButton.text = "已激活"
                activateButton.isEnabled = false
            } else {
                activateButton.text = "激活"
                activateButton.isEnabled = true
            }
            
            // 按钮事件
            activateButton.setOnClickListener { onActivate(model) }
            deleteButton.setOnClickListener { onDelete(model) }
            detailsButton.setOnClickListener { onViewDetails(model) }
        }
        
        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= 1_073_741_824 -> "${bytes / 1_073_741_824}GB"
                bytes >= 1_048_576 -> "${bytes / 1_048_576}MB"
                bytes >= 1_024 -> "${bytes / 1_024}KB"
                else -> "${bytes}B"
            }
        }
    }
} 