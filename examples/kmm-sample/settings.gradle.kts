// For local development, we use composite builds.
// For CI buils, the packages are expected to have
// been built and deployed to a local filesystem
// maven repo.
if (System.getenv("JENKINS_HOME") == null) {
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
