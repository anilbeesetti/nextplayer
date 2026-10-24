import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.android.jvm.get()))
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = libs.versions.android.jvm.get()
    targetCompatibility = libs.versions.android.jvm.get()
}

tasks.withType<Test>().configureEach {
    ignoreFailures = false
}

dependencies {
    compileOnly(libs.ktlint.ruleset.core)
    testImplementation(libs.ktlint.rule.engine)
    testImplementation(libs.ktlint.ruleset.standard)
    testImplementation(libs.junit4)
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}
