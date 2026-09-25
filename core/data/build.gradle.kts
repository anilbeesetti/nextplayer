import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "dev.anilbeesetti.nextplayer.core.data"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.android.jvm.get().toInt())
        targetCompatibility = JavaVersion.toVersion(libs.versions.android.jvm.get().toInt())
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.android.jvm.get()))
    }
}

dependencies {

    implementation(project(":core:database"))
    implementation(project(":core:media"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:datastore"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.github.anilbeesetti.nextlib.mediainfo)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.datastore.core)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
