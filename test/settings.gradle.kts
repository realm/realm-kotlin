includeBuild("../realm") {
    dependencySubstitution {
        substitute(module("io.realm:realm-gradle-plugin")).with(project(":realm-gradle"))
        substitute(module("io.realm:realm-compiler-plugin")).with(project(":realm-compiler"))
        substitute(module("io.realm:realm-compiler-plugin-shaded")).with(project(":realm-compiler"))
        substitute(module("io.realm:realm-library")).with(project(":realm-library"))
    }
}
pluginManagement {
    repositories {
        jcenter()
        google()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:4.0.1")
            } else if (requested.id.id == "io.realm.realm-kotlin-plugin") {
                // FIXME We cannot import buildSrc stuff here and seems like we cannot use '+' to force substitution
                useModule("io.realm:realm-gradle-plugin:0.0.1-SNAPSHOT")
            }
        }
    }
}
