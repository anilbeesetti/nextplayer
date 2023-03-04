plugins {
    id("nextplayer.android.library")
    id("nextplayer.android.hilt")
}

android {
    namespace = "dev.anilbeesetti.nextplayer.core.data"
}

dependencies {

    implementation(project(":core:database"))
    api(project(":core:datastore"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
