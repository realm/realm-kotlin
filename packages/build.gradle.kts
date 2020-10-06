plugins {
    kotlin("multiplatform") version Versions.kotlin apply false
    kotlin("jvm") version Versions.kotlin apply false
    kotlin("kapt") version Versions.kotlin apply false
    id("org.jlleitschuh.gradle.ktlint") version Versions.ktlintPlugin
    id("com.android.library") apply false
    `java-gradle-plugin`
}

allprojects {
    repositories {
        jcenter()
        maven { url = uri("https://dl.bintray.com/kotlin/kotlin-dev") }
    }

    version = Realm.version
    group = Realm.group

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    ktlint {
        // TODO: Figure out how to extract this so it can be shared between all projects we run ktlint on
        version.set(Versions.ktlintVersion)
        debug.set(false)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        reporters {
            // Human readable output
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
            // Checkstyle compatible output for CI
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        }
        ignoreFailures.set(false)
        filter {
            exclude("**/generated/**")
            include("**/kotlin/**")
        }
    }
}
