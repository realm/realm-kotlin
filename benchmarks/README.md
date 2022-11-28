# Realm Kotlin Benchmarks

This project contains microbenchmarks for the Realm Kotlin SDK. Benchmarks needs to be run on 
each platform individually since tools and capabilities vary greatly between platforms.

Not all platforms are supported yet.

Tooling are not yet in place for analyzing benchmark results. This must currently be done either 
manually or through tools available on each platform. Additional information is found under each
platform.


## Platforms 

### Android

Benchmarks on Android uses [Jetpack Microbenchmarks](https://developer.android.com/studio/profile/microbenchmark-overview).

Running benchmarks:
```
./gradlew androidApp:connectedCheck -e no-isolated-storage true
```

Benchmarks can also be run from within the IDE as a normal Android Integration Test.

According to the [documentation](https://developer.android.com/studio/profile/microbenchmark-write#benchmark-results), 
benchmark data should be downloaded to a JSON file here:
```
/androidApp/build/outputs/connected_android_test_additional_output/releaseAndroidTest/connected/<deviceId>/<appId>-benchmarkData.json
```

However, this does seem to work. Instead benchmark data can be pulled from the device
using this command:
```
adb pull /sdcard/Android/media/io.realm.kotlin.benchmarks.android.test ./benchmark-data/android/
```

Profiling benchmarks can be enabled through gradle settings and trace data data will be pulled
using the above `adb pull` command. See more [here](https://developer.android.com/studio/profile/microbenchmark-profile).

There does not seem to be a open source tools available for analyzing and digging deeper into the 
benchmark results. This must be done manually.

**WARNING:** The Android benchmarks have been configured so they can be run on emulators, but results from
these should generally not be trusted as variance is extremely high. Prefer running on real devices
for more accurate results. Read more [here](https://developer.android.com/studio/profile/microbenchmark-overview#benchmark-consistency). If you are running on an emulator, only emulators on API level 29 and below is working due to restrictions with scoped storage.


### JVM

Benchmarks on JVM uses [Java Microbenchmarking Harness (JMH)](https://github.com/openjdk/jmh).

Benchmarks can only be run from the commandline:
```
./gradlew jvmApp:clean jvmApp:jmh
```

Restricting benchmarks can be done using either the `jmh` closure or through Gradle. It accepts a
regexp pattern for matching
```
./gradlew jvmApp:clean jvmApp:jmh -Pjmh.include="BulkWrite*"
```

Data from the benchmark can be found in:
```
/jvmApp/build/reports/benchmarks.json
```

Note, that if the benchmark file already exists, JMH will exit successfully without running them
again. So either run `./gradlew clean` or delete the file manually before each run.

Analyzing benchmark data can be done using [this website](https://jmh.morethan.io/). It also
supports comparing two different runs.

### iOS
Not supported yet. 

### macOS
Not supported yet.


## Analyzing data

Currently, example benchmark data for each platform is stored in `/benchmark-data`. It only exists
there as a starting point for us to create additional tooling and CI support around it. The results
currently in there are from experimental runs, where no attempts has been made to create a stable
environment.

And as JMH says:

REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
experiments, perform baseline and negative tests that provide experimental control, make sure
the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
Do not assume the numbers tell you what you want them to tell.


