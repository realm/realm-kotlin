plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.realm"
version = "0.0.1-SNAPSHOT"

dependencies {
    compileOnly(kotlin("gradle-plugin"))
    kapt("com.google.auto.service:auto-service:1.0-rc6")
}

gradlePlugin {
    plugins {
        create("RealmPlugin") {
            id = "io.realm.realm-compiler-plugin"
            displayName = "Realm compiler plugin"
            implementationClass = "io.realm.gradle.GradleSubplugin"
        }
    }
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            groupId ="io.realm"
            artifactId ="gradle-plugin"
            version ="0.0.1-SNAPSHOT"
            from(components["java"])
        }
    }
}
