package io.realm.kotlin.test.platform

import okio.FileSystem

actual val platformFileSystem: FileSystem = FileSystem.SYSTEM
