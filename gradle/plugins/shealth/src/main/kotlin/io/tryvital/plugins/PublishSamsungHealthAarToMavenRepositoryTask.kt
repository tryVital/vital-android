package io.tryvital.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

abstract class PublishSamsungHealthAarToMavenRepositoryTask @Inject constructor() : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val aarFile: RegularFileProperty

    @get:Input
    abstract val groupId: Property<String>

    @get:Input
    abstract val artifactId: Property<String>

    @get:Input
    abstract val versionName: Property<String>

    @get:Internal
    internal val publishLock = Any()

    @get:Internal
    internal var published = false

    @TaskAction
    fun publishRepository() {
        synchronized(publishLock) {
            if (published) {
                return
            }

            val sourceAar = aarFile.get().asFile
            if (!sourceAar.isFile) {
                throw GradleException("Samsung Health AAR not found at $sourceAar")
            }

            val versionDirectory = versionDirectory()
            versionDirectory.mkdirs()

            val targetAar = versionDirectory.resolve("${artifactId.get()}-${versionName.get()}.aar")
            sourceAar.inputStream().use { input ->
                targetAar.outputStream().use { output -> input.copyTo(output) }
            }

            val pomFile = versionDirectory.resolve("${artifactId.get()}-${versionName.get()}.pom")
            pomFile.writeText(pomContents())

            writeChecksums(targetAar)
            writeChecksums(pomFile)
            writeMetadata()

            published = true
        }
    }

    private fun repoDirectory(): File =
        project.layout.buildDirectory.dir(SamsungHealthLocalRepoPlugin.REPOSITORY_DIRECTORY).get().asFile

    private fun artifactDirectory(): File =
        repoDirectory().resolve(groupId.get().replace('.', File.separatorChar)).resolve(artifactId.get())

    private fun versionDirectory(): File = artifactDirectory().resolve(versionName.get())

    private fun pomContents(): String = """
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>${groupId.get()}</groupId>
          <artifactId>${artifactId.get()}</artifactId>
          <version>${versionName.get()}</version>
          <packaging>aar</packaging>
          <name>${artifactId.get()}</name>
        </project>
    """.trimIndent() + System.lineSeparator()

    private fun writeMetadata() {
        val metadataFile = artifactDirectory().resolve("maven-metadata.xml")
        metadataFile.parentFile.mkdirs()
        metadataFile.writeText(
            """
            <metadata>
              <groupId>${groupId.get()}</groupId>
              <artifactId>${artifactId.get()}</artifactId>
              <versioning>
                <release>${versionName.get()}</release>
                <latest>${versionName.get()}</latest>
                <versions>
                  <version>${versionName.get()}</version>
                </versions>
              </versioning>
            </metadata>
            """.trimIndent() + System.lineSeparator()
        )
        writeChecksums(metadataFile)
    }

    private fun writeChecksums(file: File) {
        mapOf("MD5" to "md5", "SHA-1" to "sha1").forEach { (algorithm, extension) ->
            file.resolveSibling("${file.name}.$extension").writeText(digest(file, algorithm) + System.lineSeparator())
        }
    }

    private fun digest(file: File, algorithm: String): String {
        val messageDigest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) {
                    break
                }
                messageDigest.update(buffer, 0, read)
            }
        }
        return messageDigest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
