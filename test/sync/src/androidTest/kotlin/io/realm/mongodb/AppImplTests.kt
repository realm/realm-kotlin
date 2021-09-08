package io.realm.mongodb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class AppImplTests {

    @Test
    fun kajshd() {
        val app = App.create("APP_ID", Dispatchers.IO)
        runBlocking {
            app.login(EmailPassword("asdsa", "aksjdha"))
        }
        val kjahsdk = 0
    }
}
