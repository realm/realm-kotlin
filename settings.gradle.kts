rootProject.name = "realm-kotlin-root"

// TODO: Attempt to combine all projects into on project. Right now we only include test and packages to
// be able to call ktlint
// See https://docs.gradle.org/current/userguide/composite_builds.html#defining_composite_builds
// includeBuild("example")
includeBuild("packages")
includeBuild("test")
