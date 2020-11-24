import java.nio.file.Files
import java.nio.file.Paths

plugins {
    id("org.jlleitschuh.gradle.ktlint")
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
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    ktlint {
        version.set(Versions.ktlintVersion)
        additionalEditorconfigFile.set(file("$configDir/ktlint/.editorconfig"))
        debug.set(false)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        reporters {
            // Human readable output
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
        }
        ignoreFailures.set(false)
        filter {
            exclude { element ->
                // See https://github.com/JLLeitschuh/ktlint-gradle#faq and https://github.com/gradle/gradle/issues/3417
                element.file.path.contains("generated/")
            }
            include("**/kotlin/**")
        }
    }

    detekt {
        failFast = true // fail build on any finding
        buildUponDefaultConfig = true // preconfigure defaults
        config = files("$configDir/detekt/detekt.yml") // point to your custom config defining rules to run, overwriting default behavior
        baseline = file("$configDir/detekt/baseline.xml") // a way of suppressing issues before introducing detekt
        input = files(
            file("src/androidMain/kotlin"),
            file("src/androidTest/kotlin"),
            file("src/commonMain/kotlin"),
            file("src/commonTest/kotlin"),
            file("src/iosMain/kotlin"),
            file("src/iosTest/kotlin"),
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
