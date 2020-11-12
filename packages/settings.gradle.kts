rootProject.name = "realm-kotlin"
include("gradle-plugin")
include("plugin-compiler")
include("plugin-compiler-shaded")
include("library")
include("runtime-api")
include(":cinterop")
include(":jni-swig-stub")

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
            if (requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:4.0.1")
            }
        }
    }
}
