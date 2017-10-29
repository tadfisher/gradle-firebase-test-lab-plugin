package ru.gildor.gradle.firebase.testlab.internal.firebase

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.Bucket
import com.google.api.services.toolresults.ToolResults
import com.google.testing.Testing
import com.google.testing.model.AndroidInstrumentationTest
import com.google.testing.model.AndroidMatrix
import com.google.testing.model.AndroidRoboTest
import com.google.testing.model.ClientInfo
import com.google.testing.model.EnvironmentMatrix
import com.google.testing.model.FileReference
import com.google.testing.model.GoogleCloudStorage
import com.google.testing.model.ResultStorage
import com.google.testing.model.TestMatrix
import com.google.testing.model.TestSpecification
import kotlinx.coroutines.experimental.CancellationException
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.NonCancellable
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.run
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import ru.gildor.gradle.firebase.testlab.ApkSource
import ru.gildor.gradle.firebase.testlab.Matrix
import ru.gildor.gradle.firebase.testlab.Runner
import ru.gildor.gradle.firebase.testlab.TestType
import ru.gildor.gradle.firebase.testlab.internal.TestLabRunner
import ru.gildor.gradle.firebase.testlab.internal.gcloud.TestResult
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class FirebaseTestLabRunner(
        val config: Runner.Native,
        val type: TestType,
        val apks: ApkSource,
        val matrix: Matrix,
        val logger: Logger
) : TestLabRunner {

    companion object {
        const val APPLICATION_NAME = "gradle-firebase-test-lab"
    }

    private val httpTransport by lazy { GoogleNetHttpTransport.newTrustedTransport() }
    private val jsonFactory by lazy { JacksonFactory.getDefaultInstance() }

    private val credential by lazy {
        config.credentialPath?.let { path ->
            GoogleCredential.fromStream(FileInputStream(path))
        } ?: GoogleCredential.getApplicationDefault()
    }

    private val storage by lazy {
        Storage.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build()
    }

    private val testing by lazy {
        Testing.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build()
    }

    private val toolResults by lazy {
        ToolResults.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build()
    }

    private val gcsBucketPath = "gs://${config.bucketName}"

    override fun start(): Deferred<TestResult> {
        logger.lifecycle("Creating Cloud Storage bucket ${config.bucketName}")
        createBucket()

        logger.lifecycle("Uploading debug APK...")
        uploadFile(apks.apk)
        if (type == TestType.instrumentation) {
            logger.lifecycle("Uploading test APK...")
            uploadFile(apks.testApk)
        }

        val testMatrix = TestMatrix().apply {
            clientInfo = ClientInfo().apply { name = "Gradle Firebase Test Lab Plugin" }
            testSpecification = when (type) {
                TestType.instrumentation -> buildInstrumentationTest()
                TestType.robo -> buildRoboTest()
            }
            resultStorage = ResultStorage().apply {
                googleCloudStorage = GoogleCloudStorage().apply { gcsPath = gcsBucketPath }
            }
            environmentMatrix = EnvironmentMatrix().apply {
                androidMatrix = matrix.toAndroidMatrix()
            }
        }

        val triggeredTestMatrix = testing.projects().testMatrices()
                .create(config.projectId, testMatrix)
                .execute()

        return asyncPoll(triggeredTestMatrix.projectId, triggeredTestMatrix.testMatrixId)
    }

    fun asyncPoll(projectId: String, matrixId: String) = async {
        try {
            var failureCount = 0
            var done = false
            while (!done) {
                delay(1, TimeUnit.SECONDS)
                try {
                    val matrix = testing.projects().testMatrices().get(projectId, matrixId).execute()
                    if (matrix.state == "FINISHED") break
                    logger.lifecycle("Matrix $matrixId: ${matrix.state}")
                    failureCount = 0
                } catch (e: IOException) {
                    if (++failureCount == 3) {
                        logger.error("Exception while updating results for test matrix $matrixId:\n\n${e.message}")
                        break
                    }
                }
            }
        } catch(e: CancellationException) {
            run(NonCancellable) {
                logger.lifecycle("Cancelling test matrix $matrixId")
                testing.projects().testMatrices().cancel(projectId, matrixId).execute()
            }
        } catch(e: Exception) {
            throw GradleException("Well shit", e)
        }
        TestResult(false, null, "Cancelled")
    }

    private fun createBucket(): Bucket =
        Bucket().setName(config.bucketName).setLocation("US")
                .let { bucket -> storage.buckets().insert(config.projectId, bucket) }
                .execute()

    private fun uploadFile(file: File) =
        InputStreamContent("application/octet-stream", FileInputStream(file))
                .setLength(file.length())
                .let { content -> storage.objects().insert(config.bucketName, null, content) }
                .setName(file.name)
                .execute()

    private fun buildInstrumentationTest(): TestSpecification = TestSpecification().apply {
        androidInstrumentationTest = AndroidInstrumentationTest().apply {
            appApk = apks.apk.fileReference()
            testApk = apks.testApk.fileReference()
            orchestratorOption = if (config.useOrchestrator) "true" else null
        }
    }

    private fun buildRoboTest(): TestSpecification = TestSpecification().apply {
        androidRoboTest = AndroidRoboTest().apply {
            appApk = apks.apk.fileReference()
        }
    }

    private fun File.fileReference(): FileReference =
            FileReference().setGcsPath("$gcsBucketPath/$name")

    private fun Matrix.toAndroidMatrix(): AndroidMatrix = AndroidMatrix()
            .setAndroidModelIds(deviceIds)
            .setAndroidVersionIds(androidApiLevels.map { it.toString() })
            .setLocales(locales)
            .setOrientations(orientations.map { it.name }) // TODO check this
}
