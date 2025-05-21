package com.shenji.aikeyboard.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.DictionaryManager
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.databinding.ActivityDevToolsBinding
import com.shenji.aikeyboard.utils.PinyinUtils
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.min
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * 开发工具Activity - 用于包含各种开发调试工具
 */
class DevToolsActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pinyinTestButton: Button
    private lateinit var logViewerButton: Button
    private lateinit var verificationCodeButton: Button
    private lateinit var permissionCheckButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_tools_new)
        
        // 设置ActionBar标题
        supportActionBar?.title = "开发工具"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 初始化视图
        pinyinTestButton = findViewById(R.id.pinyin_test_button)
        logViewerButton = findViewById(R.id.log_viewer_button)
        verificationCodeButton = findViewById(R.id.verification_code_button)
        permissionCheckButton = findViewById(R.id.permission_check_button)
        
        // 设置按钮点击监听器
        pinyinTestButton.setOnClickListener {
            startToolActivity(PinyinTestActivity::class.java)
        }
        
        logViewerButton.setOnClickListener {
            startToolActivity(LogViewerActivity::class.java)
        }
        
        verificationCodeButton.setOnClickListener {
            startToolActivity(VerificationCodeActivity::class.java)
        }
        
        permissionCheckButton.setOnClickListener {
            startToolActivity(PermissionCheckActivity::class.java)
        }
    }
    
    /**
     * 启动工具活动
     */
    private fun startToolActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

/**
 * 拼音测试活动 - 容器活动
 */
class PinyinTestActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)
        
        // 设置标题
        supportActionBar?.title = "拼音测试"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 加载fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PinyinTestFragment())
                .commit()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

/**
 * 日志查看活动 - 容器活动
 */
class LogViewerActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)
        
        // 设置标题
        supportActionBar?.title = "日志查看"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 加载fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LogViewerFragment())
                .commit()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

/**
 * 验证码测试活动 - 容器活动
 */
class VerificationCodeActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)
        
        // 设置标题
        supportActionBar?.title = "验证码测试"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 加载fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, VerificationCodeFragment())
                .commit()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

/**
 * 权限检查活动 - 容器活动
 */
class PermissionCheckActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)
        
        // 设置标题
        supportActionBar?.title = "权限检查"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 加载fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PermissionCheckFragment())
                .commit()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

/**
 * 日志查看Fragment - 简单实现，后续可以扩展
 */
class LogViewerFragment : Fragment() {
    
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: android.os.Bundle?
    ): android.view.View? {
        return inflater.inflate(R.layout.fragment_log_viewer, container, false)
    }
} 