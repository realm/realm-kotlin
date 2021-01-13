package io.realm

// No common wrapper exists for files in Kotlin Common, so we are forced to invent our own.
// Is NSURL used for file locations on iOS?
actual class RealmFile(path: String) {
    actual fun getPath(): String {
        TODO("Not yet implemented")
    }
}