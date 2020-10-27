plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    ktlint {
        version.set(Versions.ktlintVersion)
        additionalEditorconfigFile.set(file("$rootDir/../config/ktlint/.editorconfig"))
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
        config = files("$rootDir/../config/detekt/detekt.yml") // point to your custom config defining rules to run, overwriting default behavior
        baseline = file("$rootDir/../config/detekt/baseline.xml") // a way of suppressing issues before introducing detekt
        input = files(file("src/androidMain/kotlin"),
                file("src/androidTest/kotlin"),
                file("src/commonMain/kotlin"),
                file("src/commonTest/kotlin"),
                file("src/iosMain/kotlin"),
                file("src/iosTest/kotlin"),
                file("src/jvmMain/kotlin"),
                file("src/main/kotlin"),
                file("src/macosMain/kotlin"),
                file("src/macosTest/kotlin"),
                file("src/test/kotlin"))

        reports {
            html.enabled = true // observe findings in your browser with structure and code snippets
            xml.enabled = false // checkstyle like format mainly for integrations like Jenkins
            txt.enabled = false // similar to the console output, contains issue signature to manually edit baseline files
        }
    }
}
