import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// -- Release signing ------------------------------------------------------------------------------
//
// Credentials are read from the environment first (CI) and from `local.properties` second
// (workstation). `local.properties` is gitignored, as are *.jks / *.keystore / *.p12, so no key
// material or password can reach the repository through this block.
//
//   BOKYQR_STORE_FILE     / storeFile        path to the keystore
//   BOKYQR_STORE_PASSWORD / storePassword    keystore password
//   BOKYQR_KEY_ALIAS      / keyAlias         key alias
//   BOKYQR_KEY_PASSWORD   / keyPassword      key password
//
// The release signing config is only wired up when the keystore actually exists and is readable.
// With no keystore the release build is simply unsigned, which is what you want for a local
// `assembleRelease` smoke test; it is NOT what you upload. See the Releasing section of README.md.
val signingProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

/** Environment variable first, then the matching `local.properties` key. Blank counts as unset. */
fun signingValue(variable: String, property: String): String? =
    System.getenv(variable)?.takeIf { it.isNotBlank() }
        ?: signingProperties.getProperty(property)?.takeIf { it.isNotBlank() }

/**
 * Resolves `storeFile` to a real file. A relative path is resolved against the repository root,
 * not against `:app`, so `storeFile=bokyqr-release.jks` in `local.properties` means what it says.
 */
val releaseStoreFile: File? = signingValue("BOKYQR_STORE_FILE", "storeFile")
    ?.let { path ->
        val candidate = File(path).takeIf { it.isAbsolute } ?: rootProject.file(path)
        candidate.takeIf { it.isFile && it.canRead() }
    }

val releaseSigningReady = releaseStoreFile != null

