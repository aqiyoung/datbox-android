// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    signingConfigs {
        create("release") {
            // CI 跑时从 SIGNING_KEYSTORE_FILE 等环境变量读 keystore
            // 本地跑时 (没 env) 用 ~/.openclaw/.env 的 OPENLIST_KEYSTORE_FILE
            val envFile = System.getenv("OPENCLAW_ENV_FILE") ?: "${System.getProperty("user.home")}/.openclaw/.env"
            val props = java.util.Properties()
            try {
                java.io.FileReader(envFile).use { props.load(it) }
            } catch (e: Exception) { /* env 不存在也没事 */ }
            val keystorePath = System.getenv("OPENLIST_KEYSTORE_FILE")
                ?: props.getProperty("OPENLIST_KEYSTORE_FILE")
                ?: "openlist-release.jks"
            val keystorePass = System.getenv("OPENLIST_KEYSTORE_PASS")
                ?: props.getProperty("OPENLIST_KEYSTORE_PASS")
                ?: "threelist_2026"
            val keyAlias = System.getenv("OPENLIST_KEY_ALIAS")
                ?: props.getProperty("OPENLIST_KEY_ALIAS")
                ?: "openlist"
            val keyPass = System.getenv("OPENLIST_KEY_PASS")
                ?: props.getProperty("OPENLIST_KEY_PASS")
                ?: keystorePass
            storeFile = file(keystorePath)
            storePassword = keystorePass
            this.keyAlias = keyAlias
            keyPassword = keyPass
        }
    }

    namespace = "com.threel.openlist"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.threel.openlist"
        minSdk = 26  // Android 8.0
        targetSdk = 34
        versionCode = 4
        versionName = "0.2.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            // debug 用 <your-server> (有 nginx 限速)
        }
        create("beta") {
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            isMinifyEnabled = false
            isDebuggable = true
            // beta 用 <your-server> 但跳过签名 (老板手机直装)
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        // debug 不用 release 签名 (用 Android 默认 debug.keystore)
        // beta buildType 默认 isJniDebuggable, 也不需要 release 签
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Compose + Material 3
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // 液态玻璃：RenderEffect（API 31+）做毛玻璃
    implementation("androidx.compose.ui:ui")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Retrofit + OkHttp + kotlinx.serialization
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
