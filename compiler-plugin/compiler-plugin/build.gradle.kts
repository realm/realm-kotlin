import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")

    compileOnly("com.google.auto.service:auto-service-annotations:1.0-rc6")
    kapt("com.google.auto.service:auto-service:1.0-rc6")
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("shadow")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "com.github.csolem.gradle.shadow.kotlin.example.App"))
        }
    }
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
        register("mavenJava", MavenPublication::class) {
            groupId ="io.realm"
            artifactId ="compiler-plugin"
            version ="0.0.1-SNAPSHOT"
            from(components["java"])
        }
        register("mavenJava-shaded", MavenPublication::class) {
            groupId ="io.realm"
            artifactId ="compiler-plugin-shaded"
            version ="0.0.1-SNAPSHOT"
            artifact(tasks["shadowJar"])
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
