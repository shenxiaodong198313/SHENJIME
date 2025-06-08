package com.shenji.aikeyboard.modelscope

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.shenji.aikeyboard.R
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * AI模型库主界面
 */
class ModelLibraryActivity : AppCompatActivity() {
    
    private lateinit var modelScopeManager: ModelScopeManager
    private lateinit var modelAdapter: ModelAdapter
    private lateinit var localModelAdapter: LocalModelAdapter
    
    // UI组件
    private lateinit var tabLayout: TabLayout
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var categoryChipGroup: ChipGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var refreshFab: FloatingActionButton
    
    // 当前状态
    private var currentTab = 0 // 0: 在线模型, 1: 本地模型
    private var currentCategory: ModelCategory? = null
    private var currentSearchQuery = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_library)
        
        // 初始化管理器
        modelScopeManager = ModelScopeManager(this)
        
        initViews()
        setupTabs()
        setupSearch()
        setupCategories()
        setupRecyclerView()
        setupFab()
        
        // 加载初始数据
        loadInitialData()
    }
    
    private fun initViews() {
        tabLayout = findViewById(R.id.tabLayout)
        searchEditText = findViewById(R.id.searchEditText)
        searchButton = findViewById(R.id.searchButton)
        categoryChipGroup = findViewById(R.id.categoryChipGroup)
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        refreshFab = findViewById(R.id.refreshFab)
        
        // 设置标题
        supportActionBar?.title = "AI模型库"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("在线模型"))
        tabLayout.addTab(tabLayout.newTab().setText("本地模型"))
        
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                switchTab(currentTab)
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
    
    private fun setupSearch() {
        searchButton.setOnClickListener {
            currentSearchQuery = searchEditText.text.toString().trim()
            if (currentTab == 0) {
                searchOnlineModels()
            } else {
                filterLocalModels()
            }
        }
        
        // 回车搜索
        searchEditText.setOnEditorActionListener { _, _, _ ->
            searchButton.performClick()
            true
        }
    }
    
    private fun setupCategories() {
        // 添加分类筛选
        ModelCategory.values().forEach { category ->
            val chip = Chip(this)
            chip.text = category.displayName
            chip.isCheckable = true
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // 取消其他选中的chip
                    for (i in 0 until categoryChipGroup.childCount) {
                        val otherChip = categoryChipGroup.getChildAt(i) as Chip
                        if (otherChip != chip) {
                            otherChip.isChecked = false
                        }
                    }
                    currentCategory = category
                } else {
                    currentCategory = null
                }
                
                // 重新搜索
                if (currentTab == 0) {
                    searchOnlineModels()
                } else {
                    filterLocalModels()
                }
            }
            categoryChipGroup.addView(chip)
        }
    }
    
    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // 在线模型适配器
        modelAdapter = ModelAdapter { model ->
            downloadModel(model)
        }
        
        // 本地模型适配器
        localModelAdapter = LocalModelAdapter(
            onActivate = { model -> activateModel(model) },
            onDelete = { model -> deleteModel(model) },
            onViewDetails = { model -> viewModelDetails(model) }
        )
        
        // 默认显示在线模型
        recyclerView.adapter = modelAdapter
    }
    
    private fun setupFab() {
        refreshFab.setOnClickListener {
            if (currentTab == 0) {
                loadOnlineModels()
            } else {
                loadLocalModels()
            }
        }
    }
    
    private fun switchTab(tabIndex: Int) {
        when (tabIndex) {
            0 -> {
                // 在线模型
                recyclerView.adapter = modelAdapter
                categoryChipGroup.visibility = View.VISIBLE
                searchEditText.hint = "搜索在线模型..."
                loadOnlineModels()
            }
            1 -> {
                // 本地模型
                recyclerView.adapter = localModelAdapter
                categoryChipGroup.visibility = View.GONE
                searchEditText.hint = "搜索本地模型..."
                loadLocalModels()
            }
        }
    }
    
    private fun loadInitialData() {
        loadOnlineModels()
        observeLocalModels()
        observeDownloadProgress()
    }
    
    private fun loadOnlineModels() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                // 加载热门模型
                val trendingResult = modelScopeManager.getTrendingModels(
                    task = currentCategory?.taskType,
                    limit = 20
                )
                
                if (trendingResult.isSuccess) {
                    val models = trendingResult.getOrNull() ?: emptyList()
                    modelAdapter.updateModels(models)
                    showEmptyView(models.isEmpty())
                } else {
                    showError("加载模型失败: ${trendingResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "加载在线模型失败")
                showError("加载模型失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun searchOnlineModels() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val request = ModelSearchRequest(
                    query = currentSearchQuery,
                    task = currentCategory?.taskType,
                    sort = "downloads",
                    page = 1,
                    pageSize = 50
                )
                
                val result = modelScopeManager.searchModels(request)
                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    modelAdapter.updateModels(response.models)
                    showEmptyView(response.models.isEmpty())
                } else {
                    showError("搜索失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "搜索在线模型失败")
                showError("搜索失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun loadLocalModels() {
        // 本地模型通过StateFlow自动更新
        val models = modelScopeManager.localModels.value
        localModelAdapter.updateModels(models)
        showEmptyView(models.isEmpty())
    }
    
    private fun filterLocalModels() {
        val allModels = modelScopeManager.localModels.value
        val filteredModels = if (currentSearchQuery.isBlank()) {
            allModels
        } else {
            allModels.filter { 
                it.modelName.contains(currentSearchQuery, ignoreCase = true) ||
                it.modelId.contains(currentSearchQuery, ignoreCase = true)
            }
        }
        localModelAdapter.updateModels(filteredModels)
        showEmptyView(filteredModels.isEmpty())
    }
    
    private fun observeLocalModels() {
        lifecycleScope.launch {
            modelScopeManager.localModels.collect { models ->
                if (currentTab == 1) {
                    localModelAdapter.updateModels(models)
                    showEmptyView(models.isEmpty())
                }
            }
        }
    }
    
    private fun observeDownloadProgress() {
        lifecycleScope.launch {
            modelScopeManager.downloadProgress.collect { task ->
                // 更新下载进度
                modelAdapter.updateDownloadProgress(task)
                
                when (task.status) {
                    DownloadStatus.COMPLETED -> {
                        showMessage("模型下载完成: ${task.modelName}")
                    }
                    DownloadStatus.FAILED -> {
                        showError("模型下载失败: ${task.modelName}\n${task.error}")
                    }
                    else -> {
                        // 下载中，不显示消息
                    }
                }
            }
        }
    }
    
    private fun downloadModel(model: ModelScopeModel) {
        lifecycleScope.launch {
            try {
                val result = modelScopeManager.downloadModel(model)
                if (result.isSuccess) {
                    showMessage("开始下载模型: ${model.modelName}")
                } else {
                    showError("下载失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "下载模型失败")
                showError("下载失败: ${e.message}")
            }
        }
    }
    
    private fun activateModel(model: LocalModel) {
        // TODO: 集成到现有AI引擎
        showMessage("激活模型: ${model.modelName}")
    }
    
    private fun deleteModel(model: LocalModel) {
        lifecycleScope.launch {
            try {
                val result = modelScopeManager.deleteLocalModel(model.modelId)
                if (result.isSuccess) {
                    showMessage("删除模型成功: ${model.modelName}")
                } else {
                    showError("删除失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "删除模型失败")
                showError("删除失败: ${e.message}")
            }
        }
    }
    
    private fun viewModelDetails(model: LocalModel) {
        // TODO: 显示模型详情
        showMessage("查看模型详情: ${model.modelName}")
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
    
    private fun showEmptyView(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        emptyView.text = if (currentTab == 0) "暂无在线模型" else "暂无本地模型"
    }
    
    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showError(error: String) {
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        modelScopeManager.cleanup()
    }
} 