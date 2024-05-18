plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.bugsnag) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.atomicfu) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
