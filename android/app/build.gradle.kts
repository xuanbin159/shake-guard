plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cn.shakeguard.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "cn.shakeguard.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 100
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 核心工程采用零外部膨胀的原生 Android SDK + Kotlin 声明式 UI 组件，保证 <2MB 超轻体积与全机型兼容
}
