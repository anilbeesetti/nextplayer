plugins {
    id("nextplayer.android.library")
    id("nextplayer.android.hilt")
}

android {
    namespace = "dev.anilbeesetti.nextplayer.core.notification"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))

    implementation(libs.coil)
    implementation(libs.coil.ext)

    implementation(libs.bundles.media3)
    implementation(libs.github.anilbeesetti.nextlib.media3ext)
}