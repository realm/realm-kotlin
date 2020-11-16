import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java`
    id("com.github.johnrengelman.shadow") version Versions.shadowJar
    id("realm-publisher")
}

dependencies {
    implementation(project(":plugin-compiler"))
}

val mavenPublicationName = "compilerPluginShaded"

tasks {
    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("")
        this.destinationDirectory.set(file("$buildDir/libs"))
        relocate("org.jetbrains.kotlin.com.intellij", "com.intellij")
        dependencies {
            exclude {
                it.moduleName != "plugin-compiler"
            }
        }
    }
}
tasks {
    named("jar") {
        actions.clear()
        dependsOn(
            shadowJar
        )
    }
}

realmPublish {
    pom {
        name = "Shaded Compiler Plugin"
        description = "Shaded compiler plugin for native platforms for Realm Kotlin. This artifact is not " +
            "supposed to be consumed directly, but through " +
            "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
    }
    ojo {
        publications = arrayOf(mavenPublicationName)
    }
}

publishing {
    publications {
        register<MavenPublication>(mavenPublicationName) {
            project.shadow.component(this)
            artifactId = Realm.compilerPluginIdNative
        }
    }
}
