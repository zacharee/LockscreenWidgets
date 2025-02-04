plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.bugsnag)
}

android {
    compileSdk = 35

    defaultConfig {
        namespace = "tk.zwander.lockscreenwidgets"
        applicationId = "tk.zwander.lockscreenwidgets"
        minSdk = 22
        targetSdk = 35
        versionCode = 136
        versionName = "2.21.0"

        extensions.getByType(BasePluginExtension::class.java).archivesName.set("LockscreenWidgets_${versionName}")

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

    val jdkVersion = project.properties["jdk.version"].toString()

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(jdkVersion)
        targetCompatibility = JavaVersion.toVersion(jdkVersion)
    }

    kotlinOptions {
        jvmTarget = jdkVersion
        freeCompilerArgs += "-Xcontext-receivers"
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
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.atomicfu)

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
}