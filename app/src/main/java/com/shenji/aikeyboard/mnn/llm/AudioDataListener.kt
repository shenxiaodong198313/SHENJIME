// Created by ruoyi.sjd on 2025/5/7.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.shenji.aikeyboard.mnn.llm


interface AudioDataListener {
    fun onAudioData(data: FloatArray, isEnd: Boolean): Boolean
}


