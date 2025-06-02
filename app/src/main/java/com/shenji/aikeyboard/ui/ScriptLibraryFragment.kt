package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.ScriptItem
import com.shenji.aikeyboard.data.RealmManager
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.TypedRealmObject
import kotlinx.coroutines.*

class ScriptLibraryFragment : Fragment() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ScriptAdapter
    
    private val scriptItems = mutableListOf<ScriptItem>()
    private var currentPage = 0
    private val pageSize = 20
    private var isLoading = false
    private var hasMoreData = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_script_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupRecyclerView()
        setupSwipeRefresh()
        
        // 初始化数据
        initializeData()
        loadData(true)
    }

    private fun initViews(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        recyclerView = view.findViewById(R.id.recycler_view)
    }

    private fun setupRecyclerView() {
        adapter = ScriptAdapter(scriptItems) { scriptItem ->
            // 点击话术项时关闭页面
            activity?.finish()
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // 添加滚动监听器实现上拉加载
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                
                if (!isLoading && hasMoreData) {
                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount - 5) {
                        loadData(false)
                    }
                }
            }
        })
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadData(true)
        }
        swipeRefreshLayout.setColorSchemeResources(R.color.primary_color)
    }

    private fun initializeData() {
        CoroutineScope(Dispatchers.IO).launch {
            val realm = RealmManager.getInstance()
            val existingCount = realm.query<ScriptItem>("category == 'script'").count().find()
            
            if (existingCount == 0L) {
                // 创建示例话术数据
                val sampleScripts = listOf(
                    ScriptItem().apply {
                        title = "客户开场白"
                        content = "您好！很高兴为您服务，请问有什么可以帮助您的吗？我们今天有特别优惠活动，您可以了解一下。"
                        category = "script"
                        tags = "开场,问候,优惠"
                    },
                    ScriptItem().apply {
                        title = "产品介绍话术"
                        content = "这款产品是我们的明星产品，具有以下特点：1.高品质材料 2.人性化设计 3.超长质保期 4.专业售后服务"
                        category = "script"
                        tags = "产品,介绍,特点"
                    },
                    ScriptItem().apply {
                        title = "价格异议处理"
                        content = "我理解您对价格的考虑，但请您考虑一下产品的价值。我们的产品不仅质量优秀，还提供完善的售后服务，从长远来看是非常划算的。"
                        category = "script"
                        tags = "价格,异议,处理"
                    },
                    ScriptItem().apply {
                        title = "促成交易话术"
                        content = "基于我们刚才的交流，我觉得这个产品非常适合您。现在下单还可以享受额外优惠，您觉得怎么样？"
                        category = "script"
                        tags = "成交,促成,优惠"
                    },
                    ScriptItem().apply {
                        title = "售后服务承诺"
                        content = "我们提供7天无理由退换货，1年质保，终身维护服务。有任何问题都可以随时联系我们的客服团队。"
                        category = "script"
                        tags = "售后,服务,承诺"
                    }
                )
                
                // 扩展到更多数据
                val expandedScripts = mutableListOf<ScriptItem>()
                repeat(10) { i ->
                    sampleScripts.forEach { script ->
                        expandedScripts.add(ScriptItem().apply {
                            title = "${script.title} ${i + 1}"
                            content = script.content
                            category = script.category
                            tags = script.tags
                        })
                    }
                }
                
                realm.write {
                    expandedScripts.forEach { script ->
                        copyToRealm(script)
                    }
                }
            }
        }
    }

    private fun loadData(isRefresh: Boolean) {
        if (isLoading) return
        
        isLoading = true
        
        if (isRefresh) {
            currentPage = 0
            hasMoreData = true
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val realm = RealmManager.getInstance()
                val results = realm.query<ScriptItem>("category == 'script' AND isActive == true")
                    .find()
                
                val newItems = results.drop(currentPage * pageSize).take(pageSize)
                
                withContext(Dispatchers.Main) {
                    if (isRefresh) {
                        scriptItems.clear()
                        swipeRefreshLayout.isRefreshing = false
                    }
                    
                    if (newItems.isNotEmpty()) {
                        scriptItems.addAll(newItems)
                        adapter.notifyDataSetChanged()
                        currentPage++
                    } else {
                        hasMoreData = false
                    }
                    
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefreshLayout.isRefreshing = false
                    isLoading = false
                }
            }
        }
    }
} 