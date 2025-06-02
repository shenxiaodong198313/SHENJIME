// Created by ruoyi.sjd on 2025/5/22.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.shenji.aikeyboard.mnn.main

import android.util.Log
import android.view.View
import android.widget.TextView
import com.alibaba.mls.api.download.DownloadInfo
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.mnn.model.Modality
import com.shenji.aikeyboard.mnn.modelsettings.DropDownMenuHelper
import com.shenji.aikeyboard.mnn.model.ModelVendors

class FilterComponent(private val activity: MainActivity) {
    private var vendorFilterListener: ((String?) -> Unit)? = null
    private var downloadStateFilterListener: ((String) -> Unit)? = null
    private var modalityFilterListener: ((String?) -> Unit)? = null

    private val textFilterDownloadState:TextView
    private val textFilterModality:TextView
    private val textFilterVendor:TextView
    private var vendorIndex = 0
    private var modalityIndex = 0

    companion object {
        private const val TAG = "FilterComponent"
    }

    init {
        textFilterDownloadState = activity.findViewById(R.id.filter_download_state)
        textFilterDownloadState.setOnClickListener{
            onFilterDownloadStateClick()
        }

        textFilterModality = activity.findViewById(R.id.filter_modality)
        textFilterModality.setOnClickListener {
            onFilterModalityClick()
        }

        textFilterVendor = activity.findViewById(R.id.filter_vendor)
        textFilterVendor.setOnClickListener {
            onFilterVendorClick()
        }
        
        // 确保所有过滤器都可见
        ensureFiltersVisible()
        
        Log.d(TAG, "FilterComponent initialized with all filters visible")
    }

    private fun ensureFiltersVisible() {
        textFilterDownloadState.visibility = View.VISIBLE
        textFilterModality.visibility = View.VISIBLE
        textFilterVendor.visibility = View.VISIBLE
        
        Log.d(TAG, "All filters set to visible - Download: ${textFilterDownloadState.visibility}, Modality: ${textFilterModality.visibility}, Vendor: ${textFilterVendor.visibility}")
    }

    private fun onFilterVendorClick() {
        Log.d(TAG, "Vendor filter clicked, current index: $vendorIndex")
        
        DropDownMenuHelper.showDropDownMenu(activity,
            textFilterVendor,
            ModelVendors.vendorList.toMutableList().apply {
                add(0, activity.getString(R.string.all))
            },
            currentIndex = vendorIndex,
            onItemSelected = { index, item ->
                val hasSelected = index != 0
                vendorIndex = index
                textFilterVendor.text = if(vendorIndex == 0) activity.getString(R.string.vendor_menu_title) else  item.toString()
                vendorFilterListener?.invoke(if (vendorIndex == 0) null else item.toString())
                textFilterVendor.isSelected = hasSelected
                
                Log.d(TAG, "Vendor filter selected: index=$index, item=$item, hasSelected=$hasSelected")
                
                // 确保vendor过滤器在选择后仍然可见
                textFilterVendor.visibility = View.VISIBLE
            }
        )
    }

    private fun onFilterModalityClick() {
        Log.d(TAG, "Modality filter clicked, current index: $modalityIndex")
        
        DropDownMenuHelper.showDropDownMenu(activity,
            textFilterModality,
            Modality.modalitySelectorList.toMutableList().apply {
                add(0, activity.getString(R.string.all))
            },
            currentIndex = modalityIndex,
            onItemSelected = { index, item ->
                val hasSelected = index != 0
                modalityIndex = index
                textFilterModality.text = if(!hasSelected) activity.getString(R.string.modality_menu_title) else  item.toString()
                modalityFilterListener?.invoke(if (!hasSelected) null else item.toString())
                textFilterModality.isSelected = hasSelected
                
                Log.d(TAG, "Modality filter selected: index=$index, item=$item, hasSelected=$hasSelected")
                
                // 确保所有过滤器在模态选择后仍然可见
                ensureFiltersVisible()
            }
        )
    }

    private fun onFilterDownloadStateClick() {
        Log.d(TAG, "Download state filter clicked, current state: ${textFilterDownloadState.isSelected}")
        
        textFilterDownloadState.isSelected = !textFilterDownloadState.isSelected
        downloadStateFilterListener?.invoke(if (textFilterDownloadState.isSelected) "true" else "false")
        
        Log.d(TAG, "Download state filter toggled to: ${textFilterDownloadState.isSelected}")
        
        // 确保所有过滤器在下载状态切换后仍然可见
        ensureFiltersVisible()
    }

    fun addVendorFilterListener(listener: (String?) -> Unit) {
        this.vendorFilterListener = listener
    }

    fun addModalityFilterListener(listener: (String?) -> Unit) {
        this.modalityFilterListener = listener
    }

    fun addDownloadFilterListener(listener: (String) -> Unit) {
        this.downloadStateFilterListener = listener
    }
    
    // 公共方法，用于外部确保过滤器可见性
    fun refreshFilterVisibility() {
        ensureFiltersVisible()
        Log.d(TAG, "Filter visibility refreshed externally")
    }
}


