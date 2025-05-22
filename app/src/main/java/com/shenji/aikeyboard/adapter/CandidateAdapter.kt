package com.shenji.aikeyboard.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.model.Candidate

/**
 * 候选词列表适配器
 */
class CandidateAdapter : ListAdapter<Candidate, CandidateAdapter.ViewHolder>(CandidateDiffCallback()) {
    
    // 候选词点击监听器
    private var onItemClickListener: ((Candidate) -> Unit)? = null
    
    /**
     * 设置候选词点击监听器
     */
    fun setOnItemClickListener(listener: (Candidate) -> Unit) {
        onItemClickListener = listener
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_candidate, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val candidate = getItem(position)
        holder.bind(candidate, position + 1)
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(candidate)
        }
    }
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRank: TextView = itemView.findViewById(R.id.tv_rank)
        private val tvWord: TextView = itemView.findViewById(R.id.tv_word)
        private val tvPinyin: TextView = itemView.findViewById(R.id.tv_pinyin)
        private val tvSource: TextView = itemView.findViewById(R.id.tv_source)
        private val tvFrequency: TextView = itemView.findViewById(R.id.tv_frequency)
        
        fun bind(candidate: Candidate, position: Int) {
            tvRank.text = position.toString()
            tvWord.text = candidate.word
            tvPinyin.text = "拼音: ${candidate.pinyin}"
            
            // 根据匹配类型显示不同标签
            val matchTypeText = when (candidate.matchType) {
                Candidate.MatchType.PINYIN_PREFIX -> "拼音"
                Candidate.MatchType.INITIAL_LETTER -> "首字母"
                Candidate.MatchType.SYLLABLE_SPLIT -> "音节"
                Candidate.MatchType.ACRONYM -> "缩写"
                else -> ""
            }
            
            val typeText = when (candidate.type) {
                "chars" -> "单字"
                "user" -> "用户词"
                "system" -> "系统词"
                else -> candidate.type
            }
            
            // 组合显示信息，添加来源信息
            tvSource.text = "${typeText} (${matchTypeText}) 词频: ${candidate.frequency} 来源: ${candidate.source}"
            tvFrequency.text = candidate.frequency.toString()
        }
    }
}

/**
 * 候选词差异对比回调
 */
class CandidateDiffCallback : DiffUtil.ItemCallback<Candidate>() {
    override fun areItemsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
        return oldItem.word == newItem.word
    }
    
    override fun areContentsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
        return oldItem == newItem
    }
} 