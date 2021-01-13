package io.realm

// No common wrapper exists for files in Kotlin Common, so we are forced to invent our own.
// Unclear what exactly we need to do here.
expect class RealmFile {
    fun getPath(): String
}