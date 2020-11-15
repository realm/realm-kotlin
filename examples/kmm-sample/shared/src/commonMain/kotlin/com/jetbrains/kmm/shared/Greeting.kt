package com.jetbrains.kmm.shared

class Greeting {
    @Suppress("MemberNameEqualsClassName")
    fun greeting(): String {
        return "Hello, ${Platform().platform}!"
    }
}
