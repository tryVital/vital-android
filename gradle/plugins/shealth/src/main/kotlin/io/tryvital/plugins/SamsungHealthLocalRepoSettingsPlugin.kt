package io.tryvital.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.maven

class SamsungHealthLocalRepoSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val settingsScriptFile =
            settings.rootDir.resolve("settings.gradle").takeIf { it.isFile }
                ?: settings.rootDir.resolve("settings.gradle.kts").takeIf { it.isFile }

        val settingsManagesDependencyRepositories = settingsScriptFile
            ?.takeIf { it.isFile }
            ?.readText()
            ?.contains(Regex("""(?m)^\s*dependencyResolutionManagement\s*\{"""))
            ?: false

        if (settingsManagesDependencyRepositories) {
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

        settings.gradle.beforeProject(object : Action<Project> {
            override fun execute(project: Project) {
                if (project == project.rootProject) {
                    project.extensions.extraProperties.set(
                        SamsungHealthLocalRepoPlugin.SETTINGS_MANAGED_PROPERTY,
                        settingsManagesDependencyRepositories
                    )
                }
            }
        })
    }
}
