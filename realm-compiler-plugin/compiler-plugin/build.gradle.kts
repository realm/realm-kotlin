import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.realm.build.Config.Companion.GROUP
import io.realm.build.Config.Companion.VERSION
import io.realm.build.Dependencies.Companion.AUTO_SERVICE_ANNOTATION
import io.realm.build.Dependencies.Companion.AUTO_SERVICE_COMPILER

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    compileOnly(AUTO_SERVICE_ANNOTATION)
    kapt(AUTO_SERVICE_COMPILER)

    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.4.0")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:1.4.0")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.2.6")

}

tasks {
    named<ShadowJar>("shadowJar") {
        configurations = listOf(project.configurations.compile.get())
        this.archiveClassifier.set("")
        this.destinationDirectory.set(file("$buildDir/shaded"))
        relocate("org.jetbrains.kotlin.com.intellij", "com.intellij")
    }
}

publishing {
    publications {
        register("compilerPlugin", MavenPublication::class) {
            // TODO Has to match GradleSubplugin constants
            groupId = GROUP
            artifactId ="compiler-plugin"
            version = VERSION
            from(components["java"])
        }
        register("compilerPluginShaded", MavenPublication::class) {
            // TODO Has to match GradleSubplugin constants
            groupId = GROUP
            artifactId ="compiler-plugin-shaded"
            version = VERSION
            artifact(tasks["shadowJar"])
        }
    }
}
