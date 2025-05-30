plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.realm.kotlin")
}

android {
    namespace = "com.shenji.aikeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shenji.aikeyboard"
        minSdk = 24 // 降低到24以支持更多设备
        targetSdk = 35 // 升级到最新
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 增加内存配置以支持LLM
        multiDexEnabled = true
        
        // NDK配置 - 暂时禁用
        /*
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        
        // CMake配置
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
        */
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
    }
    
    // CMake外部构建配置 - 暂时禁用
    /*
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    */
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    kotlinOptions {
        jvmTarget = "21"
    }
    
    kotlin {
        jvmToolchain(21)
    }
    
    // 增加编译时内存
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "21"
        }
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
    // Core Android libraries
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
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
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
} 