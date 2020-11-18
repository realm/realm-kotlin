// If you want to run against the local source repository just include the build with
// includeBuild("../../packages")
// FIXME MPP-BUILD How to structure example builds? Should we use local build artifacts
//  (includeBuild) or actually verified published artifacts (mavenLocal). Currently the examples
//  are only statically analyzed on CI but not actually built.
//  Postpone decision until we have a CI setup that can actually build the whole app on one node.
//  For now just compose build to allow applying our 'realm-kotlin' plugin to allow configuring the
//  project for linting purposes. We could use composite build on a single node to just reuse
//  already built packages, but we might want to postpone the building the example projects until
//  after building the actual packages.
if (System.getenv("JENKINS_HOME") != null) {
    includeBuild("../../packages")
}

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
