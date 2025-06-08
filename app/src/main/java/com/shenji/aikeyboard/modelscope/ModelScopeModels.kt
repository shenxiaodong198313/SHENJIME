package com.shenji.aikeyboard.modelscope

import java.util.Date

/**
 * ModelScope模型信息
 */
data class ModelScopeModel(
    val modelId: String,                    // 模型ID
    val modelName: String,                  // 模型名称
    val description: String,                // 模型描述
    val author: String,                     // 作者
    val tags: List<String>,                 // 标签
    val downloads: Long,                    // 下载次数
    val likes: Int,                         // 点赞数
    val createdAt: Date,                    // 创建时间
    val updatedAt: Date,                    // 更新时间
    val modelSize: Long,                    // 模型大小(字节)
    val framework: String,                  // 框架类型 (pytorch, tensorflow, etc.)
    val task: String,                       // 任务类型 (text-generation, etc.)
    val license: String,                    // 许可证
    val isPrivate: Boolean = false,         // 是否私有
    val downloadUrl: String? = null,        // 下载链接
    val configUrl: String? = null,          // 配置文件链接
    val readmeUrl: String? = null           // README链接
)

/**
 * 本地模型信息
 */
data class LocalModel(
    val modelId: String,                    // 模型ID
    val modelName: String,                  // 模型名称
    val localPath: String,                  // 本地路径
    val downloadedAt: Date,                 // 下载时间
    val version: String,                    // 版本号
    val modelSize: Long,                    // 模型大小
    val status: ModelStatus,                // 模型状态
    val framework: String,                  // 框架类型
    val task: String,                       // 任务类型
    val isActive: Boolean = false           // 是否激活使用
)

/**
 * 模型状态枚举
 */
enum class ModelStatus {
    DOWNLOADING,        // 下载中
    DOWNLOADED,         // 已下载
    INSTALLING,         // 安装中
    INSTALLED,          // 已安装
    ACTIVE,            // 激活中
    ERROR,             // 错误状态
    PAUSED             // 暂停下载
}

/**
 * 下载任务信息
 */
data class DownloadTask(
    val taskId: String,                     // 任务ID
    val modelId: String,                    // 模型ID
    val modelName: String,                  // 模型名称
    val downloadUrl: String,                // 下载链接
    val localPath: String,                  // 本地保存路径
    val totalSize: Long,                    // 总大小
    val downloadedSize: Long = 0,           // 已下载大小
    val progress: Float = 0f,               // 下载进度 0.0-1.0
    val status: DownloadStatus,             // 下载状态
    val startTime: Date? = null,            // 开始时间
    val endTime: Date? = null,              // 结束时间
    val error: String? = null               // 错误信息
)

/**
 * 下载状态枚举
 */
enum class DownloadStatus {
    PENDING,           // 等待中
    DOWNLOADING,       // 下载中
    PAUSED,           // 暂停
    COMPLETED,        // 完成
    FAILED,           // 失败
    CANCELLED         // 取消
}

/**
 * ModelScope搜索请求
 */
data class ModelSearchRequest(
    val query: String = "",                 // 搜索关键词
    val task: String? = null,               // 任务类型过滤
    val framework: String? = null,          // 框架过滤
    val sort: String = "downloads",         // 排序方式
    val page: Int = 1,                      // 页码
    val pageSize: Int = 20                  // 每页数量
)

/**
 * ModelScope搜索响应
 */
data class ModelSearchResponse(
    val models: List<ModelScopeModel>,      // 模型列表
    val total: Int,                         // 总数量
    val page: Int,                          // 当前页
    val pageSize: Int,                      // 每页数量
    val hasMore: Boolean                    // 是否有更多
)

/**
 * 模型分类
 */
enum class ModelCategory(val displayName: String, val taskType: String) {
    TEXT_GENERATION("文本生成", "text-generation"),
    CONVERSATIONAL("对话聊天", "conversational"),
    TEXT_CLASSIFICATION("文本分类", "text-classification"),
    TOKEN_CLASSIFICATION("词元分类", "token-classification"),
    QUESTION_ANSWERING("问答系统", "question-answering"),
    SUMMARIZATION("文本摘要", "summarization"),
    TRANSLATION("机器翻译", "translation"),
    TEXT_TO_SPEECH("语音合成", "text-to-speech"),
    AUTOMATIC_SPEECH_RECOGNITION("语音识别", "automatic-speech-recognition"),
    IMAGE_CLASSIFICATION("图像分类", "image-classification"),
    OBJECT_DETECTION("目标检测", "object-detection"),
    IMAGE_GENERATION("图像生成", "image-generation"),
    MULTIMODAL("多模态", "multimodal"),
    OTHER("其他", "other")
}

/**
 * 模型兼容性信息
 */
data class ModelCompatibility(
    val supportsMNN: Boolean = false,       // 支持MNN推理
    val supportsMediaPipe: Boolean = false, // 支持MediaPipe
    val minAndroidVersion: Int = 26,        // 最低Android版本
    val requiredMemory: Long = 0,           // 所需内存(MB)
    val recommendedMemory: Long = 0,        // 推荐内存(MB)
    val supportedArchitectures: List<String> = listOf("arm64-v8a"), // 支持的架构
    val notes: String? = null               // 兼容性说明
) 