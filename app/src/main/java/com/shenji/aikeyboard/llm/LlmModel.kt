package com.shenji.aikeyboard.llm

/**
 * LLM模型数据类
 */
data class LlmModel(
    val id: String,
    val name: String,
    val description: String,
    val size: String,
    val fileName: String,
    val isDownloaded: Boolean = false,
    val isInitialized: Boolean = false
) {
    companion object {
        /**
         * 获取可用的模型列表
         */
        fun getAvailableModels(): List<LlmModel> {
            return listOf(
                LlmModel(
                    id = "gemma3-1b-it-int4",
                    name = "Gemma-3-1B-IT-INT4",
                    description = "轻量级对话模型，适合移动设备使用",
                    size = "554.7 MB",
                    fileName = "gemma3-1b-it-int4.task",
                    isDownloaded = true // 假设已下载
                )
            )
        }
    }
} 