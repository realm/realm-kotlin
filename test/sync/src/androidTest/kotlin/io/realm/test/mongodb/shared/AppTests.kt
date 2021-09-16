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
import io.realm.test.mongodb.admin.ServerAdmin
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test

// Cannot run on CI yet, as it requires sync server to be started with
// tools/sync_test_server/start_server.sh
@Ignore
class AppTests {

    lateinit var app: App
    lateinit var admin: ServerAdmin

    @BeforeTest
    fun setup() {
        app = TestApp()
        admin = ServerAdmin(app)
        admin.createUser("asdf@asdf.com", "asdfasdf")
    }

    @AfterTest
    fun teaddown() {
        admin.deleteAllUsers()
    }

    @Test
    fun login() {
        runBlocking {
            app.login(EmailPassword("asdf@asdf.com", "asdfasdf")).getOrThrow()
        }
    }
}
