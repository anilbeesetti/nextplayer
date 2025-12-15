import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.mavenPublish) apply false
}

subprojects {
    plugins.withId(rootProject.libs.plugins.mavenPublish.get().pluginId) {
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
            signAllPublications()
            coordinates(
                groupId = "io.github.anilbeesetti",
                artifactId = property("POM_ARTIFACT_ID") as String,
                version = "${libs.versions.androidxMedia3.get()}-0.9.0"
            )

            pom {
                name = property("POM_NAME") as String
                description = property("POM_DESCRIPTION") as String
                url = "https://github.com/anilbeesetti/nextlib"

                licenses {
                    license {
                        name = "GNU General Public License v3.0"
                        url = "https://www.gnu.org/licenses/gpl-3.0.html"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "anilbeesetti"
                        name = "Anil Kumar Beesetti"
                    }
                }
                scm {
                    url = "https://github.com/anilbeesetti/nextlib"
                }
            }
        }
    }
}
