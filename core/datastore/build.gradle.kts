plugins {
    id("nextplayer.android.library")
    id("nextplayer.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.anilbeesetti.nextplayer.core.datastore"
}

dependencies {

    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.datastore.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
