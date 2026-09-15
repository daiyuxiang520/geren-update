import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.open.wuling"
    // Android 16 (Baklava) —— API 36
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        // 发布用包名：与历史各版本（v21–v28）保持一致，确保老用户可覆盖升级。
        // 注意：namespace 保持 com.open.wuling（对应源码目录），二者可以不同。
        applicationId = "com.wuling.app.repack"
        minSdk = 26
        targetSdk = 36
        versionCode = 42
        versionName = "3.39.0-android16"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // 仅打包中文/英文资源，减小体积（不影响功能）
        resourceConfigurations += setOf("zh", "en")

        // Read sensitive config from local.properties (fallback to env vars for CI)
        fun prop(key: String): String {
            val localProps = rootProject.file("local.properties")
            if (localProps.exists()) {
                val props = Properties()
                props.load(localProps.inputStream())
                val value = props.getProperty(key, "").trim()
                if (value.isNotEmpty()) return value
            }
            return (System.getenv(key.replace(".", "_").uppercase()) ?: "").trim()
        }

        buildConfigField("String", "CLIENT_ID", "\"${prop("wuling.client.id")}\"")
        buildConfigField("String", "CLIENT_SECRET", "\"${prop("wuling.client.secret")}\"")
        buildConfigField("String", "APP_CODE", "\"${prop("wuling.app.code")}\"")
        buildConfigField("String", "APP_VERSION", "\"${prop("wuling.app.version")}\"")
        buildConfigField("String", "BASE_URL", "\"${prop("wuling.base.url")}\"")
        buildConfigField("String", "DEVICE_IMEI", "\"${prop("wuling.device.imei")}\"")
        buildConfigField("String", "DEVICE_MODEL", "\"${prop("wuling.device.model")}\"")
        buildConfigField("String", "DEVICE_BRAND", "\"${prop("wuling.device.brand")}\"")
        buildConfigField("String", "API_VERSION", "\"${prop("wuling.api.version")}\"")
        buildConfigField("String", "API_VERSION_CODE", "\"${prop("wuling.api.version.code")}\"")

        // 菱菱邦 OAuth 客户端凭据（登录用）
        buildConfigField("String", "LLB_CLIENT_ID", "\"${prop("wuling.llb.client.id")}\"")
        buildConfigField("String", "LLB_CLIENT_SECRET", "\"${prop("wuling.llb.client.secret")}\"")

        // 能耗数据中心网关凭据（签名用）
        buildConfigField("String", "ENERGY_APP_KEY", "\"${prop("wuling.energy.app.key")}\"")
        buildConfigField("String", "ENERGY_APP_SECRET", "\"${prop("wuling.energy.app.secret")}\"")

        // 友盟+ 移动统计 AppKey（由 local.properties 注入，开源仓库为空）
        buildConfigField("String", "UMENG_APPKEY", "\"${prop("wuling.umeng.appkey")}\"")
    }

    signingConfigs {
        create("release") {
            // 签名配置全部来自环境变量，源码中不内置任何口令。
            // 使用方式见 README「构建」一节：
            //   export WULING_KEYSTORE_PATH=../keystore/wuling.keystore
            //   export WULING_KEYSTORE_PASSWORD=xxx
            //   export WULING_KEY_ALIAS=xxx
            //   export WULING_KEY_PASSWORD=xxx
            val ksPath = System.getenv("WULING_KEYSTORE_PATH")
            val ksPass = System.getenv("WULING_KEYSTORE_PASSWORD")
            val kAlias = System.getenv("WULING_KEY_ALIAS")
            val kPass = System.getenv("WULING_KEY_PASSWORD")
            if (ksPath != null && ksPass != null && kAlias != null && kPass != null) {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = kAlias
                keyPassword = kPass
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            // 注：构建容器中 R8 混淆连续崩溃，v3.1.0 起发布构建关闭混淆/资源收缩。
            // 功能不受影响（未混淆反而避免 Gson 反射序列化受 keep 规则影响），仅 APK 体积增大。
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isCrunchPngs = true

            val releaseSigningConfig = signingConfigs.getByName("release")
            signingConfig = if (releaseSigningConfig.storeFile?.exists() == true) {
                releaseSigningConfig
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // 发布构建禁用 lintVital（静态检查），避免 CI/容器环境内存不稳定导致 daemon 崩溃
    lint {
        checkReleaseBuilds = false
        abortOnError = false
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // 16KB 页大小（Android 15+ / Android 16 强制要求）：
            // 关闭 legacy 压缩打包，原生库按页对齐且未压缩存储
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Compose BOM（适配 compileSdk 36 / Kotlin 2.0.21）
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    kapt("com.google.dagger:hilt-android-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // OkHttp + Gson (for API calls)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // DataStore (for Token persistence)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coil (for image loading)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // 友盟+ 移动统计（U-App）：common 必选，asms 设备信息组件必选
    implementation("com.umeng.umsdk:common:9.9.9")
    implementation("com.umeng.umsdk:asms:1.8.7.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
