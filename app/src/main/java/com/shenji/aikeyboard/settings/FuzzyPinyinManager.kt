package com.shenji.aikeyboard.settings

import android.content.Context
import android.content.SharedPreferences
import com.shenji.aikeyboard.ShenjiApplication
import timber.log.Timber

/**
 * 模糊拼音设置管理类
 * 
 * 管理模糊拼音设置的存储和读取，提供默认配置
 */
class FuzzyPinyinManager private constructor() {
    
    companion object {
        // 单例实例
        private var instance: FuzzyPinyinManager? = null
        
        // 获取单例实例
        @JvmStatic
        fun getInstance(): FuzzyPinyinManager {
            if (instance == null) {
                instance = FuzzyPinyinManager()
            }
            return instance!!
        }
        
        // SharedPreferences文件名和键值常量
        private const val PREFS_NAME = "fuzzy_pinyin_prefs"
        
        // 声母模糊匹配键
        private const val KEY_Z_ZH = "fuzzy_z_zh"   // z = zh
        private const val KEY_C_CH = "fuzzy_c_ch"   // c = ch
        private const val KEY_S_SH = "fuzzy_s_sh"   // s = sh
        
        // 韵母模糊匹配键
        private const val KEY_AN_ANG = "fuzzy_an_ang"  // an = ang
        private const val KEY_EN_ENG = "fuzzy_en_eng"  // en = eng
        private const val KEY_IN_ING = "fuzzy_in_ing"  // in = ing
        
        // 其他模糊匹配键
        private const val KEY_L_N = "fuzzy_l_n"     // l = n
    }
    
    // 模糊拼音配置状态
    private var fuzzyEnabled = false
    
    // 声母模糊匹配
    private var zEqualsZh = false    // z = zh
    private var cEqualsCh = false    // c = ch
    private var sEqualsSh = false    // s = sh
    
    // 韵母模糊匹配
    private var anEqualsAng = false  // an = ang
    private var enEqualsEng = false  // en = eng
    private var inEqualsIng = false  // in = ing
    
    // 其他模糊匹配
    private var lEqualsN = false     // l = n
    
