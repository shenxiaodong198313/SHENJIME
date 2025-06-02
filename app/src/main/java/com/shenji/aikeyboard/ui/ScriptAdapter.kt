package com.shenji.aikeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.ScriptItem

class ScriptAdapter(
    private val items: List<ScriptItem>,
    private val onItemClick: (ScriptItem) -> Unit
) : RecyclerView.Adapter<ScriptAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.title_text)
        val contentText: TextView = view.findViewById(R.id.content_text)
        val tagsText: TextView = view.findViewById(R.id.tags_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_script, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        holder.titleText.text = item.title
        holder.contentText.text = item.content
        
        if (item.tags.isNotEmpty()) {
            holder.tagsText.text = "标签: ${item.tags}"
            holder.tagsText.visibility = View.VISIBLE
        } else {
            holder.tagsText.visibility = View.GONE
        }
        
        // 点击事件 - 自动填充内容并关闭页面
        holder.itemView.setOnClickListener {
            autoFillToInputField(holder.itemView.context, item.content)
            onItemClick(item)
        }
    }

    private fun autoFillToInputField(context: Context, text: String) {
        try {
            // 首先尝试通过输入法服务直接填充
            val inputMethodService = com.shenji.aikeyboard.keyboard.ShenjiInputMethodService.getInstance()
            if (inputMethodService != null) {
                inputMethodService.autoFillText(text)
                Toast.makeText(context, "已自动填充到输入框", Toast.LENGTH_SHORT).show()
            } else {
                // 如果输入法服务不可用，则复制到剪贴板作为备选方案
                copyToClipboard(context, text)
            }
        } catch (e: Exception) {
            // 出错时复制到剪贴板作为备选方案
            copyToClipboard(context, text)
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("话术内容", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    override fun getItemCount(): Int = items.size
} 