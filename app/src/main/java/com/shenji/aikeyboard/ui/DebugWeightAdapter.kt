package com.shenji.aikeyboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.CandidateWeight

/**
 * 候选词权重适配器，用于显示候选词的排序权重信息
 */
class DebugWeightAdapter : RecyclerView.Adapter<DebugWeightAdapter.ViewHolder>() {
    
    private val weights: MutableList<Pair<String, CandidateWeight>> = mutableListOf()
    
    fun setData(weightData: Map<String, CandidateWeight>) {
        weights.clear()
        weights.addAll(weightData.entries.map { Pair(it.key, it.value) })
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_debug_weight, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (word, weight) = weights[position]
        holder.bind(word, weight)
    }
    
    override fun getItemCount(): Int = weights.size
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCandidateWord: TextView = itemView.findViewById(R.id.tv_candidate_word)
        private val tvCandidateWeight: TextView = itemView.findViewById(R.id.tv_candidate_weight)
        
        fun bind(word: String, weight: CandidateWeight) {
            tvCandidateWord.text = word
            tvCandidateWeight.text = "阶段: ${weight.stage}, 频率: ${weight.frequency}, " +
                    "匹配类型: ${getMatchTypeName(weight.matchType)}, 长度奖励: ${weight.lengthBonus}"
        }
        
        private fun getMatchTypeName(matchType: Int): String {
            return when (matchType) {
                0 -> "精确"
                1 -> "前缀"
                2 -> "首字母"
                3 -> "模糊"
                else -> matchType.toString()
            }
        }
    }
} 