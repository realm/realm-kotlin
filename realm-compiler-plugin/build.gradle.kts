plugins {
    kotlin("jvm") version PluginVersions.kotlin
    kotlin("kapt") version PluginVersions.kotlin
    `java-gradle-plugin`
    id ("io.realm.config")
}

allprojects {
    repositories {
        jcenter()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}

