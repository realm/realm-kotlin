# Building

Pull the submodule dependencies 
```
git submodule update --init --recursive
```

## iOS

Build the a static library containing Realm with a C wrapper _(only `x86_64` so far)_.
 
```
# compile OS
cd realm-kotlin-mpp/lib/cpp_engine/external/realm-object-store
cmake .
make -j8

# compile the wrapper static library for iOS
cd realm-kotlin-mpp/lib/cpp_engine/build-ios
cmake .
make -j8 realm-objectstore-wrapper
```

## Android 

Build a shared library containing Realm with a JNI layer, ready to be invoked from Kotlin-JVM _(only `x86_64` so far)_.

``` 
# compile the dynamic wrapper library for Android
export ANDROID_NDK=/absolute/path/to/the/android-ndk
cd realm-kotlin-mpp/lib/cpp_engine/build-android
cmake -DCMAKE_TOOLCHAIN_FILE=../android.toolchain.cmake  -DANDROID_ABI="x86_64" -DENABLE_DEBUG_CORE=false .
make -j8 realm-objectstore-wrapper-android-dynamic
cp librealm-objectstore-wrapper-android-dynamic.so ../../src/androidMain/jniLibs/x86_64
```
