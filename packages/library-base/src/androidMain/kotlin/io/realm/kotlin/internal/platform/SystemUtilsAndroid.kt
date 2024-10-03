package io.realm.kotlin.internal.platform

import android.os.Build
import io.realm.kotlin.internal.RealmInitializer
import io.realm.kotlin.internal.RealmInstantImpl
import io.realm.kotlin.internal.util.Exceptions
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.types.RealmInstant
import java.io.FileNotFoundException
import java.io.InputStream

@Suppress("MayBeConst") // Cannot make expect/actual const

public actual val OS_NAME: String = "Android"

// Returns the root directory of the platform's App data
public actual fun appFilesDirectory(): String = RealmInitializer.filesDir.absolutePath

public actual fun assetFileAsStream(assetFilename: String): InputStream = try {
    RealmInitializer.asset(assetFilename)
} catch (e: FileNotFoundException) {
    throw Exceptions.assetFileNotFound(assetFilename, e)
}

// Returns the default logger for the platform
public actual fun createDefaultSystemLogger(tag: String): RealmLogger =
    LogCatLogger(tag)

public actual fun currentTime(): RealmInstant {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val jtInstant = java.time.Clock.systemUTC().instant()
        RealmInstantImpl(jtInstant.epochSecond, jtInstant.nano)
    } else {
        RealmInstantImpl(System.currentTimeMillis(), 0)
    }
}
