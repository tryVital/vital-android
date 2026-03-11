package io.tryvital.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configureEach
import org.gradle.kotlin.dsl.register

class SamsungHealthLocalRepoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        check(project == project.rootProject) {
            "${this::class.simpleName} must be applied to the root project."
        }

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

        project.gradle.allprojects {
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
        const val TASK_NAME = "publishSamsungHealthAarToLocalMavenRepository"
    }
}
