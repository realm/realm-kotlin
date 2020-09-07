includeBuild("../compiler-plugin") {
    dependencySubstitution {
        substitute(module("io.realm:gradle-plugin")).with(project(":gradle-plugin"))
        substitute(module("io.realm:compiler-plugin")).with(project(":compiler-plugin"))
        substitute(module("io.realm:compiler-plugin-shaded")).with(project(":compiler-plugin"))
    }
}