    // SharedPreferences实例
    private val prefs: SharedPreferences by lazy {
        ShenjiApplication.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 初始化，加载保存的设置
     */
    init {
        loadSettings()
    }
    
    /**
     * 加载保存的设置
     */
    private fun loadSettings() {
        try {
            // 声母模糊匹配
            zEqualsZh = prefs.getBoolean(KEY_Z_ZH, false)
            cEqualsCh = prefs.getBoolean(KEY_C_CH, false)
            sEqualsSh = prefs.getBoolean(KEY_S_SH, false)
            
            // 韵母模糊匹配
            anEqualsAng = prefs.getBoolean(KEY_AN_ANG, false)
            enEqualsEng = prefs.getBoolean(KEY_EN_ENG, false)
            inEqualsIng = prefs.getBoolean(KEY_IN_ING, false)
            
            // 其他模糊匹配
            lEqualsN = prefs.getBoolean(KEY_L_N, false)
            
            // 检查是否有任何一个模糊拼音设置被启用
            fuzzyEnabled = zEqualsZh || cEqualsCh || sEqualsSh || 
                          anEqualsAng || enEqualsEng || inEqualsIng || 
                          lEqualsN
                          
            Timber.d("已加载模糊拼音设置")
        } catch (e: Exception) {
            Timber.e(e, "加载模糊拼音设置失败")
        }
    }
    
    /**
     * 判断是否有任何模糊拼音设置启用
     */
    fun isFuzzyEnabled(): Boolean {
        return fuzzyEnabled
    }
    
    /**
     * 应用模糊拼音规则到音节
     * 根据当前启用的规则，返回可能的模糊匹配音节列表
     * 
     * @param syllable 原始音节
     * @return 包含原始音节和所有可能的模糊匹配音节的列表
     */
    fun applyFuzzyRules(syllable: String): List<String> {
        // 如果没有启用任何模糊拼音规则，直接返回原始音节
        if (!fuzzyEnabled) {
            return listOf(syllable)
        }
        
        val result = mutableSetOf(syllable)
        
        // 声母模糊匹配
        if (zEqualsZh) {
            applyInitialFuzzy(result, syllable, "z", "zh")
        }
        
        if (cEqualsCh) {
            applyInitialFuzzy(result, syllable, "c", "ch")
        }
        
        if (sEqualsSh) {
            applyInitialFuzzy(result, syllable, "s", "sh")
        }
        
        // 韵母模糊匹配
        if (anEqualsAng) {
            applyFinalFuzzy(result, syllable, "an", "ang")
        }
        
        if (enEqualsEng) {
            applyFinalFuzzy(result, syllable, "en", "eng")
        }
        
        if (inEqualsIng) {
            applyFinalFuzzy(result, syllable, "in", "ing")
        }
        
        // 其他模糊匹配
        if (lEqualsN) {
            applyInitialFuzzy(result, syllable, "l", "n")
        }
        
        return result.toList()
    }
    
    /**
     * 应用声母模糊规则
     */
    private fun applyInitialFuzzy(result: MutableSet<String>, syllable: String, a: String, b: String) {
        if (syllable.startsWith(a)) {
            result.add(b + syllable.substring(a.length))
        } else if (syllable.startsWith(b)) {
            result.add(a + syllable.substring(b.length))
        }
    }
    
    /**
     * 应用韵母模糊规则
     */
    private fun applyFinalFuzzy(result: MutableSet<String>, syllable: String, a: String, b: String) {
        if (syllable.endsWith(a)) {
            result.add(syllable.substring(0, syllable.length - a.length) + b)
        } else if (syllable.endsWith(b)) {
            result.add(syllable.substring(0, syllable.length - b.length) + a)
        }
    }
    
    // Getters
    fun isZEqualsZh(): Boolean = zEqualsZh
    fun isCEqualsCh(): Boolean = cEqualsCh
    fun isSEqualsSh(): Boolean = sEqualsSh
    fun isAnEqualsAng(): Boolean = anEqualsAng
    fun isEnEqualsEng(): Boolean = enEqualsEng
    fun isInEqualsIng(): Boolean = inEqualsIng
    fun isLEqualsN(): Boolean = lEqualsN
    
    // Setters with automatic saving
    fun setZEqualsZh(enabled: Boolean) {
        zEqualsZh = enabled
        prefs.edit().putBoolean(KEY_Z_ZH, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    fun setCEqualsCh(enabled: Boolean) {
        cEqualsCh = enabled
        prefs.edit().putBoolean(KEY_C_CH, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    fun setSEqualsSh(enabled: Boolean) {
        sEqualsSh = enabled
        prefs.edit().putBoolean(KEY_S_SH, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    fun setAnEqualsAng(enabled: Boolean) {
        anEqualsAng = enabled
        prefs.edit().putBoolean(KEY_AN_ANG, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    fun setEnEqualsEng(enabled: Boolean) {
        enEqualsEng = enabled
        prefs.edit().putBoolean(KEY_EN_ENG, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    fun setInEqualsIng(enabled: Boolean) {
        inEqualsIng = enabled
        prefs.edit().putBoolean(KEY_IN_ING, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    fun setLEqualsN(enabled: Boolean) {
        lEqualsN = enabled
        prefs.edit().putBoolean(KEY_L_N, enabled).apply()
        updateFuzzyEnabledStatus()
    }
    
    /**
     * 设置全部模糊拼音规则
     */
    fun setAll(enabled: Boolean) {
        zEqualsZh = enabled
        cEqualsCh = enabled
        sEqualsSh = enabled
        anEqualsAng = enabled
        enEqualsEng = enabled
        inEqualsIng = enabled
        lEqualsN = enabled
        
        prefs.edit()
            .putBoolean(KEY_Z_ZH, enabled)
            .putBoolean(KEY_C_CH, enabled)
            .putBoolean(KEY_S_SH, enabled)
            .putBoolean(KEY_AN_ANG, enabled)
            .putBoolean(KEY_EN_ENG, enabled)
            .putBoolean(KEY_IN_ING, enabled)
            .putBoolean(KEY_L_N, enabled)
            .apply()
            
        updateFuzzyEnabledStatus()
    }
    
    /**
     * 更新模糊拼音总开关状态
     */
    private fun updateFuzzyEnabledStatus() {
        fuzzyEnabled = zEqualsZh || cEqualsCh || sEqualsSh || 
                      anEqualsAng || enEqualsEng || inEqualsIng || 
                      lEqualsN
    }
} 