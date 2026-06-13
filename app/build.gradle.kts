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
            // 读 ~/.openclaw/.env (简单 grep KEY=VALUE)
            val envFile = File(System.getProperty("user.home"), ".openclaw/.env")
            fun envGet(key: String, default: String): String {
                System.getenv(key)?.let { return it }
                if (envFile.exists()) {
                    envFile.readLines().forEach { line ->
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2 && parts[0].trim() == key) {
                            return parts[1].trim()
                        }
                    }
                }
                return default
            }
            // Synapse 风格 Secret 名: KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
            val keystorePath = envGet("KEYSTORE_FILE", "openlist-release.jks")
            val keystorePass = envGet("KEYSTORE_PASSWORD", "threelist_2026")
            val keyAlias = envGet("KEY_ALIAS", "openlist")
            val keyPass = envGet("KEY_PASSWORD", keystorePass)
            storeFile = file(keystorePath)
            storePassword = keystorePass
            this.keyAlias = keyAlias
            keyPassword = keyPass
        }
    }

    namespace = "com.threel.openlist"
    compileSdk = 34

    defaultConfig {
        // Synapse 套路: 单一 applicationId, 不加 .beta / .debug suffix
        // 老板手机只装一个包, 后续版本能覆盖装
        applicationId = "com.threel.openlist"
        minSdk = 26  // Android 8.0
        targetSdk = 34
        versionCode = 9
        versionName = "0.2.4"
    }

    buildTypes {
        // Synapse 套路: 只 build release, 不跑 debug
        // 老板手机用正式 release 包, 调试用 .apk sideload
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
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
