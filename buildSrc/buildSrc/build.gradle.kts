// Work-around for how Gradle initializes projects and buildSrc. Without this, it would not be possible to
// access the classes defined in Constants.kt within the buildSrc project itself.
// See https://github.com/gradle/gradle/issues/11090#issuecomment-674798423 and https://github.com/Kotlin/dokka/pull/1321
plugins {
    `kotlin-dsl`
}
repositories.jcenter()
sourceSets.main {
    java {
        setSrcDirs(setOf(projectDir.parentFile.resolve("src/main/kotlin")))
        include("Config.kt")
    }
}
