# Install the Kotlin SDK
The Realm SDK for Kotlin supports the following platforms. Each has its own installation
method and requirements:

- Android
- Kotlin Multiplatform (KMP)

## Prerequisites
Before getting started, ensure your development environment
meets the following prerequisites:

- Minimum versions noted in the source code [README](/README.md)
  for:
  - Android Studio
  - JDK
  - Kotlin Plugin for Android Studio
  - Kotlin Multiplatform Mobile (KMM) Plugin (for KMP mobile projects)
- An Android Virtual Device (AVD) using a supported CPU architecture.

For a list of supported target environments, refer to the Supported Target Environments section on this page.

> **TIP*:** We recommend that you create Kotlin Multiplatform (KMP) for mobile projects using the "Kotlin Multiplatform App"
> template in Android Studio. Follow the instructions in the
> [Kotlin Multiplatform documentation](https://kotlinlang.org/docs/mobile/create-first-app.html).
>

For more details on setting up your KMP environment, refer to the [official Kotlin
Kotlin Multiplatform for mobile](https://kotlinlang.org/docs/multiplatform-mobile-setup.html) documentation. To verify your
environment setup, follow Kotlin's [guide to checking your
environment](https://kotlinlang.org/docs/multiplatform-mobile-setup.html#check-your-environment).

> **Note:**
> The Kotlin Multiplatform (KMP) ecosystem frequently changes. If you experience
> any issues installing the SDK, check your Kotlin Plugin version, since
> outdated plugins can lead to difficult to debug errors. To see which
> versions of the Kotlin Plugin are compatible with the SDK, refer to the
>[SDK changelog](https://github.com/realm/realm-kotlin/blob/master/CHANGELOG.md).
>

## Installation
> **TIP:**
> The SDK uses Realm Core database for device data persistence. When you
> install the Kotlin SDK, the package names reflect Realm naming.
>

### Add the SDK to the Project
#### Android
Add `io.realm.kotlin`, specifying the library version and
apply false, to the list of plugins in your project-level Gradle
build file, typically found at `<project>/build.gradle`:

```gradle
plugins {
   id 'io.realm.kotlin' version '1.16.0' apply false
}
```

Add the following to your app-level Gradle build file, typically
found at `<project>/app/build.gradle`:

- Add `io.realm.kotlin` to the list of plugins.
- Add the following to the list of dependencies:
   - Add `io.realm.kotlin:library-base` to the dependencies block.
   - To use coroutines with the SDK, add `org.jetbrains.kotlinx:kotlinx-coroutines-core` to the list of dependencies.

```gradle
plugins {
   id 'com.android.application'
   id 'org.jetbrains.kotlin.android'
   id 'io.realm.kotlin'
}

android {
   // ... build configuration settings
}

dependencies {
   implementation 'io.realm.kotlin:library-base:1.16.0'
   // If using coroutines with the SDK
   implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0'
}
```

#### KMP

1. Add the following to your app-level Gradle build file, typically
   found at `<project>/app/build.gradle`:
2. Add `io.realm.kotlin` to the
   list of plugins.
3. Add the following to the list of dependencies:
   - Add `io.realm.kotlin:library-base` to the dependencies block.
   - To use coroutines with the SDK, add
     `org.jetbrains.kotlinx:kotlinx-coroutines-core` to the list of
     dependencies

      ```gradle
      plugins {
         kotlin("multiplatform")
         kotlin("native.cocoapods")
         id("com.android.library")
         id("io.realm.kotlin") version "1.16.0"
      }

      version = "1.0"

      kotlin {
         android()
         iosX64()
         iosArm64()

         sourceSets {
            val commonMain by getting {
               dependencies {
                  implementation("io.realm.kotlin:library-base:1.16.0")
                  // If using coroutines with the SDK
                  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0")
               }
            }
         }
      }
      ```
4. If you use any part of the SDK inside the Android module, add the
   following compile-time dependencies to your module-level Gradle
   build file, typically found at `<project>/module/build.gradle`:

   ```gradle
   dependencies {
      compileOnly("io.realm.kotlin:library-base:1.16.0")
   }
   ```

### Sync Gradle Files
After updating the Gradle configuration,
resolve the dependencies by clicking File >
Sync Project with Gradle Files in the Android Studio menu bar.
You can now use the Kotlin SDK in your application.

## Supported Target Environments
Kotlin Multiplatform (KMP) supports a wide range of application environments
Refer also to Kotlin's [Multiplatform Gradle DSL reference: Targets](https://kotlinlang.org/docs/multiplatform-dsl-reference.html#targets) documentation.

### Supported Environments
The Kotlin SDK supports the following environments:

- android
- iosArm64
- iosSimulatorArm64
- iosX64
- jvm
- macosArm64
- macosX64

### Unsupported Environments
The Kotlin SDK does *not* support the following environments:

- androidNativeArm32
- androidNativeArm64
- androidNativeX86
- androidNativeX64
- iosArm32
- js
- linuxArm32Hfp
- linuxArm64
- linuxMips32
- linuxMipsel32
- linuxX64
- mingwX64
- mingwX86
- tvosArm64
- tvosSimulatorArm64
- tvosX64
- wasm32
- watchosArm32
- watchosArm64
- watchosSimulatorArm64
- watchosX86
- watchosX64
