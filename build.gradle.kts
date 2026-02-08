import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.aboutLibraries.get().pluginId)

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(false)
    }

    configure<com.mikepenz.aboutlibraries.plugin.AboutLibrariesExtension> {
        export {
            excludeFields.addAll("generated")
        }
    }
}

allprojects {
    tasks.withType<Test>().configureEach {
        testLogging {
            events = setOf(
                TestLogEvent.PASSED,
                TestLogEvent.FAILED,
                TestLogEvent.SKIPPED,
                TestLogEvent.STANDARD_OUT,
                TestLogEvent.STANDARD_ERROR
            )

            exceptionFormat = TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
        ignoreFailures = true
    }
}
