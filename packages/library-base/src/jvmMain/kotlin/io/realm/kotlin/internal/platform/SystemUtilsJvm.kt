package io.realm.kotlin.internal.platform

import io.realm.kotlin.Realm
import io.realm.kotlin.internal.interop.SyncConnectionParams
import io.realm.kotlin.internal.util.Exceptions
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import java.io.InputStream
import java.net.URL

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

public actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    StdOutLogger(tag, logLevel)
