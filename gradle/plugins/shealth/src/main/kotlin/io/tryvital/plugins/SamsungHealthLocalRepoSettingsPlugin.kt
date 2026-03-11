package io.tryvital.plugins

import org.gradle.api.Plugin
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.maven

class SamsungHealthLocalRepoSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val repositoryUri = settings.rootDir.resolve("build/${SamsungHealthLocalRepoPlugin.REPOSITORY_DIRECTORY}").toURI()

        val repositories = settings.dependencyResolutionManagement.repositories
        val repositoryAlreadyPresent = repositories.any { repository ->
            repository is MavenArtifactRepository && repository.url == repositoryUri
        }

        if (!repositoryAlreadyPresent) {
            repositories.maven {
                name = "samsungHealthLocalRepository"
                url = repositoryUri
            }
        }
    }
}
