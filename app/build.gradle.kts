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

    namespace = "com.datbox.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.datbox.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 10051
        versionName = "1.0.51"
    }

    buildTypes {
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.compose.ui:ui")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.webkit:webkit:1.10.0")

    testImplementation("junit:junit:4.13.2")
}
