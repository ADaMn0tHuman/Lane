plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.lanewidget"
    compileSdk = 34

    // Подпись. На GitHub Actions ключ берётся из секретов (переменные LANE_*).
    // Локально, без переменных, используется стандартный отладочный ключ.
    // Один и тот же ключ означает, что обновления ставятся поверх без удаления.
    signingConfigs {
        getByName("debug") {
            val ks = System.getenv("LANE_KEYSTORE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("LANE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LANE_KEY_ALIAS")
                keyPassword = System.getenv("LANE_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "app.lanewidget"
        minSdk = 26
        targetSdk = 34
        // Номер версии растёт сам: CI передаёт номер запуска сборки.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "1.1"
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
