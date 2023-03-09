plugins {
    id("nextplayer.android.library")
}

android {
    namespace = "dev.anilbeesetti.libs.ffcodecs"

    defaultConfig {

        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.0.0-rc02")
    implementation("com.google.errorprone:error_prone_annotations:2.18.0")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.annotation:annotation:1.6.0")
    compileOnly("org.checkerframework:checker-qual:3.18.0")
    compileOnly("org.jetbrains.kotlin:kotlin-annotations-jvm:1.8.10")
}