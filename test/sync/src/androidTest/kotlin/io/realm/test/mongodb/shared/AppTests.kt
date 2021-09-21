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
import io.realm.mongodb.EmailPassword
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.asTestApp
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

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

    @Test
    fun login() {
        runBlocking {
            app.login(EmailPassword("asdf@asdf.com", "asdfasdf")).getOrThrow()
        }
    }
}
