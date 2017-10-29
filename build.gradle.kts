import org.apache.maven.model.Dependency
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.dsl.Coroutines

plugins {
    `maven-publish`
    `java-gradle-plugin`
    `kotlin-dsl`
    kotlin("jvm", version = "1.1.51")
    id("com.gradle.plugin-publish") version "0.9.9"
}

group = "ru.gildor.gradle.firebase.testlab"
version = "0.2.0"
description = "Gradle plugin to run Android instrumentation and robo test on Firebase Test Lab"

repositories {
    jcenter()
    google()
}

dependencies {
    implementation(kotlin("stdlib", version = "1.1.51"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.19.3")
    implementation("com.android.tools.build:gradle:3.0.0")
    implementation("com.google.api-client:google-api-client:1.23.0") {
        exclude(group = "com.google.guava", module = "guava-jdk5")
    }
    implementation("com.google.apis:google-api-services-toolresults:v1beta3-rev284-1.23.0")
    implementation("com.google.apis:google-api-services-storage:v1-rev114-1.23.0")
    implementation(project(":testing-api"))

    testRuntimeOnly("com.android.tools.build:gradle:3.0.0")
    testImplementation("junit:junit:4.12")
}

evaluationDependsOnChildren()

val jar: Jar by tasks
jar.apply {
    subprojects.forEach {
        jar.dependsOn(it.tasks["classes"])
        from(it.java.sourceSets["main"].java.outputDir)
    }
}

gradlePlugin {
    (plugins) {
        "firebaseTestLab" {
            id = project.group as String
            implementationClass = "ru.gildor.gradle.firebase.testlab.FirebaseTestLabPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/gildor/gradlefirebase-test-lab-plugin"
    vcsUrl = "$website.git"
    description = project.description
    tags = listOf("firebase", "test-lab", "android", "espresso", "robo")
    withDependencies(closureOf<Any> {
        println(this)
    })

    (plugins) {
        "firebaseTestLab" {
            id = project.group as String
            displayName = "Gradle Firebase Test Lab plugin"
        }
    }
}

kotlin {
    experimental.coroutines = Coroutines.ENABLE
}

task("wrapper", type = Wrapper::class) {
    group = "build setup"
    gradleVersion = "4.2.1"
    distributionType = Wrapper.DistributionType.ALL
}

apply { from("pom.gradle") }
