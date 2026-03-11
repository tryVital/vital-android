plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
}

group = "io.tryvital"
version = "4.3.2"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

java {
    withSourcesJar()
    withJavadocJar()
}

gradlePlugin {
    website = "https://www.junction.com/"
    vcsUrl = "https://github.com/tryVital/vital-android.git"

    plugins {
        create("shealthProjectPlugin") {
            id = "io.tryvital.shealth-project-plugin"
            implementationClass = "io.tryvital.plugins.SamsungHealthLocalRepoPlugin"
            displayName = "shealth-project-plugin"
            description = "Publishes the Samsung Health Data API AAR into a local Maven repository before dependency resolution."
            tags.set(listOf("android", "maven", "aar", "samsung-health"))
        }

        create("shealthSettingsPlugin") {
            id = "io.tryvital.shealth-settings-plugin"
            implementationClass = "io.tryvital.plugins.SamsungHealthLocalRepoSettingsPlugin"
            displayName = "shealth-settings-plugin"
            description = "Adds the Samsung Health local Maven repository to dependencyResolutionManagement without overriding existing settings."
            tags.set(listOf("android", "settings", "maven", "samsung-health"))
        }
    }
}

publishing {
    repositories {
        maven {
            name = "localPluginRepository"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }

    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifactId = "shealth-project-plugin"
        }

        pom {
            packaging = "jar"
            name.set(
                if (this@configureEach.name == "pluginMaven") {
                    "shealth-project-plugin"
                } else {
                    project.name
                }
            )
            description.set("Junction Health Gradle plugin for publishing Samsung Health Data API AARs into a local Maven repository")
            url.set("https://www.junction.com/")
            inceptionYear.set("2023")
            licenses {
                license {
                    name.set("GNU Affero General Public License v3.0")
                    url.set("https://www.gnu.org/licenses/agpl-3.0.html")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("developers@junction.com")
                    name.set("Junction")
                    organization.set("Junction")
                    organizationUrl.set("https://www.junction.com/")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/tryVital/vital-android.git")
                developerConnection.set("scm:git:ssh://github.com/tryVital/vital-android.git")
                url.set("https://github.com/tryVital/vital-android")
            }
        }
    }
}
