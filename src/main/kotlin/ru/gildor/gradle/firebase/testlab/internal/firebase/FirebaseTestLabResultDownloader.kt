package ru.gildor.gradle.firebase.testlab.internal.firebase

import org.gradle.api.logging.Logger
import ru.gildor.gradle.firebase.testlab.FirebaseTestLabPluginExtension
import ru.gildor.gradle.firebase.testlab.Runner
import ru.gildor.gradle.firebase.testlab.internal.TestLabResultDownloader
import ru.gildor.gradle.firebase.testlab.internal.gcloud.TestResult
import java.io.File

internal class FirebaseTestLabResultDownloader(
        private val config: Runner.Native,
        private val artifacts: List<String>,
        private val destinationDir: File,
        private val logger: Logger?
): TestLabResultDownloader {
    override fun downloadResult(result: TestResult) {
        TODO("not implemented")
    }
}