package com.shenji.aikeyboard.settings

import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.shenji.aikeyboard.R
import timber.log.Timber

/**
 * 模糊拼音设置Activity
 * 用于管理模糊拼音设置
 */
class FuzzyPinyinSettingsActivity : AppCompatActivity() {

    // 模糊拼音管理器
    private val fuzzyPinyinManager = FuzzyPinyinManager.getInstance()
    
    // 总开关
    private lateinit var switchFuzzyEnabled: SwitchMaterial
    
    // 声母模糊匹配开关
    private lateinit var switchCCh: SwitchMaterial
    private lateinit var switchSSh: SwitchMaterial
    private lateinit var switchZZh: SwitchMaterial
    private lateinit var switchKG: SwitchMaterial
    private lateinit var switchFH: SwitchMaterial
    private lateinit var switchNL: SwitchMaterial
    private lateinit var switchRL: SwitchMaterial
    
    // 韵母模糊匹配开关
    private lateinit var switchAnAng: SwitchMaterial
    private lateinit var switchEnEng: SwitchMaterial
    private lateinit var switchInIng: SwitchMaterial
    private lateinit var switchIanIang: SwitchMaterial
    private lateinit var switchUanUang: SwitchMaterial
    private lateinit var switchAnAi: SwitchMaterial
    private lateinit var switchUnOng: SwitchMaterial
    
    // 拼音音节开关
    private lateinit var switchHuiFei: SwitchMaterial
    private lateinit var switchHuangWang: SwitchMaterial
    private lateinit var switchFengHong: SwitchMaterial
    private lateinit var switchFuHu: SwitchMaterial
    
