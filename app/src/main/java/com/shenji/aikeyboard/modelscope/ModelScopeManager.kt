package com.shenji.aikeyboard.modelscope

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.*
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * ModelScope模型管理器
 * 负责模型搜索、下载、管理和与现有AI引擎的集成
 */
class ModelScopeManager(
    private val context: Context
) : CoroutineScope {
    
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job
    
    // API服务
    private val apiService: ModelScopeApiService
    
    // 下载客户端
    private val downloadClient: OkHttpClient
    
    // 本地存储路径
    private val modelsDir: File
    
    // 下载任务管理
    private val downloadTasks = ConcurrentHashMap<String, DownloadTask>()
    private val _downloadProgress = MutableSharedFlow<DownloadTask>()
    val downloadProgress: SharedFlow<DownloadTask> = _downloadProgress.asSharedFlow()
    
    // 本地模型管理
    private val _localModels = MutableStateFlow<List<LocalModel>>(emptyList())
    val localModels: StateFlow<List<LocalModel>> = _localModels.asStateFlow()
    
    init {
        // 初始化API服务
        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.modelscope.cn/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        apiService = retrofit.create(ModelScopeApiService::class.java)
        
        // 初始化下载客户端
        downloadClient = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        // 初始化本地存储目录
        modelsDir = File(context.filesDir, "modelscope_models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        
        // 加载本地模型
        loadLocalModels()
    }
    
    /**
     * 搜索模型
     */
    suspend fun searchModels(request: ModelSearchRequest): Result<ModelSearchResponse> {
        return try {
            // 使用模拟数据进行搜索
            val allModels = getMockTrendingModels(null, 100) // 获取所有模拟数据
            
            // 根据搜索条件过滤
            val filteredModels = allModels.filter { model ->
                val matchesQuery = request.query.isBlank() || 
                    model.modelName.contains(request.query, ignoreCase = true) ||
                    model.description.contains(request.query, ignoreCase = true) ||
                    model.author.contains(request.query, ignoreCase = true) ||
                    model.tags.any { it.contains(request.query, ignoreCase = true) }
                
                val matchesTask = request.task.isNullOrBlank() ||
                    model.task.contains(request.task, ignoreCase = true) ||
                    model.tags.any { it.contains(request.task, ignoreCase = true) }
                
                val matchesFramework = request.framework.isNullOrBlank() ||
                    model.framework.contains(request.framework, ignoreCase = true)
                
                matchesQuery && matchesTask && matchesFramework
            }
            
            // 分页处理
            val startIndex = (request.page - 1) * request.pageSize
            val endIndex = minOf(startIndex + request.pageSize, filteredModels.size)
            val pageModels = if (startIndex < filteredModels.size) {
                filteredModels.subList(startIndex, endIndex)
            } else {
                emptyList()
            }
            
            val searchResponse = ModelSearchResponse(
                models = pageModels,
                total = filteredModels.size,
                page = request.page,
                pageSize = request.pageSize,
                hasMore = endIndex < filteredModels.size
            )
            
            Result.success(searchResponse)
        } catch (e: Exception) {
            Timber.e(e, "搜索模型失败")
            Result.failure(e)
        }
    }
    
    /**
     * 获取热门模型
     */
    suspend fun getTrendingModels(task: String? = null, limit: Int = 10): Result<List<ModelScopeModel>> {
        return try {
            // 暂时返回模拟数据，包含Gemma 3n等热门模型
            val mockModels = getMockTrendingModels(task, limit)
            Result.success(mockModels)
        } catch (e: Exception) {
            Timber.e(e, "获取热门模型失败")
            Result.failure(e)
        }
    }
    
    /**
     * 获取模拟的热门模型数据
     */
    private fun getMockTrendingModels(task: String?, limit: Int): List<ModelScopeModel> {
        val allModels = listOf(
            ModelScopeModel(
                modelId = "google/gemma-3n-E4B-it-litert-preview",
                modelName = "Gemma 3n (E4B IT LiteRT Preview)",
                description = "Google DeepMind最新发布的Gemma 3n模型，专为移动端优化的轻量级版本。支持多语言对话和文本生成，具有出色的推理能力。",
                author = "Google DeepMind",
                tags = listOf("text-generation", "conversational", "multilingual", "mobile-optimized"),
                downloads = 15420,
                likes = 892,
                createdAt = Date(),
                updatedAt = Date(),
                modelSize = 4200000000L, // 4.2GB
                framework = "LiteRT",
                task = "text-generation",
                license = "Apache-2.0",
                isPrivate = false
            ),
            ModelScopeModel(
                modelId = "qwen/Qwen2.5-7B-Instruct",
                modelName = "Qwen2.5-7B-Instruct",
                description = "阿里云通义千问2.5系列指令微调模型，在中文理解和生成方面表现优异，支持多轮对话。",
                author = "Alibaba Cloud",
                tags = listOf("text-generation", "chinese", "instruction-following"),
                downloads = 28750,
                likes = 1456,
                createdAt = Date(),
                updatedAt = Date(),
                modelSize = 7000000000L, // 7GB
                framework = "PyTorch",
                task = "text-generation",
                license = "Apache-2.0",
                isPrivate = false
            ),
            ModelScopeModel(
                modelId = "baichuan-inc/Baichuan2-7B-Chat",
                modelName = "Baichuan2-7B-Chat",
                description = "百川智能开源的对话模型，在中文对话任务上表现出色，支持多种应用场景。",
                author = "Baichuan Inc",
                tags = listOf("text-generation", "chat", "chinese"),
                downloads = 19340,
                likes = 723,
                createdAt = Date(),
                updatedAt = Date(),
                modelSize = 6800000000L, // 6.8GB
                framework = "PyTorch",
                task = "text-generation",
                license = "Apache-2.0",
                isPrivate = false
            ),
            ModelScopeModel(
                modelId = "microsoft/DialoGPT-medium",
                modelName = "DialoGPT Medium",
                description = "微软开发的对话生成模型，基于GPT-2架构，专门针对对话场景进行优化。",
                author = "Microsoft",
                tags = listOf("conversational-ai", "dialogue", "english"),
                downloads = 12890,
                likes = 567,
                createdAt = Date(),
                updatedAt = Date(),
                modelSize = 1200000000L, // 1.2GB
                framework = "PyTorch",
                task = "conversational",
                license = "MIT",
                isPrivate = false
            ),
            ModelScopeModel(
                modelId = "THUDM/chatglm3-6b",
                modelName = "ChatGLM3-6B",
                description = "清华大学开源的对话语言模型，支持中英文对话，具有强大的理解和生成能力。",
                author = "THUDM",
                tags = listOf("text-generation", "chat", "bilingual"),
                downloads = 34560,
                likes = 1789,
                createdAt = Date(),
                updatedAt = Date(),
                modelSize = 6200000000L, // 6.2GB
                framework = "PyTorch",
                task = "text-generation",
                license = "Apache-2.0",
                isPrivate = false
            ),
            ModelScopeModel(
                modelId = "deepseek-ai/deepseek-coder-6.7b-instruct",
                modelName = "DeepSeek Coder 6.7B Instruct",
                description = "深度求索开发的代码生成模型，专门针对编程任务优化，支持多种编程语言。",
                author = "DeepSeek AI",
                tags = listOf("code-generation", "programming", "instruction-following"),
                downloads = 8750,
                likes = 445,
                createdAt = Date(),
                updatedAt = Date(),
                modelSize = 6700000000L, // 6.7GB
                framework = "PyTorch",
                task = "code-generation",
                license = "Apache-2.0",
                isPrivate = false
            )
        )
        
        // 根据任务类型过滤
        val filteredModels = if (task != null) {
            allModels.filter { it.task.contains(task, ignoreCase = true) || it.tags.any { tag -> tag.contains(task, ignoreCase = true) } }
        } else {
            allModels
        }
        
        return filteredModels.take(limit)
    }
    
    /**
     * 获取推荐模型
     */
    suspend fun getRecommendedModels(task: String? = null, limit: Int = 10): Result<List<ModelScopeModel>> {
        return try {
            val response = apiService.getRecommendedModels(task, limit)
            if (response.isSuccessful) {
                val models = response.body()!!.data.map { it.toModelScopeModel() }
                Result.success(models)
            } else {
                Result.failure(Exception("获取推荐模型失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "获取推荐模型失败")
            Result.failure(e)
        }
    }
    
    /**
     * 下载模型
     */
    suspend fun downloadModel(model: ModelScopeModel): Result<String> {
        return try {
            // 检查是否已经在下载
            if (downloadTasks.containsKey(model.modelId)) {
                return Result.failure(Exception("模型正在下载中"))
            }
            
            // 根据模型ID构建真实的下载URL
            val downloadUrl = when (model.modelId) {
                "google/gemma-3n-E4B-it-litert-preview" -> {
                    // Gemma 3n的真实下载链接
                    "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/model.tflite"
                }
                "qwen/Qwen2.5-7B-Instruct" -> {
                    "https://modelscope.cn/models/qwen/Qwen2.5-7B-Instruct/resolve/master/pytorch_model.bin"
                }
                "baichuan-inc/Baichuan2-7B-Chat" -> {
                    "https://modelscope.cn/models/baichuan-inc/Baichuan2-7B-Chat/resolve/master/pytorch_model.bin"
                }
                "microsoft/DialoGPT-medium" -> {
                    "https://huggingface.co/microsoft/DialoGPT-medium/resolve/main/pytorch_model.bin"
                }
                "THUDM/chatglm3-6b" -> {
                    "https://modelscope.cn/models/ZhipuAI/chatglm3-6b/resolve/master/pytorch_model.bin"
                }
                "deepseek-ai/deepseek-coder-6.7b-instruct" -> {
                    "https://modelscope.cn/models/deepseek-ai/deepseek-coder-6.7b-instruct/resolve/master/pytorch_model.bin"
                }
                else -> {
                    // 默认构建下载URL
                    "https://modelscope.cn/models/${model.modelId}/resolve/master/pytorch_model.bin"
                }
            }
            
            val taskId = UUID.randomUUID().toString()
            val fileName = when {
                model.framework.contains("LiteRT", ignoreCase = true) -> "${model.modelId.replace("/", "_")}.tflite"
                model.framework.contains("ONNX", ignoreCase = true) -> "${model.modelId.replace("/", "_")}.onnx"
                model.framework.contains("MNN", ignoreCase = true) -> "${model.modelId.replace("/", "_")}.mnn"
                else -> "${model.modelId.replace("/", "_")}.bin"
            }
            
            val localPath = File(modelsDir, "${model.modelId.replace("/", "_")}/$fileName").absolutePath
            val downloadTask = DownloadTask(
                taskId = taskId,
                modelId = model.modelId,
                modelName = model.modelName,
                downloadUrl = downloadUrl,
                localPath = localPath,
                totalSize = model.modelSize,
                status = DownloadStatus.PENDING
            )
            
            downloadTasks[model.modelId] = downloadTask
            
            // 开始真实下载
            launch {
                performDownload(downloadTask)
            }
            
            Result.success(taskId)
        } catch (e: Exception) {
            Timber.e(e, "开始下载模型失败")
            Result.failure(e)
        }
    }
    

    /**
     * 执行下载
     */
    private suspend fun performDownload(task: DownloadTask) {
        try {
            val localFile = File(task.localPath)
            localFile.parentFile?.mkdirs()
            
            val request = Request.Builder()
                .url(task.downloadUrl)
                .build()
            
            val updatedTask = task.copy(
                status = DownloadStatus.DOWNLOADING,
                startTime = Date()
            )
            downloadTasks[task.modelId] = updatedTask
            _downloadProgress.emit(updatedTask)
            
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("下载失败: ${response.code}")
                }
                
                val body = response.body ?: throw Exception("响应体为空")
                val totalBytes = body.contentLength()
                
                body.byteStream().use { inputStream ->
                    FileOutputStream(localFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var downloadedBytes = 0L
                        var lastProgressUpdate = 0L
                        var bytesRead: Int
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            
                            // 每1MB或下载完成时更新进度
                            if (downloadedBytes - lastProgressUpdate >= 1024 * 1024 || bytesRead == -1) {
                                val progress = if (totalBytes > 0) {
                                    downloadedBytes.toFloat() / totalBytes.toFloat()
                                } else {
                                    0f
                                }
                                
                                val progressTask = downloadTasks[task.modelId]?.copy(
                                    downloadedSize = downloadedBytes,
                                    progress = progress
                                )
                                
                                if (progressTask != null) {
                                    downloadTasks[task.modelId] = progressTask
                                    _downloadProgress.emit(progressTask)
                                    lastProgressUpdate = downloadedBytes
                                }
                            }
                        }
                    }
                }
            }
            
            // 下载完成
            val completedTask = downloadTasks[task.modelId]?.copy(
                status = DownloadStatus.COMPLETED,
                endTime = Date(),
                progress = 1.0f
            )
            
            if (completedTask != null) {
                downloadTasks[task.modelId] = completedTask
                _downloadProgress.emit(completedTask)
                
                // 添加到本地模型列表
                addLocalModel(completedTask)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "下载模型失败: ${task.modelName}")
            val failedTask = downloadTasks[task.modelId]?.copy(
                status = DownloadStatus.FAILED,
                endTime = Date(),
                error = e.message
            )
            
            if (failedTask != null) {
                downloadTasks[task.modelId] = failedTask
                _downloadProgress.emit(failedTask)
            }
        }
    }
    
    /**
     * 添加本地模型
     */
    private fun addLocalModel(downloadTask: DownloadTask) {
        val localModel = LocalModel(
            modelId = downloadTask.modelId,
            modelName = downloadTask.modelName,
            localPath = downloadTask.localPath,
            downloadedAt = Date(),
            version = "1.0",
            modelSize = downloadTask.totalSize,
            status = ModelStatus.DOWNLOADED,
            framework = "unknown",
            task = "unknown"
        )
        
        val currentModels = _localModels.value.toMutableList()
        currentModels.add(localModel)
        _localModels.value = currentModels
        
        // 保存到本地存储
        saveLocalModels()
    }
    
    /**
     * 加载本地模型
     */
    private fun loadLocalModels() {
        // 这里可以从SharedPreferences或数据库加载
        // 暂时扫描本地目录
        launch {
            try {
                val models = mutableListOf<LocalModel>()
                modelsDir.listFiles()?.forEach { modelDir ->
                    if (modelDir.isDirectory) {
                        val modelFiles = modelDir.listFiles()?.filter { 
                            it.name.endsWith(".bin") || it.name.endsWith(".onnx") || it.name.endsWith(".mnn")
                        }
                        
                        modelFiles?.forEach { file ->
                            val localModel = LocalModel(
                                modelId = modelDir.name,
                                modelName = modelDir.name,
                                localPath = file.absolutePath,
                                downloadedAt = Date(file.lastModified()),
                                version = "1.0",
                                modelSize = file.length(),
                                status = ModelStatus.DOWNLOADED,
                                framework = when {
                                    file.name.endsWith(".mnn") -> "MNN"
                                    file.name.endsWith(".onnx") -> "ONNX"
                                    else -> "Unknown"
                                },
                                task = "unknown"
                            )
                            models.add(localModel)
                        }
                    }
                }
                _localModels.value = models
            } catch (e: Exception) {
                Timber.e(e, "加载本地模型失败")
            }
        }
    }
    
    /**
     * 保存本地模型信息
     */
    private fun saveLocalModels() {
        // 这里可以保存到SharedPreferences或数据库
        // 暂时不实现持久化
    }
    
    /**
     * 删除本地模型
     */
    suspend fun deleteLocalModel(modelId: String): Result<Unit> {
        return try {
            val modelDir = File(modelsDir, modelId)
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
            }
            
            val currentModels = _localModels.value.toMutableList()
            currentModels.removeAll { it.modelId == modelId }
            _localModels.value = currentModels
            
            saveLocalModels()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "删除本地模型失败")
            Result.failure(e)
        }
    }
    
    /**
     * 获取下载任务
     */
    fun getDownloadTask(modelId: String): DownloadTask? {
        return downloadTasks[modelId]
    }
    
    /**
     * 取消下载
     */
    fun cancelDownload(modelId: String) {
        downloadTasks[modelId]?.let { task ->
            val cancelledTask = task.copy(
                status = DownloadStatus.CANCELLED,
                endTime = Date()
            )
            downloadTasks[modelId] = cancelledTask
            launch {
                _downloadProgress.emit(cancelledTask)
            }
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        job.cancel()
    }
}

/**
 * 扩展函数：将API数据转换为模型数据
 */
private fun ModelApiData.toModelScopeModel(): ModelScopeModel {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    return ModelScopeModel(
        modelId = id,
        modelName = name,
        description = description ?: "",
        author = author,
        tags = tags ?: emptyList(),
        downloads = downloads,
        likes = likes,
        createdAt = try { dateFormat.parse(created_at) ?: Date() } catch (e: Exception) { Date() },
        updatedAt = try { dateFormat.parse(updated_at) ?: Date() } catch (e: Exception) { Date() },
        modelSize = size ?: 0L,
        framework = framework ?: "unknown",
        task = task ?: "unknown",
        license = license ?: "unknown",
        isPrivate = private
    )
} 