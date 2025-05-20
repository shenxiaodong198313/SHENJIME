package com.shenji.aikeyboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.WordFrequency

/**
 * 词频适配器，用于显示候选词查询结果
 */
class WordFrequencyAdapter : RecyclerView.Adapter<WordFrequencyAdapter.ViewHolder>() {
    
    private val words: MutableList<WordFrequency> = mutableListOf()
    
    fun setData(wordData: List<WordFrequency>) {
        words.clear()
        words.addAll(wordData)
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_candidate, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = words[position]
        holder.bind(item, position + 1)
    }
    
    override fun getItemCount(): Int = words.size
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRank: TextView = itemView.findViewById(R.id.tv_rank)
        private val tvWord: TextView = itemView.findViewById(R.id.tv_word)
        private val tvFrequency: TextView = itemView.findViewById(R.id.tv_frequency)
        
        fun bind(wordFrequency: WordFrequency, rank: Int) {
            tvRank.text = rank.toString()
            tvWord.text = wordFrequency.word
            tvFrequency.text = wordFrequency.frequency.toString()
        }
    }
} 