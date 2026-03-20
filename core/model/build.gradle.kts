import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.android.jvm.get()))
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = libs.versions.android.jvm.get()
    targetCompatibility = libs.versions.android.jvm.get()
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
