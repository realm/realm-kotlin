plugins {
    kotlin("jvm")
    kotlin("kapt")
    `maven-publish`
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    compileOnly(Deps.autoService)
    kapt(Deps.autoServiceAnnotation)

    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.4.0")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:1.4.0")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.2.6")
    testCompileOnly(project(":runtime-api"))
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


