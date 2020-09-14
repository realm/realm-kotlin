includeBuild("config")

includeBuild("realm-compiler-plugin") {
    // If adding explicit dependency substitution it seems that plugin resolution from local
    // included builds are not considered, in which case we need an explicit resolution strategy
    // for the compiler plugin; see COMPILER-ID-RESULTION in the pluginManagement clause.
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
                // COMPILER-ID-RESOLUTION: When using included build the plugin cannot be resolved
                // from id:
//            } else if (requested.id.id == "io.realm.compiler-plugin") {
//                useModule("io.realm:gradle-plugin:0.0.1-SNAPSHOT")
            }
        }
    }
}
