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
    implementation("com.ibm.icu:icu4j:73.2")

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
