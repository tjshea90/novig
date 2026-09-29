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

tasks.test {
    // The live checks (`VIGILANT_LIVE=1 bash tools/test.sh :data:test --tests '*LiveNovigSmokeTest'`) read
    // these at run time. As inputs, switching one on reruns the tests instead of Gradle calling them up to
    // date from an earlier run without it (which would pass without anything live having run).
    listOf(
        "VIGILANT_LIVE", "VIGILANT_LIVE_LEAGUES", "VIGILANT_BURST", "VIGILANT_BURST_S",
        "VIGILANT_PROBE", "VIGILANT_SOAK", "VIGILANT_SOAK_INTERVAL", "VIGILANT_SOAK_MIN",
    ).forEach { name -> inputs.property(name, providers.environmentVariable(name).orElse("")) }
    // Most of this suite's time is tests waiting out paced or retried requests (CnoClientTest,
    // PlayerTeamsTest, ExchangeClientsTest), not CPU, so test classes split across JVMs overlap
    // that waiting. Each fork is its own JVM: nothing is shared between them.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}
