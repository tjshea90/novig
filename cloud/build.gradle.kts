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

// The daily review (ReviewMain.kt): `./gradlew :cloud:review --args="<phone-data checkout> <output file>"`, run by .github/workflows/review-digest.yml.
tasks.register<JavaExec>("review") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.tjshea.vigilant.cloud.ReviewMainKt")
    jvmArgs("-Xmx8g")
}

// The daily grader of the engine's scan study (GradeMain.kt): `./gradlew :cloud:grade --args="--journal DIR --new DIR [--trades FILE --results FILE]"`, run by .github/workflows/engine-grade.yml.
tasks.register<JavaExec>("grade") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.tjshea.vigilant.cloud.GradeMainKt")
    jvmArgs("-Xmx3g")
}
