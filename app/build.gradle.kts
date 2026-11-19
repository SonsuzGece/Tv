plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kizmark.tvlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kizmark.tvlauncher"
        minSdk = 28
        targetSdk = 34
        versionCode = 6
        versionName = "4.0"
    }

    // Sabit imza: her derleme aynı anahtarla imzalanır, güncellemeler üstüne kurulur
    signingConfigs {
        create("tv") {
            storeFile = file("tvlauncher.jks")
            storePassword = "tvlauncher"
            keyAlias = "tv"
            keyPassword = "tvlauncher"
        }
    }

    // HIZ: "debug" adı kalsa da APK tam optimize derlenir (R8 küçültme, hata ayıklama kapalı).
    // Compose'un hata ayıklama modunda çalışması launcher'ı birkaç kat yavaşlatır.
    buildTypes {
        getByName("debug") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("tv")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("tv")
        }
    }

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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    // Kurulumda hazır derleme profili: ilk açılıştan itibaren hızlı
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}
