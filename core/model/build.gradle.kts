import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

tasks.withType<KotlinCompile> {
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget("21"))
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
