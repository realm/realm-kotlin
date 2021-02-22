buildscript {
    repositories {
        gradlePluginPortal()
        jcenter()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
        classpath(
            "com.android.tools.build:gradle:${Versions.Android.buildTools}")
    }
}
group = "io.realm.example"
version = Realm.version

// Applying this here causes Gradle to hang, so applying in individual modules
// subprojects {
//    buildscript {
//        repositories {
//            maven(url = "http://oss.jfrog.org/artifactory/oss-snapshot-local")
//        }
//        dependencies {
//            classpath("io.realm.kotlin:gradle-plugin:${Realm.version}")
//        }
//    }
//    repositories {
//        maven(url = "http://oss.jfrog.org/artifactory/oss-snapshot-local")
//    }
// }
repositories {
    mavenCentral()
}
