package com.shenji.aikeyboard.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.data.DictionaryRepository
import com.shenji.aikeyboard.data.Entry
import com.shenji.aikeyboard.databinding.ActivityDictionaryDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 词典详情Activity
 */
class DictionaryDetailActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_DICT_TYPE = "dict_type"
        const val EXTRA_DICT_NAME = "dict_name"
        const val PAGE_SIZE = 500
    }
    
    private lateinit var binding: ActivityDictionaryDetailBinding
    private lateinit var entryAdapter: DictionaryEntryAdapter
    private val dictionaryRepository = DictionaryRepository()
    
    private var dictType: String = ""
    private var dictName: String = ""
    private var currentOffset = 0
    private var isLoading = false
    private var hasMoreData = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取传入的词典类型
        dictType = intent.getStringExtra(EXTRA_DICT_TYPE) ?: ""
        dictName = intent.getStringExtra(EXTRA_DICT_NAME) ?: ""
        
        setupToolbar()
        setupRecyclerView()
        loadEntries(0)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.dict_detail_title, dictName)
    }
    
    private fun setupRecyclerView() {
        entryAdapter = DictionaryEntryAdapter()
        
        val layoutManager = LinearLayoutManager(this)
        binding.rvEntries.layoutManager = layoutManager
        binding.rvEntries.adapter = entryAdapter
        
        // 添加滚动监听器，实现上拉加载更多
        binding.rvEntries.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                
                if (!isLoading && hasMoreData) {
                    if ((visibleItemCount + firstVisibleItem) >= totalItemCount - 5) {
                        loadMoreEntries()
                    }
                }
            }
        })
    }
    
    /**
     * 加载词条列表
     */
    private fun loadEntries(offset: Int) {
        if (dictType.isEmpty()) {
            binding.tvEmpty.visibility = android.view.View.VISIBLE
            return
        }
        
        isLoading = true
        binding.progressBar.visibility = android.view.View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val entries = dictionaryRepository.getEntriesByType(dictType, offset, PAGE_SIZE)
                
                withContext(Dispatchers.Main) {
                    if (offset == 0) {
                        entryAdapter.submitList(entries)
                        binding.rvEntries.scrollToPosition(0)
                    } else {
                        val currentList = entryAdapter.currentList.toMutableList()
                        currentList.addAll(entries)
                        entryAdapter.submitList(currentList)
                    }
                    
                    currentOffset = offset + entries.size
                    hasMoreData = entries.size == PAGE_SIZE
                    
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.loadMoreProgress.visibility = android.view.View.GONE
                    binding.tvEmpty.visibility = if (entryAdapter.itemCount == 0) android.view.View.VISIBLE else android.view.View.GONE
                    
                    isLoading = false
                }
            } catch (e: Exception) {
                Timber.e(e, "加载词条列表失败")
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.loadMoreProgress.visibility = android.view.View.GONE
                    isLoading = false
                }
            }
        }
    }
    
    /**
     * 加载更多词条
     */
    private fun loadMoreEntries() {
        if (isLoading || !hasMoreData) return
        
        isLoading = true
        binding.loadMoreProgress.visibility = android.view.View.VISIBLE
        
        loadEntries(currentOffset)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 