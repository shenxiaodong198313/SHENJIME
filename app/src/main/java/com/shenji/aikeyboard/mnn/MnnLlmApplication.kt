// Created by ruoyi.sjd on 2024/12/18.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
package com.shenji.aikeyboard.mnn

import android.app.Application
import com.alibaba.mls.api.ApplicationProvider
import com.shenji.aikeyboard.mnn.utils.CrashUtil

class MnnLlmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApplicationProvider.set(this)
        CrashUtil.init(this)
    }
}



