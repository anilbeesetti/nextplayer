plugins {
    id("nextplayer.android.library")
    id("nextplayer.android.library.compose")
}

android {
    namespace = "dev.anilbeesetti.nextplayer.core.ui"
}

dependencies {

    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.bundles.compose)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.github.skydoves.landscapist.glide)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.android.material)

    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.testManifest)
}
