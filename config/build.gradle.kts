plugins {
    kotlin("jvm") version "1.4.0"
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
