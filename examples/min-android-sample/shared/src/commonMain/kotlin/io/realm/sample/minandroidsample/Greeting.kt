package io.realm.sample.minandroidsample

class Greeting {
    fun greeting(): String {
        return "Hello, ${Platform().platform}!"
    }
}