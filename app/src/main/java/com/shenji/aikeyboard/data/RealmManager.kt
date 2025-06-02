package com.shenji.aikeyboard.data

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration

object RealmManager {
    
    private var realm: Realm? = null
    
    fun getInstance(): Realm {
        if (realm == null) {
            val config = RealmConfiguration.Builder(
                schema = setOf(ScriptItem::class)
            )
                .name("shenji_script.realm")
                .build()
            
            realm = Realm.open(config)
        }
        return realm!!
    }
    
    fun close() {
        realm?.close()
        realm = null
    }
} 