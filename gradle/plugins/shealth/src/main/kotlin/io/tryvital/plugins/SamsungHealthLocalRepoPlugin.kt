package io.tryvital.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.kotlin.dsl.configureEach
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.register

class SamsungHealthLocalRepoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        check(project == project.rootProject) {
            "${this::class.simpleName} must be applied to the root project."
        }

        val repositoryUri = project.layout.buildDirectory.dir(REPOSITORY_DIRECTORY).get().asFile.toURI()

        val publishTask = project.tasks.register<PublishSamsungHealthAarToMavenRepositoryTask>(TASK_NAME) {
            group = "build setup"
            description = "Publishes $GROUP:$ARTIFACT:$VERSION into the local build Maven repository."
            groupId.set(GROUP)
            artifactId.set(ARTIFACT)
            versionName.set(VERSION)
            aarFile.set(
                project.providers.gradleProperty(AAR_PATH_PROPERTY).map { configuredPath ->
                    project.rootProject.layout.projectDirectory.file(configuredPath)
                },
            )
        }

        val settingsRepositoriesDeclared =
            project.rootProject.extensions.extraProperties.has(SETTINGS_MANAGED_PROPERTY) &&
                project.rootProject.extensions.extraProperties.get(SETTINGS_MANAGED_PROPERTY) == true

        project.gradle.allprojects {
            if (!settingsRepositoriesDeclared) {
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

            configurations.configureEach {
                incoming.beforeResolve {
                    publishTask.get().publishRepository()
                }
            }
        }
    }

    companion object {
        const val GROUP = "com.samsung.health"
        const val ARTIFACT = "samsung-health-data-api"
        const val VERSION = "1.0.0"
        const val AAR_PATH_PROPERTY = "samsungHealthAarPath"
        const val REPOSITORY_DIRECTORY = "local-maven/samsung-health"
        const val SETTINGS_MANAGED_PROPERTY = "samsungHealthManagedBySettings"
        const val TASK_NAME = "publishSamsungHealthAarToLocalMavenRepository"
    }
}
