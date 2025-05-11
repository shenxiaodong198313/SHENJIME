package com.shenji.aikeyboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.databinding.ItemDictionaryEntryBinding
import java.text.NumberFormat
import java.util.Locale

/**
 * 词典词条列表适配器
 */
class DictionaryEntryAdapter : ListAdapter<Entry, DictionaryEntryAdapter.ViewHolder>(DictionaryEntryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDictionaryEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemDictionaryEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: Entry) {
            binding.tvWord.text = entry.word
            binding.tvPinyin.text = entry.pinyin
            
            // 格式化词频
            val formatter = NumberFormat.getNumberInstance(Locale.getDefault())
            binding.tvFrequency.text = "词频: ${formatter.format(entry.frequency)}"
        }
    }
}

/**
 * 词典词条DiffCallback，用于优化RecyclerView性能
 */
class DictionaryEntryDiffCallback : DiffUtil.ItemCallback<Entry>() {
    override fun areItemsTheSame(oldItem: Entry, newItem: Entry): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Entry, newItem: Entry): Boolean {
        return oldItem.word == newItem.word &&
                oldItem.pinyin == newItem.pinyin &&
                oldItem.frequency == newItem.frequency &&
                oldItem.type == newItem.type
    }
} 