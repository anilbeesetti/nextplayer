import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "dev.anilbeesetti.nextplayer.core.media"
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
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Network protocols + local streaming proxy
    implementation(libs.smbj)
    implementation(libs.commons.net)
    implementation(libs.sshj)
    implementation(libs.bouncycastle.provider)
    implementation(libs.sardine.android) {
        // xpp3/stax bundle org.xmlpull.v1, which conflicts with the classes
        // already provided by the Android platform and breaks R8 minification.
        exclude(group = "xpp3", module = "xpp3")
        exclude(group = "stax", module = "stax")
        exclude(group = "stax", module = "stax-api")
    }
    implementation(libs.androidx.media3.datasource)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
