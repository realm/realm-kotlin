/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.nio.file.Files
import java.nio.file.Paths

plugins {
    id("io.gitlab.arturbosch.detekt")
}

val configDir = locateConfigDir(File(File("$rootDir").absolutePath)).path

// Searches upwards in the file tree for a directory containing a 'config'-folder
fun locateConfigDir(current: File): File {
    val configDir = Paths.get(current.path, "config")
    return if (Files.exists(configDir) && File(configDir.toUri()).isDirectory) {
        configDir.toFile()
    } else {
        val parent = current.parentFile ?: error("Couldn't locate config folder upwards in the file tree")
        locateConfigDir(parent)
    }
}

allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    val ktlint by configurations.creating

    dependencies {
        ktlint("com.pinterest:ktlint:${Versions.ktlint}") {
            attributes {
                attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            }
        }
    }

    val outputDir = "${project.buildDir}/reports/ktlint/"
    val inputFiles = project.fileTree(mapOf("dir" to "src", "include" to "**/*.kt"))

    val ktlintCheck by tasks.creating(JavaExec::class) {
        inputs.files(inputFiles)
        outputs.dir(outputDir)

        description = "Check Kotlin code style."
        classpath = ktlint
        main = "com.pinterest.ktlint.Main"
        args = listOf(
            "src/**/*.kt",
            "!src/**/generated/**",
            "!src/**/resources/**",
            "--reporter=plain",
            "--reporter=checkstyle,output=${project.buildDir}/reports/ktlint/ktlint.xml",
            "--editorconfig=${configDir}/ktlint/.editorconfig"
        )
    }

    val ktlintFormat by tasks.creating(JavaExec::class) {
        inputs.files(inputFiles)
        outputs.dir(outputDir)

        description = "Fix Kotlin code style deviations."
        classpath = ktlint
        main = "com.pinterest.ktlint.Main"
        args = listOf(
            "-F",
            "src/**/*.kt",
            "!src/**/resources/**"
        )
    }

    detekt {
        buildUponDefaultConfig = true // preconfigure defaults
        config = files("$configDir/detekt/detekt.yml") // point to your custom config defining rules to run, overwriting default behavior
        baseline = file("$configDir/detekt/baseline.xml") // a way of suppressing issues before introducing detekt
        input = files(
            file("src/androidMain/kotlin"),
            file("src/androidAndroidTest/kotlin"),
            file("src/androidTest/kotlin"),
            file("src/commonMain/kotlin"),
            file("src/commonTest/kotlin"),
            file("src/darwin/kotlin"),
            file("src/ios/kotlin"),
            file("src/iosMain/kotlin"),
            file("src/iosTest/kotlin"),
            file("src/jvm/kotlin"),
            file("src/jvmMain/kotlin"),
            file("src/main/kotlin"),
            file("src/macosMain/kotlin"),
            file("src/macosTest/kotlin"),
            file("src/test/kotlin")
        )

        reports {
            html.enabled = true // observe findings in your browser with structure and code snippets
            xml.enabled = false // checkstyle like format mainly for integrations like Jenkins
            txt.enabled = false // similar to the console output, contains issue signature to manually edit baseline files
        }
    }
}
