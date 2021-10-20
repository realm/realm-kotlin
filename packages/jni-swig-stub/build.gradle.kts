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

plugins {
    id("java-library")
    id("realm-publisher")
}

val mavenPublicationName = "jniSwigStubs"

java {
    withSourcesJar()
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.create("realmWrapperJvm") {
    doLast {
        // If task is actually triggered (not up to date) then we should clean up the old stuff
        deleteGeneratedFiles()
        exec {
            workingDir(".")
            commandLine("swig", "-java", "-c++", "-package", "io.realm.internal.interop", "-I$projectDir/../external/core/src", "-o", "$projectDir/src/main/jni/realmc.cpp", "-outdir", "$projectDir/src/main/java/io/realm/internal/interop", "realm.i")
        }
    }
    inputs.file("$projectDir/../external/core/src/realm.h")
    inputs.file("realm.i")
    outputs.dir("$projectDir/src/main/java/io/realm/internal/interop")
    outputs.dir("$projectDir/src/main/jni")
}

tasks.named("compileJava") {
    dependsOn("realmWrapperJvm")
}

realmPublish {
    pom {
        name = "JNI Swig Stubs"
        description = "Wrapper for interacting with Realm Kotlin native code from the JVM. This artifact is not " +
            "supposed to be consumed directly, but through " +
            "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        register<MavenPublication>(mavenPublicationName) {
            artifactId = Realm.jniSwigStubsId
            from(components["java"])
        }
    }
}

fun deleteGeneratedFiles() {
    delete(
        fileTree("$projectDir/src/main/java/io/realm/internal/interop/").matching {
            include("*.java")
            exclude("LongPointerWrapper.java") // not generated
        }
    )
    delete(file("$projectDir/src/main/jni/realmc.cpp"))
    delete(file("$projectDir/src/main/jni/realmc.h"))
}

tasks.named("clean") {
    doLast {
        deleteGeneratedFiles()
    }
}
