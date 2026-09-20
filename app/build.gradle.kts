import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.tjshea.vigilant.app"
    // compileSdk/targetSdk 36 = Android 16, per BRIEF.md's decided platform target.
    compileSdk = 36

    defaultConfig {
        // Permanent once picked (BRIEF.md's keystore rules) — never change this.
        applicationId = "com.tjshea.vigilant"
        // minSdk 30: the target device (a Moto G 2026) will ship far newer than this, but there's
        // no real cost to a bit of headroom for testing on whatever other device is on hand.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing comes entirely from environment variables, never from anything committed —
    // set only inside .github/workflows/release.yml, which decodes the keystore from a GitHub
    // Secret (see BRIEF.md's "The rule that will apply the moment a keystore exists"). Outside
    // that workflow (a local or CI test build) VIGILANT_KEYSTORE_PATH is unset, so `release` just
    // builds unsigned — correct for `ci.yml`'s assembleDebug-only verification, since nothing
    // outside the real release workflow should ever be able to produce a signed APK.
    val keystorePath = System.getenv("VIGILANT_KEYSTORE_PATH")
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("VIGILANT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VIGILANT_KEY_ALIAS")
                keyPassword = System.getenv("VIGILANT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

// `android.kotlinOptions { jvmTarget = "21" }` is a hard error on this Kotlin version — migrated
// to the current compilerOptions DSL (see https://kotl.in/u1r8ln, hit for real in CI 2026-09-20).
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
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
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
