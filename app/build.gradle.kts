plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.lanewidget"
    compileSdk = 34

    // Постоянный ключ подписи. Без него каждая сборка на GitHub Actions подписывается новым
    // случайным ключом, и Android не даёт поставить новую версию поверх старой.
    signingConfigs {
        getByName("debug") {
            storeFile = file("lane.keystore")
            storePassword = "lanewidget"
            keyAlias = "lane"
            keyPassword = "lanewidget"
        }
    }

    defaultConfig {
        applicationId = "app.lanewidget"
        minSdk = 26
        targetSdk = 34
        // Номер версии растёт сам: CI передаёт номер запуска сборки.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.media:media:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
