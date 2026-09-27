pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "vigilant"

include(":engine")
include(":data")
include(":app")
// Vigilant MGM: the same app for BetMGM, built from app's own sources (mgm/build.gradle.kts).
include(":mgm")
