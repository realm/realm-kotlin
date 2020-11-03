plugins {
    kotlin("jvm")
    kotlin("kapt")
    `maven-publish`
    id("com.jfrog.artifactory")
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
    compileOnly(Deps.autoService)
    kapt(Deps.autoServiceAnnotation)

    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${Versions.kotlin}")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlin}")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:${Versions.kotlinCompileTesting}")
    testImplementation(project(":runtime-api"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "${Versions.jvmTarget}"
        freeCompilerArgs = listOf("-Xjvm-default=enable")
    }
}

publishing {
    publications {
        register<MavenPublication>("compilerPlugin") {
            artifactId = Realm.compilerPluginId
            from(components["java"])
            pom {
                name.set("Compiler Plugin")
                description.set(
                    "Compiler plugin for JVM based platforms for Realm Kotlin. This artifact is not " +
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
                    name.set(Realm.IssueManagement.name)
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
                    invokeMethod("publications", "compilerPlugin")
                }
            )
        }
    )
}
