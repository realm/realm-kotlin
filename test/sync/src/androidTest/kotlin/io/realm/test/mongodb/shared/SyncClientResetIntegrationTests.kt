/*
 * Copyright 2022 Realm Inc.
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

import io.realm.internal.platform.runBlocking
import io.realm.mongodb.User
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.createUserAndLogIn
import io.realm.test.util.TestHelper
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class SyncClientResetIntegrationTests {

    private lateinit var partitionValue: String
    private lateinit var user: User
    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        partitionValue = TestHelper.randomPartitionValue()
        app = TestApp()
        val (email, password) = TestHelper.randomEmail() to "password1234"
        user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun discardUnsyncedLocalChanges_success() = runBlocking {
        // Validate that the discard local strategy onBeforeReset and onAfterReset callbacks
        // are invoked successfully when a client reset is triggered.

        // Test with multiple Realm instances as they need to be updated automatically.


    }

    @Test
    fun discardUnsyncedLocalChanges_failure() = runBlocking {
        // Validate that the discard local strategy onError callback is invoked successfully if
        // a client reset fails.

        // Test with multiple Realm instances as they must be closed manually.
    }

    @Test
    fun discardUnsyncedLocalChanges_success_attemptRecover() = runBlocking {
        // Attempts to recover data if a client reset is triggered.
    }

    @Test
    fun discardUnsyncedLocalChanges_failure_attemptRecover() = runBlocking {
        // Attempts to recover data even if a client reset fails.
    }

    @Test
    fun discardUnsyncedLocalChanges_success_throw_exception() = runBlocking {
        // Captures the behaviour of an exception is thrown within the strategy callbacks
    }

    @Test
    fun discardUnsyncedLocalChanges_failure_throw_exception() = runBlocking {
        // Captures the behaviour of an exception is thrown within the strategy callbacks
    }
}
