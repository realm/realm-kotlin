import io.realm.build.Config.Companion.COMPILER_PLUGIN
import io.realm.build.Config.Companion.GROUP
import io.realm.build.Config.Companion.VERSION
import io.realm.build.Dependencies

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    compileOnly(kotlin("gradle-plugin"))
    kapt(Dependencies.AUTO_SERVICE_COMPILER)
}

// TODO How to grap gradle plugin component and publish it instead of from(components["java"])
//   pluginSourceSet = sourceSets.named("plugin").get()
gradlePlugin {
    isAutomatedPublishing = false
    plugins {
        create("RealmPlugin") {
            id = COMPILER_PLUGIN
            displayName = "Realm compiler plugin"
            implementationClass = "io.realm.gradle.GradleSubplugin"
        }
    }
}

publishing {
    publications {
        register("gradlePlugin", MavenPublication::class) {
            groupId = GROUP
            artifactId = "gradle-plugin"
            version = VERSION
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
            internal const val PLUGIN_VERSION = "${VERSION}"
        """.trimIndent()
        )
    }
}
tasks.getByName("compileKotlin").dependsOn("pluginVersion")
