package com.shenji.aikeyboard.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.utils.SystemChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 系统检查活动
 */
class SystemCheckActivity : AppCompatActivity(), SystemChecker.CheckProgressCallback {

    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var statusText: TextView
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var runCheckButton: Button
    private lateinit var shareReportButton: Button
    
    private lateinit var systemChecker: SystemChecker
    private val checkResultAdapter = CheckResultAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_check)
        
        // 设置标题和返回按钮
        supportActionBar?.title = "系统检查"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 初始化视图
        initViews()
        
        // 初始化系统检查器
        systemChecker = SystemChecker(this)
        
        // 设置RecyclerView
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = checkResultAdapter
        
        // 设置按钮点击事件
        setupListeners()
    }
    
    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        statusText = findViewById(R.id.statusText)
        resultsRecyclerView = findViewById(R.id.resultsRecyclerView)
        runCheckButton = findViewById(R.id.runCheckButton)
        shareReportButton = findViewById(R.id.shareReportButton)
        
        // 初始状态
        progressBar.visibility = View.GONE
        progressText.visibility = View.GONE
        shareReportButton.visibility = View.GONE
    }
    
    private fun setupListeners() {
        runCheckButton.setOnClickListener {
            startSystemCheck()
        }
        
        shareReportButton.setOnClickListener {
            shareReport()
        }
    }
    
    private fun startSystemCheck() {
        Timber.d("开始系统检查")
        
        // 重置UI状态
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        progressText.visibility = View.VISIBLE
        progressText.text = "正在准备检查..."
        statusText.text = "检查中..."
        shareReportButton.visibility = View.GONE
        runCheckButton.isEnabled = false
        
        // 清空结果列表
        checkResultAdapter.clearResults()
        
        // 执行检查
        lifecycleScope.launch {
            try {
                // 执行所有检查
                systemChecker.runAllChecks(this@SystemCheckActivity)
            } catch (e: Exception) {
                Timber.e(e, "系统检查过程中发生错误")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SystemCheckActivity, "检查过程出错: ${e.message}", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                    progressText.visibility = View.GONE
                    statusText.text = "检查过程出错，请重试"
                    runCheckButton.isEnabled = true
                }
            }
        }
    }
    
    /**
     * 共享检查报告
     */
    private fun shareReport() {
        lifecycleScope.launch {
            try {
                val reportFile = systemChecker.saveReportToFile()
                
                if (reportFile != null) {
                    // 使用FileProvider获取文件URI
                    val fileUri = FileProvider.getUriForFile(
                        this@SystemCheckActivity, 
                        "com.shenji.aikeyboard.fileprovider", 
                        reportFile
                    )
                    
                    // 创建分享Intent
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.type = "text/plain"
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, "神迹输入法系统检查报告")
                    shareIntent.putExtra(Intent.EXTRA_TEXT, "请查看附件中的系统检查报告。")
                    shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri)
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    
                    // 启动分享选择器
                    startActivity(Intent.createChooser(shareIntent, "分享系统检查报告"))
                } else {
                    Toast.makeText(this@SystemCheckActivity, "无法生成报告文件", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "共享报告时出错")
                Toast.makeText(this@SystemCheckActivity, "共享报告时出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    // CheckProgressCallback接口实现
    override fun onCheckStarted(checkName: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            progressText.text = "正在检查: $checkName"
        }
    }
    
    override fun onCheckCompleted(result: SystemChecker.CheckResult) {
        lifecycleScope.launch(Dispatchers.Main) {
            // 添加到适配器
            checkResultAdapter.addResult(result)
            
            // 滚动到最新项
            resultsRecyclerView.scrollToPosition(checkResultAdapter.itemCount - 1)
        }
    }
    
    override fun onAllChecksCompleted(results: List<SystemChecker.CheckResult>, allPassed: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            // 更新UI
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
            
            val passedCount = results.count { it.passed }
            val totalCount = results.size
            
            // 设置状态文本
            if (allPassed) {
                statusText.text = "全部检查通过 (${passedCount}/${totalCount})"
                statusText.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            } else {
                statusText.text = "部分检查未通过 (${passedCount}/${totalCount})"
                statusText.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            }
            
            // 启用按钮
            runCheckButton.isEnabled = true
            shareReportButton.visibility = View.VISIBLE
        }
    }
}

/**
 * 检查结果适配器
 */
class CheckResultAdapter : RecyclerView.Adapter<CheckResultAdapter.ViewHolder>() {
    
    private val results = mutableListOf<SystemChecker.CheckResult>()
    
    fun addResult(result: SystemChecker.CheckResult) {
        results.add(result)
        notifyItemInserted(results.size - 1)
    }
    
    fun clearResults() {
        results.clear()
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_check_result, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(results[position])
    }
    
    override fun getItemCount(): Int = results.size
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameTextView: TextView = view.findViewById(R.id.checkNameTextView)
        private val statusTextView: TextView = view.findViewById(R.id.checkStatusTextView)
        private val detailsTextView: TextView = view.findViewById(R.id.checkDetailsTextView)
        private val durationTextView: TextView = view.findViewById(R.id.checkDurationTextView)
        
        fun bind(result: SystemChecker.CheckResult) {
            nameTextView.text = result.checkName
            
            if (result.passed) {
                statusTextView.text = "✓ 通过"
                statusTextView.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
            } else {
                statusTextView.text = "✗ 失败"
                statusTextView.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
            }
            
            detailsTextView.text = result.details
            durationTextView.text = "${result.duration} ms"
            
            // 设置点击展开/收起详情
            itemView.setOnClickListener {
                if (detailsTextView.visibility == View.VISIBLE) {
                    detailsTextView.visibility = View.GONE
                } else {
                    detailsTextView.visibility = View.VISIBLE
                }
            }
        }
    }
} 