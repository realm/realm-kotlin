plugins {
    java
    id("me.champeau.jmh") version "0.6.6"
}
apply(plugin = "kotlin")

dependencies {
    jmh(project(":shared"))
    jmh("io.realm.kotlin:library-sync:${Realm.version}")
    jmh("org.openjdk.jmh:jmh-core:1.33")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.33")
}

jmh {
    resultFormat.set("json")
    resultsFile.set(file("build/reports/benchmarks.json"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}
