import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Vigilant MGM (Tj, 2026-09-27: "make it also do the same exact functions to find positive EV on
 * betmgm … if smart, make this a totally separate app … so as not to disturb novig").
 *
 * A second app built from the Novig app's OWN sources and resources (`../app/src/main`), not a copy:
 * every fix reaches both. The only differences are here: its applicationId (so both install side by
 * side, each with its own storage, keys, placed bets and tracker), `BuildConfig.BOOK = "betmgm"`
 * (AppBook.kt switches every book-specific part on it), and its name and icon color
 * (src/book/res, overlaid on both build types). `app` itself is untouched by this module.
 */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

// One version for both apps: read from app/build.gradle.kts, the single place ship.sh and
// release.yml bump and read it.
val appGradle = rootProject.file("app/build.gradle.kts").readText()
val sharedVersionCode = Regex("""versionCode = (\d+)""").find(appGradle)!!.groupValues[1].toInt()
val sharedVersionName = Regex("""versionName = "([^"]+)"""").find(appGradle)!!.groupValues[1]

android {
    // The same namespace as `app`: the shared sources' R and BuildConfig live there.
    namespace = "com.tjshea.vigilant.app"
    compileSdk = 36

    defaultConfig {
        // Permanent once released (BRIEF.md's keystore rules) — never change this.
        applicationId = "com.tjshea.vigilant.betmgm"
        minSdk = 30
        targetSdk = 36
        versionCode = sharedVersionCode
        versionName = sharedVersionName
        buildConfigField("String", "BOOK", "\"betmgm\"")
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("../app/src/main/kotlin")
            res.srcDirs("../app/src/main/res")
            manifest.srcFile("../app/src/main/AndroidManifest.xml")
        }
        // Build-type source sets overlay main's resources: Vigilant MGM's name and icon color.
        getByName("debug") { res.srcDirs("src/book/res") }
        getByName("release") { res.srcDirs("src/book/res") }
    }

    // The same committed keystore as Vigilant (BRIEF.md): a different app, so no conflict.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("app/keystore/vigilant-debug.jks")
            storePassword = "android"
            keyAlias = "vigilant"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), rootProject.file("app/proguard-rules.pro"))
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", project.hasProperty("screenshots").toString())
                it.maxHeapSize = "2g"
            }
        }
    }
}

// Unit tests on the debug variant only, as in `app` (ui-test-manifest is debugImplementation).
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("app/compose-stability.conf"))
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.ui.test.manifest)
}
