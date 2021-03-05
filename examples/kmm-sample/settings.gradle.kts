// If you want to run against the local source repository just include the source projects by
// reincluding the below
includeBuild("../../packages")

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
        // TODO Publish marker artifact to OJO to allow applying plugin by id
        //  https://github.com/realm/realm-kotlin/issues/100
        // maven(url = "http://oss.jfrog.org/artifactory/oss-snapshot-local")
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
