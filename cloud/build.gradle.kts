// The GitHub engine (Tj, 2026-10-14: "put vigilant scans on GitHub runners ... the phone only sets the settings and retrieves the results"): a plain JVM program that runs Vigilant's own scanner
// and every fair-odds feed that is switched on, under the settings the phone writes, and publishes the +EV list for the phone. It runs in GitHub Actions (.github/workflows/cloud-scan.yml).
// It places NO order: it is given Novig's public client only, never a trading key (CLAUDE.md, "Cloud scan").
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

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
    testImplementation(libs.kotlinx.coroutines.test)
}

application {
    mainClass.set("com.tjshea.vigilant.cloud.CloudMainKt")
    applicationDefaultJvmArgs = listOf("-Xmx6g")
}

tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
