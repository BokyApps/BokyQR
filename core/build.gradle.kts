plugins {
    alias(libs.plugins.kotlin.jvm)
}

// :core is deliberately a plain Kotlin/JVM module with no Android dependency, so the payload
// policy can be tested on a workstation with no device, no emulator and no Android SDK.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
