plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("io.realm.kotlin")
}

android {
    namespace = "com.shenji.aikeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shenji.aikeyboard"
        minSdk = 26 // 改为与kaifa一致
        targetSdk = 34 // 改为与kaifa一致
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 增加内存配置以支持LLM
        multiDexEnabled = true
        
        // NDK配置 - 支持MNN
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        
        // CMake配置 - 支持MNN
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    
    // CMake外部构建配置 - 支持MNN
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21 // 恢复为21
        targetCompatibility = JavaVersion.VERSION_21 // 恢复为21
    }
    
    kotlinOptions {
        jvmTarget = "21" // 恢复为21
    }
    
    kotlin {
        jvmToolchain(21)
    }
    
    // 打包配置
    packaging {
        jniLibs {
            pickFirsts.add("**/libc++_shared.so")
            pickFirsts.add("**/libtensorflowlite_jni.so")
            pickFirsts.add("**/libtensorflowlite_gpu_jni.so")
        }
    }
}

dependencies {
    // Assists框架依赖
    implementation(project(":assists-framework"))
    
    // OpenCV和OCR依赖
    implementation("org.opencv:opencv:4.9.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    
    // Core Android libraries
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    
    // MultiDex support for large heap
    implementation("androidx.multidex:multidex:2.0.1")
    
    // Facebook Shimmer
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    
    // Realm database
    implementation("io.realm.kotlin:library-base:2.3.0")
    
    // YAML解析
    implementation("org.yaml:snakeyaml:2.2")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    
    // Timber for logging
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // MediaPipe LLM集成
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    implementation("com.google.mediapipe:tasks-vision:0.10.15")
    
    // OkHttp和Retrofit已在MNN依赖中包含，无需重复添加
    
    // MNN相关依赖 - 严格按照kaifa项目配置
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    
    // 网络请求 - 按照kaifa版本
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
    
    // JSON解析
    implementation("com.google.code.gson:gson:2.10.1")
    
    // 权限请求
    implementation("com.github.permissions-dispatcher:permissionsdispatcher:4.9.2")
    
    // MNN特有依赖 - 按照kaifa项目
    implementation("com.github.techinessoverloaded:progress-dialog:1.5.1")
    implementation("com.github.ybq:Android-SpinKit:1.4.0")
    implementation("com.nambimobile.widgets:expandable-fab:1.2.1")
    implementation("com.github.squti:Android-Wave-Recorder:2.0.1")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-latex:4.6.2")
    implementation("ru.noties:jlatexmath-android:0.2.0")
    implementation("ru.noties:jlatexmath-android-font-cyrillic:0.2.0")
    implementation("ru.noties:jlatexmath-android-font-greek:0.2.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    
    // Jetpack Compose - 按照kaifa版本
    val jetpackComposeVersion = "1.7.8"
    implementation("androidx.compose.foundation:foundation:$jetpackComposeVersion")
    implementation("androidx.compose.material:material-icons-extended:$jetpackComposeVersion")
    implementation("androidx.compose.ui:ui:$jetpackComposeVersion")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui-tooling-preview:$jetpackComposeVersion")
    debugImplementation("androidx.compose.ui:ui-tooling:$jetpackComposeVersion")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // 数据绑定
    implementation("androidx.databinding:databinding-runtime:8.7.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
} 