rootProject.name = "realm-kotlin"
include("plugin-gradle")
include("plugin-compiler")
include("plugin-compiler-shaded")
include("library")
include("runtime-api")
include("clib-jvm")

pluginManagement {
    plugins {
    }
    repositories {
        gradlePluginPortal()
        google()
        maven { url = uri("https://dl.bintray.com/kotlin/kotlin-dev") }
    }
    resolutionStrategy {
        eachPlugin {
            if(requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:4.0.1")
            }
        }
    }
}
