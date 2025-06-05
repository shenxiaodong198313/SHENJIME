package com.shenji.aikeyboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.ScriptItem
import java.io.IOException

class PhrasesListAdapter(
    private val phrases: List<ScriptItem>,
    private val onPhraseClick: (String) -> Unit,
    private val onAutoSendClick: (String) -> Unit
) : RecyclerView.Adapter<PhrasesListAdapter.PhraseViewHolder>() {

    class PhraseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.phrase_title)
        val contentText: TextView = view.findViewById(R.id.phrase_content)
        val autoSendButton: Button = view.findViewById(R.id.btn_auto_send)
        val imageView: ImageView = view.findViewById(R.id.phrase_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhraseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_phrase_list, parent, false)
        return PhraseViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhraseViewHolder, position: Int) {
        val phrase = phrases[position]
        
        holder.titleText.text = phrase.title
        
        // 根据类型显示不同内容
        if (phrase.type == "image") {
            // 图片类型
            holder.contentText.text = "[图片]"
            holder.imageView.visibility = View.VISIBLE
            
            // 加载图片
            loadImageFromAssets(holder.imageView, phrase.imagePath)
            
            // 点击话术条目（普通发送图片）
            holder.itemView.setOnClickListener {
                onPhraseClick(phrase.imagePath) // 传递图片路径
            }
            
            // 点击自动发送按钮（发送图片并自动确认）
            holder.autoSendButton.setOnClickListener {
                onAutoSendClick(phrase.imagePath) // 传递图片路径
            }
        } else {
            // 文本类型
            holder.contentText.text = phrase.content
            holder.imageView.visibility = View.GONE
            
            // 点击话术条目（普通发送文本）
            holder.itemView.setOnClickListener {
                onPhraseClick(phrase.content)
            }
            
            // 点击自动发送按钮（发送文本并自动确认）
            holder.autoSendButton.setOnClickListener {
                onAutoSendClick(phrase.content)
            }
        }
    }
    
    private fun loadImageFromAssets(imageView: ImageView, imagePath: String) {
        try {
            val inputStream = imageView.context.assets.open(imagePath)
            val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)
            imageView.setImageDrawable(drawable)
            inputStream.close()
        } catch (e: IOException) {
            // 如果加载失败，显示默认图标
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    override fun getItemCount(): Int = phrases.size
} 