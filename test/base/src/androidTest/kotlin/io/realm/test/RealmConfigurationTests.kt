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

package io.realm.test

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.Sample
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.use
import org.junit.After
import org.junit.Before
import org.junit.Test

class RealmConfigurationTests {

    private lateinit var tmpDir: String

    @Before
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
    }

    @After
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    @Suppress("invisible_member")
    fun testDispatcherAsWriteDispatcher() {
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .path("$tmpDir/default.realm")
            .writeDispatcher(singleThreadDispatcher("foo")).build()
        Realm.open(configuration).use { realm: Realm ->
            realm.writeBlocking {
                copyToRealm(Sample())
            }
        }
    }
}
