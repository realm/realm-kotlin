plugins {
    kotlin("jvm")
    kotlin("kapt")
    `maven-publish`
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
    compileOnly(Deps.autoService)
    kapt(Deps.autoServiceAnnotation)

    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${Versions.kotlin}")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlin}")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.2.6")
    //
    testImplementation(project(":runtime-api"))
    testImplementation(project(":cinterop"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjvm-default=enable")
    }
}

publishing {
    publications {
        register("compilerPlugin", MavenPublication::class) {
            groupId = Realm.group
            artifactId = Realm.compilerPluginId
            version = Realm.version
            from(components["java"])
        }
    }
}


