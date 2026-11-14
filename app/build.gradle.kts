plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.chaon.app"
    compileSdk = 35

    // AAOS 표준 차량 속성(기어 P단, 주차 브레이크)을 읽기 위한 플랫폼 라이브러리
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.chaon.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 10
        versionName = "0.6.1-prototype"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 공모전 시연용: 디버그 키로 서명해 바로 설치 가능한 release APK를 만든다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.vehicle.sdk)
    implementation(libs.tts.sdk)
    implementation(libs.stt.sdk)
    implementation(libs.kotlin.reflect)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
