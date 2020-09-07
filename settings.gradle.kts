// TODO Include library and existing example
//includeBuild("lib")
//includeBuild("example")

// TODO Consider making 'compiler-plugin' a stand-alone'able project by adding a settings.gradle
//  and include it by includeBuild("compiler-plugin") with dependencySubstition
//include("compiler-plugin:gradle-plugin")
//include("compiler-plugin:compiler-plugin")
includeBuild("compiler-plugin") {
    dependencySubstitution {
        substitute(module("io.realm:gradle-plugin")).with(project(":gradle-plugin"))
        substitute(module("io.realm:compiler-plugin")).with(project(":compiler-plugin"))
        substitute(module("io.realm:compiler-plugin-shaded")).with(project(":compiler-plugin"))
    }
}

//includeBuild("compiler-example")
