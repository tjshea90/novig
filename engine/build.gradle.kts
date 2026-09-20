// Pure Kotlin/JVM module — no Android dependency on purpose. This is the
// devig/EV math (RESEARCH.md §5-6): it must be independently testable
// without an Android SDK, since this project's dev container doesn't have
// one (see BRIEF.md's Toolchain section).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit)
}
