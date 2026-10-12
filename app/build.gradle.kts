plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.penn.myapplication"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.penn.myapplication"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // sherpa-onnx so 体积较大，只保留真机（arm64）和模拟器（x86_64）架构；
        // 如需支持 32 位设备，把 "armeabi-v7a" 加回来即可
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.7")
    
    // sherpa-onnx 离线语音识别（官方预编译 AAR，含 arm64-v8a/armeabi-v7a/x86/x86_64 的 JNI so）
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    // GitHub 兜底源的模型是 tar.bz2 整包，用 commons-compress 解压
    implementation("org.apache.commons:commons-compress:1.25.0")

    // QR Code generation
    implementation("com.google.zxing:core:3.5.2")
    // JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")
    // Chinese Lunar Calendar
    implementation("cn.6tail:lunar:1.7.7")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}