package com.shenji.aikeyboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.data.DictionaryModule
import com.shenji.aikeyboard.databinding.ItemDictionaryModuleBinding
import java.text.NumberFormat
import java.util.Locale

/**
 * 词典模块列表适配器
 */
class DictionaryModuleAdapter(
    private val onModuleClicked: (DictionaryModule) -> Unit
) : ListAdapter<DictionaryModule, DictionaryModuleAdapter.ViewHolder>(DictionaryModuleDiffCallback()) {

    // 保存每个类型的加载进度
    private val loadingProgress = mutableMapOf<String, Float>()
    
    /**
     * 更新加载进度
     */
    fun updateProgress(type: String, progress: Float) {
        loadingProgress[type] = progress
        
        // 找到对应类型的项并更新
        val position = currentList.indexOfFirst { it.type == type }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDictionaryModuleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onModuleClicked, loadingProgress)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemDictionaryModuleBinding,
        private val onModuleClicked: (DictionaryModule) -> Unit,
        private val loadingProgress: MutableMap<String, Float>
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(module: DictionaryModule) {
            binding.tvDictName.text = module.chineseName
            binding.tvDictType.text = module.type
            
            // 格式化词条数量
            val formatter = NumberFormat.getNumberInstance(Locale.getDefault())
            binding.tvEntryCount.text = "${formatter.format(module.entryCount)} 词条"
            
            // 处理高频词库（chars和base），显示内存加载状态
            if (module.type == "chars" || module.type == "base") {
                // 首先从适配器的进度缓存获取，如果没有则从DictionaryManager获取存储的进度
                var progress = loadingProgress[module.type] ?: 0f
                if (progress == 0f) {
                    // 如果缓存中没有进度，从DictionaryManager获取
                    progress = DictionaryManager.instance.getTypeLoadingProgress(module.type)
                    // 更新缓存
                    loadingProgress[module.type] = progress
                }
                
                // 显示进度条
                binding.progressBarLoading.visibility = View.VISIBLE
                binding.progressBarLoading.progress = (progress * 100).toInt()
                
                // 实时检查是否已加载，而不是依赖module中的isInMemory
                val isReallyLoaded = DictionaryManager.instance.isTypeLoaded(module.type)
                
                // 显示内存加载状态
                binding.tvMemoryStatus.visibility = View.VISIBLE
                binding.tvMemoryStatus.text = "内存已加载: ${if (isReallyLoaded) "是" else "否"}"
                
                // 如果已加载到内存或进度大于95%，显示内存占用
                if (isReallyLoaded && (module.memoryUsage > 0 || DictionaryManager.instance.getTypeMemoryUsage(module.type) > 0)) {
                    val memUsage = if (module.memoryUsage > 0) module.memoryUsage else DictionaryManager.instance.getTypeMemoryUsage(module.type)
                    binding.tvMemoryUsage.visibility = View.VISIBLE
                    binding.tvMemoryUsage.text = "内存占用: ${formatFileSize(memUsage)}"
                } else {
                    binding.tvMemoryUsage.visibility = View.GONE
                }
            } else {
                // 非高频词库不显示内存状态和进度条
                binding.tvMemoryStatus.visibility = View.GONE
                binding.progressBarLoading.visibility = View.GONE
                binding.tvMemoryUsage.visibility = View.GONE
            }
            
            // 设置点击事件
            binding.root.setOnClickListener {
                onModuleClicked(module)
            }
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
    }
}

/**
 * 词典模块DiffCallback，用于优化RecyclerView性能
 */
class DictionaryModuleDiffCallback : DiffUtil.ItemCallback<DictionaryModule>() {
    override fun areItemsTheSame(oldItem: DictionaryModule, newItem: DictionaryModule): Boolean {
        return oldItem.type == newItem.type
    }

    override fun areContentsTheSame(oldItem: DictionaryModule, newItem: DictionaryModule): Boolean {
        return oldItem == newItem
    }
} 