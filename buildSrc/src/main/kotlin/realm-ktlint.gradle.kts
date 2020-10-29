plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    ktlint {
        version.set(Versions.ktlintVersion)
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
}
