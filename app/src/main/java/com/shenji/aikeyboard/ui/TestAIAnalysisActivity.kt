package com.shenji.aikeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.R

class TestAIAnalysisActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_ai_analysis)
        
        val btnStartAnalysis = findViewById<Button>(R.id.btnStartAnalysis)
        val btnRequestPermission = findViewById<Button>(R.id.btnRequestPermission)
        
        btnRequestPermission.setOnClickListener {
            requestOverlayPermission()
        }
        
        btnStartAnalysis.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startAIAnalysis()
            } else {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        startActivity(intent)
    }
    
    private fun startAIAnalysis() {
        val intent = Intent(this, AIAnalysisActivity::class.java)
        intent.putExtra("test_mode", true)
        startActivity(intent)
    }
} 