package com.jetbrains.kmm.shared

@Suppress("EmptyDefaultConstructor", "MemberNameEqualsClassName")
expect class Platform() {
    val platform: String
}
