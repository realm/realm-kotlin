package io.realm.example.kmmsample

@Suppress("EmptyDefaultConstructor", "MemberNameEqualsClassName")
actual class Platform actual constructor() {
    actual val platform: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}