    // 是否正在批量更新（避免开关状态变化时的循环更新）
    private var isBatchUpdating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fuzzy_pinyin_settings)
        
        // 初始化UI
        initUI()
        
        // 加载当前设置
        loadCurrentSettings()
    }
    
    /**
     * 初始化UI元素
     */
    private fun initUI() {
        // 返回按钮
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }
        
        // 总开关
        switchFuzzyEnabled = findViewById(R.id.switch_fuzzy_enabled)
        switchFuzzyEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setFuzzyEnabled(isChecked)
                updateSwitchesEnabledState(isChecked)
                showToast(if (isChecked) "已启用模糊拼音" else "已禁用模糊拼音")
            }
        }
        
        // 声母模糊匹配开关
        switchCCh = findViewById(R.id.switch_c_ch)
        switchCCh.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setCEqualsCh(isChecked)
            }
        }
        
        switchSSh = findViewById(R.id.switch_s_sh)
        switchSSh.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setSEqualsSh(isChecked)
            }
        }
        
        switchZZh = findViewById(R.id.switch_z_zh)
        switchZZh.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setZEqualsZh(isChecked)
            }
        }
        
        switchKG = findViewById(R.id.switch_k_g)
        switchKG.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setKEqualsG(isChecked)
            }
        }
        
        switchFH = findViewById(R.id.switch_f_h)
        switchFH.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setFEqualsH(isChecked)
            }
        }
        
        switchNL = findViewById(R.id.switch_n_l)
        switchNL.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setNEqualsL(isChecked)
            }
        }
        
        switchRL = findViewById(R.id.switch_r_l)
        switchRL.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setREqualsL(isChecked)
            }
        }
        
        // 韵母模糊匹配开关
        switchAnAng = findViewById(R.id.switch_an_ang)
        switchAnAng.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setAnEqualsAng(isChecked)
            }
        }
        
        switchEnEng = findViewById(R.id.switch_en_eng)
        switchEnEng.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setEnEqualsEng(isChecked)
            }
        }
        
        switchInIng = findViewById(R.id.switch_in_ing)
        switchInIng.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setInEqualsIng(isChecked)
            }
        }
        
        switchIanIang = findViewById(R.id.switch_ian_iang)
        switchIanIang.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setIanEqualsIang(isChecked)
            }
        }
        
        switchUanUang = findViewById(R.id.switch_uan_uang)
        switchUanUang.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setUanEqualsUang(isChecked)
            }
        }
        
        switchAnAi = findViewById(R.id.switch_an_ai)
        switchAnAi.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setAnEqualsAi(isChecked)
            }
        }
        
        switchUnOng = findViewById(R.id.switch_un_ong)
        switchUnOng.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setUnEqualsOng(isChecked)
            }
        }
        
        // 拼音音节开关
        switchHuiFei = findViewById(R.id.switch_hui_fei)
        switchHuiFei.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setHuiEqualsFei(isChecked)
            }
        }
        
        switchHuangWang = findViewById(R.id.switch_huang_wang)
        switchHuangWang.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setHuangEqualsWang(isChecked)
            }
        }
        
        switchFengHong = findViewById(R.id.switch_feng_hong)
        switchFengHong.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setFengEqualsHong(isChecked)
            }
        }
        
        switchFuHu = findViewById(R.id.switch_fu_hu)
        switchFuHu.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setFuEqualsHu(isChecked)
            }
        }
    }
    
    /**
     * 加载当前的模糊拼音设置
     */
    private fun loadCurrentSettings() {
        isBatchUpdating = true
        
        try {
            // 总开关
            val fuzzyEnabled = fuzzyPinyinManager.isFuzzyEnabled()
            switchFuzzyEnabled.isChecked = fuzzyEnabled
            
            // 声母模糊匹配
            switchCCh.isChecked = fuzzyPinyinManager.isCEqualsCh()
            switchSSh.isChecked = fuzzyPinyinManager.isSEqualsSh()
            switchZZh.isChecked = fuzzyPinyinManager.isZEqualsZh()
            switchKG.isChecked = fuzzyPinyinManager.isKEqualsG()
            switchFH.isChecked = fuzzyPinyinManager.isFEqualsH()
            switchNL.isChecked = fuzzyPinyinManager.isNEqualsL()
            switchRL.isChecked = fuzzyPinyinManager.isREqualsL()
            
            // 韵母模糊匹配
            switchAnAng.isChecked = fuzzyPinyinManager.isAnEqualsAng()
            switchEnEng.isChecked = fuzzyPinyinManager.isEnEqualsEng()
            switchInIng.isChecked = fuzzyPinyinManager.isInEqualsIng()
            switchIanIang.isChecked = fuzzyPinyinManager.isIanEqualsIang()
            switchUanUang.isChecked = fuzzyPinyinManager.isUanEqualsUang()
            switchAnAi.isChecked = fuzzyPinyinManager.isAnEqualsAi()
            switchUnOng.isChecked = fuzzyPinyinManager.isUnEqualsOng()
            
            // 拼音音节
            switchHuiFei.isChecked = fuzzyPinyinManager.isHuiEqualsFei()
            switchHuangWang.isChecked = fuzzyPinyinManager.isHuangEqualsWang()
            switchFengHong.isChecked = fuzzyPinyinManager.isFengEqualsHong()
            switchFuHu.isChecked = fuzzyPinyinManager.isFuEqualsHu()
            
            // 更新开关的启用状态
            updateSwitchesEnabledState(fuzzyEnabled)
        } catch (e: Exception) {
            Timber.e(e, "加载模糊拼音设置失败")
            showToast("加载设置失败")
        } finally {
            isBatchUpdating = false
        }
    }
    
    /**
     * 更新所有子开关的启用状态
     * 当总开关关闭时，默认启用的选项不能被取消勾选
     */
    private fun updateSwitchesEnabledState(fuzzyEnabled: Boolean) {
        // 声母开关
        switchCCh.isEnabled = fuzzyEnabled || !switchCCh.isChecked
        switchSSh.isEnabled = true
        switchZZh.isEnabled = fuzzyEnabled || !switchZZh.isChecked
        switchKG.isEnabled = true
        switchFH.isEnabled = true
        switchNL.isEnabled = true
        switchRL.isEnabled = true
        
        // 韵母开关
        switchAnAng.isEnabled = fuzzyEnabled || !switchAnAng.isChecked
        switchEnEng.isEnabled = fuzzyEnabled || !switchEnEng.isChecked
        switchInIng.isEnabled = fuzzyEnabled || !switchInIng.isChecked
        switchIanIang.isEnabled = true
        switchUanUang.isEnabled = true
        switchAnAi.isEnabled = true
        switchUnOng.isEnabled = true
        
        // 拼音音节开关
        switchHuiFei.isEnabled = true
        switchHuangWang.isEnabled = true
        switchFengHong.isEnabled = true
        switchFuHu.isEnabled = true
    }
    
    /**
     * 显示Toast消息
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
} 