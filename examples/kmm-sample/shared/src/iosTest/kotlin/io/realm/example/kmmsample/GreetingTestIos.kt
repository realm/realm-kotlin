package io.realm.example.kmmsample

import kotlin.test.Test
import kotlin.test.assertTrue

class GreetingTestIos {

    @Test
    fun testExample() {
        assertTrue(Greeting().greeting().contains("iOS"), "Check iOS is mentioned")
    }
}
