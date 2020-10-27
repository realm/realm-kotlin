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
            commandLine("swig", "-java", "-c++", "-package", "io.realm.interop.gen", "-I../../external/core/src/realm", "-o", "../cinterop/src/jvmCommon/jni/realmc.cpp", "-outdir", "src/main/java/io/realm/interop/gen", "realm.i")
        }
    }
    inputs.file("realm.i")
    outputs.dir("src/main/java")
    outputs.dir("../cinterop/src/jvmCommon/jni")
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

// FIXME we need to delete all generated files under gen but keep the .gitkeep file for git
// tasks.create("cleanJvmWrapper") {
//    destroyables.register("$projectDir/src/main/java/io/realm/interop/gen/")
//    destroyables.register("$projectDir/src/jvmCommon/jni/realmc.cpp")
//    doLast {
//        delete("$projectDir/src/main/java/io/realm/interop/gen/")
//        delete("$projectDir/../cinterop/src/jvmCommon/jni/realmc.cpp")
//    }
// }
// tasks.named("clean") {
//    dependsOn("cleanJvmWrapper")
// }
