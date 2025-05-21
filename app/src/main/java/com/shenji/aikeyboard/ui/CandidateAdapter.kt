package com.shenji.aikeyboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.databinding.ItemCandidateBinding
import com.shenji.aikeyboard.model.Candidate

/**
 * 候选词适配器 - 用于展示候选词列表
 */
class CandidateAdapter : ListAdapter<Candidate, CandidateAdapter.CandidateViewHolder>(CandidateDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder {
        val binding = ItemCandidateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CandidateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
        val candidate = getItem(position)
        holder.bind(candidate, position + 1)
    }

    inner class CandidateViewHolder(private val binding: ItemCandidateBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(candidate: Candidate, rank: Int) {
            binding.tvRank.text = rank.toString()
            binding.tvWord.text = candidate.word
            
            // 显示拼音或首字母
            val pinyinInfo = when {
                candidate.pinyin.isNotEmpty() -> "拼音: ${candidate.pinyin}"
                candidate.initialLetters.isNotEmpty() -> "首字母: ${candidate.initialLetters}"
                else -> ""
            }
            binding.tvPinyin.text = pinyinInfo
            
            // 显示候选词的来源和频率信息
            val sourceInfo = when {
                candidate.type.isNotEmpty() && candidate.frequency > 0 -> 
                    "${candidate.type} (词频: ${candidate.frequency})"
                candidate.type.isNotEmpty() -> candidate.type
                candidate.frequency > 0 -> "词频: ${candidate.frequency}"
                else -> ""
            }
            binding.tvSource.text = sourceInfo
        }
    }
}

/**
 * 候选词差异比较回调
 */
class CandidateDiffCallback : DiffUtil.ItemCallback<Candidate>() {
    override fun areItemsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
        return oldItem.word == newItem.word && oldItem.pinyin == newItem.pinyin
    }

    override fun areContentsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
        return oldItem == newItem
    }
} 