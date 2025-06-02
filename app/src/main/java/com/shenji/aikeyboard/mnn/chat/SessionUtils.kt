// Created by ruoyi.sjd on 2025/5/7.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.shenji.aikeyboard.mnn.chat

import com.shenji.aikeyboard.mnn.chat.model.ChatDataItem

object SessionUtils {
    fun generateSessionName(userData: ChatDataItem):String {
        var sessionName = userData.text
        if (userData.audioUri != null) {
            sessionName = "[Audio] $sessionName"
        }
        if (userData.imageUri != null) {
            sessionName = "[Image] $sessionName"
        }
        return if (sessionName!!.length > 100) sessionName.substring(0, 100) else sessionName
    }
}


