package com.shenji.aikeyboard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.utils.AutofillAccessibilityService
import timber.log.Timber

/**
 * 权限检查Fragment - 用于检查和请求应用所需的权限
 */
class PermissionCheckFragment : Fragment() {

    companion object {
        private const val REQUEST_PERMISSIONS = 101
    }

    private lateinit var statusTextView: TextView
    private lateinit var checkAllButton: Button
    private lateinit var requestStorageButton: Button
    private lateinit var requestSmsButton: Button
    private lateinit var accessibilitySettingsButton: Button
    
    // 存储权限请求启动器
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatus()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_permission_check, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 初始化视图
        statusTextView = view.findViewById(R.id.permission_status_text_view)
        checkAllButton = view.findViewById(R.id.check_all_permissions_button)
        requestStorageButton = view.findViewById(R.id.request_storage_permission_button)
        requestSmsButton = view.findViewById(R.id.request_sms_permission_button)
        accessibilitySettingsButton = view.findViewById(R.id.accessibility_settings_button)
        
        // 设置监听器
        checkAllButton.setOnClickListener {
            updatePermissionStatus()
        }
        
        requestStorageButton.setOnClickListener {
            requestStoragePermission()
        }
        
        requestSmsButton.setOnClickListener {
            requestSmsPermission()
        }
        
        accessibilitySettingsButton.setOnClickListener {
            openAccessibilitySettings()
        }
        
        // 初始化权限状态
        updatePermissionStatus()
    }
    
    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
    
    /**
     * 更新权限状态
     */
    private fun updatePermissionStatus() {
        val statusBuilder = StringBuilder()
        statusBuilder.append("权限状态检查结果：\n\n")
        
        // 检查存储权限
        val hasStoragePermission = checkStoragePermission()
        statusBuilder.append("存储权限: ${if (hasStoragePermission) "✅ 已授予" else "❌ 未授予"}\n")
        
        // 检查短信权限
        val hasSmsPermission = checkSmsPermission()
        statusBuilder.append("短信权限: ${if (hasSmsPermission) "✅ 已授予" else "❌ 未授予"}\n")
        
        // 检查无障碍服务权限
        val hasAccessibilityPermission = AutofillAccessibilityService.isServiceEnabled(requireContext())
        statusBuilder.append("无障碍服务: ${if (hasAccessibilityPermission) "✅ 已启用" else "❌ 未启用"}\n\n")
        
        // 总体状态
        val allPermissionsGranted = hasStoragePermission && hasSmsPermission && hasAccessibilityPermission
        if (allPermissionsGranted) {
            statusBuilder.append("✅ 所有权限已授予，应用可以正常运行！")
        } else {
            statusBuilder.append("❌ 部分权限未授予，请点击相应按钮获取权限")
        }
        
        // 更新状态文本
        statusTextView.text = statusBuilder.toString()
        
        // 更新按钮状态
        requestStorageButton.isEnabled = !hasStoragePermission
        requestSmsButton.isEnabled = !hasSmsPermission
        accessibilitySettingsButton.isEnabled = !hasAccessibilityPermission
    }
    
    /**
     * 检查存储权限
     */
    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11及以上使用MANAGE_EXTERNAL_STORAGE权限
            Environment.isExternalStorageManager()
        } else {
            // Android 10及以下使用传统存储权限
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 检查短信权限
     */
    private fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 请求存储权限
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11及以上需要请求MANAGE_EXTERNAL_STORAGE权限
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse("package:${requireContext().packageName}")
                storagePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                Timber.e(e, "请求存储权限异常")
                // 备用方案：打开所有应用的文件访问权限设置
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storagePermissionLauncher.launch(intent)
            }
        } else {
            // Android 10及以下使用传统权限请求
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_PERMISSIONS
            )
        }
    }
    
    /**
     * 请求短信权限
     */
    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            ),
            REQUEST_PERMISSIONS
        )
    }
    
    /**
     * 打开无障碍服务设置
     */
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
    
    /**
     * 处理权限请求结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_PERMISSIONS) {
            updatePermissionStatus()
        }
    }
} 