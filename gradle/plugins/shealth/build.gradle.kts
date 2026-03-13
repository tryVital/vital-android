plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
}

import org.gradle.api.JavaVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "io.tryvital"
version = "5.0.0-beta.4"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

gradlePlugin {
    website = "https://www.junction.com/"
    vcsUrl = "https://github.com/tryVital/vital-android.git"

    plugins {
        create("shealthProjectPlugin") {
            id = "io.tryvital.shealth-project-plugin"
            implementationClass = "io.tryvital.plugins.SamsungHealthLocalRepoPlugin"
            displayName = "shealth-project-plugin"
            description = "Publishes the Samsung Health Data SDK AAR into a local Maven repository before dependency resolution."
            tags.set(listOf("android", "maven", "aar", "samsung-health"))
        }

        create("shealthSettingsPlugin") {
            id = "io.tryvital.shealth-settings-plugin"
            implementationClass = "io.tryvital.plugins.SamsungHealthLocalRepoSettingsPlugin"
            displayName = "shealth-settings-plugin"
            description = "Adds the local Maven repository created by io.tryvital.shealth-project-plugin to dependencyResolutionManagement without overriding existing settings."
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
        val isPluginMarkerPublication = this@configureEach.name.endsWith("PluginMarkerMaven")

        if (name == "pluginMaven") {
            artifactId = "shealth-plugins"
        }

        version = "5.0.0-beta.4"

        pom {
            name.set(
                if (this@configureEach.name == "pluginMaven") {
                    "shealth-plugins"
                } else {
                    project.name
                }
            )
            packaging = if (isPluginMarkerPublication) "pom" else "jar"
            description.set("Junction Health Gradle plugins for integrating the Samsung Health Data SDK AAR distribution")
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
