plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "rocks.myburgh.bokyqr"
    compileSdk = 35

    defaultConfig {
        applicationId = "rocks.myburgh.bokyqr"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "store"
    productFlavors {
        // Bundled (on-device) ML Kit barcode model. Proprietary, and it does need
        // play-services-tasks for the Task API, but Firebase and the ClearCut datatransport
        // libraries are forbidden on this flavor and are excluded and verified below.
        create("play") { dimension = "store" }
        // ZXing only. Must stay free of ML Kit, Play Services and Firebase.
        create("fdroid") { dimension = "store" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.security.crypto)
    // Reads orientation from picker URIs only. Needs no storage permission.
    implementation(libs.androidx.exifinterface)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Bundled (on-device) ML Kit barcode model: the model ships in the APK, so scanning works
    // with no Play Services installed and no network access.
    //
    // ML Kit does drag in two things this app must not carry:
    //   * com.google.firebase:* (firebase-components, firebase-encoders, ...) and
    //   * com.google.android.datatransport:* (transport-backend-cct, i.e. ClearCut upload).
    // Both are telemetry plumbing, and both pull in a merged ACCESS_NETWORK_STATE permission
    // the source manifest never declares. They are excluded here and re-checked by the
    // verifyPlayClasspath task below, which fails the build if either comes back.
    //
    // com.google.android.gms is deliberately NOT excluded: PlayBarcodeDecoder uses
    // com.google.android.gms.tasks.Tasks, and play-services-tasks is only the Task API, no
    // Play Services runtime. Tasks stays; Firebase and datatransport go.
    "playImplementation"(libs.mlkit.barcode.scanning) {
        exclude(group = "com.google.firebase")
        exclude(group = "com.google.android.datatransport")
    }
    "fdroidImplementation"(libs.zxing.core)
}

// The fdroid flavor must never ship ML Kit, Play Services or Firebase, directly or transitively.
// This fails the build if any of them shows up on an fdroid runtime classpath. Do not weaken it.
val verifyFdroidClasspath = tasks.register("verifyFdroidClasspath") {
    group = "verification"
    description = "Fails if ML Kit, Play Services or Firebase is on an fdroid runtime classpath."
    val configNames = listOf("fdroidDebugRuntimeClasspath", "fdroidReleaseRuntimeClasspath")
    doLast {
        val offenders = sortedSetOf<String>()
        configNames.forEach { configName ->
            configurations.getByName(configName).incoming.resolutionResult.allComponents.forEach { component ->
                val id = component.moduleVersion ?: return@forEach
                val forbidden = id.group.startsWith("com.google.mlkit") ||
                    id.group.startsWith("com.google.firebase") ||
                    id.group == "com.google.android.gms" ||
                    id.name.contains("mlkit", ignoreCase = true) ||
                    id.name.startsWith("play-services")
                if (forbidden) offenders += "${id.group}:${id.name}:${id.version} (in $configName)"
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "fdroid flavor must not depend on ML Kit, Play Services or Firebase:\n  " +
                    offenders.joinToString("\n  "),
            )
        }
    }
}

tasks.matching { it.name == "preFdroidDebugBuild" || it.name == "preFdroidReleaseBuild" }
    .configureEach { dependsOn(verifyFdroidClasspath) }
tasks.named("check") { dependsOn(verifyFdroidClasspath) }

// The play flavor is allowed exactly one Google dependency: the bundled on-device barcode
// model plus play-services-tasks for the Task API that PlayBarcodeDecoder awaits on.
// ML Kit's POM also drags in firebase-components, firebase-encoders and
// transport-backend-cct (ClearCut upload), none of which belong in this app; they are
// excluded in the dependency block and this task fails the build if any of them ever
// reappear. Do not weaken it, and do not "fix" a report here by excluding com.google.android.gms
// wholesale: Tasks is required.
val verifyPlayClasspath = tasks.register("verifyPlayClasspath") {
    group = "verification"
    description = "Fails if Firebase or datatransport is on a play runtime classpath."
    val configNames = listOf("playDebugRuntimeClasspath", "playReleaseRuntimeClasspath")
    doLast {
        val offenders = sortedSetOf<String>()
        configNames.forEach { configName ->
            configurations.getByName(configName).incoming.resolutionResult.allComponents.forEach { component ->
                val id = component.moduleVersion ?: return@forEach
                val forbidden = id.group.startsWith("com.google.firebase") ||
                    id.group.startsWith("com.google.android.datatransport")
                if (forbidden) offenders += "${id.group}:${id.name}:${id.version} (in $configName)"
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "play flavor must not depend on Firebase or datatransport (ClearCut transport):\n  " +
                    offenders.joinToString("\n  ") +
                    "\n  Add exclude(group = ...) for the offending group on the mlkit-barcode-scanning " +
                    "dependency instead of allowing it.",
            )
        }
    }
}

tasks.matching { it.name == "prePlayDebugBuild" || it.name == "prePlayReleaseBuild" }
    .configureEach { dependsOn(verifyPlayClasspath) }
tasks.named("check") { dependsOn(verifyPlayClasspath) }
