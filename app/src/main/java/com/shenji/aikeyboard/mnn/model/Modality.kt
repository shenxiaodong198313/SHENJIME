// Created by ruoyi.sjd on 2025/5/22.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.shenji.aikeyboard.mnn.model

import com.shenji.aikeyboard.mnn.model.ModelUtils.isAudioModel
import com.shenji.aikeyboard.mnn.model.ModelUtils.isVisualModel

object Modality {
    const val Text = "Text"
    const val Visual = "Visual"
    const val Audio = "Audio"
    const val Omni = "Omni"
    const val Diffusion = "Diffusion"

    fun checkModality(modelName: String, modality:String): Boolean {
        if (modality == Text) {
            return true
        } else if (modality == Visual) {
            return isVisualModel(modelName)
        } else if (modality == Audio) {
            return isAudioModel(modelName)
        } else if (modality == Omni) {
            return ModelUtils.isOmni(modelName)
        } else if (modality == Diffusion) {
            return ModelUtils.isDiffusionModel(modelName)
        }
        return false
    }

    val modalitySelectorList = listOf(Visual, Audio, Omni, Diffusion)
}


