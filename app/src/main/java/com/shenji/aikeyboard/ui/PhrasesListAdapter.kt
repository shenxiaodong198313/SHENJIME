package com.shenji.aikeyboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.ScriptItem
import java.io.IOException

class PhrasesListAdapter(
    private val phrases: List<ScriptItem>,
    private val onPhraseClick: (String) -> Unit,
    private val onAutoSendClick: (String) -> Unit,
    private val onImageClick: (String) -> Unit = { },
    private val onImageAutoSend: (String) -> Unit = { },
    private val onMixedAutoSend: (List<String>, List<String>) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<PhrasesListAdapter.PhraseViewHolder>() {

    class PhraseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.phrase_title)
        val contentText: TextView = view.findViewById(R.id.phrase_content)
        val autoSendButton: Button = view.findViewById(R.id.btn_auto_send)
        val imageView: ImageView = view.findViewById(R.id.phrase_image)
        val multiImageContainer: LinearLayout = view.findViewById(R.id.multi_image_container)
        val imageView1: ImageView = view.findViewById(R.id.phrase_image1)
        val imageView2: ImageView = view.findViewById(R.id.phrase_image2)
        val imageView3: ImageView = view.findViewById(R.id.phrase_image3)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhraseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_phrase_list, parent, false)
        return PhraseViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhraseViewHolder, position: Int) {
        val phrase = phrases[position]
        
        holder.titleText.text = phrase.title
        
        // 隐藏所有图片容器
        holder.imageView.visibility = View.GONE
        holder.multiImageContainer.visibility = View.GONE
        
        // 根据类型显示不同内容
        if (phrase.type == "mixed") {
            // 图文混合类型
            val textList = if (phrase.textList.isNotEmpty()) {
                phrase.textList.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
            
            val imagePaths = if (phrase.mixedImagePaths.isNotEmpty()) {
                phrase.mixedImagePaths.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
            
            // 显示内容描述
            holder.contentText.text = "${textList.size}条文本 + ${imagePaths.size}张图片"
            
            if (imagePaths.isNotEmpty()) {
                holder.multiImageContainer.visibility = View.VISIBLE
                
                // 加载图片预览
                val imageViews = listOf(holder.imageView1, holder.imageView2, holder.imageView3)
                imagePaths.take(3).forEachIndexed { index, imagePath ->
                    loadImageFromAssets(imageViews[index], imagePath)
                    imageViews[index].visibility = View.VISIBLE
                }
                
                // 隐藏未使用的ImageView
                for (i in imagePaths.size until 3) {
                    imageViews[i].visibility = View.GONE
                }
            }
            
            // 点击话术条目（发送第一条文本）
            holder.itemView.setOnClickListener {
                if (textList.isNotEmpty()) {
                    onPhraseClick(textList[0])
                }
            }
            
            // 点击自动发送按钮（图文混合自动发送）
            holder.autoSendButton.setOnClickListener {
                onMixedAutoSend(textList, imagePaths)
            }
        } else if (phrase.type == "image") {
            // 检查是否有多张图片
            val imagePaths = if (phrase.imagePaths.isNotEmpty()) {
                phrase.imagePaths.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else if (phrase.imagePath.isNotEmpty()) {
                listOf(phrase.imagePath)
            } else {
                emptyList()
            }
            
            if (imagePaths.size > 1) {
                // 多图片显示
                holder.contentText.text = "[${imagePaths.size}张图片]"
                holder.multiImageContainer.visibility = View.VISIBLE
                
                // 加载多张图片
                val imageViews = listOf(holder.imageView1, holder.imageView2, holder.imageView3)
                imagePaths.take(3).forEachIndexed { index, imagePath ->
                    loadImageFromAssets(imageViews[index], imagePath)
                    imageViews[index].visibility = View.VISIBLE
                    
                    // 为每张图片设置点击事件
                    imageViews[index].setOnClickListener {
                        onImageClick(imagePath)
                    }
                }
                
                // 隐藏未使用的ImageView
                for (i in imagePaths.size until 3) {
                    imageViews[i].visibility = View.GONE
                }
                
                // 点击话术条目（发送第一张图片）
                holder.itemView.setOnClickListener {
                    if (imagePaths.isNotEmpty()) {
                        onImageClick(imagePaths[0])
                    }
                }
                
                // 点击自动发送按钮（发送所有图片）
                holder.autoSendButton.setOnClickListener {
                    // 传递所有图片路径，用特殊分隔符连接
                    val allImagePaths = imagePaths.joinToString("|")
                    onImageAutoSend(allImagePaths)
                }
            } else if (imagePaths.size == 1) {
                // 单图片显示
                holder.contentText.text = "[图片]"
                holder.imageView.visibility = View.VISIBLE
                
                // 加载图片
                loadImageFromAssets(holder.imageView, imagePaths[0])
                
                // 点击话术条目（普通发送图片）
                holder.itemView.setOnClickListener {
                    onImageClick(imagePaths[0])
                }
                
                // 点击自动发送按钮（发送图片并自动确认）
                holder.autoSendButton.setOnClickListener {
                    onImageAutoSend(imagePaths[0])
                }
            }
        } else {
            // 文本类型
            holder.contentText.text = phrase.content
            
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