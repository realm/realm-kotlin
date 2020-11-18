buildscript {
    repositories {
        gradlePluginPortal()
        jcenter()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
        classpath("com.android.tools.build:gradle:${Versions.Android.buildTools}")
    }
}
group = "io.realm.example"
version = Realm.version

subprojects {
    repositories {
        mavenLocal()
    }
}
repositories {
    mavenCentral()
}
