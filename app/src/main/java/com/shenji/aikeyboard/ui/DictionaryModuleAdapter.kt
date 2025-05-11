package com.shenji.aikeyboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDictionaryModuleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onModuleClicked)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemDictionaryModuleBinding,
        private val onModuleClicked: (DictionaryModule) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(module: DictionaryModule) {
            binding.tvDictName.text = module.chineseName
            binding.tvDictType.text = module.type
            
            // 格式化词条数量
            val formatter = NumberFormat.getNumberInstance(Locale.getDefault())
            binding.tvEntryCount.text = "${formatter.format(module.entryCount)} 词条"
            
            // 设置点击事件
            binding.root.setOnClickListener {
                onModuleClicked(module)
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