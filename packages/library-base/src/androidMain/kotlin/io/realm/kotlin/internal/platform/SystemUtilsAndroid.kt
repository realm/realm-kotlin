package io.realm.kotlin.internal.platform

import io.realm.kotlin.internal.RealmInitializer
import io.realm.kotlin.internal.interop.SyncConnectionParams
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger

@Suppress("MayBeConst") // Cannot make expect/actual const

internal actual val RUNTIME: SyncConnectionParams.Runtime = SyncConnectionParams.Runtime.ANDROID
internal actual val RUNTIME_VERSION: String = android.os.Build.VERSION.SDK_INT.toString()
internal actual val CPU_ARCH: String =
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
        @Suppress("DEPRECATION")
        android.os.Build.CPU_ABI
    } else {
        android.os.Build.SUPPORTED_ABIS[0]
    }
internal actual val OS_NAME: String = "Android"
internal actual val OS_VERSION: String = android.os.Build.VERSION.RELEASE
internal actual val DEVICE_MANUFACTURER: String = android.os.Build.MANUFACTURER
internal actual val DEVICE_MODEL: String = android.os.Build.MODEL

// Returns the root directory of the platform's App data
internal actual fun appFilesDirectory(): String = RealmInitializer.filesDir.absolutePath

// Returns the default logger for the platform
internal actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    LogCatLogger(tag, logLevel)
