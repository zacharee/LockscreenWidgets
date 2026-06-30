plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.zwander.lswinterconnect"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        aidl = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.material)
    implementation(libs.bugsnag.android)
    implementation(libs.gson)
}