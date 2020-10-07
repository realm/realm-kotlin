buildscript {
    repositories {
        jcenter()
    }
}

tasks.register("ktlintCheck") {
    description = "Runs ktlintCheck on all projects."
    group = "Verification"
    dependsOn.addAll(listOf("ktlintCheckPackages", "ktlintCheckTest", "ktlintCheckExample"))
}

tasks.register<GradleBuild>("ktlintCheckExample") {
    description = "Run ktlintCheck on /example project"
    buildFile = file("example/build.gradle")
    tasks = listOf("ktlintCheck")
}

tasks.register("ktlintCheckPackages") {
    description = "Run ktlintCheck on /package project"
    dependsOn(gradle.includedBuild("packages").task(":ktlintCheck"))
}

tasks.register("ktlintCheckTest") {
    description = "Run ktlintCheck on /test project"
    dependsOn(gradle.includedBuild("test").task(":ktlintCheck"))
}

tasks.register("ktlintFormat") {
    description = "Runs ktlintFormat on all projects."
    group = "Formatting"
    dependsOn.addAll(listOf("ktlintFormatPackages", "ktlintFormatTest", "ktlintFormatExample"))
}

tasks.register<GradleBuild>("ktlintFormatExample") {
    description = "Run ktlintFormat on /example project"
    buildFile = file("example/build.gradle")
    tasks = listOf("ktlintFormat")
}

tasks.register("ktlintFormatPackages") {
    description = "Run ktlintFormat on /package project"
    dependsOn(gradle.includedBuild("packages").task(":ktlintFormat"))
}

tasks.register("ktlintFormatTest") {
    description = "Run ktlintFormat on /test project"
    dependsOn(gradle.includedBuild("test").task(":ktlintFormat"))
}
