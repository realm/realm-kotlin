includeBuild("config")

includeBuild("realm-compiler-plugin") {
//    dependencySubstitution {
//        substitute(module("io.realm:gradle-plugin")).with(project(":gradle-plugin"))
//        substitute(module("io.realm:compiler-plugin")).with(project(":compiler-plugin"))
//        substitute(module("io.realm:compiler-plugin-shaded")).with(project(":compiler-plugin"))
//    }
}

include("lib")

// TODO Remove when real libary build matures
include(":compiler-example")

// TODO Structure consuming example
//includeBuild("example")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:4.0.1")
            }
        }
    }
}
