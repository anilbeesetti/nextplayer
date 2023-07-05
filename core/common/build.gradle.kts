plugins {
    id("nextplayer.android.library")
    id("nextplayer.android.hilt")
}

android {
    namespace = "dev.anilbeesetti.nextplayer.core.common"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ibm.icu4j)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
