/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

publishing {
    publications {
        register<MavenPublication>(mavenPublicationName) {
            artifactId = Realm.compilerPluginIdNative
            project.shadow.component(this)
            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))
        }
    }
}
