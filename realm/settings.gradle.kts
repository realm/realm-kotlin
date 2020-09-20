rootProject.name = "realm-kotlin"
include("realm-gradle")
include("realm-compiler")
include("realm-library")

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
