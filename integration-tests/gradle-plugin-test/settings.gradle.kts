rootProject.name = "gradle-plugin-test"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("file://${rootDir.absolutePath}/../../packages/build/m2-buildrepo")
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("file://${rootDir.absolutePath}/../../packages/build/m2-buildrepo")
    }
}

include(":single-platform")
include("multi-platform")
