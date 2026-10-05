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
val androidSdkPresent: Boolean = run {
    val props = java.util.Properties()
    val localProps = file("local.properties")
    if (localProps.isFile) {
        localProps.inputStream().use { props.load(it) }
    }
    // A candidate only counts as an SDK if its "platforms" directory holds at least one
    // installed platform, so a blank, missing or container-only sdk.dir is ignored and the
    // next candidate (ANDROID_HOME, then ANDROID_SDK_ROOT) is tried instead.
    fun hasInstalledPlatforms(dir: String?): Boolean {
        if (dir.isNullOrBlank()) return false
        val platforms = java.io.File(dir, "platforms")
        return platforms.isDirectory && platforms.listFiles()?.any { it.isDirectory } == true
    }

    val candidates = listOf(
        props.getProperty("sdk.dir"),
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT")
    )
    candidates.any { hasInstalledPlatforms(it) }
}

include(":core")

if (androidSdkPresent) {
    include(":app")
} else {
    logger.lifecycle(
        "BokyQR: no Android SDK with an installed platform found, so :app is not configured " +
            "for this invocation. ':core' is a pure JVM module and still builds. Install the " +
            "SDK and set sdk.dir in local.properties to assemble the app flavors."
    )
}
