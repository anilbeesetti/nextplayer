plugins {
    id("nextplayer.jvm.library")
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
