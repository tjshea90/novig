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
include(":lab")
// Vigilant MGM: the same app for BetMGM, built from app's own sources (mgm/build.gradle.kts).
// DORMANT since 2026-09-27 (Tj: "Novig mgm should be dormant and no changes made at all unless I ask
// for it"): frozen at v0.17.0, left out of every build, test run and release unless asked for with
// `-Pmgm` (`./gradlew -Pmgm :mgm:assembleRelease`). See CLAUDE.md "Vigilant MGM is dormant".
if (gradle.startParameter.projectProperties.containsKey("mgm")) include(":mgm")
