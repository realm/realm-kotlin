buildscript {
    repositories {
        if (Realm.ciBuild) {
            maven("file://${rootProject.rootDir.absolutePath}/../packages/build/m2-buildrepo")
        }
        google()
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
        classpath("com.android.tools.build:gradle:${Versions.Android.buildTools}")
        classpath ("io.realm.kotlin:gradle-plugin:${Realm.version}")
    }
}
group = "io.realm.example"
version = Realm.version

allprojects {
    repositories {
        if (Realm.ciBuild) {
            maven("file://${rootProject.rootDir.absolutePath}/../packages/build/m2-buildrepo")
        }
        google()
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
        jcenter() // Required by detekt
    }
}
