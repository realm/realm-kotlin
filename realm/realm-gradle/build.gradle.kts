plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    compileOnly(kotlin("gradle-plugin"))
}

gradlePlugin {
    plugins {
        create("RealmPlugin") {
            id = Realm.pluginId
            displayName = "Realm compiler plugin"
            implementationClass = "io.realm.gradle.RealmPlugin"
        }
    }
}

// TODO If we could find out how to grap gradle plugin component and publish it instead of
//  from(components["java"]) we could be able to combine multiple plugins with different source sets
//  into this module by speficing the gradlePlugin source sets with
//   pluginSourceSet = sourceSets.named("plugin").get()
publishing {
    publications {
        register("gradlePlugin", MavenPublication::class) {
            artifactId = "realm-gradle-plugin"
            from(components["java"])
        }
    }
}

// Make version information available at runtime
val versionDirectory = "$buildDir/generated/source/version/"
sourceSets {
    main {
        java.srcDir(versionDirectory)
    }
}
tasks.create("pluginVersion") {
    val outputDir = file(versionDirectory)

    inputs.property("version", project.version)
    outputs.dir(outputDir)

    doLast {
        val versionFile = file("$outputDir/io/realm/gradle/version.kt")
        versionFile.parentFile.mkdirs()
        versionFile.writeText("""
            // Generated file. Do not edit!
            package io.realm.gradle
            internal const val PLUGIN_VERSION = "${project.version}"
        """.trimIndent()
        )
    }
}
tasks.getByName("compileKotlin").dependsOn("pluginVersion")
