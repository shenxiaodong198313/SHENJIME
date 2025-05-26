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
        
        // 总开关
        private const val KEY_FUZZY_ENABLED = "fuzzy_enabled"
        
        // 声母模糊匹配键
        private const val KEY_C_CH = "fuzzy_c_ch"   // c = ch (默认启用)
        private const val KEY_S_SH = "fuzzy_s_sh"   // s = sh
        private const val KEY_Z_ZH = "fuzzy_z_zh"   // z = zh (默认启用)
        private const val KEY_K_G = "fuzzy_k_g"     // k = g
        private const val KEY_F_H = "fuzzy_f_h"     // f = h
        private const val KEY_N_L = "fuzzy_n_l"     // n = l
        private const val KEY_R_L = "fuzzy_r_l"     // r = l
        
        // 韵母模糊匹配键
        private const val KEY_AN_ANG = "fuzzy_an_ang"  // an = ang (默认启用)
        private const val KEY_EN_ENG = "fuzzy_en_eng"  // en = eng (默认启用)
        private const val KEY_IN_ING = "fuzzy_in_ing"  // in = ing (默认启用)
        private const val KEY_IAN_IANG = "fuzzy_ian_iang"  // ian = iang
        private const val KEY_UAN_UANG = "fuzzy_uan_uang"  // uan = uang
        private const val KEY_AN_AI = "fuzzy_an_ai"    // an = ai
        private const val KEY_UN_ONG = "fuzzy_un_ong"  // un = ong
        
        // 拼音音节
        private const val KEY_HUI_FEI = "fuzzy_hui_fei"      // hui = fei
        private const val KEY_HUANG_WANG = "fuzzy_huang_wang" // huang = wang
        private const val KEY_FENG_HONG = "fuzzy_feng_hong"  // feng = hong
        private const val KEY_FU_HU = "fuzzy_fu_hu"          // fu = hu
        
        // v/ü模糊匹配（为了向后兼容）
        private const val KEY_V_U = "fuzzy_v_u"             // v = ü
    }
    
    // 模糊拼音总开关状态
    private var fuzzyEnabled = false
    
    // 声母模糊匹配
    private var cEqualsCh = true     // c = ch (默认启用)
    private var sEqualsSh = false    // s = sh
    private var zEqualsZh = true     // z = zh (默认启用)
    private var kEqualsG = false     // k = g
    private var fEqualsH = false     // f = h
    private var nEqualsL = false     // n = l
    private var rEqualsL = false     // r = l
    
    // 韵母模糊匹配
    private var anEqualsAng = true   // an = ang (默认启用)
    private var enEqualsEng = true   // en = eng (默认启用)
    private var inEqualsIng = true   // in = ing (默认启用)
    private var ianEqualsIang = false // ian = iang
    private var uanEqualsUang = false // uan = uang
    private var anEqualsAi = false   // an = ai
    private var unEqualsOng = false  // un = ong
    
    // 拼音音节
    private var huiEqualsFei = false    // hui = fei
    private var huangEqualsWang = false // huang = wang
    private var fengEqualsHong = false  // feng = hong
    private var fuEqualsHu = false      // fu = hu
    
    // v/ü模糊匹配（为了向后兼容）
    private var vEqualsU = true         // v = ü (默认启用，支持v代替ü的输入)

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
            // 总开关
            fuzzyEnabled = prefs.getBoolean(KEY_FUZZY_ENABLED, false)
            
            // 声母模糊匹配
            cEqualsCh = prefs.getBoolean(KEY_C_CH, true)
            sEqualsSh = prefs.getBoolean(KEY_S_SH, false)
            zEqualsZh = prefs.getBoolean(KEY_Z_ZH, true)
            kEqualsG = prefs.getBoolean(KEY_K_G, false)
            fEqualsH = prefs.getBoolean(KEY_F_H, false)
            nEqualsL = prefs.getBoolean(KEY_N_L, false)
            rEqualsL = prefs.getBoolean(KEY_R_L, false)
            
            // 韵母模糊匹配
            anEqualsAng = prefs.getBoolean(KEY_AN_ANG, true)
            enEqualsEng = prefs.getBoolean(KEY_EN_ENG, true)
            inEqualsIng = prefs.getBoolean(KEY_IN_ING, true)
            ianEqualsIang = prefs.getBoolean(KEY_IAN_IANG, false)
            uanEqualsUang = prefs.getBoolean(KEY_UAN_UANG, false)
            anEqualsAi = prefs.getBoolean(KEY_AN_AI, false)
            unEqualsOng = prefs.getBoolean(KEY_UN_ONG, false)
            
            // 拼音音节
            huiEqualsFei = prefs.getBoolean(KEY_HUI_FEI, false)
            huangEqualsWang = prefs.getBoolean(KEY_HUANG_WANG, false)
            fengEqualsHong = prefs.getBoolean(KEY_FENG_HONG, false)
            fuEqualsHu = prefs.getBoolean(KEY_FU_HU, false)
            
            // v/ü模糊匹配（为了向后兼容）
            vEqualsU = prefs.getBoolean(KEY_V_U, true)
                          
            Timber.d("已加载模糊拼音设置，总开关: $fuzzyEnabled")
        } catch (e: Exception) {
            Timber.e(e, "加载模糊拼音设置失败")
        }
    }
    
    /**
     * 判断是否启用模糊拼音
     */
    fun isFuzzyEnabled(): Boolean {
        return fuzzyEnabled
    }
    
    /**
     * 设置模糊拼音总开关
     */
    fun setFuzzyEnabled(enabled: Boolean) {
        fuzzyEnabled = enabled
        prefs.edit().putBoolean(KEY_FUZZY_ENABLED, enabled).apply()
        Timber.d("设置模糊拼音总开关: $enabled")
    }
    
    /**
     * 检查某个选项是否为默认启用项
     */
    fun isDefaultEnabled(key: String): Boolean {
        return when (key) {
            KEY_C_CH, KEY_Z_ZH, KEY_AN_ANG, KEY_EN_ENG, KEY_IN_ING -> true
            else -> false
        }
    }
    
    /**
     * 应用模糊拼音规则到音节
     * 根据当前启用的规则，返回可能的模糊匹配音节列表
     * 
     * @param syllable 原始音节
     * @return 包含原始音节和所有可能的模糊匹配音节的列表
     */
    fun applyFuzzyRules(syllable: String): List<String> {
        // 如果没有启用模糊拼音总开关，直接返回原始音节
        if (!fuzzyEnabled) {
            return listOf(syllable)
        }
        
        val result = mutableSetOf(syllable)
        
        // 声母模糊匹配
        if (cEqualsCh) {
            applyInitialFuzzy(result, syllable, "c", "ch")
        }
        
        if (sEqualsSh) {
            applyInitialFuzzy(result, syllable, "s", "sh")
        }
        
        if (zEqualsZh) {
            applyInitialFuzzy(result, syllable, "z", "zh")
        }
        
        if (kEqualsG) {
            applyInitialFuzzy(result, syllable, "k", "g")
        }
        
        if (fEqualsH) {
            applyInitialFuzzy(result, syllable, "f", "h")
        }
        
        if (nEqualsL) {
            applyInitialFuzzy(result, syllable, "n", "l")
        }
        
        if (rEqualsL) {
            applyInitialFuzzy(result, syllable, "r", "l")
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
        
        if (ianEqualsIang) {
            applyFinalFuzzy(result, syllable, "ian", "iang")
        }
        
        if (uanEqualsUang) {
            applyFinalFuzzy(result, syllable, "uan", "uang")
        }
        
        if (anEqualsAi) {
            applyFinalFuzzy(result, syllable, "an", "ai")
        }
        
        if (unEqualsOng) {
            applyFinalFuzzy(result, syllable, "un", "ong")
        }
        
        // 拼音音节
        if (huiEqualsFei) {
            if (syllable == "hui") result.add("fei")
            else if (syllable == "fei") result.add("hui")
        }
        
        if (huangEqualsWang) {
            if (syllable == "huang") result.add("wang")
            else if (syllable == "wang") result.add("huang")
        }
        
        if (fengEqualsHong) {
            if (syllable == "feng") result.add("hong")
            else if (syllable == "hong") result.add("feng")
        }
        
        if (fuEqualsHu) {
            if (syllable == "fu") result.add("hu")
            else if (syllable == "hu") result.add("fu")
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
    fun isCEqualsCh(): Boolean = cEqualsCh
    fun isSEqualsSh(): Boolean = sEqualsSh
    fun isZEqualsZh(): Boolean = zEqualsZh
    fun isKEqualsG(): Boolean = kEqualsG
    fun isFEqualsH(): Boolean = fEqualsH
    fun isNEqualsL(): Boolean = nEqualsL
    fun isREqualsL(): Boolean = rEqualsL
    fun isAnEqualsAng(): Boolean = anEqualsAng
    fun isEnEqualsEng(): Boolean = enEqualsEng
    fun isInEqualsIng(): Boolean = inEqualsIng
    fun isIanEqualsIang(): Boolean = ianEqualsIang
    fun isUanEqualsUang(): Boolean = uanEqualsUang
    fun isAnEqualsAi(): Boolean = anEqualsAi
    fun isUnEqualsOng(): Boolean = unEqualsOng
    fun isHuiEqualsFei(): Boolean = huiEqualsFei
    fun isHuangEqualsWang(): Boolean = huangEqualsWang
    fun isFengEqualsHong(): Boolean = fengEqualsHong
    fun isFuEqualsHu(): Boolean = fuEqualsHu
    
    // v/ü模糊匹配getter（为了向后兼容）
    fun isVEqualsU(): Boolean = vEqualsU
    
    // Setters with automatic saving
    fun setCEqualsCh(enabled: Boolean) {
        cEqualsCh = enabled
        prefs.edit().putBoolean(KEY_C_CH, enabled).apply()
    }
    
    fun setSEqualsSh(enabled: Boolean) {
        sEqualsSh = enabled
        prefs.edit().putBoolean(KEY_S_SH, enabled).apply()
    }
    
    fun setZEqualsZh(enabled: Boolean) {
        zEqualsZh = enabled
        prefs.edit().putBoolean(KEY_Z_ZH, enabled).apply()
    }
    
    fun setKEqualsG(enabled: Boolean) {
        kEqualsG = enabled
        prefs.edit().putBoolean(KEY_K_G, enabled).apply()
    }
    
    fun setFEqualsH(enabled: Boolean) {
        fEqualsH = enabled
        prefs.edit().putBoolean(KEY_F_H, enabled).apply()
    }
    
    fun setNEqualsL(enabled: Boolean) {
        nEqualsL = enabled
        prefs.edit().putBoolean(KEY_N_L, enabled).apply()
    }
    
    fun setREqualsL(enabled: Boolean) {
        rEqualsL = enabled
        prefs.edit().putBoolean(KEY_R_L, enabled).apply()
    }
    
    fun setAnEqualsAng(enabled: Boolean) {
        anEqualsAng = enabled
        prefs.edit().putBoolean(KEY_AN_ANG, enabled).apply()
    }
    
    fun setEnEqualsEng(enabled: Boolean) {
        enEqualsEng = enabled
        prefs.edit().putBoolean(KEY_EN_ENG, enabled).apply()
    }
    
    fun setInEqualsIng(enabled: Boolean) {
        inEqualsIng = enabled
        prefs.edit().putBoolean(KEY_IN_ING, enabled).apply()
    }
    
    fun setIanEqualsIang(enabled: Boolean) {
        ianEqualsIang = enabled
        prefs.edit().putBoolean(KEY_IAN_IANG, enabled).apply()
    }
    
    fun setUanEqualsUang(enabled: Boolean) {
        uanEqualsUang = enabled
        prefs.edit().putBoolean(KEY_UAN_UANG, enabled).apply()
    }
    
    fun setAnEqualsAi(enabled: Boolean) {
        anEqualsAi = enabled
        prefs.edit().putBoolean(KEY_AN_AI, enabled).apply()
    }
    
    fun setUnEqualsOng(enabled: Boolean) {
        unEqualsOng = enabled
        prefs.edit().putBoolean(KEY_UN_ONG, enabled).apply()
    }
    
    fun setHuiEqualsFei(enabled: Boolean) {
        huiEqualsFei = enabled
        prefs.edit().putBoolean(KEY_HUI_FEI, enabled).apply()
    }
    
    fun setHuangEqualsWang(enabled: Boolean) {
        huangEqualsWang = enabled
        prefs.edit().putBoolean(KEY_HUANG_WANG, enabled).apply()
    }
    
    fun setFengEqualsHong(enabled: Boolean) {
        fengEqualsHong = enabled
        prefs.edit().putBoolean(KEY_FENG_HONG, enabled).apply()
    }
    
    fun setFuEqualsHu(enabled: Boolean) {
        fuEqualsHu = enabled
        prefs.edit().putBoolean(KEY_FU_HU, enabled).apply()
    }
    
    // v/ü模糊匹配setter（为了向后兼容）
    fun setVEqualsU(enabled: Boolean) {
        vEqualsU = enabled
        prefs.edit().putBoolean(KEY_V_U, enabled).apply()
    }
    
    /**
     * 设置全部模糊拼音规则
     */
    fun setAll(enabled: Boolean) {
        cEqualsCh = enabled
        sEqualsSh = enabled
        zEqualsZh = enabled
        kEqualsG = enabled
        fEqualsH = enabled
        nEqualsL = enabled
        rEqualsL = enabled
        anEqualsAng = enabled
        enEqualsEng = enabled
        inEqualsIng = enabled
        ianEqualsIang = enabled
        uanEqualsUang = enabled
        anEqualsAi = enabled
        unEqualsOng = enabled
        huiEqualsFei = enabled
        huangEqualsWang = enabled
        fengEqualsHong = enabled
        fuEqualsHu = enabled
        
        prefs.edit()
            .putBoolean(KEY_C_CH, enabled)
            .putBoolean(KEY_S_SH, enabled)
            .putBoolean(KEY_Z_ZH, enabled)
            .putBoolean(KEY_K_G, enabled)
            .putBoolean(KEY_F_H, enabled)
            .putBoolean(KEY_N_L, enabled)
            .putBoolean(KEY_R_L, enabled)
            .putBoolean(KEY_AN_ANG, enabled)
            .putBoolean(KEY_EN_ENG, enabled)
            .putBoolean(KEY_IN_ING, enabled)
            .putBoolean(KEY_IAN_IANG, enabled)
            .putBoolean(KEY_UAN_UANG, enabled)
            .putBoolean(KEY_AN_AI, enabled)
            .putBoolean(KEY_UN_ONG, enabled)
            .putBoolean(KEY_HUI_FEI, enabled)
            .putBoolean(KEY_HUANG_WANG, enabled)
            .putBoolean(KEY_FENG_HONG, enabled)
            .putBoolean(KEY_FU_HU, enabled)
            .apply()
    }
} 