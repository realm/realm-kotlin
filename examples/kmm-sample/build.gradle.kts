buildscript {
    repositories {
        gradlePluginPortal()
        jcenter()
        google()
        mavenCentral()
    }
    dependencies {
        // FIXME Fetch this from realm repository
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.20-RC")
        classpath("com.android.tools.build:gradle:4.0.1")
    }
}
group = "com.jetbrains"
version = "1.0-SNAPSHOT"

subprojects {
    repositories {
        mavenLocal()
    }
}
repositories {
    mavenCentral()
}
