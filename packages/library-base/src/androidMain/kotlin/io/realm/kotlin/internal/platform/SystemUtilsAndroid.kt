package io.realm.kotlin.internal.platform

import io.realm.kotlin.internal.interop.SyncConnectionParams
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger

@Suppress("MayBeConst") // Cannot make expect/actual const

public actual val RUNTIME: SyncConnectionParams.Runtime = SyncConnectionParams.Runtime.ANDROID
public actual val RUNTIME_VERSION: String = android.os.Build.VERSION.SDK_INT.toString()
public actual val CPU_ARCH: String
    get() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            @Suppress("DEPRECATION")
            return android.os.Build.CPU_ABI
        } else {
            return android.os.Build.SUPPORTED_ABIS[0]
        }
    }
public actual val OS_NAME: String = "Android"
public actual val OS_VERSION: String = android.os.Build.VERSION.RELEASE
public actual val DEVICE_MANUFACTURER: String = android.os.Build.MANUFACTURER
public actual val DEVICE_MODEL: String = android.os.Build.MODEL

// Returns the root directory of the platform's App data
public actual fun appFilesDirectory(): String = RealmInitializer.filesDir.absolutePath

// Returns the default logger for the platform
public actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    LogCatLogger(tag, logLevel)
