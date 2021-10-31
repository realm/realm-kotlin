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
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
    }
}
rootProject.name = "KmmSample"

include(":androidApp")
include(":shared")
include(":compose-desktop")
