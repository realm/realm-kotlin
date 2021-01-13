package io.realm

import java.io.File

// Use standard java.io.File on the JVM
actual class RealmFile(path: String): File(path)