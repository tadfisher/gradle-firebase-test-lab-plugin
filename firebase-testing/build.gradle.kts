plugins {
    `java-library`
    id("com.jetbrains.python.envs") version "0.0.19"
}

envs {
    bootstrapDirectory = File(buildDir, "bootstrap")
    envsDirectory = File(buildDir, "envs")
    condaenv("python27", "2.7", listOf("google-apis-client-generator==1.4.3"))
}

java {
    sourceSets {
        "main" {
            java {
                srcDir("$buildDir/generated/src/main/java")
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api("com.google.api-client:google-api-client:1.23.0")
    api("com.google.apis:google-api-services-toolresults:v1beta3-rev284-1.23.0")
    api("com.google.cloud:google-cloud-storage:1.8.0")
}

task("generateLibrary", type = Exec::class) {
    val env = envs.condaEnvs[0]
    commandLine = listOf("${env.envDir}/bin/generate_library",
        "--input=$projectDir/testing_v1.json",
        "--language=java",
        "--output_dir=$buildDir/generated/src/main/java")
}

afterEvaluate {
    tasks {
        "generateLibrary" {
            dependsOn(tasks["build_envs"])
        }
        "compileJava" {
            dependsOn(tasks["generateLibrary"])
        }
    }
}
