rootProject.name = "realm-kotlin"
include("plugin-gradle")
include("plugin-compiler")
include("plugin-compiler-shaded")
include("library")

pluginManagement {
    plugins {
    }
    repositories {
        gradlePluginPortal()
        google()
    }
    resolutionStrategy {
        eachPlugin {
            if(requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:4.0.1")
            }
        }
    }
}
