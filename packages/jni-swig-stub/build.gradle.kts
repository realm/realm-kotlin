plugins {
    id("java-library")
    `maven-publish`
}

java {
    withSourcesJar()
    dependencies {
        api(project(":runtime-api"))
    }
}
group = Realm.group
version = Realm.version

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

publishing {
    publications {
        create<MavenPublication>("maven") {
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
