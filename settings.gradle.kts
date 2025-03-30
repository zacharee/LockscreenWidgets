@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

include(":app"/*, ":spannedlm"*/)
rootProject.name = "Lockscreen Widgets"

//project(":spannedlm").projectDir = new File("../SpannedGridLayoutManager/spannedgridlayoutmanager")