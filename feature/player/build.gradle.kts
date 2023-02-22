plugins {
    id("nextplayer.android.library")
    id("nextplayer.android.hilt")
}

android {
    namespace = "dev.anilbeesetti.nextplayer.feature.player"

    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewModel.ktx)
    implementation(libs.google.android.material)
    implementation(libs.androidx.constraintlayout)

    // Media3
    implementation(libs.bundles.media3)

    implementation(libs.timber)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
