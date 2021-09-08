package io.realm.mongodb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class AppImplTests {

    @Test
    fun kajshd() {
        runBlocking {
            val app =
                App.create(appConfigurationOf("APP_ID", "http://127.0.0.1:9090", Dispatchers.IO))
            app.login(EmailPassword("asdsa", "aksjdha"))
        }
        val kjahsdk = 0
    }
}
