plugins {
    kotlin("jvm") version PluginVersions.kotlin
    `java-gradle-plugin`
}

repositories {
    jcenter()
}

gradlePlugin {
    plugins {
        create("dependencies") {
            id = "io.realm.config"
            // TODO Consider renaming package as /build/ is ignored by git
            implementationClass = "io.realm.build.Config"
        }
    }
}
