plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.enduro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.enduro"
        minSdk        = 26
        targetSdk     = 34
        versionCode   = 1
        versionName   = "1.0"

        buildConfigField("String", "BACKEND_BASE_URL",
            "\"${project.findProperty("BACKEND_BASE_URL") ?: "https://api.example.com"}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Garmin Health SDK  (must be installed locally as a flat dir repo in practice)
    // implementation(files("libs/GarminHealthSDK.aar"))

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
