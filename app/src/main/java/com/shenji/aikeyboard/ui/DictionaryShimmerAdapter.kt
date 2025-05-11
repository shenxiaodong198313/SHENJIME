package com.shenji.aikeyboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R

/**
 * 词典模块骨架屏适配器
 */
class DictionaryShimmerAdapter(private val itemCount: Int = 5) : 
    RecyclerView.Adapter<DictionaryShimmerAdapter.ShimmerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShimmerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.shimmer_dictionary_module, parent, false)
        return ShimmerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShimmerViewHolder, position: Int) {
        // 骨架屏不需要绑定数据
    }

    override fun getItemCount(): Int = itemCount

    class ShimmerViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView)
} 