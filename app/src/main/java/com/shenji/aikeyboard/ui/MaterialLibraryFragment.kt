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

class MaterialLibraryFragment : Fragment() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ScriptAdapter
    
    private val materialItems = mutableListOf<ScriptItem>()
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
        adapter = ScriptAdapter(materialItems) { scriptItem ->
            // 点击资料项时关闭页面
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
            val existingCount = realm.query<ScriptItem>("category == 'material'").count().find()
            
            if (existingCount == 0L) {
                // 创建示例资料数据
                val sampleMaterials = listOf(
                    ScriptItem().apply {
                        title = "产品技术参数"
                        content = "产品尺寸：长×宽×高 = 200×150×80mm\n重量：1.2kg\n材质：航空级铝合金\n工作温度：-20°C至+60°C\n防护等级：IP67"
                        category = "material"
                        tags = "技术,参数,规格"
                    },
                    ScriptItem().apply {
                        title = "公司简介资料"
                        content = "我们公司成立于2010年，是一家专业从事高端制造业的企业。拥有员工500余人，年产值达到5亿元。获得ISO9001质量认证，产品远销海外30多个国家。"
                        category = "material"
                        tags = "公司,简介,资质"
                    },
                    ScriptItem().apply {
                        title = "行业发展趋势"
                        content = "根据最新市场调研报告，该行业预计在未来5年内将保持15%的年增长率。主要驱动因素包括：技术创新、政策支持、市场需求增长等。"
                        category = "material"
                        tags = "行业,趋势,报告"
                    },
                    ScriptItem().apply {
                        title = "竞品对比分析"
                        content = "与竞品A相比：我们的产品在性能上提升20%，价格优势明显。与竞品B相比：我们的售后服务更完善，客户满意度更高。"
                        category = "material"
                        tags = "竞品,对比,分析"
                    },
                    ScriptItem().apply {
                        title = "客户案例分享"
                        content = "某知名企业采用我们的解决方案后，生产效率提升了30%，成本降低了15%。客户评价：'产品质量稳定，服务响应及时，是值得信赖的合作伙伴。'"
                        category = "material"
                        tags = "案例,客户,成功"
                    }
                )
                
                // 扩展到更多数据
                val expandedMaterials = mutableListOf<ScriptItem>()
                repeat(8) { i ->
                    sampleMaterials.forEach { material ->
                        expandedMaterials.add(ScriptItem().apply {
                            title = "${material.title} ${i + 1}"
                            content = material.content
                            category = material.category
                            tags = material.tags
                        })
                    }
                }
                
                realm.write {
                    expandedMaterials.forEach { material ->
                        copyToRealm(material)
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
                val results = realm.query<ScriptItem>("category == 'material' AND isActive == true")
                    .find()
                
                val newItems = results.drop(currentPage * pageSize).take(pageSize)
                
                withContext(Dispatchers.Main) {
                    if (isRefresh) {
                        materialItems.clear()
                        swipeRefreshLayout.isRefreshing = false
                    }
                    
                    if (newItems.isNotEmpty()) {
                        materialItems.addAll(newItems)
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