buildscript {
    repositories {
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

allprojects {
    repositories {
        google()
        mavenCentral()
        jcenter() // Required by detekt
    }
}
