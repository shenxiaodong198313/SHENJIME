package com.shenji.aikeyboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R

/**
 * 调试阶段适配器，用于显示不同阶段的查询结果
 */
class DebugStageAdapter : RecyclerView.Adapter<DebugStageAdapter.ViewHolder>() {
    
    private val stages: MutableList<Pair<Int, List<String>>> = mutableListOf()
    
    fun setData(stageData: Map<Int, List<String>>) {
        stages.clear()
        stages.addAll(stageData.entries.sortedBy { it.key }.map { Pair(it.key, it.value) })
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_debug_stage, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (stage, results) = stages[position]
        holder.bind(stage, results)
    }
    
    override fun getItemCount(): Int = stages.size
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStageTitle: TextView = itemView.findViewById(R.id.tv_stage_title)
        private val tvStageResults: TextView = itemView.findViewById(R.id.tv_stage_results)
        
        fun bind(stage: Int, results: List<String>) {
            tvStageTitle.text = "阶段$stage"
            tvStageResults.text = if (results.isEmpty()) {
                "无结果"
            } else {
                results.joinToString(", ")
            }
        }
    }
} 