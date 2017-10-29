package ru.gildor.gradle.firebase.testlab.internal

import ru.gildor.gradle.firebase.testlab.internal.gcloud.TestResult

interface TestLabResultDownloader {
    fun downloadResult(result: TestResult)
}