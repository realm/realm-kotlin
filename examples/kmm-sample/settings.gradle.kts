// If you want to run against the local source repository just include the source projects by
// reincluding the below
// includeBuild("../../packages")

// Use local sources for CI builds
if (System.getenv("JENKINS_HOME") != null) {
    includeBuild("../../packages")
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        jcenter()
        mavenCentral()
        // FIXME Update sample projects to use public releases/snapshot OJO
        //  https://github.com/realm/realm-kotlin/issues/74
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
