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
    
    // 全选开关
    private lateinit var switchSelectAll: SwitchMaterial
    
    // 声母模糊匹配开关
    private lateinit var switchZZh: SwitchMaterial
    private lateinit var switchCCh: SwitchMaterial
    private lateinit var switchSSh: SwitchMaterial
    
    // 韵母模糊匹配开关
    private lateinit var switchAnAng: SwitchMaterial
    private lateinit var switchEnEng: SwitchMaterial
    private lateinit var switchInIng: SwitchMaterial
    
    // 其他模糊匹配开关
    private lateinit var switchLN: SwitchMaterial
    private lateinit var switchVU: SwitchMaterial
    
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
        
        // 全选开关
        switchSelectAll = findViewById(R.id.switch_select_all)
        switchSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                setAllSwitches(isChecked)
                fuzzyPinyinManager.setAll(isChecked)
                showToast(if (isChecked) "已启用全部模糊拼音规则" else "已禁用全部模糊拼音规则")
            }
        }
        
        // 声母模糊匹配开关
        switchZZh = findViewById(R.id.switch_z_zh)
        switchZZh.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setZEqualsZh(isChecked)
                updateSelectAllSwitch()
            }
        }
        
        switchCCh = findViewById(R.id.switch_c_ch)
        switchCCh.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setCEqualsCh(isChecked)
                updateSelectAllSwitch()
            }
        }
        
        switchSSh = findViewById(R.id.switch_s_sh)
        switchSSh.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setSEqualsSh(isChecked)
                updateSelectAllSwitch()
            }
        }
        
        // 韵母模糊匹配开关
        switchAnAng = findViewById(R.id.switch_an_ang)
        switchAnAng.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setAnEqualsAng(isChecked)
                updateSelectAllSwitch()
            }
        }
        
        switchEnEng = findViewById(R.id.switch_en_eng)
        switchEnEng.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setEnEqualsEng(isChecked)
                updateSelectAllSwitch()
            }
        }
        
        switchInIng = findViewById(R.id.switch_in_ing)
        switchInIng.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setInEqualsIng(isChecked)
                updateSelectAllSwitch()
            }
        }
        
        // 其他模糊匹配开关
        switchLN = findViewById(R.id.switch_l_n)
        switchLN.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setLEqualsN(isChecked)
                updateSelectAllSwitch()
            }
        }
        
        // v/ü模糊匹配开关
        switchVU = findViewById(R.id.switch_v_u)
        switchVU.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatchUpdating) {
                fuzzyPinyinManager.setVEqualsU(isChecked)
                updateSelectAllSwitch()
                showToast(if (isChecked) "已启用v/ü模糊匹配" else "已禁用v/ü模糊匹配")
            }
        }
    }
    
    /**
     * 加载当前的模糊拼音设置
     */
    private fun loadCurrentSettings() {
        isBatchUpdating = true
        
        try {
            // 声母模糊匹配
            switchZZh.isChecked = fuzzyPinyinManager.isZEqualsZh()
            switchCCh.isChecked = fuzzyPinyinManager.isCEqualsCh()
            switchSSh.isChecked = fuzzyPinyinManager.isSEqualsSh()
            
            // 韵母模糊匹配
            switchAnAng.isChecked = fuzzyPinyinManager.isAnEqualsAng()
            switchEnEng.isChecked = fuzzyPinyinManager.isEnEqualsEng()
            switchInIng.isChecked = fuzzyPinyinManager.isInEqualsIng()
            
            // 其他模糊匹配
            switchLN.isChecked = fuzzyPinyinManager.isLEqualsN()
            switchVU.isChecked = fuzzyPinyinManager.isVEqualsU()
            
            // 更新全选开关状态
            updateSelectAllSwitch()
        } catch (e: Exception) {
            Timber.e(e, "加载模糊拼音设置失败")
            showToast("加载设置失败")
        } finally {
            isBatchUpdating = false
        }
    }
    
    /**
     * 设置所有开关的状态
     */
    private fun setAllSwitches(checked: Boolean) {
        isBatchUpdating = true
        
        try {
            // 声母模糊匹配
            switchZZh.isChecked = checked
            switchCCh.isChecked = checked
            switchSSh.isChecked = checked
            
            // 韵母模糊匹配
            switchAnAng.isChecked = checked
            switchEnEng.isChecked = checked
            switchInIng.isChecked = checked
            
            // 其他模糊匹配
            switchLN.isChecked = checked
            switchVU.isChecked = checked
        } finally {
            isBatchUpdating = false
        }
    }
    
    /**
     * 更新全选开关状态
     * 如果所有规则都被选中，则全选开关也应该被选中
     */
    private fun updateSelectAllSwitch() {
        val allChecked = switchZZh.isChecked && switchCCh.isChecked && switchSSh.isChecked &&
                        switchAnAng.isChecked && switchEnEng.isChecked && switchInIng.isChecked &&
                        switchLN.isChecked && switchVU.isChecked
        
        // 避免循环触发
        isBatchUpdating = true
        switchSelectAll.isChecked = allChecked
        isBatchUpdating = false
    }
    
    /**
     * 显示Toast消息
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
} 