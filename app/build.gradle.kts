import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi") version "1.3.5" // JVM screenshot rendering for store/README mockups
}

// Release signing: env vars (CI) or keystore.properties (local, git-ignored). Without either, the debug key is used.
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(key: String): String? = System.getenv(key) ?: signing.getProperty(key)
val releaseKeystore = secret("HURU_KEYSTORE_FILE")

android {
    namespace = "io.github.thiemotorres.huru"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.thiemotorres.huru"
        minSdk = 28 // Fire OS 7+
        targetSdk = 34
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = findProperty("versionName") as String? ?: "0.1.0"
        // Ko-fi QR only outside the Amazon Appstore (its rules on external payments are unclear): -Pstore=amazon
        buildConfigField("boolean", "DONATE", (findProperty("store") != "amazon").toString())
    }
    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = file(releaseKeystore)
                storePassword = secret("HURU_KEYSTORE_PASSWORD")
                keyAlias = secret("HURU_KEY_ALIAS")
                keyPassword = secret("HURU_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1") // HLS fallback for live, .m3u8 episodes
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // real org.json instead of the Android stubs in unit tests
    testImplementation("io.coil-kt:coil-test:2.7.0") // fake images for the mockup screenshots
}
