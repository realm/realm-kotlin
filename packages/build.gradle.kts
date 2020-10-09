plugins {
    id("realm-lint")
    id("com.android.library") apply false
    `java-gradle-plugin`
}

allprojects {
    repositories {
        jcenter()
        maven { url = uri("https://dl.bintray.com/kotlin/kotlin-dev") }
    }

    version = Realm.version
    group = Realm.group

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}
