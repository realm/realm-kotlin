plugins {
    id("com.android.library") version "7.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.7.10" apply false
}

// Explicitly adding the plugin to the classpath as it makes it easier to control the version
// centrally (don't need version in the 'plugins' block). Further, snapshots are not published with
// marker interface so would need to be added to the classpath manually anyway.
buildscript {
    dependencies {
        // FIXME How to share version through here ... should we go
        classpath("io.realm.kotlin:gradle-plugin:1.2.0-SNAPSHOT")
    }
}
group = "io.realm.test"
version = "1.0-SNAPSHOT"

// An attempt to make 
tasks.register("integrationTest") {
  dependsOn(":single-platform:connectedDebugAndroidTest")
} 
