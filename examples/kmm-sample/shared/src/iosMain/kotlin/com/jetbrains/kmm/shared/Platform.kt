package com.jetbrains.kmm.shared

import platform.UIKit.UIDevice

@Suppress("EmptyDefaultConstructor", "MemberNameEqualsClassName")
actual class Platform actual constructor() {
    actual val platform: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}
