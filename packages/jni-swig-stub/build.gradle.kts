plugins {
    id("java-library")
    id("realm-publisher")
}

val mavenPublicationName = "jniSwigStubs"

java {
    withSourcesJar()
    dependencies {
        api(project(":runtime-api"))
    }
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.create("realmWrapperJvm") {
    doLast {
        exec {
            workingDir(".")
            commandLine("swig", "-java", "-c++", "-package", "io.realm.interop", "-I$projectDir/../../external/core/src/realm", "-o", "$projectDir/src/main/jni/realmc.cpp", "-outdir", "$projectDir/src/main/java/io/realm/interop", "realm.i")
        }
    }
    inputs.file("realm.i")
    outputs.dir("$projectDir/src/main/java/io/realm/interop")
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
    ojo {
        publications = arrayOf(mavenPublicationName)
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

tasks.create("cleanJvmWrapper") {
    destroyables.register("$projectDir/src/main/java/io/realm/interop/gen/")
    destroyables.register("$projectDir/src/jvmCommon/jni/realmc.cpp")
    doLast {
        delete(
            fileTree("$projectDir/src/main/java/io/realm/interop/").matching {
                include("*.java")
                exclude("LongPointerWrapper.java") // not generated
            }
        )
        delete("$projectDir/src/main/jni/")
    }
}

tasks.named("clean") {
    dependsOn("cleanJvmWrapper")
}
