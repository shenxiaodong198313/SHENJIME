// Created by ruoyi.sjd on 2024/12/25.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
package com.shenji.aikeyboard.mnn.llm

import android.text.TextUtils
import com.shenji.aikeyboard.mnn.chat.model.ChatDataItem

class ChatService {
    private val transformerSessionMap: MutableMap<String, ChatSession> = HashMap()
    private val diffusionSessionMap: MutableMap<String, ChatSession> = HashMap()

    @Synchronized
    fun createLlmSession(
        modelId: String?,
        modelDir: String?,
        sessionIdParam: String?,
        chatDataItemList: List<ChatDataItem>?,
        supportOmni:Boolean
    ): LlmSession {
        var sessionId:String = if (TextUtils.isEmpty(sessionIdParam)) {
            System.currentTimeMillis().toString()
        } else {
            sessionIdParam!!
        }
        val session = LlmSession(modelId!!, sessionId, modelDir!!, chatDataItemList)
        session.supportOmni = supportOmni
        transformerSessionMap[sessionId] = session
        return session
    }

    @Synchronized
    fun createDiffusionSession(
        modelId: String?,
        modelDir: String?,
        sessionIdParam: String?,
        chatDataItemList: List<ChatDataItem>?
    ): ChatSession {
        var sessionId:String = if (TextUtils.isEmpty(sessionIdParam)) {
            System.currentTimeMillis().toString()
        } else {
            sessionIdParam!!
        }
        val session = DiffusionSession( sessionId, modelDir!!)
        diffusionSessionMap[sessionId] = session
        return session
    }

    @Synchronized
    fun getSession(sessionId: String): ChatSession? {
        return if (transformerSessionMap.containsKey(sessionId)) {
            transformerSessionMap[sessionId]
        } else {
            diffusionSessionMap[sessionId]
        }
    }

    @Synchronized
    fun removeSession(sessionId: String) {
        transformerSessionMap.remove(sessionId)
        diffusionSessionMap.remove(sessionId)
    }

    companion object {
        private var instance: ChatService? = null

        @JvmStatic
        @Synchronized
        fun provide(): ChatService {
            if (instance == null) {
                instance = ChatService()
            }
            return instance!!
        }
    }
}



