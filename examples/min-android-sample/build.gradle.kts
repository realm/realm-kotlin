// This project can only build against local deployed artifacts
buildscript {
    extra["realmVersion"] = file("${rootProject.rootDir.absolutePath}/../../buildSrc/src/main/kotlin/Config.kt")
        .readLines()
        .first { it.contains("const val version") }
        .let {
            it.substringAfter("\"").substringBefore("\"")
        }

    repositories {
        maven(url = "file://${rootProject.rootDir.absolutePath}/../../packages/build/m2-buildrepo")
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(uri("https://oss.sonatype.org/content/repositories/snapshots"))
    }
    dependencies {
        classpath("com.android.tools.build:gradle:4.0.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")
        classpath("io.realm.kotlin:gradle-plugin:${rootProject.extra["realmVersion"]}")
    }
}

tasks.create("clean", Delete::class) {
    delete.add(rootProject.buildDir)
}

allprojects {
    repositories {
        maven(url = "file://${rootProject.rootDir.absolutePath}/../../packages/build/m2-buildrepo")
        google()
        mavenCentral()
    }
}
