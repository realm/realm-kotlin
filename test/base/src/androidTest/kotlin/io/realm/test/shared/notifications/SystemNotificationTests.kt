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
package io.realm.test.shared.notifications

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.Sample
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.Utils
import kotlinx.coroutines.runBlocking
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
        configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .path(path = "$tmpDir/default.realm").build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // Sanity check to ensure that this doesn't cause crashes
    @Test
    fun multipleSchedulersOnSameThread() {
        Utils.printlntid("main")
        val baseRealm = Realm.open(configuration) as io.realm.internal.RealmImpl
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
