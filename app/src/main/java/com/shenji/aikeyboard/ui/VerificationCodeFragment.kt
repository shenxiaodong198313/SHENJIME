package com.shenji.aikeyboard.ui

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.utils.AutofillAccessibilityService
import com.shenji.aikeyboard.utils.SmsReceiver
import timber.log.Timber

/**
 * 验证码测试Fragment - 用于测试验证码自动填写功能
 */
class VerificationCodeFragment : Fragment(), SmsReceiver.OnVerificationCodeReceivedListener {

    companion object {
        private const val REQUEST_SMS_PERMISSION = 100
    }

    private lateinit var statusTextView: TextView
    private lateinit var testEditText: EditText
    private lateinit var requestPermissionButton: Button
    private lateinit var accessibilitySettingsButton: Button
    private lateinit var simulateButton: Button
    private lateinit var clearButton: Button
    
    private val smsReceiver = SmsReceiver()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_verification_code, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 初始化视图
        statusTextView = view.findViewById(R.id.status_text_view)
        testEditText = view.findViewById(R.id.test_edit_text)
        requestPermissionButton = view.findViewById(R.id.request_permission_button)
        accessibilitySettingsButton = view.findViewById(R.id.accessibility_settings_button)
        simulateButton = view.findViewById(R.id.simulate_button)
        clearButton = view.findViewById(R.id.clear_button)
        
        // 设置监听器
        requestPermissionButton.setOnClickListener {
            requestSmsPermission()
        }
        
        accessibilitySettingsButton.setOnClickListener {
            openAccessibilitySettings()
        }
        
        simulateButton.setOnClickListener {
            simulateVerificationCode()
        }
        
        clearButton.setOnClickListener {
            testEditText.setText("")
        }
        
        // 设置短信接收器监听器
        smsReceiver.listener = this
        
        // 更新状态
        updateStatus()
    }
    
    override fun onResume() {
        super.onResume()
        
        // 注册短信接收器
        if (hasSmsPermission()) {
            registerSmsReceiver()
        }
        
        // 更新状态
        updateStatus()
    }
    
    override fun onPause() {
        super.onPause()
        
        // 注销短信接收器
        try {
            requireContext().unregisterReceiver(smsReceiver)
        } catch (e: Exception) {
            // 忽略未注册的异常
        }
    }
    
    /**
     * 请求短信权限
     */
    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
            REQUEST_SMS_PERMISSION
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
     * 模拟验证码
     */
    private fun simulateVerificationCode() {
        val code = (100000..999999).random().toString()
        onVerificationCodeReceived(code)
        Toast.makeText(requireContext(), "模拟验证码: $code", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 注册短信接收器
     */
    private fun registerSmsReceiver() {
        val intentFilter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        requireContext().registerReceiver(smsReceiver, intentFilter)
    }
    
    /**
     * 检查是否有短信权限
     */
    private fun hasSmsPermission(): Boolean {
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
     * 更新状态文本
     */
    private fun updateStatus() {
        val smsPermissionGranted = hasSmsPermission()
        val accessibilityServiceEnabled = AutofillAccessibilityService.isServiceEnabled(requireContext())
        
        val statusBuilder = StringBuilder()
        statusBuilder.append("短信权限: ${if (smsPermissionGranted) "已授予" else "未授予"}\n")
        statusBuilder.append("无障碍服务: ${if (accessibilityServiceEnabled) "已启用" else "未启用"}\n\n")
        
        if (smsPermissionGranted && accessibilityServiceEnabled) {
            statusBuilder.append("验证码自动填写功能已准备就绪！\n")
            statusBuilder.append("当收到验证码短信时，将自动填写到输入框中。")
        } else {
            statusBuilder.append("需要授予以下权限才能使用验证码自动填写功能：\n")
            if (!smsPermissionGranted) {
                statusBuilder.append("1. 短信读取权限 - 点击\"请求权限\"按钮\n")
            }
            if (!accessibilityServiceEnabled) {
                statusBuilder.append("2. 无障碍服务权限 - 点击\"无障碍设置\"按钮，找到并启用\"神机键盘\"服务\n")
            }
        }
        
        statusTextView.text = statusBuilder.toString()
    }
    
    /**
     * 处理验证码接收
     */
    override fun onVerificationCodeReceived(code: String) {
        Timber.d("收到验证码: $code")
        
        // 在UI线程中更新
        activity?.runOnUiThread {
            // 设置验证码到测试输入框
            testEditText.setText(code)
            
            // 设置验证码到无障碍服务
            AutofillAccessibilityService.setVerificationCode(code)
            
            Toast.makeText(requireContext(), "收到验证码: $code", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 处理权限请求结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_SMS_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // 权限已授予，注册短信接收器
                registerSmsReceiver()
                updateStatus()
            } else {
                Toast.makeText(requireContext(), "需要短信权限才能自动读取验证码", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 