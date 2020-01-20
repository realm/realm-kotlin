A POC of a Realm Kotlin Multiplatform library.  

# Building the project

- First [build](./cpp_engine/README.md) the CPP binaries.
- Publish the library locally
```
cd path/to/lib
./gradlew publishToMavenLocal
```
 
# Running Tests:

## Running test on iOS
- Start a `x86_64` simulator _(tested on iPhone 11 Pro Max)_
- Run from <path/to/project/lib> `/gradlew iosTest`

## Running test on Android
- Start an `x86_64` emulator 
- Run from <path/to/project/lib> `/gradlew connectedAndroidTest`

# Example project

A sample project under [example](./example) directory demonstrate how to consume this library in a Multiplatform Kotlin Project targeting iOS and Android.