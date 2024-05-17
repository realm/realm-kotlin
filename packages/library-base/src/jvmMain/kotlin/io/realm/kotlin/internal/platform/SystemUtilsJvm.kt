package io.realm.kotlin.internal.platform

import io.realm.kotlin.Realm
import io.realm.kotlin.internal.RealmInstantImpl
import io.realm.kotlin.internal.interop.SyncConnectionParams
import io.realm.kotlin.internal.util.Exceptions
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.types.RealmInstant
import java.io.InputStream
import java.net.URL
import java.time.Clock

public actual val RUNTIME: SyncConnectionParams.Runtime = SyncConnectionParams.Runtime.JVM
public actual val RUNTIME_VERSION: String = System.getProperty("java.version")
public actual val CPU_ARCH: String = System.getProperty("os.arch")
public actual val OS_NAME: String = System.getProperty("os.name")
public actual val OS_VERSION: String = System.getProperty("os.version")
public actual val DEVICE_MANUFACTURER: String = ""
public actual val DEVICE_MODEL: String = ""

@Suppress("FunctionOnlyReturningConstant")
public actual fun appFilesDirectory(): String = System.getProperty("user.dir") ?: "."

public actual fun assetFileAsStream(assetFilename: String): InputStream {
    val classLoader = Realm.javaClass.classLoader
    val resource: URL = classLoader.getResource(assetFilename) ?: throw Exceptions.assetFileNotFound(assetFilename)
    return resource.openStream()
}

public actual fun createDefaultSystemLogger(tag: String): RealmLogger =
    StdOutLogger(tag)

/**
 * Since internalNow() should only logically return a value after the Unix epoch, it is safe to create a RealmInstant
 * without considering having to pass negative nanoseconds.
 */
@Suppress("NewApi") // The implementation in SystemUtilsAndroid has a guard to only use systemUTC on API >= 26
public actual fun currentTime(): RealmInstant {
    val jtInstant = Clock.systemUTC().instant()
    return RealmInstantImpl(jtInstant.epochSecond, jtInstant.nano)
}
