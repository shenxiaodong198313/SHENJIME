package com.shenji.aikeyboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.data.DictionaryModule
import com.shenji.aikeyboard.databinding.ItemDictionaryGroupHeaderBinding
import com.shenji.aikeyboard.databinding.ItemDictionaryModuleBinding
import java.text.NumberFormat
import java.util.Locale

/**
 * 词典模块列表适配器
 */
class DictionaryModuleAdapter(
    private val onModuleClicked: (DictionaryModule) -> Unit
) : ListAdapter<DictionaryModule, RecyclerView.ViewHolder>(DictionaryModuleDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_GROUP_HEADER = 0
        private const val VIEW_TYPE_MODULE = 1
    }

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

    override fun getItemViewType(position: Int): Int {
        val module = getItem(position)
        return if (module.isGroupHeader) {
            VIEW_TYPE_GROUP_HEADER
        } else {
            VIEW_TYPE_MODULE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GROUP_HEADER -> {
                val binding = ItemDictionaryGroupHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                GroupHeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemDictionaryModuleBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ModuleViewHolder(binding, onModuleClicked, loadingProgress)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val module = getItem(position)
        when (holder) {
            is GroupHeaderViewHolder -> holder.bind(module)
            is ModuleViewHolder -> holder.bind(module)
        }
    }

    /**
     * 分组标题ViewHolder
     */
    class GroupHeaderViewHolder(
        private val binding: ItemDictionaryGroupHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(module: DictionaryModule) {
            binding.tvGroupTitle.text = module.chineseName
            
            // 格式化词条数量
            val formatter = NumberFormat.getNumberInstance(Locale.getDefault())
            
            when (module.type) {
                "precompiled" -> {
                    binding.tvGroupCount.text = "${formatter.format(module.entryCount)} 条"
                }
                "realm" -> {
                    // 从后面的列表项计算持久化词典数量
                    binding.tvGroupCount.text = "${formatter.format(module.entryCount)} 条"
                }
                else -> {
                    binding.tvGroupCount.text = "${formatter.format(module.entryCount)} 条"
                }
            }
        }
    }

    /**
     * 词典模块ViewHolder
     */
    class ModuleViewHolder(
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
            
            // 隐藏内存加载状态
            binding.tvMemoryStatus.visibility = View.GONE
            binding.tvMemoryUsage.visibility = View.GONE
            
            // 重置背景色
            binding.root.setCardBackgroundColor(
                binding.root.context.getColor(android.R.color.white)
            )
            
            // 隐藏加载进度条
            binding.progressBarLoading.visibility = View.GONE
            
            // 设置点击事件
            binding.root.setOnClickListener {
                onModuleClicked(module)
            }
        }
        
        // 格式化文件大小
        private fun formatSize(size: Long): String {
            val kb = 1024.0
            val mb = kb * 1024
            val gb = mb * 1024
            
            return when {
                size < kb -> "$size B"
                size < mb -> String.format("%.2f KB", size / kb)
                size < gb -> String.format("%.2f MB", size / mb)
                else -> String.format("%.2f GB", size / gb)
            }
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