import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.UUID

plugins {
    alias(libs.plugins.android.application)
}

val jdkVersion = project.findProperty("jdk.version").toString()

val compileSdkVersion = project.findProperty("compileSdk").toString().toInt()
val compileSdkMinorVersion = project.findProperty("compileSdkMinor").toString().toInt()
val minSdkVersion = project.findProperty("minSdk").toString().toInt()

android {
    namespace = "dev.zwander.lswwallpaper"
    compileSdk {
        version = release(compileSdkVersion) {
            minorApiLevel = compileSdkMinorVersion
        }
    }

    defaultConfig {
        applicationId = "dev.zwander.lswwallpaper"
        minSdk = minSdkVersion
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 29
        versionCode = 3
        versionName = "1.2"

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
        sourceCompatibility = JavaVersion.toVersion(jdkVersion)
        targetCompatibility = JavaVersion.toVersion(jdkVersion)
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }
}

afterEvaluate {
    base {
        archivesName.set("WAWallpaperCompanion_${android.defaultConfig.versionName}")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(jdkVersion))
        freeCompilerArgs.addAll(
            "-Xcollection-literals",
        )
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