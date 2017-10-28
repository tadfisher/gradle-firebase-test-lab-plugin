
plugins {
    `maven-publish`
    `java-gradle-plugin`
    `kotlin-dsl`
    kotlin("jvm")
    id("com.gradle.plugin-publish") version "0.9.9"
}

group = "ru.gildor.gradle.firebase.testlab"
version = "0.2.0"
description = "Gradle plugin to run Android instrumentation and robo test on Firebase Test Lab"

repositories {
    jcenter()
    google()
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
    website = "https://github.com/gildor/gradle-firebase-test-lab-plugin"
    vcsUrl = "$website.git"
    description = project.description
    tags = listOf("firebase", "test-lab", "android", "espresso", "robo")

    (plugins) {
        "firebaseTestLab" {
            id = project.group as String
            displayName = "Gradle Firebase Test Lab plugin"
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.android.tools.build:gradle:3.0.0")
    implementation(project(":firebase-testing"))

    testRuntimeOnly("com.android.tools.build:gradle:3.0.0")
    testImplementation("junit:junit:4.12")
}

task("wrapper", type = Wrapper::class) {
    group = "build setup"
    gradleVersion = "4.2.1"
    distributionType = Wrapper.DistributionType.ALL
}