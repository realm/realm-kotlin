plugins {
    kotlin("jvm") version "1.4.0"
    kotlin("kapt") version "1.4.0"
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