android {
    namespace = "rocks.myburgh.bokyqr"
    // Play requires new apps and updates to target API 36 (Android 16).
    compileSdk = 36

    defaultConfig {
        applicationId = "rocks.myburgh.bokyqr"
        // minSdk 26 is unchanged: nothing in this change set needs a newer floor.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("BOKYQR_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("BOKYQR_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("BOKYQR_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    flavorDimensions += "store"
    productFlavors {
        // Bundled (on-device) ML Kit barcode model. Proprietary, and it does need
        // play-services-tasks for the Task API. It also links against the Firebase component
        // SPI and datatransport's in-process plumbing, so only the ClearCut upload backend is
        // excluded; verifyPlayClasspath enforces the exact allow-list below.
        create("play") { dimension = "store" }
        // ZXing only. Must stay free of ML Kit, Play Services and Firebase.
        create("fdroid") { dimension = "store" }
    }

    buildTypes {
        release {
            // Only signed when a keystore was actually found. Unsigned otherwise, deliberately:
            // a locally built unsigned release still exercises R8 and resource shrinking, and
            // Play Console will of course reject an unsigned AAB.
            signingConfig = signingConfigs.findByName("release")
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
    implementation(libs.androidx.compose.material.icons.extended)
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
    // ML Kit's POM (com.google.mlkit:common:18.11.0 and com.google.mlkit:vision-common:17.3.0)
    // declares compile-scope dependencies on com.google.firebase:firebase-components,
    // firebase-encoders, firebase-encoders-json and on com.google.android.datatransport:transport-api
    // and transport-runtime. Those are SPI, not telemetry: ML Kit links against them and a
    // NoClassDefFoundError at BarcodeScanning.getClient() would be a crash the classpath guard
    // could not see. An earlier version of this file excluded com.google.firebase wholesale; that
    // was trading a broken scanner for a clean dependency tree, so it is gone.
    //
    // What is excluded is the one module that can actually move bytes or schedule work:
    // transport-backend-cct is the ClearCut uploader, its AlarmManager/JobScheduler wake-ups and
    // the ACCESS_NETWORK_STATE permission it contributes to the merged manifest. Without a backend
    // registered, transport-runtime has no uploader to hand a payload to, so ML Kit's logging has
    // nowhere to go and is dropped. One targeted module, not a blanket group.
    "playImplementation"(libs.mlkit.barcode.scanning) {
        exclude(group = "com.google.android.datatransport", module = "transport-backend-cct")
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
            for (component in configurations.getByName(configName).incoming.resolutionResult.allComponents) {
                val id = component.moduleVersion ?: continue
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

// The play flavor may carry ML Kit and the SPI it links against, and nothing else from
// com.google.firebase / com.google.android.datatransport / com.google.android.gms. This task is
// an allow-list, not a deny-list: anything new in those three groups is treated as a regression
// until someone writes down why it is SPI rather than telemetry. That is what keeps the guard
// meaningful after the blanket `exclude(group = "com.google.firebase")` was replaced with a
// targeted one — it catches a new datatransport backend, a new Firebase SDK or an analytics
// client appearing in ML Kit's POM, which a "Firebase is absent" check could not.
//
// Do not widen ALLOWED_PLAY_SPI without a comment saying what the module is and why it is not
// telemetry.
val ALLOWED_PLAY_SPI = setOf(
    // Task API only (no Play Services runtime). PlayBarcodeDecoder awaits Tasks.await(...).
    "com.google.android.gms:play-services-tasks",
    "com.google.android.gms:play-services-basement",
    "com.google.android.gms:play-services-base",
    // The thin Play Services barcode-scanning API shim that barcode-scanning itself depends on.
    // It carries the model-invoker plumbing, not a Play Services runtime, and BarcodeScanning
    // .getClient() goes through it.
    "com.google.android.gms:play-services-mlkit-barcode-scanning",
    // Firebase component registry plus the encoders ML Kit serialises its own logging payloads
    // with. ML Kit links against these at runtime; removing them crashes
    // BarcodeScanning.getClient() while every dependency check still passes.
    "com.google.firebase:firebase-components",
    "com.google.firebase:firebase-encoders",
    "com.google.firebase:firebase-encoders-json",
    // Compile-time annotations (@Keep, @Nullable) only. No runtime behaviour, nothing to send.
    "com.google.firebase:firebase-annotations",
    // Datatransport plumbing with no backend registered: in-process only, nothing is uploaded.
    "com.google.android.datatransport:transport-api",
    "com.google.android.datatransport:transport-runtime",
)

val TELEMETRY_GROUPS = listOf(
    "com.google.firebase",
    "com.google.android.datatransport",
    "com.google.android.gms",
)

val verifyPlayClasspath = tasks.register("verifyPlayClasspath") {
    group = "verification"
    description = "Fails if a non-SPI Firebase, datatransport or Play Services module reaches a play classpath."
    val configNames = listOf("playDebugRuntimeClasspath", "playReleaseRuntimeClasspath")
    doLast {
        val offenders = sortedSetOf<String>()
        configNames.forEach { configName ->
            for (component in configurations.getByName(configName).incoming.resolutionResult.allComponents) {
                val id = component.moduleVersion ?: continue
                if (TELEMETRY_GROUPS.none { it == id.group }) continue
                val coordinate = "${id.group}:${id.name}"
                if (coordinate !in ALLOWED_PLAY_SPI) {
                    offenders += "$coordinate:${id.version} (in $configName)"
                }
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "play flavor may only carry ML Kit plus the SPI listed in ALLOWED_PLAY_SPI.\n" +
                    "These look like telemetry and must not ship:\n  " +
                    offenders.joinToString("\n  ") +
                    "\n  Add exclude(group = ..., module = ...) for the offending module on the " +
                    "mlkit-barcode-scanning dependency. If a module is genuinely SPI, add it to " +
                    "ALLOWED_PLAY_SPI with a comment explaining why it is not telemetry.",
            )
        }
    }
}

tasks.matching { it.name == "prePlayDebugBuild" || it.name == "prePlayReleaseBuild" }
    .configureEach { dependsOn(verifyPlayClasspath) }
tasks.named("check") { dependsOn(verifyPlayClasspath) }

// Trusting user-installed CAs makes an APK trivial to intercept and impersonate, which is exactly
// what the debug build needs from a proxy and exactly what must never reach a store. Debug gets
// its own network_security_config.xml under src/debug; the one under src/main is what ships.
//
// This asserts the source-level half of that separation, which is the half that can be checked
// without unpacking and diffing a built artifact: a user anchor in the shipped file is a build
// failure, not a review finding. It is deliberately about src/main only — the debug override is
// supposed to trust user CAs. The packaging half (that the release AAB contains src/main's file
// and not src/debug's) is a packaging concern AGP already owns, since src/debug resources never
// merge into a release variant.
val verifyReleaseTrustAnchors = tasks.register("verifyReleaseTrustAnchors") {
    group = "verification"
    description = "Fails if the shipped network security config trusts user-installed CAs or permits cleartext."
    val shipped = file("src/main/res/xml/network_security_config.xml")
    doLast {
        if (!shipped.isFile) throw GradleException("Missing $shipped; the app has no trust policy to check.")
        val xml = shipped.readText()
        val offenders = buildList {
            if (xml.contains("src=\"user\"")) add("<certificates src=\"user\"> (debug-only trust anchor)")
            if (Regex("""cleartextTrafficPermitted\s*=\s*"true"""").containsMatchIn(xml)) {
                add("cleartextTrafficPermitted=\"true\"")
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "src/main/res/xml/network_security_config.xml must trust system CAs only:\n  " +
                    offenders.joinToString("\n  ") +
                    "\n  Debug builds have their own override in src/debug/res/xml; never widen the " +
                    "one that ships.",
            )
        }
    }
}

tasks.matching { it.name == "preFdroidReleaseBuild" || it.name == "prePlayReleaseBuild" }
    .configureEach { dependsOn(verifyReleaseTrustAnchors) }
tasks.named("check") { dependsOn(verifyReleaseTrustAnchors) }
