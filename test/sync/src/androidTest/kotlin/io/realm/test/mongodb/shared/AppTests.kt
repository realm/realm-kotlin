/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.test.mongodb.shared

import io.realm.mongodb.App
import io.realm.mongodb.Credentials
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.asTestApp
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

const val TEST_APP_1 = "testapp1" // Id for the default test app
const val BASE_URL = "http://127.0.0.1:9090"

class AppTests {

    lateinit var app: App

    @BeforeTest
    fun setup() {
        app = TestApp()
        app.asTestApp.createUser("asdf@asdf.com", "asdfasdf")
    }

    @AfterTest
    fun teadDown() {
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
    }

    // TODO Minimal subset of login tests. Migrate AppTest from realm-java, when full API is in
    //  place
    // TODO Exhaustive test on io.realm.mongodb.internal.Provider
    @Ignore // FIXME Tests crashes when doing multiple logins
    @Test
    fun loginAnonymous() {
        runBlocking {
            app.login(Credentials.anynomous()).getOrThrow()
        }
    }

    @Test
    fun loginEmailPassword() {
        runBlocking {
            app.login(Credentials.emailPassword("asdf@asdf.com", "asdfasdf")).getOrThrow()
        }
    }

    @Test
    fun loginNonCredentialImplThrows() {
        runBlocking {
            assertFailsWith<IllegalArgumentException> { app.login(object : Credentials {}) }
        }
    }

    @Ignore // FIXME Tests crashes when doing multiple logins
    @Test
    fun loginInvalidUserThrows() {
        val credentials = Credentials.emailPassword("foo", "bar")
        runBlocking {
            // TODO Should be AppException (ErrorCode.INVALID_EMAIL_PASSWORD, ex.errorCode)
            //  https://github.com/realm/realm-kotlin/issues/426
            assertFailsWith<RuntimeException> {
                app.login(credentials).getOrThrow()
            }
        }
    }
}
