pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BokyQR"

// :core is a pure JVM module and is always included. :app needs an Android SDK with at
// least one installed platform, so it is only configured when one is actually available.
// This keeps `./gradlew :core:test` runnable on a machine with no Android SDK at all.
data class SdkCandidate(
    val source: String,
    val path: String?,
    val installedPlatforms: List<String> = emptyList(),
)

fun probeSdk(path: String?): List<String> {
    if (path.isNullOrBlank()) return emptyList()
    val platforms = java.io.File(path, "platforms")
    if (!platforms.isDirectory) return emptyList()
    return (platforms.listFiles() ?: emptyArray())
        .filter { it.isDirectory }
        .map { it.name }
        .sorted()
}

val props = java.util.Properties()
val localProps = file("local.properties")
if (localProps.isFile) {
    localProps.inputStream().use { props.load(it) }
}

val sdkCandidates = listOf(
    SdkCandidate("local.properties sdk.dir", props.getProperty("sdk.dir")),
    SdkCandidate("ANDROID_HOME", System.getenv("ANDROID_HOME")),
    SdkCandidate("ANDROID_SDK_ROOT", System.getenv("ANDROID_SDK_ROOT")),
)
val resolvedSdk = sdkCandidates
    .map { it.copy(installedPlatforms = probeSdk(it.path)) }
    .firstOrNull { it.installedPlatforms.isNotEmpty() }

include(":core")

if (resolvedSdk != null) {
    include(":app")
} else {
    // :app is silently missing from the project here, and every `./gradlew :app:...` invocation
    // then fails with "Project with path ':app' could not be found", which says nothing about the
    // real cause. Print the actual reason and every path that was probed.
    val report = sdkCandidates.joinToString("\n") { candidate ->
        val path = candidate.path
        val why = when {
            path.isNullOrBlank() -> "not set"
            !java.io.File(path).isDirectory -> "directory does not exist"
            else -> "exists, but ${java.io.File(path, "platforms")} holds no installed platform " +
                "(an SDK with no platforms/android-* cannot compile anything)"
        }
        "    - ${candidate.source}: ${path ?: "<unset>"} -> $why"
    }
    logger.lifecycle(
        "\n" +
            "  BokyQR: :app was NOT configured for this invocation.\n" +
            "  Reason: no Android SDK with an installed platform was found.\n\n" +
            "  Paths probed, in order:\n$report\n\n" +
            "  Fix: install an SDK with at least one platforms/android-* directory and either\n" +
            "       set sdk.dir in local.properties or export ANDROID_HOME. Then\n" +
            "       './gradlew :app:assembleFdroidDebug' or ':app:assemblePlayDebug' will work.\n\n" +
            "  ':core' is a pure JVM module and is configured either way: ':core:test' still runs,\n" +
            "  which is why the payload policy can be tested on a machine with no SDK at all.\n"
    )
}
