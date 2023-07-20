pluginManagement {
    includeBuild("build-logic")
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
 * Use a local copy of nextlib by uncommenting the lines below.
 * Assuming, that nextplayer and nextlib have the same parent directory.
 * If this is not the case, please change the path in includeBuild().
 */

//includeBuild("../nextlib") {
//    dependencySubstitution {
//        substitute(module("com.github.anilbeesetti:nextlib")).using(project(":ffcodecs"))
//    }
//}

rootProject.name = "NextPlayer"
include(":app")
include(":core:common")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:domain")
include(":core:media")
include(":core:ui")
include(":feature:player")
include(":feature:settings")
include(":feature:videopicker")
include(":core:model")
