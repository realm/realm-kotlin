@file:Suppress("invisible_reference", "invisible_member")
/*
 * Copyright 2020 Realm Inc.
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
package io.realm.shared.notifications

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.internal.singleThreadDispatcher
import io.realm.util.PlatformUtils
import io.realm.util.Utils
import kotlinx.coroutines.runBlocking
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * System wide tests that do not fit elsewhere.
 */
class SystemNotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration =
            RealmConfiguration(path = "$tmpDir/default.realm", schema = setOf(Sample::class))
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // Sanity check to ensure that this doesn't cause crashes
    @Test
    // I think there is some kind of resource issue when combining too many realms/schedulers. If
    // this test is enabled execution of all test sometimes fails. Something similarly happens if
    // the public realm_open in Realm.open is extended to take a dispatcher to setup notifications.
    fun multipleSchedulersOnSameThread() {
        Utils.printlntid("main")
        val baseRealm = Realm.open(configuration)
        val dispatcher = singleThreadDispatcher("background")
        val writer1 = io.realm.internal.SuspendableWriter(baseRealm, dispatcher)
        val writer2 = io.realm.internal.SuspendableWriter(baseRealm, dispatcher)
        runBlocking {
            baseRealm.write { copyToRealm(Sample()) }
            writer1.write { copyToRealm(Sample()) }
            writer2.write { copyToRealm(Sample()) }
            baseRealm.write { copyToRealm(Sample()) }
            writer1.write { copyToRealm(Sample()) }
            writer2.write { copyToRealm(Sample()) }
        }
    }
}
