// If you want to run against the local source repository just include the build with
// includeBuild("../../packages")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        jcenter()
        mavenCentral()
        // FIXME Consider adding OJO repository, but for now just use local builds
        mavenLocal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android" || requested.id.name == "kotlin-android-extensions") {
                useModule("com.android.tools.build:gradle:4.0.1")
            }
        }
    }
}
rootProject.name = "KmmSample"

include(":androidApp")
include(":shared")
