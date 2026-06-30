import java.util.UUID

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.zwander.lswwallpaper"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.zwander.lswwallpaper"
        minSdk = 23
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 29
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["build_uuid"] = UUID.nameUUIDFromBytes("LSWWallpaperCompanion_${versionCode}".toByteArray()).toString()
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
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

afterEvaluate {
    base {
        archivesName.set("LSWWallpaperCompanion_${android.defaultConfig.versionName}")
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.material)
    implementation(libs.bugsnag.android)
    implementation(libs.hiddenapibypass)

    implementation(project(":lswinterconnect"))
}