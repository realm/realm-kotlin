package io.realm.sample.minandroidsample

actual class Platform actual constructor() {
    actual val platform: String = "JVM running on: ${System.getProperty("os.name")}"
}