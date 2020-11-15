package io.realm.example.kmmsample

class Greeting {
    @Suppress("MemberNameEqualsClassName")
    fun greeting(): String {
        return "Hello, ${Platform().platform}!"
    }
}
