plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("realm-publisher")
}

val mavenPublicationName = "compilerPlugin"

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
    // Added to prevent warnings about inconsistent versions
    // w: Runtime JAR files in the classpath should have the same version. These files were found in the classpath:
    // w: Consider providing an explicit dependency on kotlin-reflect 1.4 to prevent strange errors
    implementation(kotlin("reflect"))
    compileOnly(Deps.autoService)
    kapt(Deps.autoServiceAnnotation)

    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${Versions.kotlin}")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlin}")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:${Versions.kotlinCompileTesting}")
    testImplementation(project(":runtime-api"))
    testImplementation(project(":cinterop"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "${Versions.jvmTarget}"
        freeCompilerArgs = listOf("-Xjvm-default=enable")
    }
}

realmPublish {
    pom {
        name = "Compiler Plugin"
        description = "Compiler plugin for JVM based platforms for Realm Kotlin. This artifact is not " +
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
            artifactId = Realm.compilerPluginId
            from(components["java"])
        }
    }
}
