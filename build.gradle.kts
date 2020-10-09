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

tasks.register<Exec>("ktlintCheckExample") {
    description = "Run ktlintCheck on /example project"
    workingDir = file("${rootDir}/example")
    commandLine = listOf("./gradlew", "ktlintCheck")
}

tasks.register<Exec>("ktlintCheckPackages") {
    description = "Run ktlintCheck on /packages project"
    workingDir = file("${rootDir}/packages")
    commandLine = listOf("./gradlew", "ktlintCheck")
}

tasks.register<Exec>("ktlintCheckTest") {
    description = "Run ktlintCheck on /test project"
    workingDir = file("${rootDir}/test")
    commandLine = listOf("./gradlew", "ktlintCheck")
}

tasks.register("ktlintFormat") {
    description = "Runs ktlintFormat on all projects."
    group = "Formatting"
    dependsOn.addAll(listOf("ktlintFormatPackages", "ktlintFormatTest", "ktlintFormatExample"))
}

tasks.register<Exec>("ktlintFormatExample") {
    description = "Run ktlintFormat on /example project"
    workingDir = file("${rootDir}/example")
    commandLine = listOf("./gradlew", "ktlintFormat")
}

tasks.register<Exec>("ktlintFormatPackages") {
    description = "Run ktlintFormat on /package project"
    workingDir = file("${rootDir}/packages")
    commandLine = listOf("./gradlew", "ktlintFormat")
}

tasks.register<Exec>("ktlintFormatTest") {
    description = "Run ktlintFormat on /test project"
    workingDir = file("${rootDir}/test")
    commandLine = listOf("./gradlew", "ktlintFormat")
}
