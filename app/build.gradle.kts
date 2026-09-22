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
        versionCode = 4
        versionName = "0.3.0"
    }

    // Signs with a keystore committed directly into the repo — Tj's explicit call (2026-09-20),
    // matching fantasy-football's precedent: no GitHub Secret, no manual setup step, works the
    // same locally and in CI. app/keystore/vigilant-debug.jks uses Android's standard well-known
    // debug password on purpose — there's no real secret in it to protect. This is a deliberate
    // security tradeoff (anyone can forge an "update" signed with this same public key), fine
    // with nothing sensitive in the app yet — see BRIEF.md's keystore section for the reasoning
    // and when to revisit it (the day this app holds real Novig API credentials).
    signingConfigs {
        create("release") {
            storeFile = file("keystore/vigilant-debug.jks")
            storePassword = "android"
            keyAlias = "vigilant"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
