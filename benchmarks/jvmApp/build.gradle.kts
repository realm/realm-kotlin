plugins {
    java
    id("me.champeau.jmh") version Versions.jmhPlugin
}
apply(plugin = "kotlin")

dependencies {
    jmh(project(":shared"))
    jmh("io.realm.kotlin:library-base:${Realm.version}")
    jmh("org.openjdk.jmh:jmh-core:${Versions.jmh}")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:${Versions.jmh}")
}

jmh {
    if (extra.has("jmh.include")) {
        includes.add(extra.get("jmh.include") as String)
    }
    resultFormat.set("json")
    resultsFile.set(file("build/reports/benchmarks.json"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}
