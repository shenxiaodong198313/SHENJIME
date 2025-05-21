package com.shenji.aikeyboard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.settings.InputMethodSettingsActivity
import com.shenji.aikeyboard.ui.DevToolsActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 添加输入法设置入口
        findViewById<Button>(R.id.btn_ime_settings)?.setOnClickListener {
            val intent = Intent(this, InputMethodSettingsActivity::class.java)
            startActivity(intent)
        }
        
        // 添加开发工具入口
        findViewById<Button>(R.id.btnDevTools)?.setOnClickListener {
            val intent = Intent(this, DevToolsActivity::class.java)
            startActivity(intent)
        }
    }
} 