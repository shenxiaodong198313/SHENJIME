package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.shenji.aikeyboard.R

class OrderActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置窗口属性
        setupWindowFlags()
        
        setContentView(R.layout.activity_order)
        
        // 隐藏ActionBar
        supportActionBar?.hide()
        
        initViews()
        setupViewPager()
        setupClickListeners()
    }

    /**
     * 设置窗口标志
     */
    private fun setupWindowFlags() {
        try {
            // 设置窗口为全屏，但保持状态栏可见
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            
            // 设置状态栏透明
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            
            // 设置系统UI可见性
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
            
        } catch (e: Exception) {
            // 忽略错误
        }
    }

    private fun initViews() {
        tabLayout = findViewById(R.id.tab_layout)
        viewPager = findViewById(R.id.view_pager)
        
        // 返回按钮现在在setupClickListeners中设置
    }

    private fun setupViewPager() {
        val adapter = OrderPagerAdapter(this)
        viewPager.adapter = adapter
        
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "话术库"
                1 -> "资料库"
                else -> ""
            }
        }.attach()
    }

    /**
     * 设置点击事件
     */
    private fun setupClickListeners() {
        // 返回按钮点击事件
        findViewById<View>(R.id.back_button).setOnClickListener {
            finish()
        }
        
        // 点击背景区域关闭页面
        findViewById<View>(R.id.backgroundOverlay).setOnClickListener {
            finish()
        }
    }

    override fun finish() {
        super.finish()
        // 不使用动画，避免回到输入法首页
        overridePendingTransition(0, 0)
    }

    override fun onBackPressed() {
        // 直接关闭，不调用super，避免回到输入法首页
        finish()
    }

    private class OrderPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ScriptLibraryFragment()
                1 -> MaterialLibraryFragment()
                else -> ScriptLibraryFragment()
            }
        }
    }
} 