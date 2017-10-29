package ru.gildor.gradle.firebase.testlab

import com.android.build.gradle.api.TestVariant
import groovy.lang.Closure
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import ru.gildor.gradle.firebase.testlab.internal.Artifacts
import ru.gildor.gradle.firebase.testlab.internal.ArtifactsImpl
import ru.gildor.gradle.firebase.testlab.internal.TestLabResultDownloader
import ru.gildor.gradle.firebase.testlab.internal.TestLabRunner
import ru.gildor.gradle.firebase.testlab.internal.firebase.FirebaseTestLabResultDownloader
import ru.gildor.gradle.firebase.testlab.internal.firebase.FirebaseTestLabRunner
import ru.gildor.gradle.firebase.testlab.internal.gcloud.GcloudCliResultDownloader
import ru.gildor.gradle.firebase.testlab.internal.gcloud.GcloudCliRunner
import java.io.File

open class FirebaseTestLabPluginExtension(private val project: Project) {

    var runner: Runner = Runner.Cli(project)
    var ignoreFailures: Boolean = false
    var enableVariantLessTasks = false
    val matrices: NamedDomainObjectContainer<Matrix> = project.container(Matrix::class.java)
    internal val artifacts: Artifacts = ArtifactsImpl()

    fun cli(closure: Closure<Runner.Cli>): Runner.Cli =
            Runner.Cli(project).apply {
                project.configure(this, closure)
            }
    fun cli(configure: Runner.Cli.() -> Unit): Runner.Cli = Runner.Cli(project).apply(configure)

    fun native(closure: Closure<Runner.Native>): Runner.Native =
            Runner.Native(project).apply {
                project.configure(this, closure)
            }
    fun native(configure: Runner.Native.() -> Unit): Runner.Native = Runner.Native(project).apply(configure)

    @Suppress("unused")
    fun copyArtifact(configure: Artifacts.() -> Unit) {
        configure(artifacts)
    }

    @Suppress("unused")
    fun matrices(closure: Closure<Matrix>) {
        matrices.configure(closure)
    }

    @Suppress("unused")
    fun copyArtifact(closure: Closure<Artifacts>) {
        project.configure(artifacts, closure)
    }
}

class Matrix(val name: String) {
    var locales: List<String> = listOf("en")
    var orientations: List<Orientation> = listOf(Orientation.portrait)
    var androidApiLevels: List<Int> = emptyList()
    var deviceIds: List<String> = emptyList()
    var timeoutSec: Long = 0
}

sealed class Runner(project: Project) {
    abstract fun validate()
    abstract fun buildTestLabRunner(
            type: TestType,
            matrix: Matrix,
            apks: ApkSource,
            logger: Logger
    ): TestLabRunner

    abstract fun buildResultDownloader(
            artifacts: List<String>,
            destinationDir: File,
            logger: Logger
    ): TestLabResultDownloader

    val defaultBucketName = "build-${project.name.toLowerCase()}-${System.currentTimeMillis()}"

    class Cli(project: Project) : Runner(project) {
        var gcloudPath: String = ""
        var bucketName: String = defaultBucketName

        override fun validate() {
            if (!File(gcloudPath, "bin/gcloud").canExecute()) {
                throw GradleException("gcloud CLI not found on $gcloudPath/bin. Please specify correct path")
            }
            if (!File(gcloudPath, "bin/gsutil").canExecute()) {
                throw GradleException("gsutil CLI not found on $gcloudPath/bin. Please specify correct path")
            }
        }

        override fun buildTestLabRunner(
                type: TestType,
                matrix: Matrix,
                apks: ApkSource,
                logger: Logger
        ): TestLabRunner = GcloudCliRunner(type, logger, File(gcloudPath), bucketName, matrix, apks)

        override fun buildResultDownloader(
                artifacts: List<String>,
                destinationDir: File,
                logger: Logger
        ): TestLabResultDownloader =
                GcloudCliResultDownloader(artifacts, destinationDir, File(gcloudPath), bucketName, logger)
    }

    class Native(project: Project): Runner(project) {
        var bucketName: String = defaultBucketName
        var credentialPath: String? = null
        var projectId: String? = null
        var useOrchestrator: Boolean = false

        override fun validate() {}

        override fun buildTestLabRunner(
                type: TestType,
                matrix: Matrix,
                apks: ApkSource,
                logger: Logger
        ): TestLabRunner = FirebaseTestLabRunner(this, type, apks, matrix, logger)

        override fun buildResultDownloader(
                artifacts: List<String>,
                destinationDir: File,
                logger: Logger
        ): TestLabResultDownloader =
                FirebaseTestLabResultDownloader(this, artifacts, destinationDir, logger)
    }
}

interface ApkSource {
    val testApk: File
    val apk: File
}

enum class Orientation {
    landscape,
    portrait
}

enum class TestType {
    instrumentation,
    robo
}

class BuildParameterApkSource(private val project: Project) : ApkSource {
    override val testApk: File
        get() = File(project.property("testApk") as String)
    override val apk: File
        get() = File(project.property("apk") as String)

}

internal class VariantApkSource(variant: TestVariant) : ApkSource {
    override val apk: File = variant.testedVariant.outputs.first().outputFile
    override val testApk: File = variant.outputs.first().outputFile
}
