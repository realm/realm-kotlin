## 0.2.0 (YYYY-MM-DD)

### Breaking Changes
* The Realm Kotlin Gradle plugin has changed name from `realm-kotlin` to `io.realm.kotlin` to align with Gradle Plugin Portal requirements.

### Enhancements
* The Realm Kotlin Gradle plugin is now available on Gradle Plugin Portal and can be used with the Plugin DSL and `gradlePluginPortal()` as the buildscript repository. A minimal setup of using this approach can be found [here](https://plugins.gradle.org/plugin/io.realm.kotlin).

### Fixed
* None.

### Internal
* None.


## 0.1.0 (2021-05-07)

This is the first public Alpha release of the Realm Kotlin SDK for Android and Kotlin Multiplatform.

A minimal setup for including this library looks like this:

```
// Top-level build.gradle file
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("io.realm.kotlin:gradle-plugin:0.1.0")
    }
}

allprojects {
    repositories {
    	mavenCentral()
    }
}

// Project build.gradle file
// Only include multiplatform if building a multiplatform project.
plugins {
	kotlin("multiplatform")
	id("com.android.library")
	id("realm-kotlin")
}
```

See the [README](https://github.com/realm/realm-kotlin#readme) for more information.

Please report any issues [here](https://github.com/realm/realm-kotlin/issues/new). 
