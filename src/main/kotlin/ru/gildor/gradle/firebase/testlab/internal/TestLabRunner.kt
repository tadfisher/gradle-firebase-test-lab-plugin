package ru.gildor.gradle.firebase.testlab.internal

import kotlinx.coroutines.experimental.Deferred
import ru.gildor.gradle.firebase.testlab.internal.gcloud.TestResult

interface TestLabRunner {
    fun start(): Deferred<TestResult>
}