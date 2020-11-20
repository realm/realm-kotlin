buildscript {
    repositories {
        jcenter()
    }
}

val subprojects = listOf("packages", "test", "examples/kmm-sample")
fun taskName(subdir: String): String {
    return subdir.split("/", "-").map { it.capitalize() }.joinToString(separator = "")
}

tasks.register("ktlintCheck") {
    description = "Runs ktlintCheck on all projects."
    group = "Verification"
    dependsOn(subprojects.map { "ktlintCheck${taskName(it)}" })
}

tasks.register("ktlintFormat") {
    description = "Runs ktlintFormat on all projects."
    group = "Formatting"
    dependsOn(subprojects.map { "ktlintFormat${taskName(it)}" })
}

tasks.register("detekt") {
    description = "Runs detekt on all projects."
    group = "Verification"
    dependsOn(subprojects.map { "detekt${taskName(it)}" })
}

subprojects.forEach { subdir ->
    tasks.register<Exec>("ktlintCheck${taskName(subdir)}") {
        description = "Run ktlintCheck on /$subdir project"
        workingDir = file("${rootDir}/$subdir")
        commandLine = listOf("./gradlew", "ktlintCheck")
    }
    tasks.register<Exec>("ktlintFormat${taskName(subdir)}") {
        description = "Run ktlintFormat on /$subdir project"
        workingDir = file("${rootDir}/$subdir")
        commandLine = listOf("./gradlew", "ktlintFormat")
    }
    tasks.register<Exec>("detekt${taskName(subdir)}") {
        description = "Run detekt on /$subdir project"
        workingDir = file("${rootDir}/$subdir")
        commandLine = listOf("./gradlew", "detekt")
    }
}

tasks.register<Exec>("ojoUpload") {
    description = "Publish all Realm SNAPSHOT artifacts to OJO"
    group = "Publishing"
    workingDir = file("${rootDir}/packages")
    commandLine = listOf("./gradlew", "artifactoryPublish")
}
