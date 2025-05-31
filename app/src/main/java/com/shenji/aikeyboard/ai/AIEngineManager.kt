package com.shenji.aikeyboard.ai

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * AI引擎管理器
 * 负责多引擎的统一管理和调度
 */
class AIEngineManager private constructor() {
    
    companion object {
        private const val TAG = "AIEngineManager"
        
        @Volatile
        private var INSTANCE: AIEngineManager? = null
        
        fun getInstance(): AIEngineManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AIEngineManager().also { INSTANCE = it }
            }
        }
    }
    
    private val engines = mutableMapOf<String, AIEngine>()
    private var currentEngine: AIEngine? = null
    private var currentEngineId: String? = null
    private val mutex = Mutex()
    
    /**
     * 注册AI引擎
     */
    suspend fun registerEngine(engineId: String, engine: AIEngine) = mutex.withLock {
        engines[engineId] = engine
        Timber.d("$TAG: 注册AI引擎 - $engineId: ${engine.engineInfo.name}")
    }
    
    /**
     * 切换当前引擎
     */
    suspend fun switchEngine(engineId: String): Boolean = mutex.withLock {
        val engine = engines[engineId]
        if (engine == null) {
            Timber.w("$TAG: 引擎不存在 - $engineId")
            return@withLock false
        }
        
        try {
            // 释放当前引擎
            currentEngine?.let { current ->
                Timber.d("$TAG: 释放当前引擎 - $currentEngineId")
                current.release()
            }
            
            // 初始化新引擎
            Timber.d("$TAG: 初始化新引擎 - $engineId")
            val success = engine.initialize()
            
            if (success) {
                currentEngine = engine
                currentEngineId = engineId
                Timber.i("$TAG: 成功切换到引擎 - $engineId")
                return@withLock true
            } else {
                Timber.e("$TAG: 引擎初始化失败 - $engineId")
                return@withLock false
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 切换引擎异常 - $engineId")
            return@withLock false
        }
    }
    
    /**
     * 获取当前引擎
     */
    fun getCurrentEngine(): AIEngine? = currentEngine
    
    /**
     * 获取当前引擎ID
     */
    fun getCurrentEngineId(): String? = currentEngineId
    
    /**
     * 获取所有可用引擎
     */
    fun getAvailableEngines(): Map<String, AIEngineInfo> {
        return engines.mapValues { it.value.engineInfo }
    }
    
    /**
     * 检查引擎是否已注册
     */
    fun isEngineRegistered(engineId: String): Boolean {
        return engines.containsKey(engineId)
    }
    
    /**
     * 获取引擎信息
     */
    fun getEngineInfo(engineId: String): AIEngineInfo? {
        return engines[engineId]?.engineInfo
    }
    
    /**
     * 移除引擎
     */
    suspend fun removeEngine(engineId: String): Boolean = mutex.withLock {
        val engine = engines[engineId]
        if (engine != null) {
            try {
                // 如果是当前引擎，先释放
                if (currentEngineId == engineId) {
                    engine.release()
                    currentEngine = null
                    currentEngineId = null
                }
                
                engines.remove(engineId)
                Timber.d("$TAG: 移除引擎 - $engineId")
                return@withLock true
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 移除引擎异常 - $engineId")
                return@withLock false
            }
        }
        return@withLock false
    }
    
    /**
     * 释放所有引擎
     */
    suspend fun releaseAll() = mutex.withLock {
        try {
            currentEngine?.release()
            currentEngine = null
            currentEngineId = null
            
            engines.values.forEach { engine ->
                try {
                    engine.release()
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: 释放引擎异常")
                }
            }
            
            engines.clear()
            Timber.d("$TAG: 已释放所有引擎")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 释放所有引擎异常")
        }
    }
    
    /**
     * 获取引擎状态信息
     */
    fun getEngineStatus(): EngineStatus {
        return EngineStatus(
            totalEngines = engines.size,
            currentEngineId = currentEngineId,
            currentEngineInfo = currentEngine?.engineInfo,
            availableEngines = engines.keys.toList()
        )
    }
}

/**
 * 引擎状态信息
 */
data class EngineStatus(
    val totalEngines: Int,
    val currentEngineId: String?,
    val currentEngineInfo: AIEngineInfo?,
    val availableEngines: List<String>
) 