plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.bugsnag)
}

android {
    compileSdk = 34

    defaultConfig {
        namespace = "tk.zwander.lockscreenwidgets"
        applicationId = "tk.zwander.lockscreenwidgets"
        minSdk = 22
        targetSdk = 34
        versionCode = 120
        versionName = "2.15.8"

        extensions.getByType(BasePluginExtension::class.java).archivesName.set("LockscreenWidgets_${versionName}")
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
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
    }
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.atomicfu)

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.preference.ktx)

    implementation(libs.hiddenapibypass)
    implementation(libs.gson)
    implementation(libs.material)
    implementation(libs.byte.buddy.android)
    implementation(libs.seekBarPreference)
    implementation(libs.collapsiblePreferenceCategory)
    implementation(libs.colorpicker)
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
    implementation(libs.accompanist.pager)
    implementation(libs.accompanist.themeadapter.material)
    implementation(libs.accompanist.themeadapter.material3)

    implementation(libs.bugsnag.android)
    implementation(libs.taskerpluginlibrary)

    implementation(libs.relinker)
    implementation(libs.compose.spinkit)
}