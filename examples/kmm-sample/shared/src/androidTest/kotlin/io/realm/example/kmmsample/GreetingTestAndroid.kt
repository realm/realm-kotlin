package io.realm.example.kmmsample

import org.junit.Assert
import org.junit.Test

class GreetingTestAndroid {

    @Test
    fun testExample() {
        Assert.assertTrue("Check Android is mentioned", Greeting().greeting().contains("Android"))
    }
}
