includeBuild("../config")

// Include if you need to develop the compiler plugin simultaneous with the example in one IDE instance
//includeBuild("../realm-compiler-plugin") {
//    dependencySubstitution {
//        substitute(module("io.realm:gradle-plugin")).with(project(":gradle-plugin"))
//        substitute(module("io.realm:compiler-plugin")).with(project(":compiler-plugin"))
//        substitute(module("io.realm:compiler-plugin-shaded")).with(project(":compiler-plugin"))
//    }
//}

pluginManagement {
    plugins {
        kotlin("multiplatform") version "1.4.0"
        id("io.realm.config")
    }
}
