package io.realm.mongodb

import io.realm.internal.platform.singleThreadDispatcher
import io.realm.mongodb.internal.KtorNetworkTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

const val TEST_APP_1 = "testapp1"       // Id for the default test app
const val TEST_APP_2 = "testapp2"       // ID for the 2nd test app (copy of the first one)
const val BASE_URL = "http://127.0.0.1:9090"

class AppImplTests {

    @ExperimentalCoroutinesApi
    @Test
    fun kajshd() {
        // Send request directly to the local server to get the actual app ID
        val applicationId = KtorNetworkTransport(
            timeoutMs = 5000,
            dispatcher = Dispatchers.Default
        ).sendRequest(
            "get",
            "http://127.0.0.1:8888/$TEST_APP_1",
            mapOf(),
            "",
            true
        ).let { response ->
            when (response.httpResponseCode) {
                200 -> response.body
                else -> throw IllegalStateException(response.toString())
            }
        }

        val app = App.create(appConfigurationOf(applicationId, BASE_URL, Dispatchers.Default))
        runBlocking {
            val b = "hello"
            try {
                // Create user first
                val user: User = app.login(EmailPassword("asdf@asdf.com", "asdfasdf"))
                    .getOrThrow()
                val kajhsdkjh = 0
            } catch (e: Exception) {
                val kjhasd = 0
            }
        }
        val kjahsdk = 0
    }
}
