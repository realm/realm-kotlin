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
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("java-library")
    id("realm-publisher")
}

val mavenPublicationName = "jniSwigStubs"

val generatedSourceRoot = "$buildDir/generated/sources"

java {
    withSourcesJar()
    withJavadocJar()
    sourceSets {
        main {
            java.srcDir("$generatedSourceRoot/java")
        }
    }

    sourceCompatibility = Versions.sourceCompatibilityVersion
    targetCompatibility = Versions.targetCompatibilityVersion
}

val realmWrapperJvm: Task = tasks.create("realmWrapperJvm") {
    doLast {
        // If task is actually triggered (not up to date) then we should clean up the old stuff
        delete(fileTree(generatedSourceRoot))
        exec {
            workingDir(".")
            commandLine("swig", "-java", "-c++", "-package", "io.realm.kotlin.internal.interop", "-I$projectDir/../external/core/src", "-o", "$generatedSourceRoot/jni/realmc.cpp", "-outdir", "$generatedSourceRoot/java/io/realm/kotlin/internal/interop", "$projectDir/realm.i")
        }
    }
    inputs.file("$projectDir/../external/core/src/realm.h")
    inputs.file("realm.i")
    inputs.dir("$projectDir/src/main/jni")
    // Specifying full paths triggers creation of dirs, which would otherwise cause swig to fail
    outputs.dir("$generatedSourceRoot/java/io/realm/kotlin/internal/interop")
    outputs.dir("$generatedSourceRoot/jni")
}

tasks.named("javadoc") {
    enabled = false
}

tasks.withType(JavaCompile::class) {
    dependsOn(realmWrapperJvm)
}

tasks.getByName("sourcesJar").dependsOn(realmWrapperJvm)

realmPublish {
    pom {
        name = "JNI Swig Stubs"
        description = "Wrapper for interacting with Realm Kotlin native code from the JVM. This artifact is not " +
            "supposed to be consumed directly, but through " +
            "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
    }
}

publishing {
    publications {
        register<MavenPublication>(mavenPublicationName) {
            artifactId = Realm.jniSwigStubsId
            from(components["java"])
        }
    }
}
