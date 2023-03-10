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
    }
}
rootProject.name = "NextPlayer"
include(":app")
include(":feature:player")
include(":feature:videopicker")
include(":core:database")
include(":core:data")
include(":core:ui")
include(":core:common")
include(":core:datastore")
include(":core:domain")
include(":libs:ffcodecs")
