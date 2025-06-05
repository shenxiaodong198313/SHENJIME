package com.shenji.aikeyboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.ScriptItem

class PhrasesListAdapter(
    private val phrases: List<ScriptItem>,
    private val onPhraseClick: (String) -> Unit
) : RecyclerView.Adapter<PhrasesListAdapter.PhraseViewHolder>() {

    class PhraseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.phrase_title)
        val contentText: TextView = view.findViewById(R.id.phrase_content)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhraseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_phrase_list, parent, false)
        return PhraseViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhraseViewHolder, position: Int) {
        val phrase = phrases[position]
        
        holder.titleText.text = phrase.title
        holder.contentText.text = phrase.content
        
        holder.itemView.setOnClickListener {
            onPhraseClick(phrase.content)
        }
    }

    override fun getItemCount(): Int = phrases.size
} 