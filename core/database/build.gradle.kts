import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.anilbeesetti.nextplayer.core.database"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.android.jvm.get().toInt())
        targetCompatibility = JavaVersion.toVersion(libs.versions.android.jvm.get().toInt())
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.android.jvm.get()))
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
