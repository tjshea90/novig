// Pure Kotlin/JVM module — repositories, the Novig/The-Odds-API clients, and the scanner that
// ties them to the `engine` module's math. No Android dependency, same reasoning as `engine`
// (see its build file) — this lets the data-fetching and JSON-parsing logic be unit tested for
// real in this dev container even without an Android SDK.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":engine"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    // Ed25519 + deterministic P-256 signing for Novig's NOVIG-V3 scheme, identical on every
    // Android version and in JVM tests (lightweight API only; R8 strips the rest).
    implementation(libs.bouncycastle)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
