package com.shenji.aikeyboard.modelscope

import retrofit2.Response
import retrofit2.http.*

/**
 * ModelScope API服务接口
 */
interface ModelScopeApiService {
    
    /**
     * 搜索模型
     */
    @GET("api/v1/models")
    suspend fun searchModels(
        @Query("search") query: String = "",
        @Query("task") task: String? = null,
        @Query("framework") framework: String? = null,
        @Query("sort") sort: String = "downloads",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ModelSearchApiResponse>
    
    /**
     * 获取模型详情
     */
    @GET("api/v1/models/{modelId}")
    suspend fun getModelDetails(
        @Path("modelId") modelId: String
    ): Response<ModelDetailsApiResponse>
    
    /**
     * 获取模型文件列表
     */
    @GET("api/v1/models/{modelId}/files")
    suspend fun getModelFiles(
        @Path("modelId") modelId: String
    ): Response<ModelFilesApiResponse>
    
    /**
     * 获取热门模型
     */
    @GET("api/v1/models/trending")
    suspend fun getTrendingModels(
        @Query("task") task: String? = null,
        @Query("limit") limit: Int = 10
    ): Response<ModelSearchApiResponse>
    
    /**
     * 获取推荐模型
     */
    @GET("api/v1/models/recommended")
    suspend fun getRecommendedModels(
        @Query("task") task: String? = null,
        @Query("limit") limit: Int = 10
    ): Response<ModelSearchApiResponse>
}

/**
 * API响应数据类
 */
data class ModelSearchApiResponse(
    val data: List<ModelApiData>,
    val total: Int,
    val page: Int,
    val limit: Int
)

data class ModelDetailsApiResponse(
    val data: ModelApiData
)

data class ModelFilesApiResponse(
    val data: List<ModelFileData>
)

data class ModelApiData(
    val id: String,
    val name: String,
    val description: String?,
    val author: String,
    val tags: List<String>?,
    val downloads: Long,
    val likes: Int,
    val created_at: String,
    val updated_at: String,
    val framework: String?,
    val task: String?,
    val license: String?,
    val private: Boolean,
    val size: Long?
)

data class ModelFileData(
    val name: String,
    val size: Long,
    val download_url: String,
    val type: String
) 