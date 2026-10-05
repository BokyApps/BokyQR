plugins {
    alias(libs.plugins.kotlin.jvm)
}

// :core is deliberately a plain Kotlin/JVM module with no Android dependency, so the payload
// policy can be tested on a workstation with no device, no emulator and no Android SDK.
kotlin {
    jvmToolchain(17)
}

// No coroutines dependency: :core is synchronous and pure, and keeping the module free of
// kotlinx keeps its test run free of transitive resolution as well.
dependencies {
    testImplementation(libs.junit4)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
