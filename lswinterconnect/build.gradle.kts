import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

val jdkVersion = project.findProperty("jdk.version").toString()

val compileSdkProjectVersion = project.findProperty("compileSdk").toString().toInt()
val compileSdkMinorVersion = project.findProperty("compileSdkMinor").toString().toInt()
val targetSdkVersion = project.findProperty("targetSdk").toString().toInt()
val minSdkVersion = project.findProperty("minSdk").toString().toInt()

android {
    namespace = "dev.zwander.lswinterconnect"
    compileSdk {
        version = release(compileSdkProjectVersion) {
            minorApiLevel = compileSdkMinorVersion
        }
    }

    defaultConfig {
        minSdk = minSdkVersion
        lint.targetSdk = targetSdkVersion
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

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(jdkVersion))
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
        )
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.material)
    implementation(libs.bugsnag.android)
    implementation(libs.gson)
}