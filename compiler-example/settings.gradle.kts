//import io.realm.build.Config.Companion.GROUP
includeBuild("../config")

// Include if you need to develop the compiler plugin simultaneous with the example in one IDE instance
//includeBuild("../realm-compiler-plugin") {
//    dependencySubstitution {
//        substitute(module("$GROUP:gradle-plugin")).with(project(":gradle-plugin"))
//        substitute(module("$GROUP:compiler-plugin")).with(project(":compiler-plugin"))
//        substitute(module("$GROUP:compiler-plugin-shaded")).with(project(":compiler-plugin"))
//    }
//}
