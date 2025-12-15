pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}


/**
 * Use the vendored copy of nextlib.
 *
 * This allows us to patch Media3 extensions (ASF/WMV demux + VC-1/WMA decode) without
 * forking/publishing artifacts.
 */
val nextLibDirPath = "third_party/nextlib"
if (File(nextLibDirPath).exists()) {
    includeBuild(nextLibDirPath) {
        dependencySubstitution {
            substitute(module("io.github.anilbeesetti:nextlib-media3ext")).using(project(":media3ext"))
            substitute(module("io.github.anilbeesetti:nextlib-mediainfo")).using(project(":mediainfo"))
        }
    }
}

rootProject.name = "NextPlayer"
include(":app")
include(":core:common")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:domain")
include(":core:media")
include(":core:model")
include(":core:ui")
include(":feature:player")
include(":feature:settings")
include(":feature:videopicker")
