package sample

import platform.Foundation.*

actual fun path(): kotlin.String =
    NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first().toString()
