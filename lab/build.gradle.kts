// The GitHub research lab (Tj, 2026-10-09: "copy as much of the research lab into GitHub as possible"): a plain JVM program that runs Vigilant's own scanner, paper lab and paper bid lab against
// Novig's public API, ESPN, Kalshi, Polymarket and (with a key) SportsGameOdds Pro, with no phone. It runs in GitHub Actions (.github/workflows/lab-record.yml) and writes the same files the phone does.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

// :data (and :engine under it) are configured first: on a fresh GitHub runner `:lab:run` alone otherwise failed to find :data's variants ("No variants exist").
evaluationDependsOn(":engine")
evaluationDependsOn(":data")

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":data"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}

application {
    mainClass.set("com.tjshea.vigilant.lab.LabMainKt")
}
