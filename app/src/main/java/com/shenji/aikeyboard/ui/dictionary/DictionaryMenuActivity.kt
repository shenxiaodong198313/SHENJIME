package com.shenji.aikeyboard.ui.dictionary

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.shenji.aikeyboard.R
import com.shenji.aikeyboard.ui.trie.TrieBuildActivity
import timber.log.Timber

/**
 * 词典管理菜单Activity
 * 提供两个入口：Realm词典管理和双Trie树管理
 */
class DictionaryMenuActivity : AppCompatActivity() {
    
    private lateinit var realmDictButton: Button
    private lateinit var trieButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary_menu)
        
        // 设置返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "词典管理中心"
        
        // 初始化按钮
        initButtons()
    }
    
    /**
     * 初始化按钮
     */
    private fun initButtons() {
        realmDictButton = findViewById(R.id.realm_dict_button)
        trieButton = findViewById(R.id.trie_button)
        
        // 设置Realm词典按钮点击事件
        realmDictButton.setOnClickListener {
            openRealmDictManager()
        }
        
        // 设置Trie树按钮点击事件
        trieButton.setOnClickListener {
            openTrieManager()
        }
    }
    
    /**
     * 打开Realm词典管理
     */
    private fun openRealmDictManager() {
        try {
            Timber.d("打开Realm词典管理")
            val intent = Intent(this, DictionaryListActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "打开Realm词典管理失败")
        }
    }
    
    /**
     * 打开Trie树管理
     */
    private fun openTrieManager() {
        try {
            Timber.d("打开Trie树管理")
            val intent = Intent(this, TrieBuildActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "打开Trie树管理失败")
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 