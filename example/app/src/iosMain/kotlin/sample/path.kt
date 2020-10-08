package sample

import platform.Foundation.* // ktlint-disable no-wildcard-imports

actual fun path(): kotlin.String =
    NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first().toString()
