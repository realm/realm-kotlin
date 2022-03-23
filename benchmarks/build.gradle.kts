plugins {
    id("org.jetbrains.kotlin.jvm") apply false
    `java-gradle-plugin`
    id("realm-lint")
}

buildscript {
    extra["ciBuild"] = Realm.ciBuild
    repositories {
        if (extra["ciBuild"] as Boolean) {
            maven("file://${rootProject.rootDir.absolutePath}/../packages/build/m2-buildrepo")
        }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("io.realm.kotlin:gradle-plugin:${Realm.version}")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
        classpath("com.android.tools.build:gradle:${Versions.Android.buildTools}")
        classpath("androidx.benchmark:benchmark-gradle-plugin:${Versions.androidxBenchmarkPlugin}")
    }
}

allprojects {
    repositories {
        if (rootProject.extra.has("ciBuild") &&  rootProject.extra["ciBuild"] as Boolean) {
            maven("file://${rootProject.rootDir.absolutePath}/../packages/build/m2-buildrepo")
        }
        google()
        mavenCentral()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = Versions.jvmTarget
    }
}

//tasks.register("clean", Delete::class) {
//    delete(rootProject.buildDir)
//}
