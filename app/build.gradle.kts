import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.UUID

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.bugsnag)
}

val jdkVersion = project.properties["jdk.version"].toString()

android {
    compileSdk = 36

    defaultConfig {
        namespace = "tk.zwander.lockscreenwidgets"
        applicationId = "tk.zwander.lockscreenwidgets"
        minSdk = 23
        targetSdk = 36
        versionCode = 156
        versionName = "3.0.0-alpha11"

        extensions.getByType(BasePluginExtension::class.java).archivesName.set("LockscreenWidgets_${versionName}")
        manifestPlaceholders["build_uuid"] = UUID.nameUUIDFromBytes("InstallWithOptions_${versionCode}".toByteArray()).toString()

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                cppFlags += ""
                arguments.add("-DANDROID_WEAK_API_DEFS=ON")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
        aidl = true
        prefab = true
    }

    buildTypes {
        all {
            buildConfigField("Integer", "DATABASE_VERSION", project.properties["databaseVersion"].toString())
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(jdkVersion)
        targetCompatibility = JavaVersion.toVersion(jdkVersion)
    }

    packaging {
        resources.excludes.add("META-INF/library_release.kotlin_module")
        jniLibs.pickFirsts += "**/libbugsnag-ndk.so"
    }
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    ndkVersion = "28.1.13356709"

    dependenciesInfo {
        // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles (for Google Play)
        includeInBundle = false
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
    implementation(fileTree("libs") { include("*.jar") })
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.atomicfu)
    implementation(kotlin("reflect"))

    implementation(libs.core.ktx)
    implementation(libs.core.remoteviews)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.preference.ktx)

    implementation(libs.hiddenapibypass)
    implementation(libs.gson)
    implementation(libs.material)
    implementation(libs.byte.buddy.android)
    implementation(libs.seekBarPreference)
    implementation(libs.patreonSupportersRetrieval)
    implementation(libs.spannedGridLayoutManager)
    implementation(libs.composeIntroSlider)
//    implementation(project(":spannedlm"))

    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(libs.accompanist.drawablepainter)

    implementation(libs.bugsnag.android)
    implementation(libs.bugsnag.exitinfo)
    implementation(libs.bugsnag.android.performance)
    implementation(libs.bugsnag.android.performance.appcompat)
    implementation(libs.bugsnag.android.performance.compose)

    implementation(libs.taskerpluginlibrary)

    implementation(libs.relinker)
    implementation(libs.compose.spinkit)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.storage)

    implementation(libs.colorpicker.compose)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}