package com.shenji.aikeyboard.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import timber.log.Timber
import java.util.regex.Pattern

/**
 * 短信接收器，用于监听收到的短信并提取验证码
 */
class SmsReceiver : BroadcastReceiver() {

    // 验证码提取的正则表达式模式
    private val verificationCodePattern = Pattern.compile("(\\d{4,6})")
    
    // 验证码监听器
    var listener: OnVerificationCodeReceivedListener? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
            Timber.d("收到短信广播")
            
            // 从intent中获取短信消息
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            // 处理每条短信
            messages?.let {
                val smsBody = buildMessageBody(messages)
                Timber.d("短信内容: $smsBody")
                
                // 尝试提取验证码
                val verificationCode = extractVerificationCode(smsBody)
                verificationCode?.let { code ->
                    Timber.d("提取到验证码: $code")
                    listener?.onVerificationCodeReceived(code)
                }
            }
        }
    }
    
    /**
     * 从短信中提取验证码
     * @param message 短信内容
     * @return 提取到的验证码，如果没有则返回null
     */
    private fun extractVerificationCode(message: String): String? {
        val matcher = verificationCodePattern.matcher(message)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }
    
    /**
     * 构建完整的短信内容
     * @param messages 短信消息数组
     * @return 完整的短信内容
     */
    private fun buildMessageBody(messages: Array<SmsMessage>): String {
        return messages.joinToString("") { it.messageBody }
    }
    
    /**
     * 验证码接收监听器接口
     */
    interface OnVerificationCodeReceivedListener {
        /**
         * 当接收到验证码时回调
         * @param code 接收到的验证码
         */
        fun onVerificationCodeReceived(code: String)
    }
} 