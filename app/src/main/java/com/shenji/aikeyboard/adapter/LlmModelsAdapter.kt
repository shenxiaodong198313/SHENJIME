package com.shenji.aikeyboard.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.databinding.ItemLlmModelBinding
import com.shenji.aikeyboard.llm.LlmModel

/**
 * LLM模型列表适配器
 */
class LlmModelsAdapter(
    private val onModelClick: (LlmModel) -> Unit
) : ListAdapter<LlmModel, LlmModelsAdapter.ModelViewHolder>(ModelDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val binding = ItemLlmModelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ModelViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ModelViewHolder(
        private val binding: ItemLlmModelBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(model: LlmModel) {
            binding.apply {
                tvModelName.text = model.name
                tvModelSize.text = model.size
                tvModelDescription.text = model.description
                
                // 设置状态指示器
                if (model.isDownloaded) {
                    ivStatus.setImageResource(android.R.drawable.presence_online)
                } else {
                    ivStatus.setImageResource(android.R.drawable.presence_offline)
                }
                
                // 设置点击事件
                root.setOnClickListener {
                    onModelClick(model)
                }
            }
        }
    }
    
    private class ModelDiffCallback : DiffUtil.ItemCallback<LlmModel>() {
        override fun areItemsTheSame(oldItem: LlmModel, newItem: LlmModel): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: LlmModel, newItem: LlmModel): Boolean {
            return oldItem == newItem
        }
    }
} 