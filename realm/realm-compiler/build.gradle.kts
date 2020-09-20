import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `maven-publish`
    id("com.github.johnrengelman.shadow") version Versions.shadowJar
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    compileOnly(Deps.autoService)
    kapt(Deps.autoServiceAnnotation)

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
            groupId = Realm.group
            artifactId = Realm.compilerPluginId
            version = Realm.version
            from(components["java"])
        }
        register("compilerPluginShaded", MavenPublication::class) {
            groupId = Realm.group
            artifactId = Realm.compilerPluginIdNative
            version = Realm.version
            artifact(tasks["shadowJar"])
        }
    }
}
