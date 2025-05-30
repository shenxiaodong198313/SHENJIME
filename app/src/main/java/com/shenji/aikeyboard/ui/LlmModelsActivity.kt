package com.shenji.aikeyboard.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.databinding.ActivityLlmModelsBinding
import com.shenji.aikeyboard.llm.LlmModel
import com.shenji.aikeyboard.adapter.LlmModelsAdapter

/**
 * LLM模型列表Activity
 * 显示可用的AI聊天模型列表
 */
class LlmModelsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLlmModelsBinding
    private lateinit var modelsAdapter: LlmModelsAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLlmModelsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupRecyclerView()
        loadModels()
    }
    
    private fun setupUI() {
        // 设置工具栏
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "AI Chat models"
        }
        
        // 设置标题和描述
        binding.tvTitle.text = "Chat with on-device large language models"
    }
    
    private fun setupRecyclerView() {
        modelsAdapter = LlmModelsAdapter { model ->
            // 点击模型项，进入聊天界面
            val intent = Intent(this, LlmChatActivity::class.java).apply {
                putExtra("model_id", model.id)
                putExtra("model_name", model.name)
            }
            startActivity(intent)
        }
        
        binding.recyclerViewModels.apply {
            layoutManager = LinearLayoutManager(this@LlmModelsActivity)
            adapter = modelsAdapter
        }
    }
    
    private fun loadModels() {
        val models = LlmModel.getAvailableModels()
        modelsAdapter.submitList(models)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 