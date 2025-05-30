package com.shenji.aikeyboard.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.databinding.ItemChatMessageUserBinding
import com.shenji.aikeyboard.databinding.ItemChatMessageAiBinding
import com.shenji.aikeyboard.model.ChatMessage

/**
 * 聊天消息适配器
 */
class ChatMessagesAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(MessageDiffCallback()) {
    
    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isFromUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val binding = ItemChatMessageUserBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                UserMessageViewHolder(binding)
            }
            VIEW_TYPE_AI -> {
                val binding = ItemChatMessageAiBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                AiMessageViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AiMessageViewHolder -> holder.bind(message)
        }
    }
    
    class UserMessageViewHolder(
        private val binding: ItemChatMessageUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: ChatMessage) {
            binding.tvMessage.text = message.content
        }
    }
    
    class AiMessageViewHolder(
        private val binding: ItemChatMessageAiBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: ChatMessage) {
            binding.tvMessage.text = message.content
        }
    }
    
    private class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.timestamp == newItem.timestamp
        }
        
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
} 