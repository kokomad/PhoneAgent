plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val geminiKey: String = ((project.findProperty("GEMINI_KEY") as String?) ?: "")
    .replace("\\", "").replace("\"", "").trim()

android {
    namespace = "com.khader.phoneagent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.khader.phoneagent"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "DEFAULT_GEMINI_KEY", "\"$geminiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
