plugins {
    id("java-library")
    `maven-publish`
    id("com.jfrog.artifactory")
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

publishing {
    publications {
        register<MavenPublication>(mavenPublicationName) {
            artifactId = Realm.jniSwigStubsId
            from(components["java"])
            pom {
                name.set("JNI Swig Stubs")
                description.set(
                    "Wrapper for interacting with Realm Kotlin native code from the JVM. This artifact is not " +
                        "supposed to be consumed directly, but through " +
                        "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
                )
                url.set(Realm.projectUrl)
                licenses {
                    license {
                        name.set(Realm.License.name)
                        url.set(Realm.License.url)
                    }
                }
                issueManagement {
                    system.set(Realm.IssueManagement.system)
                    url.set(Realm.IssueManagement.url)
                }
                scm {
                    connection.set(Realm.SCM.connection)
                    developerConnection.set(Realm.SCM.developerConnection)
                    url.set(Realm.SCM.url)
                }
            }
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

artifactory {
    setContextUrl("https://oss.jfrog.org/artifactory")
    publish(
        delegateClosureOf<org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig> {
            repository(
                delegateClosureOf<groovy.lang.GroovyObject> {
                    setProperty("repoKey", "oss-snapshot-local")
                    setProperty("username", if (project.hasProperty("bintrayUser")) project.properties["bintrayUser"] else "noUser")
                    setProperty("password", if (project.hasProperty("bintrayKey")) project.properties["bintrayKey"] else "noKey")
                }
            )
            defaults(
                delegateClosureOf<groovy.lang.GroovyObject> {
                    invokeMethod("publications", mavenPublicationName)
                }
            )
        }
    )
}
