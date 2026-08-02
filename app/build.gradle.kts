plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hubery.dynamicislandport"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hubery.dynamicislandport"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    // Xposed API stubs — provided at runtime by LSPosed
    // Downloaded in CI; place api-82.jar in app/libs/ for local builds
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
