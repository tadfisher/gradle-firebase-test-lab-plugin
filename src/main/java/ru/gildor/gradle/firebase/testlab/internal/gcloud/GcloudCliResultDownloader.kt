package ru.gildor.gradle.firebase.testlab.internal.gcloud

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import ru.gildor.gradle.firebase.testlab.internal.utils.command
import ru.gildor.gradle.firebase.testlab.internal.utils.startCommand
import java.io.File

interface TestLabResultDownloader {
    fun downloadResult(result: TestResult)
}

class GcloudCliResultDownloader(
        private val artifacts: List<String>,
        private val destinationDir: File,
        private val gcloudPath: File?,
        private val bucket: String,
        private val logger: Logger?
) : TestLabResultDownloader {
    override fun downloadResult(result: TestResult) {
        if (artifacts.isEmpty()) {
            logger?.lifecycle("Artifact downloading not configured")
            return
        }
        //Read result
        getResultDirs(result).forEach { resultDir ->
            logger?.debug("Downloading artifacts from $resultDir")
            val matrixName = resultDir.split("/").last()
            artifacts.forEach { resource ->
                val destination = "$destinationDir/$matrixName"
                val destFile = File(destination)
                prepareDestination(destFile)
                destFile.mkdir()
                downloadResource("$resultDir$resource", destination)
            }
        }
    }

    private fun prepareDestination(destination: File) {
        logger?.debug("gcloud: Preparing destination dir $destination")
        if (destination.exists()) {
            logger?.debug("gcloud: Destination $destination is exists, delete recursively")
            if (!destination.isDirectory) {
                throw GradleException("Destination path $destination is not directory")
            }
            destination.deleteRecursively()
        }
        destination.mkdir()
        if (!destination.exists()) {
            throw GradleException("Cannot create destination dir $destination")
        }
    }

    private fun downloadResource(source: String, destination: String): Boolean {
        return "${command("gsutil", gcloudPath)} -m cp $source $destination"
                .startCommand()
                .apply {
                    inputStream.bufferedReader().forEachLine { logger?.lifecycle(it) }
                    errorStream.bufferedReader().forEachLine { logger?.lifecycle(it) }
                }
                .waitFor() == 0
    }

    private fun getResultDirs(result: TestResult) =
            "${command("gsutil", gcloudPath)} ls gs://$bucket/${result.resultDir}"
                    .startCommand()
                    .inputStream
                    .bufferedReader()
                    .readLines()
                    .filter { it.endsWith("/") }

}

fun main(args: Array<String>) {
    GcloudCliResultDownloader(
            listOf("logcat", "test_result*xml"),
            File("build/reports/cloudTest").apply { mkdir() },
            File("/Library/google-cloud-sdk/bin/"),
            "android_ci",
            null

    ).downloadResult(TestResult(true, "2016-11-11_16:26:14.673688_pdiU", ""))
}