plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "be.woogitsu.lekki"
    compileSdk = 35

    defaultConfig {
        applicationId = "be.woogitsu.lekki"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Brak własnego keystore w repo: release podpisany kluczem debug,
            // żeby dało się go od razu zainstalować z pliku.
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
    lint {
        abortOnError = true
        disable += setOf("QueryPermissionsNeeded", "OldTargetApi", "GradleDependency", "NewerVersionAvailable")
    }
}

// Celowo zero zależności (bez AndroidX/Compose): mniej RAM, szybszy start.
